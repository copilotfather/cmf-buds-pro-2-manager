package com.cmf.budscontroller.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class EqPreset(
    val name: String,
    val bands: List<Int>, // 5 bands in dB (-10 to +10)
    val isCustom: Boolean = false
)

data class EqualizerState(
    val isEnabled: Boolean = true,
    val activePresetName: String = "Nothing Clean",
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0), // 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
    val presets: List<EqPreset> = defaultPresets
) {
    companion object {
        val defaultPresets = listOf(
            EqPreset("Nothing Clean", listOf(0, 0, 0, 0, 0)),
            EqPreset("Basshead 808", listOf(8, 5, 1, 2, 4)),
            EqPreset("Vocal Clarity", listOf(-2, 1, 6, 5, 2)),
            EqPreset("Electronic / EDM", listOf(7, 3, -1, 4, 6)),
            EqPreset("Rock & Metal", listOf(5, 3, -2, 5, 5)),
            EqPreset("Acoustic Air", listOf(2, 1, 3, 4, 7))
        )
    }
}

class EqualizerManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("cmf_eq_prefs", Context.MODE_PRIVATE)
    private var equalizer: Equalizer? = null

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    init {
        loadPersistedState()
    }

    private fun loadPersistedState() {
        val isEnabled = prefs.getBoolean("eq_enabled", true)
        val activeName = prefs.getString("eq_active_preset", "Nothing Clean") ?: "Nothing Clean"
        val customPresets = loadCustomPresetsFromPrefs()

        val allPresets = EqualizerState.defaultPresets + customPresets

        val activePreset = allPresets.find { it.name == activeName }
        val bands = activePreset?.bands ?: listOf(0, 0, 0, 0, 0)

        _state.value = EqualizerState(
            isEnabled = isEnabled,
            activePresetName = activeName,
            bandLevels = bands,
            presets = allPresets
        )
    }

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

        prefs.edit().putString("eq_active_preset", preset.name).apply()

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
                activePresetName = com.cmf.budscontroller.i18n.AppStrings.eqPresetCustom,
                bandLevels = updated
            )
            prefs.edit().putString("eq_active_preset", com.cmf.budscontroller.i18n.AppStrings.eqPresetCustom).apply()

            equalizer?.let { eq ->
                try {
                    eq.setBandLevel(bandIndex.toShort(), (clamped * 100).toShort())
                } catch (_: Exception) {}
            }
        }
    }

    fun saveCurrentAsPreset(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false

        val currentBands = _state.value.bandLevels
        val newPreset = EqPreset(trimmed, currentBands, isCustom = true)

        val updatedPresets = _state.value.presets.filter { it.name != trimmed } + newPreset
        _state.value = _state.value.copy(
            activePresetName = trimmed,
            presets = updatedPresets
        )

        saveCustomPresetsToPrefs(updatedPresets.filter { it.isCustom })
        prefs.edit().putString("eq_active_preset", trimmed).apply()
        return true
    }

    fun deleteCustomPreset(name: String) {
        val updatedPresets = _state.value.presets.filterNot { it.isCustom && it.name == name }
        val fallbackName = if (_state.value.activePresetName == name) "Nothing Clean" else _state.value.activePresetName
        val fallbackPreset = updatedPresets.find { it.name == fallbackName } ?: updatedPresets.first()

        _state.value = _state.value.copy(
            activePresetName = fallbackPreset.name,
            bandLevels = fallbackPreset.bands,
            presets = updatedPresets
        )
        saveCustomPresetsToPrefs(updatedPresets.filter { it.isCustom })
        applyPreset(fallbackPreset.name)
    }

    fun toggleEqualizer(enabled: Boolean) {
        _state.value = _state.value.copy(isEnabled = enabled)
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
        try {
            equalizer?.enabled = enabled
        } catch (_: Exception) {}
    }

    // --- JSON IMPORT & EXPORT (Issue #3) ---

    fun exportPresetsToJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("device", "CMF Buds Pro 2")
        root.put("activePreset", _state.value.activePresetName)

        val bandsArr = JSONArray()
        _state.value.bandLevels.forEach { bandsArr.put(it) }
        root.put("currentBands", bandsArr)

        val presetsArr = JSONArray()
        _state.value.presets.forEach { p ->
            val pObj = JSONObject()
            pObj.put("name", p.name)
            pObj.put("isCustom", p.isCustom)
            val bArr = JSONArray()
            p.bands.forEach { bArr.put(it) }
            pObj.put("bands", bArr)
            presetsArr.put(pObj)
        }
        root.put("presets", presetsArr)

        return root.toString(2)
    }

    fun importPresetsFromJson(jsonString: String): Result<Int> {
        return runCatching {
            val root = JSONObject(jsonString)
            val presetsArr = root.optJSONArray("presets") ?: return@runCatching 0
            var importedCount = 0

            val currentCustom = _state.value.presets.filter { it.isCustom }.toMutableList()

            for (i in 0 until presetsArr.length()) {
                val pObj = presetsArr.getJSONObject(i)
                val name = pObj.getString("name").trim()
                val bArr = pObj.getJSONArray("bands")
                if (bArr.length() >= 5) {
                    val bands = (0..4).map { idx -> bArr.getInt(idx).coerceIn(-10, 10) }
                    currentCustom.removeAll { it.name == name }
                    currentCustom.add(EqPreset(name, bands, isCustom = true))
                    importedCount++
                }
            }

            if (importedCount > 0) {
                val allPresets = EqualizerState.defaultPresets + currentCustom
                _state.value = _state.value.copy(presets = allPresets)
                saveCustomPresetsToPrefs(currentCustom)
            }
            importedCount
        }
    }

    private fun loadCustomPresetsFromPrefs(): List<EqPreset> {
        val jsonStr = prefs.getString("user_custom_presets", null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<EqPreset>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.getString("name")
                val bArr = obj.getJSONArray("bands")
                val bands = (0..4).map { bArr.getInt(it).coerceIn(-10, 10) }
                list.add(EqPreset(name, bands, isCustom = true))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustomPresetsToPrefs(customPresets: List<EqPreset>) {
        val arr = JSONArray()
        customPresets.forEach { p ->
            val obj = JSONObject()
            obj.put("name", p.name)
            val bArr = JSONArray()
            p.bands.forEach { bArr.put(it) }
            obj.put("bands", bArr)
            arr.put(obj)
        }
        prefs.edit().putString("user_custom_presets", arr.toString()).apply()
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null
    }
}
