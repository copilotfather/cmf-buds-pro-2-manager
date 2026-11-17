package com.cmf.budscontroller.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.media.CmfMediaState
import com.cmf.budscontroller.ui.theme.*

@Composable
fun CmfMusicPlayerCard(
    mediaState: CmfMediaState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onOpenSettings: () -> Unit,
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
        // 1. Header Bar: Status & Source Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pulsing dot only if playing (GPU graphicsLayer to avoid recompositions)
                if (mediaState.isPlaying) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer { this.alpha = alpha }
                            .clip(CircleShape)
                            .background(vibe.accent)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(CmfTextSecondary.copy(alpha = 0.4f))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppStrings.mediaNowPlaying,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = CmfTextSecondary,
                    fontFamily = FontFamily.Default
                )
            }

            // App/Player tag
            val appLabel = when {
                mediaState.appName.isNotEmpty() -> mediaState.appName
                mediaState.isPlaying -> "AUDIO STREAM"
                else -> "STANDBY"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CmfSurfaceSubtle)
                    .border(1.dp, CmfBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = appLabel.take(14),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = if (mediaState.isPlaying) vibe.accent else CmfTextSecondary,
                    fontFamily = FontFamily.Default
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Main Track & Visualizer Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track & Artist Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = mediaState.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = CmfTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Default
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (mediaState.album.isNotEmpty()) "${mediaState.artist} • ${mediaState.album}" else mediaState.artist,
                    fontSize = 12.sp,
                    color = CmfTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Default
                )
            }

            // Animated Retro Waveform Equalizer Bars
            CmfEqualizerBars(
                isPlaying = mediaState.isPlaying,
                accentColor = vibe.accent
            )
        }

        // 3. Progress Bar (if duration is known)
        if (mediaState.durationMs > 0) {
            Spacer(modifier = Modifier.height(14.dp))

            val targetProgress = (mediaState.positionMs.toFloat() / mediaState.durationMs.toFloat()).coerceIn(0f, 1f)
            val progressFraction by animateFloatAsState(
                targetValue = targetProgress,
                animationSpec = tween(900, easing = LinearEasing),
                label = "media_progress"
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = vibe.accent,
                    trackColor = CmfSurfaceSubtle
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(mediaState.positionMs),
                        fontSize = 10.sp,
                        color = CmfTextSecondary,
                        fontFamily = FontFamily.Default
                    )
                    Text(
                        text = formatDuration(mediaState.durationMs),
                        fontSize = 10.sp,
                        color = CmfTextSecondary,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Playback Controls Row: |<  [PLAY/PAUSE]  >| (Vector Icons)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous Track
            Box(
                modifier = Modifier
                    .size(dimens.skipButtonSize)
                    .clip(CircleShape)
                    .background(CmfSurfaceSubtle)
                    .border(1.2.dp, CmfBorder, CircleShape)
                    .scalePressFeedback("previous_track_press", onPrevious),
                contentAlignment = Alignment.Center
            ) {
                CmfIconSkipPrevious(
                    modifier = Modifier.size(18.dp),
                    tint = CmfTextPrimary
                )
            }

            // Play / Pause Button (Hero Vibe Button)
            Box(
                modifier = Modifier
                    .size(dimens.playButtonSize)
                    .clip(CircleShape)
                    .background(vibe.accent)
                    .scalePressFeedback("play_pause_press", onTogglePlayPause),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = mediaState.isPlaying,
                    transitionSpec = {
                        (fadeIn(tween(130)) + scaleIn(tween(160), initialScale = 0.78f)) togetherWith
                            (fadeOut(tween(100)) + scaleOut(tween(120), targetScale = 0.78f))
                    },
                    label = "play_pause_icon"
                ) { isPlaying ->
                    if (isPlaying) {
                        CmfIconPause(
                            modifier = Modifier.size(20.dp),
                            tint = CmfBlack
                        )
                    } else {
                        CmfIconPlay(
                            modifier = Modifier.size(20.dp),
                            tint = CmfBlack
                        )
                    }
                }
            }

            // Next Track
            Box(
                modifier = Modifier
                    .size(dimens.skipButtonSize)
                    .clip(CircleShape)
                    .background(CmfSurfaceSubtle)
                    .border(1.2.dp, CmfBorder, CircleShape)
                    .scalePressFeedback("next_track_press", onNext),
                contentAlignment = Alignment.Center
            ) {
                CmfIconSkipNext(
                    modifier = Modifier.size(18.dp),
                    tint = CmfTextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Volume Slider Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CmfSurfaceSubtle)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CmfIconSpeaker(
                modifier = Modifier.size(18.dp),
                tint = CmfTextSecondary
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "VOL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = CmfTextSecondary,
                fontFamily = FontFamily.Default
            )

            Spacer(modifier = Modifier.width(8.dp))

            Slider(
                value = mediaState.volumePercent.toFloat(),
                onValueChange = { onVolumeChanged(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = vibe.accent,
                    activeTrackColor = vibe.accent,
                    inactiveTrackColor = CmfBorder
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${mediaState.volumePercent}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CmfTextPrimary,
                fontFamily = FontFamily.Default,
                modifier = Modifier.width(38.dp)
            )
        }

        // 6. Permission Helper Banner if listener is not enabled
        if (!mediaState.hasListenerPermission) {
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CmfSurfaceSubtle)
                    .border(1.dp, vibe.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppStrings.mediaPermissionTitle,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = vibe.accent,
                            fontFamily = FontFamily.Default
                        )
                        Text(
                            text = AppStrings.mediaPermissionDesc,
                            fontSize = 10.sp,
                            color = CmfTextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(vibe.accent)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = AppStrings.mediaPermissionEnable,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = CmfBlack,
                            fontFamily = FontFamily.Default
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CmfEqualizerBars(
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val bars = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "eq_bars")
        val h1 by infiniteTransition.animateFloat(
            initialValue = 6f,
            targetValue = 24f,
            animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "h1"
        )
        val h2 by infiniteTransition.animateFloat(
            initialValue = 18f,
            targetValue = 8f,
            animationSpec = infiniteRepeatable(tween(450, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
            label = "h2"
        )
        val h3 by infiniteTransition.animateFloat(
            initialValue = 10f,
            targetValue = 26f,
            animationSpec = infiniteRepeatable(tween(320, easing = FastOutLinearInEasing), RepeatMode.Reverse),
            label = "h3"
        )
        val h4 by infiniteTransition.animateFloat(
            initialValue = 22f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
            label = "h4"
        )
        listOf(h1.dp, h2.dp, h3.dp, h4.dp)
    } else {
        listOf(4.dp, 4.dp, 4.dp, 4.dp)
    }

    Row(
        modifier = modifier
            .height(28.dp)
            .width(32.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { actualHeight ->
            val animatedHeight by animateDpAsState(
                targetValue = actualHeight,
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                label = "eq_bar_height"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animatedHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isPlaying) accentColor else CmfBorder)
            )
        }
    }
}

@Composable
private fun Modifier.scalePressFeedback(label: String, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f),
        label = label
    )
    return this
        .scale(scale)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
