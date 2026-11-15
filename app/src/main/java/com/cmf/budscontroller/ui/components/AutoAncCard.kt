package com.cmf.budscontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.sensor.AmbientNoiseState
import com.cmf.budscontroller.ui.theme.*

@Composable
fun AutoAncCard(
    noiseState: AmbientNoiseState,
    onToggled: (Boolean) -> Unit,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ДИНАМИЧЕСКИЙ АВТО-ANC",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = CmfTextSecondary,
                    fontFamily = FontFamily.Default
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Анализ шума через микрофон смартфона",
                    fontSize = 11.sp,
                    color = CmfTextPrimary
                )
            }
            Switch(
                checked = noiseState.isAutoAncActive,
                onCheckedChange = onToggled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CmfTextPrimary,
                    checkedTrackColor = CmfOrange,
                    uncheckedThumbColor = CmfTextSecondary,
                    uncheckedTrackColor = CmfSurfaceSubtle,
                    uncheckedBorderColor = CmfBorder
                )
            )
        }

        if (noiseState.isAutoAncActive) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CmfSurfaceSubtle)
                    .border(1.dp, CmfBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ОКРУЖАЮЩИЙ ШУМ",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CmfTextSecondary,
                        fontFamily = FontFamily.Default
                    )
                    Text(
                        text = "${noiseState.currentDecibels} dB",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = if (noiseState.currentDecibels > 65) CmfOrange else CmfGreen,
                        fontFamily = FontFamily.Default
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "АКТИВНАЯ СЦЕНА",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CmfTextSecondary,
                        fontFamily = FontFamily.Default
                    )
                    Text(
                        text = noiseState.noiseEnvironment,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = CmfTextPrimary
                    )
                }
            }
        }
    }
}
