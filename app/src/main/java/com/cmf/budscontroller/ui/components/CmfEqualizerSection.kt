package com.cmf.budscontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.cmf.budscontroller.audio.EqualizerState
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.ui.theme.*

@Composable
fun CmfEqualizerSection(
    eqState: EqualizerState,
    onPresetSelected: (String) -> Unit,
    onBandChanged: (Int, Int) -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val bandFreqNames = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    val dimens = LocalCmfDimens.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(CmfGlassCardBrush)
            .border(1.2.dp, CmfGlassBorderBrush, RoundedCornerShape(dimens.cardCorner))
            .padding(dimens.cardPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = AppStrings.headerEqualizer,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = CmfTextSecondary,
                    fontFamily = FontFamily.Default
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${AppStrings.eqPresetLabel}: ${eqState.activePresetName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CmfOrange,
                    fontFamily = FontFamily.Default
                )
            }

            Switch(
                checked = eqState.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CmfTextPrimary,
                    checkedTrackColor = CmfOrange,
                    uncheckedThumbColor = CmfTextSecondary,
                    uncheckedTrackColor = CmfSurfaceSubtle,
                    uncheckedBorderColor = CmfBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Preset Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            eqState.presets.forEach { preset ->
                val isSelected = eqState.activePresetName == preset.name
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(1.dp, if (isSelected) CmfOrange else CmfBorder, CircleShape)
                        .background(if (isSelected) CmfOrange else CmfSurfaceSubtle)
                        .clickable { onPresetSelected(preset.name) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = preset.name,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        color = if (isSelected) CmfTextPrimary else CmfTextSecondary,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5 Equalizer Sliders
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            eqState.bandLevels.forEachIndexed { index, levelDb ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (levelDb > 0) "+$levelDb" else "$levelDb",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (levelDb != 0) CmfOrange else CmfTextSecondary,
                        fontFamily = FontFamily.Default
                    )
                    Slider(
                        value = levelDb.toFloat(),
                        onValueChange = { onBandChanged(index, it.toInt()) },
                        valueRange = -10f..10f,
                        steps = 19,
                        modifier = Modifier.padding(vertical = 2.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = CmfOrange,
                            activeTrackColor = CmfOrange,
                            inactiveTrackColor = CmfSurfaceSubtle
                        )
                    )
                    Text(
                        text = bandFreqNames.getOrElse(index) { "" },
                        fontSize = 9.sp,
                        color = CmfTextSecondary,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }
    }
}
