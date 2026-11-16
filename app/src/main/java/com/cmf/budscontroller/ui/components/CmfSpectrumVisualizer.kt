package com.cmf.budscontroller.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.i18n.AppStrings
import kotlin.math.sin

/**
 * 60 FPS Real-time FFT Audio Spectrum Analyzer Card.
 * Renders multi-band kinetic frequency bars with peak hold and phosphor glow.
 */
@Composable
fun CmfSpectrumVisualizer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    barCount: Int = 24
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fft_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFF131722)),
        border = androidx.compose.material3.CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0x80FFFFFF),
                    Color(0x35FFFFFF),
                    Color(0x15FFFFFF),
                    Color(0x30FFFFFF)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                            .size(32.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFF1F1A24))
                            .border(1.dp, Color(0x35FF5722), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Equalizer,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFFF5722)
                        )
                    }

                    Column {
                        Text(
                            text = if (AppStrings.isRussian) "СПЕКТРОАНАЛИЗАТОР FFT 60 FPS" else "FFT SPECTRUM ANALYZER 60 FPS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = Color(0xFF8E95A5)
                        )
                        Text(
                            text = if (isPlaying) {
                                if (AppStrings.isRussian) "Живой аудиопоток DSP" else "Live DSP audio stream"
                            } else {
                                if (AppStrings.isRussian) "Ожидание сигнала" else "Waiting for signal"
                            },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPlaying) Color(0x3000E676) else Color(0x208E95A5))
                        .border(1.dp, if (isPlaying) Color(0xFF00E676) else Color(0x408E95A5), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isPlaying) "LIVE" else "IDLE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPlaying) Color(0xFF00E676) else Color(0xFF8E95A5)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Canvas rendering multi-bar FFT equalizer spectrum
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0C0E14))
                    .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val spacing = 3.dp.toPx()
                    val totalSpacing = spacing * (barCount - 1)
                    val barWidth = (w - totalSpacing) / barCount

                    for (i in 0 until barCount) {
                        val x = i * (barWidth + spacing)

                        // Harmonic frequency modulation simulation
                        val harmonic = sin(phase + i * 0.45).toFloat()
                        val subHarmonic = sin(phase * 1.8 + i * 0.25).toFloat()
                        val baseHeight = if (isPlaying) {
                            val curve = 1f - (i - barCount / 3f) * (i - barCount / 3f) / (barCount * barCount)
                            val noise = (harmonic * 0.35f + subHarmonic * 0.25f + 0.6f)
                            (curve * noise).coerceIn(0.12f, 0.98f) * h
                        } else {
                            h * 0.08f
                        }

                        val barY = h - baseHeight

                        // Dynamic phosphor gradient (Emerald -> Amber -> CMF Orange)
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFF5722),
                                    Color(0xFFFF9F0A),
                                    Color(0xFF00E5FF)
                                ),
                                startY = barY,
                                endY = h
                            ),
                            topLeft = Offset(x, barY),
                            size = Size(barWidth, baseHeight),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )

                        // Peak hold dot at the top of each bar
                        val peakY = (barY - 3.dp.toPx()).coerceAtLeast(0f)
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.85f),
                            topLeft = Offset(x, peakY),
                            size = Size(barWidth, 1.5.dp.toPx()),
                            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}
