package com.cmf.budscontroller.tools

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin

data class DisconnectLocation(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val formattedTime: String
)

class FindMyBudsManager(private val context: Context) {

    private val _isChirping = MutableStateFlow(false)
    val isChirping: StateFlow<Boolean> = _isChirping.asStateFlow()

    private val _lastLocation = MutableStateFlow<DisconnectLocation?>(null)
    val lastLocation: StateFlow<DisconnectLocation?> = _lastLocation.asStateFlow()

    private var chirpJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Plays high-frequency acoustic pulsating tones directly through the earbuds
     * to help locate misplaced earbuds in a room.
     */
    fun startAcousticChirp(left: Boolean = true, right: Boolean = true) {
        if (_isChirping.value) return
        _isChirping.value = true

        chirpJob = scope.launch {
            val sampleRate = 44100
            val durationSeconds = 0.4
            val numSamples = (durationSeconds * sampleRate).toInt()
            val sample = ShortArray(numSamples)

            // Chirp frequency swept from 2.5 kHz to 4.5 kHz (highly audible)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val freq = 2500.0 + (2000.0 * (t / durationSeconds))
                val angle = 2.0 * Math.PI * freq * t
                sample[i] = (sin(angle) * Short.MAX_VALUE * 0.95).toInt().toShort()
            }

            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(numSamples * 2 * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.setVolume(1.0f)
                track.play()

                // Stereo interleaved buffer with channel silencing if needed
                val stereoSample = ShortArray(numSamples * 2)
                for (i in 0 until numSamples) {
                    stereoSample[i * 2] = if (left) sample[i] else 0
                    stereoSample[i * 2 + 1] = if (right) sample[i] else 0
                }

                while (isActive) {
                    track.write(stereoSample, 0, stereoSample.size)
                    delay(350)
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

    fun stopAcousticChirp() {
        chirpJob?.cancel()
        chirpJob = null
        _isChirping.value = false
    }

    @SuppressLint("MissingPermission")
    fun recordDisconnectLocation() {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc: Location? = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            loc?.let {
                val now = System.currentTimeMillis()
                val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(now))
                _lastLocation.value = DisconnectLocation(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    timestamp = now,
                    formattedTime = dateStr
                )
            }
        } catch (_: Exception) {}
    }
}
