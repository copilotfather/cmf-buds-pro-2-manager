package com.cmf.budscontroller.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqPreset(
    val name: String,
    val bands: List<Int> // 5 bands in dB (-10 to +10)
)

data class EqualizerState(
    val isEnabled: Boolean = true,
    val activePresetName: String = "Nothing Clean",
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0), // 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
    val presets: List<EqPreset> = listOf(
        EqPreset("Nothing Clean", listOf(0, 0, 0, 0, 0)),
        EqPreset("Basshead 808", listOf(8, 5, 1, 2, 4)),
        EqPreset("Vocal Clarity", listOf(-2, 1, 6, 5, 2)),
        EqPreset("Electronic / EDM", listOf(7, 3, -1, 4, 6)),
        EqPreset("Rock & Metal", listOf(5, 3, -2, 5, 5)),
        EqPreset("Acoustic Air", listOf(2, 1, 3, 4, 7))
    )
)

class EqualizerManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("cmf_eq_prefs", Context.MODE_PRIVATE)
    private var equalizer: Equalizer? = null

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun initEqualizer(audioSessionId: Int = 0) {
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = _state.value.isEnabled
            }
            applyPreset(_state.value.activePresetName)
        } catch (_: Exception) {}
    }

    fun applyPreset(presetName: String) {
        val preset = _state.value.presets.find { it.name == presetName } ?: return
        _state.value = _state.value.copy(
            activePresetName = preset.name,
            bandLevels = preset.bands
        )

        equalizer?.let { eq ->
            preset.bands.forEachIndexed { index, levelDb ->
                try {
                    val milliBels = (levelDb * 100).coerceIn(-1200, 1200)
                    eq.setBandLevel(index.toShort(), milliBels.toShort())
                } catch (_: Exception) {}
            }
        }
    }

    fun setCustomBand(bandIndex: Int, levelDb: Int) {
        val updated = _state.value.bandLevels.toMutableList()
        if (bandIndex in updated.indices) {
            val clamped = levelDb.coerceIn(-10, 10)
            updated[bandIndex] = clamped
            _state.value = _state.value.copy(
                activePresetName = "Кастомный",
                bandLevels = updated
            )

            equalizer?.let { eq ->
                try {
                    eq.setBandLevel(bandIndex.toShort(), (clamped * 100).toShort())
                } catch (_: Exception) {}
            }
        }
    }

    fun toggleEqualizer(enabled: Boolean) {
        _state.value = _state.value.copy(isEnabled = enabled)
        try {
            equalizer?.enabled = enabled
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null
    }
}
