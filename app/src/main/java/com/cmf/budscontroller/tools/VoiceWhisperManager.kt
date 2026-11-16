package com.cmf.budscontroller.tools

import android.content.Context
import android.speech.tts.TextToSpeech
import com.cmf.budscontroller.i18n.AppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class WhisperSettings(
    val announceOnConnect: Boolean = false,
    val announceHourlyTime: Boolean = false,
    val speakRemainingHours: Boolean = false
)

class VoiceWhisperManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false

    private val _settings = MutableStateFlow(WhisperSettings())
    val settings: StateFlow<WhisperSettings> = _settings.asStateFlow()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val loc = if (AppStrings.isRussian) Locale("ru", "RU") else Locale.US
            tts?.language = loc
            isInitialized = true
        }
    }

    fun speakBatteryStatus(left: Int, right: Int, remainingTimeText: String) {
        if (!_settings.value.announceOnConnect || !isInitialized) return
        val loc = if (AppStrings.isRussian) Locale("ru", "RU") else Locale.US
        tts?.language = loc
        val minBud = minOf(left, right)
        val text = if (AppStrings.isRussian) {
            if (_settings.value.speakRemainingHours) {
                "Наушники подключены. Заряд $minBud процентов. Осталось слушать $remainingTimeText."
            } else {
                "Наушники подключены. Заряд $minBud процентов."
            }
        } else {
            if (_settings.value.speakRemainingHours) {
                "Earbuds connected. Battery at $minBud percent. Estimated listening time: $remainingTimeText."
            } else {
                "Earbuds connected. Battery at $minBud percent."
            }
        }
        speak(text)
    }

    fun speak(text: String) {
        if (!isInitialized) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cmf_whisper")
    }

    fun toggleAnnounceOnConnect(enabled: Boolean) {
        _settings.value = _settings.value.copy(announceOnConnect = enabled)
    }

    fun toggleAnnounceHourlyTime(enabled: Boolean) {
        _settings.value = _settings.value.copy(announceHourlyTime = enabled)
    }

    fun toggleSpeakHours(enabled: Boolean) {
        _settings.value = _settings.value.copy(speakRemainingHours = enabled)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
