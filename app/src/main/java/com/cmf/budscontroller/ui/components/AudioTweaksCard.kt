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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.audio.AudioProcessingState
import com.cmf.budscontroller.ui.theme.*

@Composable
fun AudioTweaksCard(
    audioState: AudioProcessingState,
    onBalanceChanged: (Float) -> Unit,
    onLoudnessChanged: (Int) -> Unit,
    onMonoToggled: (Boolean) -> Unit,
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
        Text(
            text = "БАЛАНС КАНАЛОВ И ЗВУК",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = CmfTextSecondary,
            fontFamily = FontFamily.Default
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Balance Slider (Left <-> Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Баланс (L / R):", fontSize = 12.sp, color = CmfTextPrimary)
            val balanceText = when {
                audioState.channelBalance < -0.05f -> "Левее (${(audioState.channelBalance * -100).toInt()}%)"
                audioState.channelBalance > 0.05f -> "Правее (${(audioState.channelBalance * 100).toInt()}%)"
                else -> "Центр"
            }
            Text(text = balanceText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CmfOrange, fontFamily = FontFamily.Default)
        }

        Slider(
            value = audioState.channelBalance,
            onValueChange = onBalanceChanged,
            valueRange = -1.0f..1.0f,
            colors = SliderDefaults.colors(
                thumbColor = CmfOrange,
                activeTrackColor = CmfOrange,
                inactiveTrackColor = CmfSurfaceSubtle
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "L", fontSize = 10.sp, color = CmfTextSecondary, fontFamily = FontFamily.Default)
            Text(text = "0", fontSize = 10.sp, color = CmfTextSecondary, fontFamily = FontFamily.Default)
            Text(text = "R", fontSize = 10.sp, color = CmfTextSecondary, fontFamily = FontFamily.Default)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Loudness Booster (Усилитель громкости)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Усилитель громкости:", fontSize = 12.sp, color = CmfTextPrimary)
            Text(
                text = "+${audioState.loudnessBoostMilliBels / 100} dB",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CmfOrange,
                fontFamily = FontFamily.Default
            )
        }

        Slider(
            value = audioState.loudnessBoostMilliBels.toFloat(),
            onValueChange = { onLoudnessChanged(it.toInt()) },
            valueRange = 0f..800f,
            colors = SliderDefaults.colors(
                thumbColor = CmfOrange,
                activeTrackColor = CmfOrange,
                inactiveTrackColor = CmfSurfaceSubtle
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Mono Audio toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Моно в один наушник", fontSize = 12.sp, color = CmfTextPrimary)
                Text(text = "Объединяет стерео в один канал", fontSize = 10.sp, color = CmfTextSecondary)
            }
            Switch(
                checked = audioState.isMonoAudio,
                onCheckedChange = onMonoToggled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CmfTextPrimary,
                    checkedTrackColor = CmfOrange,
                    uncheckedThumbColor = CmfTextSecondary,
                    uncheckedTrackColor = CmfSurfaceSubtle,
                    uncheckedBorderColor = CmfBorder
                )
            )
        }
    }
}
