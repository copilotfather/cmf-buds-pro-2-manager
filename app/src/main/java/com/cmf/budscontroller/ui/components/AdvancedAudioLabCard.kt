package com.cmf.budscontroller.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.audio.AdvancedAudioLabState
import com.cmf.budscontroller.audio.LabLatencyPreset
import com.cmf.budscontroller.audio.LabNoiseLayer
import com.cmf.budscontroller.ui.theme.*

/**
 * AUDIO LAB — интеграция технологий из CMF_Buds_Pro_2_Audio_Tech:
 * APE (8-band EQ) • SAC (ANC калибровка) • IWNM (микшер шума)
 * SLG (безопасное прослушивание) • IBP (батарея) • ULLE (задержка)
 */
@Composable
fun AdvancedAudioLabCard(
    labState: AdvancedAudioLabState,
    onBandChanged: (Int, Float) -> Unit,
    onLabPresetSelected: (String) -> Unit,
    onLeakageChanged: (Float) -> Unit,
    onAncFeedback: (String) -> Unit,
    onLayerToggled: (LabNoiseLayer, Boolean) -> Unit,
    onLayerVolumeChanged: (LabNoiseLayer, Float) -> Unit,
    onMasterVolumeChanged: (Float) -> Unit,
    onMixerMinutesChanged: (Int) -> Unit,
    onStartMixer: () -> Unit,
    onStopMixer: () -> Unit,
    onLatencyPresetSelected: (LabLatencyPreset) -> Unit
) {
    val accent = LocalVibeTheme.current.accent
    val expanded = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CmfGlassCardBrush)
            .border(1.2.dp, CmfGlassBorderBrush, RoundedCornerShape(22.dp))
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded.value = !expanded.value },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AUDIO LAB",
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Default
                )
                Text(
                    text = "APE • SAC • IWNM • SLG • IBP • ULLE",
                    color = CmfTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Default
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (expanded.value) "СВЕРНУТЬ" else "ОТКРЫТЬ",
                    color = accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Default
                )
            }
        }

        // QUICK STATUS ROW
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LabStatPill("ДОЗА", "${labState.dailyDosePercent}%", labState.riskLevelColor(), Modifier.weight(1f))
            LabStatPill("БАТАРЕЯ", labState.batteryTimeFormatted, accent, Modifier.weight(1f))
            LabStatPill("ANC", if (labState.ancCalibration.isCalibrated) "OK" else "—", accent, Modifier.weight(1f))
        }

        if (expanded.value) {
            LabDivider()

            // ============ APE: 8-BAND PARAMETRIC EQ ============
            LabSectionTitle("APE · ПАРАМЕТРИЧЕСКИЙ EQ 8 ПОЛОС", accent)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                labState.labPresets.forEach { (name, _) ->
                    LabChip(
                        text = name,
                        selected = labState.activeLabPreset == name,
                        accent = accent,
                        onClick = { onLabPresetSelected(name) }
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                labState.parametricBands.forEachIndexed { index, band ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = labFreqLabel(band.freq),
                            color = CmfTextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Default,
                            modifier = Modifier.width(46.dp)
                        )
                        LabMiniSlider(
                            value = band.gainDb,
                            range = -12f..12f,
                            onValueChange = { onBandChanged(index, it) },
                            accent = accent,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "%+.0f".format(band.gainDb) + "dB",
                            color = CmfTextPrimary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Default,
                            modifier = Modifier.width(48.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }

            LabDivider()

            // ============ SAC: ANC CALIBRATION ============
            LabSectionTitle("SAC · КАЛИБРОВКА ANC", accent)
            Text(
                text = "Утечка: %d%% · Канал: %.1fx · Точность: %d%%".format(
                    (labState.ancCalibration.leakageFactor * 100).toInt(),
                    labState.ancCalibration.canalVolume,
                    (labState.ancCalibration.confidence * 100).toInt()
                ),
                color = CmfTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Default
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("УТЕЧКА", color = CmfTextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Default, modifier = Modifier.width(58.dp))
                LabMiniSlider(
                    value = labState.ancCalibration.leakageFactor,
                    range = 0f..1f,
                    onValueChange = { onLeakageChanged(it) },
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "СИЛА ANC: ${labState.ancCalibration.recommendedStrength}%",
                color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Default
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LabChip("СЛАБO ↓", false, accent, onClick = { onAncFeedback("more") })
                LabChip("ИДЕАЛЬНО", false, accent, onClick = { onAncFeedback("perfect") })
                LabChip("СИЛЬНО ↑", false, accent, onClick = { onAncFeedback("less") })
            }

            LabDivider()

            // ============ IWNM: NOISE MIXER ============
            LabSectionTitle("IWNM · МИКШЕР ШУМА", accent)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LabNoiseLayer.entries.forEach { layer ->
                    val enabled = labState.layerEnabled[layer] == true
                    LabChip(
                        text = layer.title,
                        selected = enabled,
                        accent = accent,
                        onClick = { onLayerToggled(layer, !enabled) }
                    )
                }
            }
            LabNoiseLayer.entries.filter { labState.layerEnabled[it] == true }.forEach { layer ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = layer.title, color = CmfTextSecondary, fontSize = 9.sp,
                        fontFamily = FontFamily.Default, modifier = Modifier.width(58.dp)
                    )
                    LabMiniSlider(
                        value = labState.layerVolume[layer] ?: 0.6f,
                        range = 0f..1f,
                        onValueChange = { onLayerVolumeChanged(layer, it) },
                        accent = accent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("МАСТЕР", color = CmfTextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Default, modifier = Modifier.width(58.dp))
                LabMiniSlider(
                    value = labState.mixerMasterVolume,
                    range = 0f..1f,
                    onValueChange = { onMasterVolumeChanged(it) },
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(15, 30, 45, 60, 120).forEach { mins ->
                    LabChip(
                        text = "${mins}м",
                        selected = labState.mixerSelectedMinutes == mins,
                        accent = accent,
                        onClick = { onMixerMinutesChanged(mins) }
                    )
                }
            }
            if (labState.isMixerActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ИГРАЕТ · ${labState.mixerMinutesRemaining}м ост.",
                        color = CmfGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default, modifier = Modifier.weight(1f)
                    )
                    LabChip("СТОП", true, CmfGreen, onClick = onStopMixer)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent)
                        .clickable(onClick = onStartMixer)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "▶  ЗАПУСТИТЬ МИКШЕР",
                        color = CmfBlack, fontSize = 11.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Default
                    )
                }
            }

            LabDivider()

            // ============ SLG: SAFE LISTENING ============
            LabSectionTitle("SLG · БЕЗОПАСНОЕ ПРОСЛУШИВАНИЕ", accent)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CmfSurfaceSubtle)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ДОЗА ЗА ДЕНЬ: ${labState.dailyDosePercent}%",
                        color = CmfTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default
                    )
                    Text(
                        text = when (labState.riskLevel) {
                            com.cmf.budscontroller.audio.LabRiskLevel.OK -> "Уровень безопасен"
                            com.cmf.budscontroller.audio.LabRiskLevel.WARNING -> "Рекомендую снизить громкость"
                            com.cmf.budscontroller.audio.LabRiskLevel.DANGER -> "Пауза обязательна!"
                        },
                        color = CmfTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Default
                    )
                }
                Text(
                    text = labState.riskLevel.title,
                    color = labState.riskLevelColor(),
                    fontSize = 11.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Default
                )
            }

            LabDivider()

            // ============ ULLE: LATENCY ============
            LabSectionTitle("ULLE · ЗАДЕРЖКА", accent)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LabLatencyPreset.entries.forEach { preset ->
                    LabChip(
                        text = preset.title,
                        selected = labState.latencyPreset == preset,
                        accent = accent,
                        onClick = { onLatencyPresetSelected(preset) }
                    )
                }
            }
            Text(
                text = labState.latencyPreset.subtitle,
                color = CmfTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Default
            )

            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

