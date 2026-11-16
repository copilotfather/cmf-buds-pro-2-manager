package com.cmf.budscontroller.tools

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.cmf.budscontroller.bluetooth.CmfBluetoothManager
import com.cmf.budscontroller.protocol.AncMode
import com.cmf.budscontroller.i18n.AppStrings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Random

enum class RelaxNoiseType {
    NONE,
    PINK_NOISE,
    BROWN_NOISE,
    WHITE_NOISE;

    val title: String
        get() = when (this) {
            NONE -> AppStrings.sleepNoiseNone
            PINK_NOISE -> AppStrings.sleepNoisePink
            BROWN_NOISE -> AppStrings.sleepNoiseBrown
            WHITE_NOISE -> AppStrings.sleepNoiseWhite
        }
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val minutesRemaining: Int = 0,
    val selectedMinutes: Int = 30,
    val noiseType: RelaxNoiseType = RelaxNoiseType.BROWN_NOISE
)

class SleepTimerManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager = CmfBluetoothManager.getInstance()

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var noiseJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startTimer(minutes: Int, noiseType: RelaxNoiseType) {
        stopTimer()
        _state.value = _state.value.copy(
            isActive = true,
            minutesRemaining = minutes,
            selectedMinutes = minutes,
            noiseType = noiseType
        )

        if (noiseType != RelaxNoiseType.NONE) {
            startNoiseGenerator(noiseType)
        }

        timerJob = scope.launch {
            var remainingSec = minutes * 60
            while (isActive && remainingSec > 0) {
                delay(1000)
                remainingSec--
                _state.value = _state.value.copy(minutesRemaining = (remainingSec + 59) / 60)

                // Smooth fadeout during last 60 seconds
                if (remainingSec <= 60 && remainingSec % 10 == 0) {
                    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (curVol > 1) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, curVol - 1, 0)
                    }
                }
            }

            bluetoothManager.setAnc(AncMode.OFF)
            stopTimer()
        }
    }

    private fun startNoiseGenerator(type: RelaxNoiseType) {
        noiseJob?.cancel()
        noiseJob = scope.launch {
            val sampleRate = 44100
            val bufferSize = sampleRate / 2
            val random = Random()
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

                track.setVolume(0.5f)
                track.play()

                var lastBrown = 0.0

                while (isActive) {
                    for (i in 0 until bufferSize) {
                        val white = (random.nextDouble() * 2.0 - 1.0)
                        val out = when (type) {
                            RelaxNoiseType.WHITE_NOISE -> white * 0.4
                            RelaxNoiseType.PINK_NOISE -> {
                                lastBrown = (lastBrown * 0.95) + (white * 0.15)
                                lastBrown * 0.5
                            }
                            RelaxNoiseType.BROWN_NOISE -> {
                                lastBrown = (lastBrown * 0.985) + (white * 0.08)
                                lastBrown * 0.8
                            }
                            else -> 0.0
                        }
                        sample[i] = (out * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
                    }

                    val stereo = ShortArray(bufferSize * 2)
                    for (i in 0 until bufferSize) {
                        stereo[i * 2] = sample[i]
                        stereo[i * 2 + 1] = sample[i]
                    }

                    track.write(stereo, 0, stereo.size)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        noiseJob?.cancel()
        timerJob = null
        noiseJob = null
        _state.value = _state.value.copy(isActive = false, minutesRemaining = 0)
    }
}
