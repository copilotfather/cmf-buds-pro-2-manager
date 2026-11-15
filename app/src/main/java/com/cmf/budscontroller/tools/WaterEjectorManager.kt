package com.cmf.budscontroller.tools

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin

data class WaterEjectState(
    val isRunning: Boolean = false,
    val progressSeconds: Int = 0,
    val totalSeconds: Int = 15
)

class WaterEjectorManager {

    private val _state = MutableStateFlow(WaterEjectState())
    val state: StateFlow<WaterEjectState> = _state.asStateFlow()

    private var ejectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Emits a dynamic resonant frequency sweep (140 Hz - 180 Hz) at high amplitude
     * to physically vibrate the dual-driver acoustic diaphragm and expel moisture/dust.
     */
    fun startEjection(onComplete: () -> Unit = {}) {
        if (_state.value.isRunning) return
        _state.value = _state.value.copy(isRunning = true, progressSeconds = 0)

        ejectJob = scope.launch {
            val sampleRate = 44100
            val bufferSize = sampleRate / 2
            val sample = ShortArray(bufferSize)

            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize * 2 * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.setVolume(1.0f)
                track.play()

                val totalSec = _state.value.totalSeconds
                for (sec in 1..totalSec) {
                    if (!isActive) break

                    val baseFreq = 145.0 + (sec % 3) * 15.0
                    for (i in 0 until bufferSize) {
                        val t = i.toDouble() / sampleRate
                        val freq = baseFreq + 25.0 * sin(2.0 * Math.PI * 4.0 * t)
                        val angle = 2.0 * Math.PI * freq * t
                        val amp = sin(angle) * (Short.MAX_VALUE * 0.98)
                        sample[i] = amp.toInt().toShort()
                    }

                    val stereoBuffer = ShortArray(bufferSize * 2)
                    for (i in 0 until bufferSize) {
                        stereoBuffer[i * 2] = sample[i]
                        stereoBuffer[i * 2 + 1] = sample[i]
                    }

                    track.write(stereoBuffer, 0, stereoBuffer.size)
                    _state.value = _state.value.copy(progressSeconds = sec)
                    delay(1000)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}
                _state.value = _state.value.copy(isRunning = false, progressSeconds = 0)
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun stopEjection() {
        ejectJob?.cancel()
        ejectJob = null
        _state.value = _state.value.copy(isRunning = false, progressSeconds = 0)
    }
}
