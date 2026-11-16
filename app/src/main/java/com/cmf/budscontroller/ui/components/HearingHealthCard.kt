package com.cmf.budscontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    val dosePercent = hearingStatus.dailySafeDosePercent
    val isOverdose = dosePercent > 100
    val doseColor = when {
        isOverdose -> Color(0xFFFF334B)
        dosePercent > 75 -> Color(0xFFFFB300)
        dosePercent > 40 -> Color(0xFF00E5FF)
        else -> Color(0xFF00E676)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(listOf(Color(0x15FFFFFF), Color(0x08FFFFFF)))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1B202D))
                            .border(1.dp, Color(0x30FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HealthAndSafety,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = doseColor
                        )
                    }

                    Column {
                        Text(
                            text = AppStrings.hearingProtectionTitle,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.3.sp,
                            color = CmfTextSecondary
                        )
                        Text(
                            text = if (AppStrings.isRussian) "Акустическая доза ВОЗ/ITU" else "WHO/ITU Acoustic Dose",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CmfTextPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(doseColor.copy(alpha = 0.16f))
                        .border(1.dp, doseColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "$dosePercent% ${AppStrings.doseLabel}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = doseColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dual Glow Progress Track
            val doseFraction = (dosePercent / 100f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF0C0E14))
                    .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(5.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = doseFraction.coerceAtLeast(0.02f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF00E676),
                                    if (isOverdose) Color(0xFFFF334B) else doseColor
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metrics Pills Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Session Chip
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D0F14))
                        .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = Color(0xFF64D2FF)
                        )
                        Column {
                            Text(if (AppStrings.isRussian) "СЕССИЯ" else "SESSION", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CmfTextDisabled)
                            Text("${hearingStatus.sessionMinutes} ${AppStrings.minUnit} / ${hearingStatus.restThresholdMinutes}${AppStrings.minUnit.first()}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CmfTextPrimary)
                        }
                    }
                }

                // Volume Chip
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D0F14))
                        .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = Color(0xFFFF9F0A)
                        )
                        Column {
                            Text(if (AppStrings.isRussian) "ГРОМКОСТЬ" else "VOLUME", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CmfTextDisabled)
                            Text("${hearingStatus.currentVolumePercent}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CmfTextPrimary)
                        }
                    }
                }
            }

            if (hearingStatus.isRestRecommended) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x2BFF5722))
                        .border(1.dp, Color(0x80FF5722), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF5722),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (AppStrings.isRussian) "Рекомендуется 10-минутный отдых для слуха" else "10-minute ear rest recommended",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = CmfTextPrimary
                        )
                    }

                    FilledTonalButton(
                        onClick = onResetSession,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFFF5722),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (AppStrings.isRussian) "СБРОС" else "RESET", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
