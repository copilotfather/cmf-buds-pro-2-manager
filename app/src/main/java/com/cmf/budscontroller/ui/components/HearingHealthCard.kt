package com.cmf.budscontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.cmf.budscontroller.i18n.AppStrings
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.analytics.HearingStatus
import com.cmf.budscontroller.ui.theme.*

@Composable
fun HearingHealthCard(
    hearingStatus: HearingStatus,
    onResetSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CmfSurface)
            .border(1.dp, CmfBorder, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppStrings.hearingProtectionTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = CmfTextSecondary,
                fontFamily = FontFamily.Default
            )
            Text(
                text = "${hearingStatus.dailySafeDosePercent}% ${AppStrings.doseLabel}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = if (hearingStatus.dailySafeDosePercent > 100) Color(0xFFFF1744) else Color(0xFF00E676),
                fontFamily = FontFamily.Default
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        val doseFraction = (hearingStatus.dailySafeDosePercent / 100f).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { doseFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (hearingStatus.dailySafeDosePercent > 100) Color(0xFFFF1744) else Color(0xFF00E676),
            trackColor = CmfSurfaceSubtle
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Сессия: ${hearingStatus.sessionMinutes} мин (порог: ${hearingStatus.restThresholdMinutes}м)",
                fontSize = 12.sp,
                color = CmfTextPrimary
            )
            Text(
                text = "Громкость: ${hearingStatus.currentVolumePercent}%",
                fontSize = 12.sp,
                color = CmfTextSecondary,
                fontFamily = FontFamily.Default
            )
        }

        if (hearingStatus.isRestRecommended) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CmfOrangeSubtle)
                    .border(1.dp, CmfOrange, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Рекомендуется 10-минутный отдых для слуха",
                    fontSize = 12.sp,
                    color = CmfTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onResetSession) {
                    Text(
                        text = "СБРОС",
                        color = CmfOrange,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }
    }
}
