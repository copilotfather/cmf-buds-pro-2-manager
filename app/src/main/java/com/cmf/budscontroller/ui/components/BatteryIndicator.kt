package com.cmf.budscontroller.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.analytics.BatteryPrediction
import com.cmf.budscontroller.protocol.BatteryInfo
import com.cmf.budscontroller.ui.theme.*

@Composable
fun CmfBatteryHero(
    battery: BatteryInfo,
    prediction: BatteryPrediction,
    modifier: Modifier = Modifier
) {
    val vibe = LocalVibeTheme.current
    val dimens = LocalCmfDimens.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(CmfGlassCardBrush)
            .border(0.8.dp, CmfGlassBorderBrush, RoundedCornerShape(dimens.cardCorner))
            .padding(dimens.cardPadding)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Аккумуляторы",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = CmfTextPrimary,
                fontFamily = FontFamily.Default
            )
            Text(
                text = if (battery.isLeftConnected || battery.isRightConnected || battery.leftPercent > 0) "Подключено" else "Ожидание",
                fontSize = 13.sp,
                color = if (battery.leftPercent > 0) CmfGreen else CmfTextSecondary,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Default
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Three Apple-style Battery Cards (Left, Right, Case)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppleBatteryCard(
                title = "Левый",
                percent = battery.leftPercent,
                isCharging = battery.isLeftCharging,
                iconType = "L"
            )
            AppleBatteryCard(
                title = "Правый",
                percent = battery.rightPercent,
                isCharging = battery.isRightCharging,
                iconType = "R"
            )
            AppleBatteryCard(
                title = "Кейс",
                percent = battery.casePercent,
                isCharging = battery.isCaseCharging,
                iconType = "C"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Apple Summary Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CmfSurfaceSubtle)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Осталось: ${prediction.earbudFormattedTime}",
                fontSize = 12.sp,
                color = CmfTextPrimary,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Default
            )
            Text(
                text = "С кейсом: ${prediction.totalWithCaseFormattedTime}",
                fontSize = 12.sp,
                color = CmfTextSecondary,
                fontFamily = FontFamily.Default
            )
        }
    }
}

@Composable
private fun RowScope.AppleBatteryCard(
    title: String,
    percent: Int,
    isCharging: Boolean,
    iconType: String
) {
    val vibe = LocalVibeTheme.current
    val hasCharge = percent in 0..100
    val displayPercent = if (hasCharge) "$percent%" else "—"

    val batteryColor = when {
        isCharging -> CmfGreen
        percent in 0..20 -> CmfRed
        hasCharge -> vibe.accent
        else -> CmfTextSecondary
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(CmfSurfaceSubtle)
            .border(0.6.dp, CmfBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Battery Badge Ring
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(batteryColor.copy(alpha = 0.15f))
                .border(1.5.dp, batteryColor.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isCharging) {
                CmfIconBolt(
                    modifier = Modifier.size(18.dp),
                    tint = CmfGreen
                )
            } else {
                Text(
                    text = iconType,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = batteryColor,
                    fontFamily = FontFamily.Default
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = displayPercent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = CmfTextPrimary,
            fontFamily = FontFamily.Default
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = if (isCharging) "Зарядка" else title,
            fontSize = 11.sp,
            color = if (isCharging) CmfGreen else CmfTextSecondary,
            fontFamily = FontFamily.Default
        )
    }
}
