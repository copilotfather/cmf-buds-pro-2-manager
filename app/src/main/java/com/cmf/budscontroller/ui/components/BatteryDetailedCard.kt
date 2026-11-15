package com.cmf.budscontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.analytics.BatteryPrediction
import com.cmf.budscontroller.ui.theme.*

@Composable
fun BatteryDetailedCard(
    prediction: BatteryPrediction,
    modifier: Modifier = Modifier
) {
    val vibe = LocalVibeTheme.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CmfSurface)
            .border(1.dp, CmfBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ПРОГНОЗ РАБОТЫ АККУМУЛЯТОРА",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = CmfTextSecondary,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Default
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "Осталось слушать:",
                    fontSize = 12.sp,
                    color = CmfTextSecondary
                )
                Text(
                    text = prediction.earbudFormattedTime,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = vibe.accent,
                    fontFamily = FontFamily.Default
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "С кейсом:",
                    fontSize = 12.sp,
                    color = CmfTextSecondary
                )
                Text(
                    text = prediction.totalWithCaseFormattedTime,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = CmfTextPrimary,
                    fontFamily = FontFamily.Default
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val fraction = (prediction.earbudMinutesLeft / (11f * 60f)).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = vibe.accent,
            trackColor = CmfSurfaceSubtle
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Зарядок от кейса: ~${prediction.caseRechargesCount} раз",
                fontSize = 12.sp,
                color = CmfTextPrimary
            )
            Text(
                text = "Расход: ~${prediction.dischargeRatePerHour}%/ч",
                fontSize = 12.sp,
                color = CmfTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Профиль: ${prediction.powerConsumptionProfile}",
            fontSize = 11.sp,
            color = CmfTextSecondary
        )
    }
}
