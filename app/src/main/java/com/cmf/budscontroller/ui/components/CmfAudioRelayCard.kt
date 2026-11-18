package com.cmf.budscontroller.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.relay.AudioRelayState
import com.cmf.budscontroller.relay.BufferPreset
import com.cmf.budscontroller.relay.RelayMode
import com.cmf.budscontroller.ui.theme.*

@Composable
fun CmfAudioRelayCard(
    relayState: AudioRelayState,
    onToggleRelay: () -> Unit,
    onModeSelected: (RelayMode) -> Unit,
    onBufferPresetSelected: (BufferPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val vibe = LocalVibeTheme.current

    val dimens = LocalCmfDimens.current
    val relayInteraction = remember { MutableInteractionSource() }
    val relayPressed by relayInteraction.collectIsPressedAsState()
    val relayScale by animateFloatAsState(
        targetValue = if (relayPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 520f),
        label = "relay_toggle_press"
    )
    val relayStatusColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (relayState.clientConnected) Color(0xFF00E676) else vibe.accent,
        animationSpec = tween(240),
        label = "relay_status_color"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(CmfGlassCardBrush)
            .border(1.2.dp, CmfGlassBorderBrush, RoundedCornerShape(dimens.cardCorner))
            .padding(dimens.cardPadding)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(relayStatusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppStrings.relayTitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = CmfTextSecondary,
                    fontFamily = FontFamily.Default
                )
            }

            Text(
                text = if (relayState.clientConnected) "ACTIVE 10ms" else "STANDBY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = relayStatusColor,
                fontFamily = FontFamily.Default
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Visual Bridge Schematic: [PC] == USB == [PHONE] == BT == [BUDS]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CmfSurfaceSubtle)
                .border(1.dp, CmfBorder, RoundedCornerShape(16.dp))
                .padding(vertical = 14.dp, horizontal = 16.dp)
        ) {
            val pulseProgress = if (relayState.isRunning) {
                val infiniteTransition = rememberInfiniteTransition(label = "bus_pulse")
                val progress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "pulseProgress"
                )
                progress
            } else {
                0f
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CmfBridgeNode(title = if (AppStrings.isRussian) "ПК (USB)" else "PC (USB)", isConnected = relayState.clientConnected, accent = vibe.accent)
                    CmfBridgeNode(title = if (AppStrings.isRussian) "ТЕЛЕФОН" else "PHONE", isConnected = relayState.isRunning, accent = vibe.accent)
                    CmfBridgeNode(title = "CMF BUDS", isConnected = true, accent = vibe.accent)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Animated Bus Line
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                ) {
                    val w = size.width
                    val midY = size.height / 2

                    // Base connection trace
                    drawLine(
                        color = CmfBorder,
                        start = Offset(20f, midY),
                        end = Offset(w - 20f, midY),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Animated travelling pulse packets
                    if (relayState.isRunning) {
                        val pulseX1 = 20f + (w * 0.45f) * pulseProgress
                        val pulseX2 = w * 0.5f + (w * 0.45f) * pulseProgress

                        drawCircle(
                            color = vibe.accent,
                            radius = 3.dp.toPx(),
                            center = Offset(pulseX1, midY)
                        )
                        drawCircle(
                            color = vibe.accent,
                            radius = 3.dp.toPx(),
                            center = Offset(pulseX2, midY)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Relay Mode Selector (3 Pills)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CmfSurfaceSubtle)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            RelayMode.values().forEach { mode ->
                val isSelected = relayState.mode == mode
                val modeInteraction = remember(mode) { MutableInteractionSource() }
                val modePressed by modeInteraction.collectIsPressedAsState()
                val modeScale by animateFloatAsState(
                    targetValue = if (modePressed) 0.96f else 1f,
                    animationSpec = spring(dampingRatio = 0.74f, stiffness = 560f),
                    label = "relay_mode_press"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = dimens.controlTouchTarget)
                        .scale(modeScale)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (isSelected) vibe.accent else Color.Transparent)
                        .clickable(interactionSource = modeInteraction, indication = null) { onModeSelected(mode) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.title,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        color = if (isSelected) CmfBlack else CmfTextSecondary,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Buffer Preset Selector (Ultra / Balanced / Stable)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BufferPreset.values().forEach { preset ->
                val isSelected = relayState.bufferPreset == preset
                val presetInteraction = remember(preset) { MutableInteractionSource() }
                val presetPressed by presetInteraction.collectIsPressedAsState()
                val presetScale by animateFloatAsState(
                    targetValue = if (presetPressed) 0.96f else 1f,
                    animationSpec = spring(dampingRatio = 0.74f, stiffness = 560f),
                    label = "relay_buffer_press"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = dimens.controlTouchTarget)
                        .scale(presetScale)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            1.dp,
                            if (isSelected) vibe.accent else CmfBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .background(if (isSelected) vibe.accentSubtle else CmfSurfaceSubtle)
                        .clickable(interactionSource = presetInteraction, indication = null) { onBufferPresetSelected(preset) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = preset.title,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                        color = if (isSelected) vibe.accent else CmfTextSecondary,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big Tactile Start / Stop Relay Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scale(relayScale)
                .clip(RoundedCornerShape(16.dp))
                .background(if (relayState.isRunning) Color(0xFF1E1E22) else vibe.accent)
                .border(1.5.dp, if (relayState.isRunning) vibe.accent else Color.Transparent, RoundedCornerShape(16.dp))
                .clickable(interactionSource = relayInteraction, indication = null) { onToggleRelay() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (relayState.isRunning) (if (AppStrings.isRussian) "ОСТАНОВИТЬ МОСТ" else "STOP BRIDGE") else (if (AppStrings.isRussian) "ВКЛЮЧИТЬ ПЕРЕАДРЕСАЦИЮ USB" else "START USB RELAY"),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = if (relayState.isRunning) vibe.accent else CmfBlack,
                fontFamily = FontFamily.Default
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status & PC Instructions Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CmfSurfaceSubtle)
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = if (AppStrings.isRussian) "СТАТУС: ${relayState.statusMessage}" else "STATUS: ${relayState.statusMessage}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (relayState.clientConnected) relayStatusColor else CmfTextPrimary,
                    fontFamily = FontFamily.Default
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (AppStrings.isRussian) "Как подключить к ПК: подключите телефон по USB и запустите cmf_pc_relay.bat на компьютере. Звук ПК сразу пойдет в наушники!" else "How to connect: Connect phone via USB and run cmf_pc_relay.bat on your PC. PC audio will stream straight to your earbuds!",
                    fontSize = 10.sp,
                    color = CmfTextSecondary
                )
            }
        }
    }
}

@Composable
private fun CmfBridgeNode(
    title: String,
    isConnected: Boolean,
    accent: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isConnected) accent.copy(alpha = 0.15f) else CmfSurface)
            .border(1.dp, if (isConnected) accent else CmfBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = if (isConnected) accent else CmfTextSecondary,
            fontFamily = FontFamily.Default
        )
    }
}
