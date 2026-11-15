package com.cmf.budscontroller.analytics

import com.cmf.budscontroller.protocol.AncMode
import com.cmf.budscontroller.protocol.BatteryInfo
import com.cmf.budscontroller.protocol.DeviceStatus
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class BatteryPrediction(
    val earbudMinutesLeft: Int,
    val earbudFormattedTime: String,
    val totalWithCaseMinutesLeft: Int,
    val totalWithCaseFormattedTime: String,
    val caseRechargesCount: Float,
    val dischargeRatePerHour: Float,
    val powerConsumptionProfile: String
)

object BatteryAnalyticsManager {

    // CMF Buds Pro 2 specs: 60 mAh per earbud, 460 mAh case
    // Base runtimes at 50% volume:
    private const val MAX_HOURS_ANC_OFF = 11.0f
    private const val MAX_HOURS_TRANSPARENCY = 8.0f
    private const val MAX_HOURS_ANC_ON = 6.5f

    private val historySamples = mutableListOf<Pair<Long, Int>>() // Timestamp to min percent

    fun recordSample(battery: BatteryInfo) {
        val validBuds = listOfNotNull(
            if (battery.leftPercent > 0) battery.leftPercent else null,
            if (battery.rightPercent > 0) battery.rightPercent else null
        )
        if (validBuds.isEmpty()) return

        val currentMin = validBuds.minOrNull() ?: return
        val now = System.currentTimeMillis()
        historySamples.add(now to currentMin)

        // Keep last 60 minutes of history
        val cutoff = now - 60 * 60 * 1000
        historySamples.removeAll { it.first < cutoff }
    }

    fun calculatePrediction(status: DeviceStatus): BatteryPrediction {
        val battery = status.battery

        if (!status.isConnected) {
            return BatteryPrediction(
                earbudMinutesLeft = 0,
                earbudFormattedTime = "--",
                totalWithCaseMinutesLeft = 0,
                totalWithCaseFormattedTime = "--",
                caseRechargesCount = 0f,
                dischargeRatePerHour = 0f,
                powerConsumptionProfile = "Наушники не подключены"
            )
        }

        // Determine active bud battery level (handles single earbud in ear)
        val validBuds = listOfNotNull(
            if (battery.leftPercent > 0) battery.leftPercent else null,
            if (battery.rightPercent > 0) battery.rightPercent else null
        )

        val isAnyCharging = battery.isLeftCharging || battery.isRightCharging

        if (isAnyCharging) {
            val curLevel = validBuds.maxOrNull() ?: 0
            val minutesToFull = (((100 - curLevel) / 100f) * 50f).toInt().coerceAtLeast(3)
            val casePct = if (battery.casePercent > 0) battery.casePercent else 0
            val caseRecharges = (casePct / 100f) * 3.1f

            return BatteryPrediction(
                earbudMinutesLeft = minutesToFull,
                earbudFormattedTime = if (curLevel >= 100) "Заряжено" else "~$minutesToFull мин до 100%",
                totalWithCaseMinutesLeft = (caseRecharges * 11f * 60f).toInt(),
                totalWithCaseFormattedTime = formatMinutes((caseRecharges * 11f * 60f).toInt()),
                caseRechargesCount = String.format(Locale.US, "%.1f", caseRecharges).toFloatOrNull() ?: caseRecharges,
                dischargeRatePerHour = 0f,
                powerConsumptionProfile = "Зарядка в кейсе (Fast Charge)"
            )
        }

        if (validBuds.isEmpty()) {
            return BatteryPrediction(
                earbudMinutesLeft = 0,
                earbudFormattedTime = "0 мин",
                totalWithCaseMinutesLeft = 0,
                totalWithCaseFormattedTime = "0 мин",
                caseRechargesCount = 0f,
                dischargeRatePerHour = 0f,
                powerConsumptionProfile = "Наушники разряжены"
            )
        }

        val activeBudPercent = validBuds.minOrNull() ?: 0
        val casePercent = if (battery.casePercent >= 0) battery.casePercent else 0

        // Base drain rate per hour (%)
        var baseHoursFull = when (status.ancMode) {
            AncMode.OFF -> MAX_HOURS_ANC_OFF
            AncMode.TRANSPARENCY -> MAX_HOURS_TRANSPARENCY
            AncMode.NOISE_CANCELLATION -> MAX_HOURS_ANC_ON
        }

        // Adjust for Ultra Bass (level 0..5)
        val bassDrainPenalty = status.ultraBassLevel * 0.03f // up to 15% reduction at level 5
        baseHoursFull *= (1f - bassDrainPenalty)

        // Adjust for Game Mode / Low Latency
        if (status.isGameModeEnabled) {
            baseHoursFull *= 0.90f // 10% reduction
        }

        val theoreticalDrainRate = 100f / baseHoursFull

        // Check if we have empirical real-time drain rate from listening history
        val measuredDrainRate = calculateEmpiricalDrainRate()
        val finalDrainRate = if (measuredDrainRate != null && measuredDrainRate > 4f) {
            // Weighted average: 60% empirical, 40% theoretical
            (measuredDrainRate * 0.6f) + (theoreticalDrainRate * 0.4f)
        } else {
            theoreticalDrainRate
        }

        val remainingHours = activeBudPercent / finalDrainRate
        val earbudMinutes = (remainingHours * 60).toInt()

        // Case recharge multiplier:
        // Case (460 mAh) has ~80% efficiency in charging two 60 mAh buds (120 mAh total)
        // 460 * 0.8 / 120 = ~3.06 full cycles for 100% case
        val caseRecharges = (casePercent / 100f) * 3.1f
        val totalMinutesWithCase = earbudMinutes + (caseRecharges * baseHoursFull * 60).toInt()

        val profileName = when {
            status.isGameModeEnabled -> "Игровой (Повышенный расход)"
            status.ancMode == AncMode.NOISE_CANCELLATION -> "Шумоподавление (${status.ancLevel.title})"
            status.ancMode == AncMode.TRANSPARENCY -> "Прозрачность"
            else -> "Энергосберегающий (ANC Выкл)"
        }

        return BatteryPrediction(
            earbudMinutesLeft = earbudMinutes,
            earbudFormattedTime = formatMinutes(earbudMinutes),
            totalWithCaseMinutesLeft = totalMinutesWithCase,
            totalWithCaseFormattedTime = formatMinutes(totalMinutesWithCase),
            caseRechargesCount = String.format(Locale.US, "%.1f", caseRecharges).toFloatOrNull() ?: caseRecharges,
            dischargeRatePerHour = String.format(Locale.US, "%.1f", finalDrainRate).toFloatOrNull() ?: finalDrainRate,
            powerConsumptionProfile = profileName
        )
    }

    private fun calculateEmpiricalDrainRate(): Float? {
        if (historySamples.size < 2) return null
        val first = historySamples.first()
        val last = historySamples.last()
        val timeDiffHours = (last.first - first.first) / (1000f * 60f * 60f)
        val percentDiff = (first.second - last.second).toFloat()

        if (timeDiffHours < 0.15f || percentDiff <= 0f) return null // Need at least ~10 mins
        return percentDiff / timeDiffHours
    }

    private fun formatMinutes(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}ч ${mins}мин"
            hours > 0 -> "${hours}ч"
            else -> "${mins}мин"
        }
    }
}
