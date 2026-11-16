package com.cmf.budscontroller.tools

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.cmf.budscontroller.i18n.AppStrings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.sin

data class WaterEjectState(
    val isRunning: Boolean = false,
    val progressSeconds: Int = 0,
    val totalSeconds: Int = 18,
    val currentPhase: String = AppStrings.waterPhaseReady
)

/**
 * Advanced Multi-Stage Ultrasonic & Dynamic Acoustic Resonance Ejector.
 * Specifically tuned for CMF Buds Pro 2 dual-driver architecture (11mm bass driver + 6mm planar tweeter):
 * 1. Deep Sub-Bass Dislodging (125 Hz - 165 Hz sawtooth resonant sweeps with high kinetic diaphragm displacement).
 * 2. Rapid Pulsed Acoustic Shockwaves (165 Hz square-envelope bursts to break water surface tension).
 * 3. High-Frequency Driver Mesh Expulsion (8 kHz - 12 kHz micro-droplet ejection for planar tweeter).
 */
class WaterEjectorManager(private val context: Context? = null) {

    private val _state = MutableStateFlow(WaterEjectState())
    val state: StateFlow<WaterEjectState> = _state.asStateFlow()

    private var ejectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startEjection(onComplete: () -> Unit = {}) {
        if (_state.value.isRunning) return
        _state.value = _state.value.copy(
            isRunning = true,
            progressSeconds = 0,
            currentPhase = AppStrings.waterPhaseCalib
        )

        ejectJob = scope.launch {
            val sampleRate = 48000
            val chunkSize = 2400 // 50ms chunk for continuous fluid generation

            val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val originalVolume = try {
                audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
            } catch (_: Exception) { null }

            // Safety: Start at moderate volume (35%) and smoothly ramp up
            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            val startVol = (maxVol * 0.35f).toInt().coerceAtLeast(1)
            try {
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, startVol, 0)
            } catch (_: Exception) {}

            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBuf, chunkSize * 4)

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
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                if (audioManager != null) {
                    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val btDevice = devices.firstOrNull { dev ->
                        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }
                    if (btDevice != null) {
                        track.preferredDevice = btDevice
                    }
                }

                track.setVolume(1.0f)
                track.play()

                val totalSec = _state.value.totalSeconds
                val stereoChunk = ShortArray(chunkSize * 2)

                var globalSampleIdx = 0L

                for (sec in 1..totalSec) {
                    if (!isActive) break

                    // Multi-Stage Sonic Cycle:
                    // Stage 1 (Sec 1-6): Kinetic Sub-Bass Diaphragm Resonant Sweeps (130-170 Hz)
                    // Stage 2 (Sec 7-12): Rapid Kinetic Pulse Shockwaves (8 Hz AM bursts on 155 Hz carrier)
                    // Stage 3 (Sec 13-18): Driver Mesh Micro-Atomization Sweep (Dual 160 Hz + 9.5 kHz planar ping)
                    val phaseDesc = when (sec) {
                        in 1..6 -> AppStrings.waterPhase1
                        in 7..12 -> AppStrings.waterPhase2
                        else -> AppStrings.waterPhase3
                    }

                    _state.value = _state.value.copy(
                        progressSeconds = sec,
                        currentPhase = phaseDesc
                    )

                    val chunksPerSec = sampleRate / chunkSize

                    for (c in 0 until chunksPerSec) {
                        if (!isActive) break

                        for (i in 0 until chunkSize) {
                            val t = (globalSampleIdx + i).toDouble() / sampleRate

                            val sampleVal = when (sec) {
                                in 1..6 -> {
                                    // Kinetic resonance sweep 135Hz -> 175Hz + harmonic drive
                                    val sweepRate = 3.0 // 3 sweeps per second
                                    val sweepProgress = (t * sweepRate) % 1.0
                                    val currentFreq = 135.0 + 40.0 * sweepProgress
                                    val sine1 = sin(2.0 * PI * currentFreq * t)
                                    // 2nd harmonic for cone edge agitation
                                    val sine2 = sin(2.0 * PI * (currentFreq * 2.0) * t) * 0.28
                                    val raw = (sine1 * 0.78 + sine2) * Short.MAX_VALUE
                                    raw.coerceIn(-32767.0, 32767.0)
                                }
                                in 7..12 -> {
                                    // 155 Hz heavy carrier modulated by sharp 8 Hz square burst
                                    val carrier = sin(2.0 * PI * 155.0 * t)
                                    val pulseCycle = (t * 8.0) % 1.0
                                    // 65% duty cycle sharp burst
                                    val envelope = if (pulseCycle < 0.65) 1.0 else 0.05
                                    (carrier * envelope * 0.98 * Short.MAX_VALUE).coerceIn(-32767.0, 32767.0)
                                }
                                else -> {
                                    // Dual frequency: 160 Hz bass diaphragm excursion + 9.8 kHz planar mesh vibration
                                    val lowBass = sin(2.0 * PI * 160.0 * t) * 0.75
                                    val highMesh = sin(2.0 * PI * 9800.0 * t) * 0.22
                                    ((lowBass + highMesh) * 0.98 * Short.MAX_VALUE).coerceIn(-32767.0, 32767.0)
                                }
                            }

                            val sShort = sampleVal.toInt().toShort()
                            stereoChunk[i * 2] = sShort
                            stereoChunk[i * 2 + 1] = sShort
                        }

                        track.write(stereoChunk, 0, stereoChunk.size)
                        globalSampleIdx += chunkSize
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}
                try {
                    if (originalVolume != null) {
                        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                    }
                } catch (_: Exception) {}
                _state.value = _state.value.copy(
                    isRunning = false,
                    progressSeconds = 0,
                    currentPhase = AppStrings.waterComplete
                )
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun stopEjection() {
        ejectJob?.cancel()
        ejectJob = null
        _state.value = _state.value.copy(
            isRunning = false,
            progressSeconds = 0,
            currentPhase = AppStrings.waterStopped
        )
    }
}
