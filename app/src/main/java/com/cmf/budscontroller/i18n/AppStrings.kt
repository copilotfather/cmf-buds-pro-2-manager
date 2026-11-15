package com.cmf.budscontroller.i18n

import java.util.Locale

object AppStrings {
    val isRussian: Boolean
        get() = Locale.getDefault().language.lowercase().startsWith("ru")

    // Tabs
    val tabOverview get() = if (isRussian) "Обзор" else "Overview"
    val tabSound get() = if (isRussian) "Звук" else "Sound"
    val tabStats get() = if (isRussian) "Статы" else "Insights"
    val tabFeatures get() = if (isRussian) "Опции" else "Features"
    val tabBridge get() = if (isRussian) "Мост" else "Bridge"

    // Top Bar Status
    val statusConnectedLdac get() = if (isRussian) "Подключено • Hi-Res LDAC" else "Connected • Hi-Res LDAC"
    val statusReady get() = if (isRussian) "Готов к подключению" else "Ready to Connect"
    val actionFind get() = if (isRussian) "Найти" else "Find"

    // Section Headers
    val headerNoiseControl get() = if (isRussian) "ШУМОПОДАВЛЕНИЕ" else "NOISE CONTROL"
    val headerUltraBass get() = if (isRussian) "УЛЬТРА БАС" else "ULTRA BASS"
    val headerSmartDial get() = if (isRussian) "SMART DIAL" else "SMART DIAL"
    val headerGameMode get() = if (isRussian) "ИГРОВОЙ РЕЖИМ" else "GAME MODE"
    val headerSonar get() = if (isRussian) "ПОИСК НАУШНИКОВ" else "FIND MY BUDS"
    val headerWaterEject get() = if (isRussian) "ОЧИСТКА ОТ ВОДЫ" else "WATER EJECT"
    val headerSleepTimer get() = if (isRussian) "ТАЙМЕР СНА" else "SLEEP TIMER"
    val headerVoiceWhisper get() = if (isRussian) "ГОЛОСОВОЙ ШЕПОТ" else "VOICE WHISPER"
    val headerAppUpdate get() = if (isRussian) "ОБНОВЛЕНИЕ ПРИЛОЖЕНИЯ (GITHUB OTA)" else "APP UPDATE (GITHUB OTA)"
    val headerAudioBridge get() = if (isRussian) "БЕСПРОВОДНОЙ АУДИО МОСТ" else "WIRELESS AUDIO BRIDGE"
    val headerDeepAnalytics get() = if (isRussian) "АНАЛИТИКА И СТАТИСТИКА ИСПОЛЬЗОВАНИЯ" else "LISTENING ANALYTICS & INSIGHTS"

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
