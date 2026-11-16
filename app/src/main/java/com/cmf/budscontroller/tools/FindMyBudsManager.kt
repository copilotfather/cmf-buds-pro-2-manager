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
import android.util.Log
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

    private val TAG = "FindMyBudsManager"

    private val _isChirping = MutableStateFlow(false)
    val isChirping: StateFlow<Boolean> = _isChirping.asStateFlow()

    private val _lastLocation = MutableStateFlow<DisconnectLocation?>(null)
    val lastLocation: StateFlow<DisconnectLocation?> = _lastLocation.asStateFlow()
    private val locationPrefs = context.getSharedPreferences("buds_location", Context.MODE_PRIVATE)

    init {
        if (locationPrefs.contains("time")) {
            val time = locationPrefs.getLong("time", 0)
            _lastLocation.value = DisconnectLocation(
                java.lang.Double.longBitsToDouble(locationPrefs.getLong("lat", 0)),
                java.lang.Double.longBitsToDouble(locationPrefs.getLong("lon", 0)), time,
                java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(time))
            )
        }
    }

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
            dev.type in listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET) &&
                dev.productName.toString().lowercase().replace(Regex("[^a-z0-9]"), "") == "cmfbudspro2"
        }
    }

    /**
     * Plays acoustic pulsating tones with PROGRESSIVE VOLUME RAMP-UP.
     * To protect user hearing in case earbuds are accidentally in ears:
     * - Pulse 1: Gentle ~25% volume
     * - Pulse 2: Moderate ~50% volume
     * - Pulse 3: High ~75% volume
     * - Pulse 4+: Full search volume ~90%
     * NEVER routes to the phone's built-in speaker.
     */
    fun startAcousticChirp(left: Boolean = true, right: Boolean = true) {
        if (!left && !right) return
        if (_isChirping.value) return

        val btDevice = getBluetoothAudioDevice()
        // CRITICAL: If Bluetooth earbuds are not connected as audio output, NEVER play through phone speaker!
        if (btDevice == null) {
            _isChirping.value = false
            return
        }

        _isChirping.value = true

        chirpJob = scope.launch {
            val sampleRate = 44100
            val durationSeconds = 0.40
            val numSamples = (durationSeconds * sampleRate).toInt()
            val sample = ShortArray(numSamples)

            // High-pitched acoustic chirp swept from 2.8 kHz to 4.5 kHz with smooth envelope
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val freq = 2800.0 + (1700.0 * (t / durationSeconds))
                val angle = 2.0 * Math.PI * freq * t
                // Smooth Hann envelope at start/end to prevent harsh clicks
                val envelope = 0.5 * (1.0 - kotlin.math.cos(2.0 * Math.PI * (i.toDouble() / numSamples)))
                sample[i] = (sin(angle) * Short.MAX_VALUE * 0.75 * envelope).toInt().toShort()
            }

            // Stereo interleaved buffer with strict channel silencing
            val stereoSample = ShortArray(numSamples * 2)
            for (i in 0 until numSamples) {
                stereoSample[i * 2] = if (left) sample[i] else 0
                stereoSample[i * 2 + 1] = if (right) sample[i] else 0
            }

            var track: AudioTrack? = null
            try {
                // Save original media volume
                savedVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                // Start at gentle volume (25%) so user isn't blasted
                val rampVolumes = listOf(
                    (maxVol * 0.25f).toInt().coerceAtLeast(1),
                    (maxVol * 0.50f).toInt().coerceAtLeast(1),
                    (maxVol * 0.75f).toInt().coerceAtLeast(1),
                    (maxVol * 0.90f).toInt().coerceAtLeast(1)
                )

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

                if (btDevice != null) {
                    track.preferredDevice = btDevice
                }

                track.setVolume(1.0f)
                track.play()

                var chirpCount = 0

                while (isActive) {
                    // Progressively ramp volume over first 4 chirps
                    val targetVol = if (chirpCount < rampVolumes.size) {
                        rampVolumes[chirpCount]
                    } else {
                        rampVolumes.last()
                    }
                    try {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                    } catch (_: Exception) {}

                    // Safety check: if routing falls back to internal speaker, abort immediately!
                    val routed = track.routedDevice
                    if (getBluetoothAudioDevice() == null || (routed != null && routed.id != btDevice.id)) {
                        break
                    }

                    track.write(stereoSample, 0, stereoSample.size)
                    chirpCount++
                    delay(350)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Chirp error", e)
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}

                // Guaranteed restoration of previous media volume
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
            val loc: Location? = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }

            loc?.let {
                val now = it.time
                val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(now))
                _lastLocation.value = DisconnectLocation(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    timestamp = now,
                    formattedTime = dateStr
                )
                locationPrefs.edit().putLong("lat", java.lang.Double.doubleToLongBits(it.latitude))
                    .putLong("lon", java.lang.Double.doubleToLongBits(it.longitude)).putLong("time", now).apply()
            }
        } catch (_: Exception) {}
    }
}
