package com.cmf.budscontroller.ui.components

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cmf.budscontroller.i18n.AppStrings
import kotlin.math.ln
import kotlin.math.sqrt

@Composable
fun CmfSpectrumVisualizer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    barCount: Int = 24
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val safeBarCount = barCount.coerceIn(8, 48)
    val hasAudioPermission = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var screenActive by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var levels by remember(safeBarCount) { mutableStateOf(List(safeBarCount) { 0f }) }
    var captureAvailable by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> screenActive = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> screenActive = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(safeBarCount, isPlaying, screenActive, hasAudioPermission) {
        if (!isPlaying || !screenActive || !hasAudioPermission) {
            levels = List(safeBarCount) { 0f }
            captureAvailable = false
            onDispose { }
        } else {
            val visualizer = try {
                Visualizer(0)
            } catch (_: Exception) {
                null
            }

            if (visualizer == null) {
                captureAvailable = false
                onDispose { }
            } else {
                captureAvailable = false
                val mainHandler = Handler(Looper.getMainLooper())
                val captureRate = Visualizer.getMaxCaptureRate().coerceAtMost(30_000)
                val listener = object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer, waveform: ByteArray, samplingRate: Int) = Unit

                    override fun onFftDataCapture(visualizer: Visualizer, fft: ByteArray, samplingRate: Int) {
                        val capturedLevels = fftBarLevels(fft, safeBarCount)
                        mainHandler.post {
                            levels = capturedLevels
                            captureAvailable = true
                        }
                    }
                }

                val started = try {
                    visualizer.setDataCaptureListener(listener, captureRate, false, true) == Visualizer.SUCCESS &&
                        visualizer.setEnabled(true) == Visualizer.SUCCESS
                } catch (_: Exception) {
                    false
                }
                if (!started) visualizer.release()

                onDispose {
                    if (started) {
                        runCatching { visualizer.setEnabled(false) }
                    }
                    mainHandler.removeCallbacksAndMessages(null)
                    if (started) visualizer.release()
                }
            }
        }
    }

    val animatedLevels = List(safeBarCount) { index ->
        val animated by animateFloatAsState(
            targetValue = levels.getOrElse(index) { 0f },
            animationSpec = tween(75),
            label = "fft_bar_$index"
        )
        animated
    }
    val accent = MaterialTheme.colorScheme.primary
    val isLive = isPlaying && screenActive && hasAudioPermission && captureAvailable

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.material3.CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.12f))
                            .border(1.dp, accent.copy(alpha = 0.28f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Equalizer, contentDescription = null, modifier = Modifier.size(16.dp), tint = accent)
                    }
                    Column {
                        Text(
                            text = if (AppStrings.isRussian) "СПЕКТРОАНАЛИЗАТОР" else "SPECTRUM ANALYZER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when {
                                !isPlaying -> if (AppStrings.isRussian) "Ожидание воспроизведения" else "Waiting for playback"
                                !hasAudioPermission -> if (AppStrings.isRussian) "Нужен доступ к аудио" else "Audio permission required"
                                !screenActive -> if (AppStrings.isRussian) "Пауза в фоне" else "Paused in background"
                                isLive -> if (AppStrings.isRussian) "Анализ системного аудио" else "System audio capture"
                                else -> if (AppStrings.isRussian) "Захват недоступен" else "Capture unavailable"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isLive) Color(0x3000E676) else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, if (isLive) Color(0xFF00E676) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isLive) "LIVE" else "IDLE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLive) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0C0E14))
                    .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                val spacing = 3.dp.toPx()
                val barWidth = (size.width - spacing * (safeBarCount - 1)) / safeBarCount
                val baseline = 3.dp.toPx()

                animatedLevels.forEachIndexed { index, level ->
                    val height = (level * (size.height - baseline)).coerceAtLeast(2.dp.toPx())
                    val left = index * (barWidth + spacing)
                    val top = size.height - height
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(accent, Color(0xFF00E676)),
                            startY = top,
                            endY = size.height
                        ),
                        topLeft = Offset(left, top),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }
        }
    }
}

private fun fftBarLevels(fft: ByteArray, barCount: Int): List<Float> {
    if (fft.size < 4) return List(barCount) { 0f }
    val maxBin = (fft.size / 2 - 1).coerceAtLeast(1)
    val logMaxBin = ln(maxBin.toDouble())

    return List(barCount) { index ->
        val startBin = kotlin.math.exp(logMaxBin * index / barCount).toInt().coerceIn(1, maxBin)
        val endBin = if (index == barCount - 1) {
            maxBin + 1
        } else {
            kotlin.math.exp(logMaxBin * (index + 1) / barCount).toInt().coerceIn(startBin + 1, maxBin + 1)
        }
        var peak = 0f
        for (bin in startBin until endBin) {
            val real = fft[bin * 2].toInt()
            val imaginary = fft[bin * 2 + 1].toInt()
            peak = maxOf(peak, sqrt((real * real + imaginary * imaginary).toFloat()))
        }
        (peak / 180f).coerceIn(0f, 1f)
    }
}
