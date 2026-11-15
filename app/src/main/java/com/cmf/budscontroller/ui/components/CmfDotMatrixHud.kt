package com.cmf.budscontroller.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.media.CmfMediaState
import com.cmf.budscontroller.protocol.DeviceStatus
import com.cmf.budscontroller.ui.theme.*
import kotlin.math.sin

enum class DotMatrixMode(val label: String) {
    SPECTRUM("VU SPECTRUM"),
    MARQUEE("LED MARQUEE"),
    TECH_HUD("SYSTEM HUD")
}

@Composable
fun CmfDotMatrixHud(
    mediaState: CmfMediaState,
    deviceStatus: DeviceStatus,
    modifier: Modifier = Modifier
) {
    val vibe = LocalVibeTheme.current
    var activeMode by remember { mutableStateOf(DotMatrixMode.SPECTRUM) }

    // Only animate when media is actively playing or in marquee mode (prevents background lag)
    val shouldAnimate = mediaState.isPlaying || activeMode == DotMatrixMode.MARQUEE
    val time = if (shouldAnimate) {
        val infiniteTransition = rememberInfiniteTransition(label = "matrix_anim")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 100f,
            animationSpec = infiniteRepeatable(
                animation = tween(10000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "time"
        )
        anim
    } else 0f

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
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(vibe.accent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Светодиодный индикатор",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = CmfTextSecondary,
                    fontFamily = FontFamily.Default
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(vibe.accentSubtle)
                    .border(1.dp, vibe.accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable {
                        activeMode = when (activeMode) {
                            DotMatrixMode.SPECTRUM -> DotMatrixMode.MARQUEE
                            DotMatrixMode.MARQUEE -> DotMatrixMode.TECH_HUD
                            DotMatrixMode.TECH_HUD -> DotMatrixMode.SPECTRUM
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = activeMode.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = vibe.accent,
                    fontFamily = FontFamily.Default
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dot-Matrix Canvas (7 rows x 34 columns)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0A0A0A))
                .border(1.dp, CmfBorder, RoundedCornerShape(14.dp))
                .clickable {
                    activeMode = when (activeMode) {
                        DotMatrixMode.SPECTRUM -> DotMatrixMode.MARQUEE
                        DotMatrixMode.MARQUEE -> DotMatrixMode.TECH_HUD
                        DotMatrixMode.TECH_HUD -> DotMatrixMode.SPECTRUM
                    }
                }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rows = 7
                val cols = 34
                val cellW = size.width / cols
                val cellH = size.height / rows
                val dotRadius = (cellW.coerceAtMost(cellH) * 0.38f)

                val inactiveColor = Color(0xFF1C1C1E)
                val activeColor = vibe.accent
                val isMusicPlaying = mediaState.isPlaying

                for (c in 0 until cols) {
                    // Compute active row height based on mode
                    val activeRows = when (activeMode) {
                        DotMatrixMode.SPECTRUM -> {
                            if (isMusicPlaying) {
                                // Dynamic animated frequency bars
                                val wave1 = sin(time * 0.4f + c * 0.45f) * 0.5f + 0.5f
                                val wave2 = sin(time * 0.8f + c * 0.2f) * 0.5f + 0.5f
                                ((wave1 * 0.6f + wave2 * 0.4f) * rows).toInt().coerceIn(1, rows)
                            } else {
                                // Standby gentle breathing wave
                                (sin(time * 0.2f + c * 0.15f) * 1.5f + 2f).toInt().coerceIn(1, 3)
                            }
                        }
                        DotMatrixMode.MARQUEE -> {
                            // Moving pattern banner
                            val shift = ((time * 3f + c) % cols).toInt()
                            if (shift % 3 == 0 || (shift % 5 == 0 && c > 10)) 5 else 2
                        }
                        DotMatrixMode.TECH_HUD -> {
                            // Equalized tech metric steps
                            when {
                                c < 8 -> (deviceStatus.battery.leftPercent / 100f * rows).toInt()
                                c in 9..16 -> (deviceStatus.ultraBassLevel / 5f * rows).toInt()
                                c in 18..25 -> if (deviceStatus.isGameModeEnabled) 6 else 2
                                else -> (mediaState.volumePercent / 100f * rows).toInt()
                            }
                        }
                    }

                    for (r in 0 until rows) {
                        val invertedRow = rows - 1 - r
                        val isLit = invertedRow < activeRows
                        val color = if (isLit) activeColor else inactiveColor

                        val cx = c * cellW + cellW / 2
                        val cy = r * cellH + cellH / 2

                        drawCircle(
                            color = color,
                            radius = dotRadius,
                            center = Offset(cx, cy)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Matrix Subtitle & Codec Tag
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (activeMode) {
                    DotMatrixMode.SPECTRUM -> if (mediaState.isPlaying) "REALTIME 32-CH AUDIO SPECTRUM" else "AUDIO ENGINE IDLE"
                    DotMatrixMode.MARQUEE -> mediaState.title.uppercase().take(28)
                    DotMatrixMode.TECH_HUD -> "BASS: +${deviceStatus.ultraBassLevel} • ANC: ${deviceStatus.ancLevel.name} • VOL: ${mediaState.volumePercent}%"
                },
                fontSize = 10.sp,
                color = CmfTextSecondary,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Default
            )

            Text(
                text = "LDAC 990k",
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                color = vibe.accent,
                fontFamily = FontFamily.Default
            )
        }
    }
}
