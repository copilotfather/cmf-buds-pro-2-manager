package com.cmf.budscontroller.analytics

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HearingStatus(
    val sessionMinutes: Int,
    val dailyListeningMinutes: Int,
    val dailySafeDosePercent: Int,
    val currentVolumePercent: Int,
    val isRestRecommended: Boolean,
    val restThresholdMinutes: Int = 50
)

class HearingProtectionManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _status = MutableStateFlow(
        HearingStatus(
            sessionMinutes = 0,
            dailyListeningMinutes = 0,
            dailySafeDosePercent = 0,
            currentVolumePercent = 50,
            isRestRecommended = false
        )
    )
    val status: StateFlow<HearingStatus> = _status.asStateFlow()

    private var sessionStartTime: Long = System.currentTimeMillis()
    private var accumulatedDailyMinutes: Int = 0

    fun updateMetrics(isBudsConnected: Boolean) {
        if (!isBudsConnected) {
            sessionStartTime = System.currentTimeMillis()
            _status.value = _status.value.copy(sessionMinutes = 0, isRestRecommended = false)
            return
        }

        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumePercent = if (maxVol > 0) (currentVol * 100) / maxVol else 50

        val now = System.currentTimeMillis()
        val sessionMins = ((now - sessionStartTime) / 60000).toInt()

        // Exposure dose calculation based on volume curve (exponential above 65%)
        // WHO standard: >80 dB causes accelerated hearing fatigue
        val doseWeight = when {
            volumePercent > 85 -> 3.0f // Dangerous high volume
            volumePercent > 70 -> 1.5f // Elevated volume
            volumePercent > 50 -> 1.0f // Moderate
            else -> 0.5f               // Safe whisper volume
        }

        val safeDose = ((accumulatedDailyMinutes + sessionMins) * doseWeight / 4.8f).toInt().coerceIn(0, 150)
        val restAlert = sessionMins >= _status.value.restThresholdMinutes

        _status.value = _status.value.copy(
            sessionMinutes = sessionMins,
            dailyListeningMinutes = accumulatedDailyMinutes + sessionMins,
            dailySafeDosePercent = safeDose,
            currentVolumePercent = volumePercent,
            isRestRecommended = restAlert
        )
    }

    fun resetSession() {
        sessionStartTime = System.currentTimeMillis()
        _status.value = _status.value.copy(sessionMinutes = 0, isRestRecommended = false)
    }
}
