package com.cmf.budscontroller.tools

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
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
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var savedVolume: Int? = null

    /**
     * Finds the connected Bluetooth audio output device (earbuds).
     * Returns null if earbuds are not connected for audio output.
     */
    private fun getBluetoothAudioDevice(): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.firstOrNull { dev ->
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
    }

    /**
     * Plays high-frequency acoustic pulsating tones EXCLUSIVELY through the connected earbuds.
     * NEVER routes to the phone's built-in speaker.
     */
    fun startAcousticChirp(left: Boolean = true, right: Boolean = true) {
        if (_isChirping.value) return

        val btDevice = getBluetoothAudioDevice()
        // CRITICAL: If Bluetooth earbuds are not connected as audio output, NEVER play through phone speaker!
        if (btDevice == null && !audioManager.isBluetoothA2dpOn) {
            _isChirping.value = false
            return
        }

        _isChirping.value = true

        chirpJob = scope.launch {
            val sampleRate = 44100
            val durationSeconds = 0.45
            val numSamples = (durationSeconds * sampleRate).toInt()
            val sample = ShortArray(numSamples)

            // High-pitched acoustic chirp swept from 3.0 kHz to 5.2 kHz (piercing for locating earbuds)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val freq = 3000.0 + (2200.0 * (t / durationSeconds))
                val angle = 2.0 * Math.PI * freq * t
                sample[i] = (sin(angle) * Short.MAX_VALUE * 0.95).toInt().toShort()
            }

            // Stereo interleaved buffer with strict channel silencing
            val stereoSample = ShortArray(numSamples * 2)
            for (i in 0 until numSamples) {
                stereoSample[i * 2] = if (left) sample[i] else 0
                stereoSample[i * 2 + 1] = if (right) sample[i] else 0
            }

            var track: AudioTrack? = null
            try {
                // Save original media volume and set Bluetooth stream to max for audibility
                savedVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)

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
                    .setBufferSizeInBytes(stereoSample.size * 2 * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                // Explicitly bind output to the Bluetooth device
                if (btDevice != null) {
                    track.preferredDevice = btDevice
                }

                track.setVolume(1.0f)
                track.play()

                while (isActive) {
                    // Safety check: if routing falls back to the phone's internal speaker, ABORT immediately!
                    val routed = track.routedDevice
                    if (routed != null && (
                        routed.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                        routed.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    )) {
                        break
                    }

                    track.write(stereoSample, 0, stereoSample.size)
                    delay(300)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}

                // Restore previous media volume
                savedVolume?.let { prevVol ->
                    try {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, prevVol, 0)
                    } catch (_: Exception) {}
                    savedVolume = null
                }
                _isChirping.value = false
            }
        }
    }

    fun stopAcousticChirp() {
        chirpJob?.cancel()
        chirpJob = null
        savedVolume?.let { prevVol ->
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, prevVol, 0)
            } catch (_: Exception) {}
            savedVolume = null
        }
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
