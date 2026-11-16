package com.cmf.budscontroller.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

object AppStrings {

    // Language mode: "auto" (system default), "en", "ru"
    var selectedLanguage by mutableStateOf("auto")

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("cmf_prefs", Context.MODE_PRIVATE)
        selectedLanguage = prefs.getString("app_language", "auto") ?: "auto"
    }

    fun setLanguage(context: Context, lang: String) {
        selectedLanguage = lang
        val prefs = context.getSharedPreferences("cmf_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_language", lang).apply()
    }

    val isRussian: Boolean
        get() = when (selectedLanguage) {
            "ru" -> true
            "en" -> false
            else -> Locale.getDefault().language.lowercase().startsWith("ru")
        }

    // Navigation Tabs
    val tabOverview get() = if (isRussian) "Обзор" else "Overview"
    val tabSound get() = if (isRussian) "Звук" else "Sound"
    val tabStats get() = if (isRussian) "Статы" else "Insights"
    val tabFeatures get() = if (isRussian) "Опции" else "Features"
    val tabBridge get() = if (isRussian) "Мост" else "Bridge"

    // Top Bar Status
    val statusConnected get() = if (isRussian) "Подключено" else "Connected"
    val statusConnectedLdac get() = if (isRussian) "Подключено • Hi-Res LDAC" else "Connected • Hi-Res LDAC"
    val statusDisconnected get() = if (isRussian) "Не подключено" else "Not Connected"
    val statusReady get() = if (isRussian) "Готов к подключению" else "Ready to Connect"
    val actionFind get() = if (isRussian) "Найти" else "Find"
    val actionDevices get() = if (isRussian) "Устройства" else "Devices"
    val actionRefresh get() = if (isRussian) "Обновить" else "Refresh"
    val actionSettings get() = if (isRussian) "Настройки" else "Settings"
    val actionLanguage get() = if (isRussian) "Язык" else "Language"

    // Disconnected State Banner
    val bannerNotConnectedTitle get() = if (isRussian) "Наушники не подключены" else "Earbuds Not Connected"
    val bannerNotConnectedSubtitle get() = if (isRussian) 
        "Включите Bluetooth и выберите наушники для управления шумоподавлением, эквалайзером и жестами." 
        else "Turn on Bluetooth and pair your earbuds to control ANC, equalizer, and gestures."
    val btnConnectNow get() = if (isRussian) "Подключить наушники" else "Connect Earbuds"

    // Hearing Safety Warnings
    val safetyWarningTitle get() = if (isRussian) "⚠️ ВНИМАНИЕ: СНИМИТЕ НАУШНИКИ" else "⚠️ CAUTION: REMOVE EARBUDS"
    val findBudsSafetyWarning get() = if (isRussian)
        "Для поиска наушников будет воспроизведён громкий звуковой сигнал на максимальной громкости с плавным нарастанием.\n\nНИКОГДА не запускайте поиск, если наушники находятся в ушах — это может вызвать дискомфорт или повредить слух!"
        else "A loud high-frequency tone will play at high volume with gradual ramp-up to help you locate your earbuds in the room.\n\nDO NOT start this while wearing the earbuds in your ears — high volume can harm your hearing!"
    val waterEjectSafetyWarning get() = if (isRussian)
        "Для очистки от воды воспроизводятся мощные низкочастотные и ультразвуковые резонансные волны.\n\nОбязательно снимите наушники с ушей перед началом очистки!"
        else "Powerful acoustic resonant waves will play to expel water droplets from driver meshes.\n\nPlease take off the earbuds from your ears before starting!"
    val btnStartSearch get() = if (isRussian) "Я снял наушники, начать" else "I removed them, Start"
    val btnStartWaterEject get() = if (isRussian) "Я снял наушники, очистить" else "I removed them, Eject"
    val btnCancel get() = if (isRussian) "Отмена" else "Cancel"
    val btnClose get() = if (isRussian) "Закрыть" else "Close"

    // Section Headers
    val headerNoiseControl get() = if (isRussian) "ШУМОПОДАВЛЕНИЕ" else "NOISE CONTROL"
    val headerUltraBass get() = if (isRussian) "УЛЬТРА БАС" else "ULTRA BASS"
    val headerSmartDial get() = if (isRussian) "SMART DIAL" else "SMART DIAL"
    val headerGameMode get() = if (isRussian) "ИГРОВОЙ РЕЖИМ" else "GAME MODE"
    val headerSonar get() = if (isRussian) "ПОИСК НАУШНИКОВ" else "FIND MY BUDS"
    val headerWaterEject get() = if (isRussian) "ОЧИСТКА ОТ ВОДЫ" else "WATER EJECT"
    val headerSleepTimer get() = if (isRussian) "ТАЙМЕР СНА" else "SLEEP TIMER"
    val headerVoiceWhisper get() = if (isRussian) "ГОЛОСОВОЙ ШЕПОТ" else "VOICE WHISPER"
    val headerAppUpdate get() = if (isRussian) "ОБНОВЛЕНИЕ ПРИЛОЖЕНИЯ" else "APP UPDATE"
    val headerAudioBridge get() = if (isRussian) "БЕСПРОВОДНОЙ АУДИО МОСТ" else "WIRELESS AUDIO BRIDGE"
    val headerDeepAnalytics get() = if (isRussian) "АНАЛИТИКА И СТАТИСТИКА" else "ANALYTICS & STATS"

    // Noise Control Modes & Levels
    val ancOff get() = if (isRussian) "Выкл" else "Off"
    val ancTransparency get() = if (isRussian) "Прозрачность" else "Transparency"
    val ancCancellation get() = if (isRussian) "Шумоподавление" else "Noise Cancelling"
    val ancLevelHigh get() = if (isRussian) "Высокое" else "High"
    val ancLevelMid get() = if (isRussian) "Среднее" else "Mid"
    val ancLevelLow get() = if (isRussian) "Слабое" else "Low"
    val ancLevelAdaptive get() = if (isRussian) "Адаптивное" else "Adaptive"

    // Bento Card Subtitles
    val ultraBassLevel get() = if (isRussian) "Уровень" else "Level"
    val gameModeActive get() = if (isRussian) "Включен (Низкий пинг)" else "Enabled (Low Latency)"
    val gameModeInactive get() = if (isRussian) "Выключен" else "Disabled"
    val sonarBeeping get() = if (isRussian) "Звучит сигнал..." else "Playing tone..."
    val sonarPlaySound get() = if (isRussian) "Подать сигнал" else "Play Sound"

    // Battery & Prediction
    val earbudBatteryPrefix get() = if (isRussian) "Наушники:" else "Earbuds:"
    val caseBatteryPrefix get() = if (isRussian) "С кейсом:" else "With case:"
    val batteryCase get() = if (isRussian) "КЕЙС" else "CASE"

    // Sound Tab
    val headerCodec get() = if (isRussian) "BLUETOOTH АУДИОКОДЕК" else "BLUETOOTH AUDIO CODEC"
    val headerEqualizer get() = if (isRussian) "СТУДИЙНЫЙ ЭКВАЛАЙЗЕР" else "STUDIO EQUALIZER"
    val headerSpatialAudio get() = if (isRussian) "ПРОСТРАНСТВЕННОЕ АУДИО 360°" else "SPATIAL AUDIO 360°"
    val headerChannelBalance get() = if (isRussian) "БАЛАНС КАНАЛОВ И УСИЛЕНИЕ" else "CHANNEL BALANCE & BOOST"

    // Features Tab
    val headerSmartFeatures get() = if (isRussian) "ИНТЕЛЛЕКТУАЛЬНЫЕ ФУНКЦИИ" else "INTELLIGENT FEATURES"
    val autoAncTitle get() = if (isRussian) "АДАПТИВНЫЙ АВТО-ANC" else "ADAPTIVE AUTO-ANC"
    val autoAncActive get() = if (isRussian) "Активен" else "Active"
    val autoAncInactive get() = if (isRussian) "Калибровка по микрофону" else "Auto-tune via mic"
    val waterEjectTitle get() = if (isRussian) "ОЧИСТКА ОТ ВОДЫ И ПЫЛИ" else "SONIC WATER EJECT"
    val waterEjectSubtitle get() = if (isRussian) "Акустический срыв натяжения (18 сек)" else "Acoustic resonance ejection (18s)"
    val sleepTimerTitle get() = if (isRussian) "ТАЙМЕР СНА И РЕЛАКС" else "SLEEP TIMER & RELAX"
    val sleepTimerSubtitle get() = if (isRussian) "Звуки природы и авто-выключение" else "Ambient sounds & auto-stop"
    val voiceWhisperTitle get() = if (isRussian) "ГОЛОСОВОЙ ШЕПОТ" else "VOICE WHISPER"
    val voiceWhisperSubtitle get() = if (isRussian) "Озвучивание заряда в ухо" else "Voice battery prompts in ear"

    // Dialog Titles
    val selectDeviceTitle get() = if (isRussian) "Выберите устройство" else "Select Device"
    val noBondedDevices get() = if (isRussian) 
        "Нет сопряжённых Bluetooth устройств. Подключите CMF Buds Pro 2 в настройках телефона." 
        else "No paired Bluetooth devices. Pair your CMF Buds in Android Settings first."

    // Subtitles & Stats
    val volDistributionTitle get() = if (isRussian) "РАСПРЕДЕЛЕНИЕ ГРОМКОСТИ" else "VOLUME DISTRIBUTION"
    val volLevelsTitle get() = if (isRussian) "Уровни громкости" else "Sound Levels"
    val volTotal get() = if (isRussian) "Всего:" else "Total:"
    val minUnit get() = if (isRussian) "мин" else "min"
    val volQuiet get() = if (isRussian) "Тихая" else "Low"
    val volMid get() = if (isRussian) "Средняя" else "Mid"
    val volLoud get() = if (isRussian) "Громкая" else "High"

    val weeklyActivityTitle get() = if (isRussian) "АКТИВНОСТЬ ПО ДНЯМ НЕДЕЛИ" else "WEEKLY ACTIVITY"
    val topDayLabel get() = if (isRussian) "Топ день:" else "Top day:"
    val caseOpensTitle get() = if (isRussian) "ОТКРЫТИЙ КЕЙСА" else "CASE OPENS"
    val todayLabel get() = if (isRussian) "раз сегодня" else "today"
    val totalLabel get() = if (isRussian) "Всего за всё время:" else "All-time total:"
    val batteryLifeTitle get() = if (isRussian) "АВТОНОМНОСТЬ" else "BATTERY LIFE"
    val batteryHealthLabel get() = if (isRussian) "Здоровье батареи:" else "Battery Health:"
    val hearingProtectionTitle get() = if (isRussian) "ЗАЩИТА СЛУХА (ВОЗ)" else "HEARING PROTECTION (WHO)"
    val doseLabel get() = if (isRussian) "ДОЗЫ" else "DOSE"
}