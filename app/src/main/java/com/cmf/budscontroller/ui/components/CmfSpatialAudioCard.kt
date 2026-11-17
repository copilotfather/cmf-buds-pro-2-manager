package com.cmf.budscontroller.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.audio.SpatialAudioState
import com.cmf.budscontroller.audio.SpatialMode
import com.cmf.budscontroller.audio.VocalClarity
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.ui.theme.*

@Composable
fun CmfSpatialAudioCard(
    spatialState: SpatialAudioState,
    onModeSelected: (SpatialMode) -> Unit,
    onSoundstageWidthChanged: (Int) -> Unit,
    onVocalClaritySelected: (VocalClarity) -> Unit,
    onStartSealTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vibe = LocalVibeTheme.current
    val dimens = LocalCmfDimens.current

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
                CmfIconSpatial3D(
                    modifier = Modifier.size(18.dp),
                    tint = vibe.accent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppStrings.headerSpatialAudio,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = CmfTextSecondary,
                    fontFamily = FontFamily.Default
                )
            }

            Text(
                text = "${spatialState.soundstageWidth}% ${AppStrings.spatialStage}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = vibe.accent,
                fontFamily = FontFamily.Default
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive 3D Holographic Soundfield Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CmfSurfaceSubtle)
                .border(1.dp, CmfBorder, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            val waveRadius = if (spatialState.mode != SpatialMode.OFF) {
                val infiniteTransition = rememberInfiniteTransition(label = "spatial_pulse")
                val radius by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 0.95f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1800, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "waveRadius"
                )
                radius
            } else {
                1f
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val center = Offset(w * 0.5f, h * 0.5f)

                // Virtual head silhouette in center
                drawCircle(
                    color = Color(0xFF222225),
                    radius = h * 0.26f,
                    center = center
                )
                drawCircle(
                    color = vibe.accent.copy(alpha = 0.6f),
                    radius = h * 0.12f,
                    center = center
                )

                // Left & Right earbud audio emission nodes
                val spread = (spatialState.soundstageWidth / 100f) * (w * 0.26f)
                val leftNode = Offset(center.x - spread, center.y)
                val rightNode = Offset(center.x + spread, center.y)

                drawCircle(color = vibe.accent, radius = 6.dp.toPx(), center = leftNode)
                drawCircle(color = vibe.accent, radius = 6.dp.toPx(), center = rightNode)

                // Expanding spatial acoustic waves
                val maxWave = h * 0.45f
                val currentR = maxWave * waveRadius
                val alpha = (1f - waveRadius).coerceIn(0f, 1f) * 0.7f

                drawCircle(
                    color = vibe.accent.copy(alpha = alpha),
                    radius = currentR,
                    center = leftNode,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawCircle(
                    color = vibe.accent.copy(alpha = alpha),
                    radius = currentR,
                    center = rightNode,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Tech HUD Overlay
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "L // 3D EMITTER",
                    fontSize = 9.sp,
                    color = CmfTextSecondary,
                    fontFamily = FontFamily.Default
                )
                Text(
                    text = "R // 3D EMITTER",
                    fontSize = 9.sp,
                    color = CmfTextSecondary,
                    fontFamily = FontFamily.Default
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Spatial Mode Selector (4 Pills)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CmfSurfaceSubtle)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            SpatialMode.values().forEach { mode ->
                val isSelected = spatialState.mode == mode
                val modeInteraction = androidx.compose.runtime.remember(mode) { MutableInteractionSource() }
                val modePressed by modeInteraction.collectIsPressedAsState()
                val modeScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (modePressed) 0.96f else 1f,
                    animationSpec = spring(dampingRatio = 0.74f, stiffness = 560f),
                    label = "spatial_mode_press"
                )
                val modeBackground by animateColorAsState(
                    targetValue = if (isSelected) vibe.accent else Color.Transparent,
                    animationSpec = tween(200),
                    label = "spatial_mode_background"
                )
                val modeTextColor by animateColorAsState(
                    targetValue = if (isSelected) CmfBlack else CmfTextSecondary,
                    animationSpec = tween(200),
                    label = "spatial_mode_text"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .scale(modeScale)
                        .clip(RoundedCornerShape(13.dp))
                        .background(modeBackground)
                        .clickable(interactionSource = modeInteraction, indication = null) { onModeSelected(mode) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.title,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        color = modeTextColor,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Soundstage Width Slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CmfSurfaceSubtle)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppStrings.spatialWidth,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = CmfTextSecondary,
                fontFamily = FontFamily.Default
            )

            Spacer(modifier = Modifier.width(8.dp))

            Slider(
                value = spatialState.soundstageWidth.toFloat(),
                onValueChange = { onSoundstageWidthChanged(it.toInt()) },
                valueRange = 100f..200f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = vibe.accent,
                    activeTrackColor = vibe.accent,
                    inactiveTrackColor = CmfBorder
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${spatialState.soundstageWidth}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = CmfTextPrimary,
                fontFamily = FontFamily.Default
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Ear Tip Seal Diagnostic Test Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CmfSurfaceSubtle)
                .border(1.dp, if (spatialState.sealResult.sealScore > 0) Color(0xFF00E676).copy(alpha = 0.5f) else CmfBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CmfIconEarTipSeal(
                            modifier = Modifier.size(18.dp),
                            tint = if (spatialState.sealResult.sealScore > 0) Color(0xFF00E676) else vibe.accent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppStrings.earTipTestTitle,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = CmfTextPrimary,
                            fontFamily = FontFamily.Default
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (spatialState.sealResult.isTesting) CmfSurface else vibe.accent)
                            .clickable { onStartSealTest() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (spatialState.sealResult.isTesting) AppStrings.spatialBtnTesting else AppStrings.spatialBtnTest,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (spatialState.sealResult.isTesting) vibe.accent else CmfBlack,
                            fontFamily = FontFamily.Default
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = spatialState.sealResult.rating,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (spatialState.sealResult.sealScore >= 90) Color(0xFF00E676) else CmfTextPrimary,
                    fontFamily = FontFamily.Default
                )

                Text(
                    text = spatialState.sealResult.description,
                    fontSize = 11.sp,
                    color = CmfTextSecondary
                )
            }
        }
    }
}
