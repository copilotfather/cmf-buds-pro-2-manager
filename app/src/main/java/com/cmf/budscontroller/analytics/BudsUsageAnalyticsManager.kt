package com.cmf.budscontroller.analytics

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import com.cmf.budscontroller.protocol.BatteryInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class VolumeTimeStats(
    val lowVolumeMinutes: Int = 0,    // 0 - 30%
    val midVolumeMinutes: Int = 0,    // 31 - 70%
    val highVolumeMinutes: Int = 0    // 71 - 100%
) {
    val totalMinutes: Int get() = lowVolumeMinutes + midVolumeMinutes + highVolumeMinutes
}

data class DayActivity(
    val dayName: String,
    val minutes: Int
)

data class BudsUsageInsights(
    val volumeStats: VolumeTimeStats = VolumeTimeStats(lowVolumeMinutes = 0, midVolumeMinutes = 0, highVolumeMinutes = 0),
    val currentVolumePercent: Int = 50,
    val weeklyActivity: List<DayActivity> = emptyList(),
    val caseOpensToday: Int = 0,
    val totalCaseOpens: Int = 0,
    val averageFullBatteryHours: Float = 0f,
    val batteryHealthEstimate: Int = 100,
    val favoriteHour: String = "--:--",
    val mostActiveDay: String = "Нет данных"
)

class BudsUsageAnalyticsManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("cmf_deep_analytics", Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _insights = MutableStateFlow(loadPersistedInsights())
    val insights: StateFlow<BudsUsageInsights> = _insights.asStateFlow()

    private var previousCaseConnected = false

    fun startTracking(isConnectedProvider: () -> Boolean, batteryProvider: () -> BatteryInfo) {
        scope.launch {
            while (isActive) {
                delay(30_000) // каждые 30 секунд обновляем аналитику
                if (isConnectedProvider()) {
                    recordActiveSession(batteryProvider())
                }
            }
        }
    }

    fun recordCaseEvent(isOpened: Boolean) {
        if (!isOpened) return

        val todayKey = getTodayDateKey()
        val currentOpensToday = prefs.getInt("opens_$todayKey", 0) + 1
        val totalOpens = prefs.getInt("total_opens", 0) + 1

        prefs.edit()
            .putInt("opens_$todayKey", currentOpensToday)
            .putInt("total_opens", totalOpens)
            .apply()

        _insights.value = _insights.value.copy(
            caseOpensToday = currentOpensToday,
            totalCaseOpens = totalOpens
        )
    }

    private fun recordActiveSession(battery: BatteryInfo) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volPercent = ((curVol.toFloat() / maxVol) * 100).toInt()

        val todayKey = getTodayDateKey()
        var low = prefs.getInt("vol_low", 0)
        var mid = prefs.getInt("vol_mid", 0)
        var high = prefs.getInt("vol_high", 0)

        when {
            volPercent <= 35 -> low += 1
            volPercent <= 70 -> mid += 1
            else -> high += 1
        }

        val todayMin = prefs.getInt("day_min_$todayKey", 0) + 1

        // Фиксируем активный час суток (0..23)
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hourCount = prefs.getInt("hour_stat_$currentHour", 0) + 1

        prefs.edit()
            .putInt("vol_low", low)
            .putInt("vol_mid", mid)
            .putInt("vol_high", high)
            .putInt("day_min_$todayKey", todayMin)
            .putInt("hour_stat_$currentHour", hourCount)
            .apply()

        // Проверяем кейс: если кейс появился в статусе с процентом, фиксируем открытие
        if (battery.casePercent > 0 && !previousCaseConnected) {
            recordCaseEvent(true)
        }
        previousCaseConnected = battery.casePercent > 0

        _insights.value = buildInsightsFromData(
            low = low,
            mid = mid,
            high = high,
            todayOpens = prefs.getInt("opens_$todayKey", 0),
            totalOpens = prefs.getInt("total_opens", 0),
            currentVol = volPercent
        )
    }

    private fun calculateWeeklyActivity(): List<DayActivity> {
        val days = listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
        val result = mutableListOf<DayActivity>()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        // Берем последние 7 дней (только реальные накопленные минуты)
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val key = sdf.format(cal.time)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
            val min = prefs.getInt("day_min_$key", 0)
            result.add(DayActivity(days[dayOfWeek.coerceIn(0, 6)], min))
        }
        return result
    }

    private fun getTodayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun buildInsightsFromData(
        low: Int,
        mid: Int,
        high: Int,
        todayOpens: Int,
        totalOpens: Int,
        currentVol: Int
    ): BudsUsageInsights {
        val weekly = calculateWeeklyActivity()
        val totalActiveMins = weekly.sumOf { it.minutes }
        val maxDay = weekly.maxByOrNull { it.minutes }

        val mostActiveDay = if (maxDay != null && maxDay.minutes > 0) {
            when (maxDay.dayName) {
                "Пн" -> "Понедельник"
                "Вт" -> "Вторник"
                "Ср" -> "Среда"
                "Чт" -> "Четверг"
                "Пт" -> "Пятница"
                "Сб" -> "Суббота"
                "Вс" -> "Воскресенье"
                else -> maxDay.dayName
            }
        } else {
            "Нет сессий"
        }

        // Поиск любимого часа прослушивания
        var topHour = -1
        var topHourCount = 0
        for (h in 0..23) {
            val cnt = prefs.getInt("hour_stat_$h", 0)
            if (cnt > topHourCount) {
                topHourCount = cnt
                topHour = h
            }
        }

        val favoriteHourText = if (topHour >= 0) {
            String.format(Locale.getDefault(), "%02d:00 - %02d:00", topHour, (topHour + 1) % 24)
        } else {
            "Пока не определен"
        }

        // Автономность рассчитывается на основе реального расхода (база CMF Buds Pro 2: 7.5 часов на одном заряде)
        val avgHours = if (totalActiveMins > 0) {
            val highVolRatio = high.toFloat() / (low + mid + high).coerceAtLeast(1)
            // При громкости >70% автономность падает с 7.5 до 5.8 часов
            val calculated = 7.5f - (highVolRatio * 1.7f)
            String.format(Locale.US, "%.1f", calculated).toFloatOrNull() ?: 7.5f
        } else {
            7.5f
        }

        // Износ батареи: 1 полный цикл разряда (500 минут работы) = -0.05%
        val cycles = totalActiveMins / 500f
        val health = (100 - (cycles * 0.05f).toInt()).coerceIn(80, 100)

        return BudsUsageInsights(
            volumeStats = VolumeTimeStats(low, mid, high),
            currentVolumePercent = currentVol,
            weeklyActivity = weekly,
            caseOpensToday = todayOpens,
            totalCaseOpens = totalOpens,
            averageFullBatteryHours = avgHours,
            batteryHealthEstimate = health,
            favoriteHour = favoriteHourText,
            mostActiveDay = mostActiveDay
        )
    }

    private fun loadPersistedInsights(): BudsUsageInsights {
        val todayKey = getTodayDateKey()
        val low = prefs.getInt("vol_low", 0)
        val mid = prefs.getInt("vol_mid", 0)
        val high = prefs.getInt("vol_high", 0)
        val opensToday = prefs.getInt("opens_$todayKey", 0)
        val totalOpens = prefs.getInt("total_opens", 0)

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volPercent = ((curVol.toFloat() / maxVol) * 100).toInt()

        return buildInsightsFromData(
            low = low,
            mid = mid,
            high = high,
            todayOpens = opensToday,
            totalOpens = totalOpens,
            currentVol = volPercent
        )
    }
}
