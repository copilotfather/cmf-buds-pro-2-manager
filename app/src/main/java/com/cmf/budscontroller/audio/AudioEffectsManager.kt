package com.cmf.budscontroller.audio

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioProcessingState(
    val isSystemEqEnabled: Boolean = false,
    val channelBalance: Float = 0.0f, // -1.0f (Full Left) to +1.0f (Full Right)
    val loudnessBoostMilliBels: Int = 0, // 0..1000 mB
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0), // 5 standard bands
    val isMonoAudio: Boolean = false
)

class AudioEffectsManager(private val context: Context) {

    private val TAG = "AudioEffectsManager"
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val _state = MutableStateFlow(AudioProcessingState())
    val state: StateFlow<AudioProcessingState> = _state.asStateFlow()

    fun initEffects(audioSessionId: Int = 0) {
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                enabled = true
            }
            _state.value = _state.value.copy(isSystemEqEnabled = true)
        } catch (e: Exception) {
            Log.w(TAG, "Audio effect init warning (audio session 0 might be restricted on some ROMs): ${e.message}")
        }
    }

    fun setBandLevel(bandIndex: Int, levelMilliBels: Int) {
        try {
            equalizer?.setBandLevel(bandIndex.toShort(), levelMilliBels.toShort())
            val updated = _state.value.bandLevels.toMutableList()
            if (bandIndex in updated.indices) {
                updated[bandIndex] = levelMilliBels
                _state.value = _state.value.copy(bandLevels = updated)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set EQ band", e)
        }
    }

    fun setLoudnessGain(milliBels: Int) {
        try {
            val clamped = milliBels.coerceIn(0, 800)
            loudnessEnhancer?.setTargetGain(clamped)
            _state.value = _state.value.copy(loudnessBoostMilliBels = clamped)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set loudness gain", e)
        }
    }

    fun setChannelBalance(balance: Float) {
        val clamped = balance.coerceIn(-1.0f, 1.0f)
        _state.value = _state.value.copy(channelBalance = clamped)
    }

    fun setMonoAudio(enabled: Boolean) {
        _state.value = _state.value.copy(isMonoAudio = enabled)
    }

    fun release() {
        try {
            equalizer?.release()
            loudnessEnhancer?.release()
        } catch (_: Exception) {}
        equalizer = null
        loudnessEnhancer = null
    }
}