private fun AdvancedAudioLabState.riskLevelColor(): Color = when (riskLevel) {
    com.cmf.budscontroller.audio.LabRiskLevel.OK -> CmfGreen
    com.cmf.budscontroller.audio.LabRiskLevel.WARNING -> Color(0xFFFFC107)
    com.cmf.budscontroller.audio.LabRiskLevel.DANGER -> Color(0xFFFF5252)
}

private fun labFreqLabel(freq: Int): String = when {
    freq < 1000 -> "${freq}Hz"
    else -> "%.1fk".format(freq / 1000f)
}

@Composable
private fun LabStatPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CmfSurfaceSubtle)
            .border(0.8.dp, CmfBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, color = CmfTextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Default)
        Text(text = value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Default)
    }
}

@Composable
private fun LabSectionTitle(title: String, accent: Color) {
    Text(
        text = "▸ $title",
        color = accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        fontFamily = FontFamily.Default
    )
}

@Composable
private fun LabChip(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) accent.copy(alpha = 0.2f) else CmfSurfaceSubtle)
            .border(1.dp, if (selected) accent else CmfBorder, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (selected) accent else CmfTextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Default
        )
    }
}

@Composable
private fun LabMiniSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val normalized = (value - range.start) / (range.endInclusive - range.start)
    androidx.compose.material3.Slider(
        value = normalized.coerceIn(0f, 1f),
        onValueChange = { norm ->
            onValueChange(range.start + norm * (range.endInclusive - range.start))
        },
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = CmfSurfaceSubtle
        ),
        modifier = modifier.height(28.dp)
    )
}

@Composable
private fun LabDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.8.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, CmfBorder, Color.Transparent)
                )
            )
    )
}
