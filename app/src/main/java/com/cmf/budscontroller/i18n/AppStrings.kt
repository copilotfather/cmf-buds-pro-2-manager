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
    val doneBtn get() = if (isRussian) "Готово" else "Done"

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
    val headerSmartDial get() = if (isRussian) "КОЛЕСО SMART DIAL" else "SMART DIAL"
    val smartDialRotateHeader get() = if (isRussian) "ДЕЙСТВИЕ ПРИ ВРАЩЕНИИ КОЛЕСИКА:" else "DIAL ROTATION ACTION:"
    val headerGameMode get() = if (isRussian) "ИГРОВОЙ РЕЖИМ" else "GAME MODE"
    val headerSonar get() = if (isRussian) "ПОИСК НАУШНИКОВ" else "FIND MY BUDS"
    val headerWaterEject get() = if (isRussian) "ОЧИСТКА ОТ ВОДЫ" else "WATER EJECT"
    val headerSleepTimer get() = if (isRussian) "ТАЙМЕР СНА" else "SLEEP TIMER"
    val headerVoiceWhisper get() = if (isRussian) "ГОЛОСОВОЙ ШЕПОТ" else "VOICE WHISPER"
    val headerAppUpdate get() = if (isRussian) "ОБНОВЛЕНИЕ ПРИЛОЖЕНИЯ" else "APP UPDATE"
    val headerAudioBridge get() = if (isRussian) "БЕСПРОВОДНОЙ АУДИО МОСТ" else "WIRELESS AUDIO BRIDGE"
    val headerDeepAnalytics get() = if (isRussian) "АНАЛИТИКА И СТАТИСТИКА" else "ANALYTICS & STATS"
    val headerEqualizer get() = if (isRussian) "СТУДИЙНЫЙ ЭКВАЛАЙЗЕР" else "STUDIO EQUALIZER"
    val headerSpatialAudio get() = if (isRussian) "ПРОСТРАНСТВЕННОЕ АУДИО 360°" else "SPATIAL AUDIO 360°"
    val headerChannelBalance get() = if (isRussian) "БАЛАНС КАНАЛОВ И УСИЛЕНИЕ" else "CHANNEL BALANCE & BOOST"
    val headerSmartFeatures get() = if (isRussian) "ИНТЕЛЛЕКТУАЛЬНЫЕ ФУНКЦИИ" else "INTELLIGENT FEATURES"
    val headerCodec get() = if (isRussian) "BLUETOOTH АУДИОКОДЕК" else "BLUETOOTH AUDIO CODEC"
    val headerSonicLab get() = if (isRussian) "ADVANCED AUDIO LAB" else "ADVANCED AUDIO LAB"

    // Noise Control Modes & Levels
    val ancOff get() = if (isRussian) "Выкл" else "Off"
    val ancTransparency get() = if (isRussian) "Прозрачность" else "Transparency"
    val ancCancellation get() = if (isRussian) "Шумоподавление" else "Noise Cancelling"
    val ancDepthLabel get() = if (isRussian) "Уровень шумоподавления" else "Noise cancellation level"
    val ancLevelHigh get() = if (isRussian) "Высокое" else "High"
    val ancLevelMid get() = if (isRussian) "Среднее" else "Mid"
    val ancLevelLow get() = if (isRussian) "Слабое" else "Low"
    val ancLevelAdaptive get() = if (isRussian) "Адаптивное" else "Adaptive"

    // Bento Card Subtitles
    val ultraBassLevel get() = if (isRussian) "Уровень" else "Level"
    val off get() = if (isRussian) "Выкл" else "Off"
    val gameModeActive get() = if (isRussian) "Включен (Низкий пинг)" else "Enabled (Low Latency)"
    val gameModeInactive get() = if (isRussian) "Выключен" else "Disabled"
    val sonarBeeping get() = if (isRussian) "Звучит сигнал..." else "Playing tone..."
    val sonarPlaySound get() = if (isRussian) "Подать сигнал" else "Play Sound"

    // Battery & Prediction
    val earbudBatteryPrefix get() = if (isRussian) "Наушники:" else "Earbuds:"
    val caseBatteryPrefix get() = if (isRussian) "С кейсом:" else "With case:"
    val batteryCase get() = if (isRussian) "КЕЙС" else "CASE"
    val batteryLeft get() = if (isRussian) "ЛЕВЫЙ" else "LEFT"
    val batteryRight get() = if (isRussian) "ПРАВЫЙ" else "RIGHT"
    val chargingLabel get() = if (isRussian) "Зарядка" else "Charging"

    // Features Tab
    val autoAncTitle get() = if (isRussian) "АДАПТИВНЫЙ АВТО-ANC" else "ADAPTIVE AUTO-ANC"
    val autoAncActive get() = if (isRussian) "Активен" else "Active"
    val autoAncInactive get() = if (isRussian) "Калибровка по микрофону" else "Auto-tune via mic"
    val waterEjectTitle get() = if (isRussian) "ОЧИСТКА ОТ ВОДЫ И ПЫЛИ" else "SONIC WATER EJECT"
    val waterEjectSubtitle get() = if (isRussian) "Акустический срыв натяжения (18 сек)" else "Acoustic resonance ejection (18s)"
    val btnStopEject get() = if (isRussian) "Остановить очистку" else "Stop Ejection"
    val btnStartEject get() = if (isRussian) "Запустить резонансную очистку (18 сек)" else "Start Resonance Ejection (18s)"

    val sleepTimerTitle get() = if (isRussian) "ТАЙМЕР СНА И РЕЛАКС" else "SLEEP TIMER & RELAX"
    val sleepTimerSubtitle get() = if (isRussian) "Звуки природы и авто-выключение" else "Ambient sounds & auto-stop"
    val sleepRemainingLabel get() = if (isRussian) "Осталось:" else "Remaining:"

    val voiceWhisperTitle get() = if (isRussian) "ГОЛОСОВОЙ ШЕПОТ" else "VOICE WHISPER"
    val voiceWhisperSubtitle get() = if (isRussian) "Озвучивание заряда в ухо" else "Voice battery prompts in ear"

    // Dialog Titles & Messages
    val selectDeviceTitle get() = if (isRussian) "Выберите устройство" else "Select Device"
    val noBondedDevices get() = if (isRussian) 
        "Нет сопряжённых Bluetooth устройств. Подключите CMF Buds Pro 2 в настройках телефона." 
        else "No paired Bluetooth devices. Pair your CMF Buds in Android Settings first."
    val settingsTitle get() = if (isRussian) "Настройки" else "Settings"
    val settingsLanguage get() = if (isRussian) "Язык интерфейса" else "Interface Language"
    val settingsAuto get() = if (isRussian) "Системный" else "System Default"
    val settingsEnglish get() = "English"
    val settingsRussian get() = "Русский"
    val settingsVersion get() = if (isRussian) "Версия ПО" else "App Version"
    val settingsCheckUpdates get() = if (isRussian) "Проверить обновления" else "Check for Updates"
    val settingsAuthor get() = if (isRussian) "Разработчик" else "Developer"

    // Subtitles & Stats
    val volDistributionTitle get() = if (isRussian) "РАСПРЕДЕЛЕНИЕ ГРОМКОСТИ" else "VOLUME DISTRIBUTION"
    val volLevelsTitle get() = if (isRussian) "Уровни громкости" else "Sound Levels"
    val volTotal get() = if (isRussian) "Всего:" else "Total:"
    val minUnit get() = if (isRussian) "мин" else "min"
    val hourUnit get() = if (isRussian) "ч" else "h"
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

    // Audio Tweaks & Spatial
    val balanceLabel get() = if (isRussian) "Баланс:" else "Balance:"
    val boostLabel get() = if (isRussian) "Буст:" else "Boost:"
    val vocalLabel get() = if (isRussian) "Вокал:" else "Vocal:"
    val spatialDirect get() = if (isRussian) "Стерео Direct" else "Stereo Direct"
    val spatialStudio get() = "STUDIO 3D"
    val spatialHolo get() = "HOLO 360°"
    val spatialCinema get() = "CINEMA"
    val spatialDirectDesc get() = if (isRussian) "Обычное стерео" else "Direct stereo"
    val spatialStudioDesc get() = if (isRussian) "Студийная сцена" else "Studio stage"
    val spatialHoloDesc get() = if (isRussian) "Голографический зал" else "Holographic hall"
    val spatialCinemaDesc get() = if (isRussian) "Кинотеатр и диалоги" else "Cinema & dialogs"
    val spatialStage get() = if (isRussian) "СЦЕНА" else "STAGE"
    val spatialWidth get() = if (isRussian) "ШИРИНА:" else "WIDTH:"
    val spatialBtnTesting get() = if (isRussian) "ТЕСТ..." else "TESTING..."
    val spatialBtnTest get() = if (isRussian) "ПРОВЕРИТЬ" else "TEST"

    val vocalStandard get() = if (isRussian) "СТАНДАРТ" else "STANDARD"
    val vocalClear get() = if (isRussian) "ЧЕТКИЙ ВОКАЛ" else "CLEAR VOCALS"
    val vocalMax get() = if (isRussian) "МАКС. ГОЛОС" else "MAX VOICE"

    val earTipTestTitle get() = if (isRussian) "ТЕСТ ПРИЛЕГАНИЯ НАСАДОК" else "EAR TIP FIT TEST"
    val earTipTesting get() = if (isRussian) "Идет калибровка..." else "Calibrating..."
    val earTipTestingDesc get() = if (isRussian) "Слушайте тестовый сигнал в наушниках" else "Listen to the test tone in your earbuds"
    val earTipIdeal get() = if (isRussian) "ИДЕАЛЬНОЕ ПРИЛЕГАНИЕ (96%)" else "PERFECT SEAL (96%)"
    val earTipIdealDesc get() = if (isRussian) "Полная герметичность: бас и шумоподавление работают на 100%" else "Complete acoustic seal: bass and ANC perform at 100%"
    val earTipGood get() = if (isRussian) "ХОРОШАЯ ПОСАДКА (85%)" else "GOOD FIT (85%)"
    val earTipGoodDesc get() = if (isRussian) "Герметичность в норме, проверьте положение насадок" else "Normal seal: check ear tip positioning if needed"
    val earTipNotTested get() = if (isRussian) "Тест не проводился" else "Not tested yet"
    val earTipNotTestedDesc get() = if (isRussian) "Вставьте оба наушника и запустите тест" else "Insert both earbuds and run test"
    val btnRunTest get() = if (isRussian) "Запустить тест герметичности" else "Run Fit Test"

    // Equalizer
    val eqPresetCustom get() = if (isRussian) "Пользовательский" else "Custom"
    val eqPresetLabel get() = if (isRussian) "Пресет" else "Preset"

    // Advanced Audio Lab
    val labPresetCustom get() = if (isRussian) "КАСТОМ" else "CUSTOM"
    val labLatencyUltra get() = "ULTRA 10MS"
    val labLatencyBalanced get() = "BALANCED 25MS"
    val labLatencyQuality get() = "QUALITY 50MS"
    val labLatencyUltraDesc get() = if (isRussian) "Минимальная задержка для игр" else "Ultra-low latency for gaming"
    val labLatencyBalancedDesc get() = if (isRussian) "Баланс звука и задержки" else "Balanced latency & sound quality"
    val labLatencyQualityDesc get() = if (isRussian) "Максимальная стабильность" else "Max stability & bit-depth"
    val labRiskOk get() = if (isRussian) "НОРМА" else "NORMAL"
    val labRiskWarning get() = if (isRussian) "ВНИМАНИЕ" else "WARNING"
    val labRiskDanger get() = if (isRussian) "ОПАСНО" else "DANGER"

    // Nature Sounds
    val soundRain get() = if (isRussian) "Дождь" else "Rain"
    val soundWind get() = if (isRussian) "Ветер" else "Wind"
    val soundFire get() = if (isRussian) "Огонь" else "Fireplace"
    val soundOcean get() = if (isRussian) "Океан" else "Ocean"
    val soundBirds get() = if (isRussian) "Птицы" else "Forest Birds"
    val soundCity get() = if (isRussian) "Город" else "City Ambiance"
    val soundForest get() = if (isRussian) "Лес" else "Forest"

    // Codec Manager
    val codecTitleHiRes get() = if (isRussian) "BLUETOOTH АУДИОКОДЕК ВЫСОКОГО РАЗРЕШЕНИЯ" else "HI-RES BLUETOOTH AUDIO CODEC"
    val codecLdacTitle get() = if (isRussian) "Sony LDAC (Аппаратный Hi-Res)" else "Sony LDAC (Hardware Hi-Res)"
    val codecLdacDesc get() = if (isRussian) "Активирует чип LDAC в наушниках CMF. Максимальное качество студийного звука" else "Enables hardware LDAC DSP in CMF Buds. 990 kbps studio sound"
    val codecAacTitle get() = if (isRussian) "AAC (Энергосберегающий)" else "AAC (Energy Efficient)"
    val codecAacDesc get() = if (isRussian) "Стандартный сбалансированный режим наушников по умолчанию" else "Standard balanced default earbud codec"
    val codecSbcTitle get() = if (isRussian) "SBC (Базовый)" else "SBC (Basic)"
    val codecSbcDesc get() = if (isRussian) "Универсальный профиль совместимости" else "Universal fallback compatibility profile"
    val codecSysSettings get() = if (isRussian) "Системные настройки кодека" else "System Developer Options"
    val codecDialogTitle get() = if (isRussian) "Аппаратный аудиокодек наушников" else "Hardware Audio Codec"
    val codecDialogDesc get() = if (isRussian) 
        "В чип CMF Buds Pro 2 аппаратно зашиты кодеки LDAC, AAC и SBC. При выборе кодек активируется непосредственно в прошивке наушников и синхронизируется с телефоном:"
        else "CMF Buds Pro 2 hardware firmware supports LDAC, AAC, and SBC. Selected codec switches directly inside earbud DSP:"

    // Smart Dial
    val dialDialogTitle get() = if (isRussian) "Колесико Smart Dial" else "Smart Dial Configuration"
    val dialActionVolume get() = if (isRussian) "Громкость (Шаг ±1)" else "Volume (Step ±1)"
    val dialActionAnc get() = if (isRussian) "Режимы ANC (Шумодав / Прозрачность)" else "ANC Modes (Noise Cancel / Transparency)"
    val dialActionBass get() = if (isRussian) "Уровень Ultra Bass (0 .. 5)" else "Ultra Bass Level (0 .. 5)"
    val dialActionTrack get() = if (isRussian) "Перемотка треков (Вперед / Назад)" else "Track Skip (Next / Prev)"
    val dialActionPlayPause get() = if (isRussian) "Плей / Пауза трека" else "Play / Pause Track"
    val dialActionGameMode get() = if (isRussian) "Игровой режим (Low Latency)" else "Game Mode (Low Latency)"
    val dialActionMute get() = if (isRussian) "Заглушить звук" else "Mute Sound"
    val dialActionAssistant get() = if (isRussian) "Голосовой ассистент" else "Voice Assistant"

    // Sleep Timer Noise Options
    val sleepNoiseNone get() = if (isRussian) "Без звука (только таймер)" else "Mute (Timer Only)"
    val sleepNoisePink get() = if (isRussian) "Розовый шум (сон)" else "Pink Noise (Sleep)"
    val sleepNoiseBrown get() = if (isRussian) "Глубокий шум (океан / дождь)" else "Brown Noise (Deep Relaxation)"
    val sleepNoiseWhite get() = if (isRussian) "Белый шум (концентрация)" else "White Noise (Focus)"

    // Water Eject Phases
    val waterPhaseReady get() = if (isRussian) "Готов к запуску" else "Ready to start"
    val waterPhaseCalib get() = if (isRussian) "Калибровка и срыв натяжения..." else "Calibrating and disrupting tension..."
    val waterPhase1 get() = if (isRussian) "Фаза 1: Срыв поверхностного натяжения (130-170 Гц)" else "Phase 1: Surface tension disruption (130-170 Hz)"
    val waterPhase2 get() = if (isRussian) "Фаза 2: Импульсные ударные гидро-волны (8 Гц импульс)" else "Phase 2: Pulsed acoustic hydro-waves (8 Hz pulse)"
    val waterPhase3 get() = if (isRussian) "Фаза 3: Микро-очистка планарной сетки (Dual Acoustic)" else "Phase 3: Planar mesh micro-ejection (Dual Acoustic)"
    val waterComplete get() = if (isRussian) "Очистка завершена" else "Ejection complete"
    val waterStopped get() = if (isRussian) "Остановлено" else "Stopped"

    // Audio Relay Bridge
    val relayTitle get() = if (isRussian) "БЕСПРОВОДНОЙ АУДИО МОСТ" else "WIRELESS AUDIO BRIDGE"
    val relayStreamTitle get() = if (isRussian) "ТРАНСЛЯЦИЯ ЗВУКА С ПК (LOW LATENCY)" else "PC AUDIO RELAY (LOW LATENCY)"
    val relaySubtitleRunning get() = if (isRussian) "Сервер запущен (Порт 8089) • Задержка" else "Server active (Port 8089) • Latency"
    val relaySubtitleStopped get() = if (isRussian) "Передавайте звук с компьютера через USB и ADB" else "Stream PC audio over USB with ADB forwarding"
    val relayParamsLabel get() = if (isRussian) "Параметры:" else "Preset:"
    val relayGuideHeader get() = if (isRussian) "ПОШАГОВАЯ ИНСТРУКЦИЯ ПОДКЛЮЧЕНИЯ" else "STEP-BY-STEP SETUP GUIDE"
    val relayStep1 get() = if (isRussian) "Включите тумблер выше" else "Enable the toggle above"
    val relayStep1Desc get() = if (isRussian) "Телефон запустит локальный TCP сервер на порту 8089 и подготовит аудио-декодер к приёму PCM-потока 48 кГц." else "The phone will start a local TCP server on port 8089 to receive uncompressed 48 kHz PCM audio."
    val relayStep2 get() = if (isRussian) "Подключите телефон по USB" else "Connect your phone over USB"
    val relayStep2Desc get() = if (isRussian) "Включите USB-отладку, подтвердите компьютер на телефоне и запустите cmf_pc_relay_usb.bat из папки PC_Client." else "Enable USB debugging, authorize the computer on your phone, then run cmf_pc_relay_usb.bat from PC_Client."
    val relayStep3 get() = if (isRussian) "Готово! Чистый звук ПК в наушниках" else "Done! Crystal clear PC audio in your buds"
    val relayStep3Desc get() = if (isRussian) "Звук с Windows передаётся напрямую без сжатия в наушники CMF Buds Pro 2 с минимальной задержкой для игр и видео." else "Windows audio streams uncompressed directly to CMF Buds Pro 2 with minimal latency for gaming and movies."
    val relayModePcToBuds get() = if (isRussian) "ПК ➔ НАУШНИКИ" else "PC ➔ EARBUDS"
    val relayModeMicToPc get() = if (isRussian) "МИКРОФОН ➔ ПК" else "MIC ➔ PC"
    val relayModeDuplex get() = if (isRussian) "ДУПЛЕКС (ОБА)" else "DUPLEX (BOTH)"
    val relayModePcToBudsDesc get() = if (isRussian) "Звук с компьютера идет в CMF Buds" else "PC sound streams to CMF Buds"
    val relayModeMicToPcDesc get() = if (isRussian) "Микрофон наушников передается в ПК" else "Buds microphone streams to PC"
    val relayModeDuplexDesc get() = if (isRussian) "Звук игры в уши + микрофон в Discord" else "Game audio to ears + mic to Discord"

    // GitHub Updates
    val updateHeader get() = if (isRussian) "ОБНОВЛЕНИЕ ПРИЛОЖЕНИЯ (GITHUB OTA)" else "APP UPDATES (GITHUB OTA)"
    val updateChecking get() = if (isRussian) "Проверка обновлений на GitHub..." else "Checking for updates on GitHub..."
    val updateDownloading get() = if (isRussian) "Загрузка обновления:" else "Downloading update:"
    val updateAvailable get() = if (isRussian) "Доступна новая версия:" else "New version available:"
    val updateTapToInstall get() = if (isRussian) "Нажмите для установки" else "Tap to install"
    val updateUpToDate get() = if (isRussian) "У вас актуальная версия" else "You have the latest version"
    val updateDownloadBtn get() = if (isRussian) "Скачать" else "Download"
    val updateBrowserBtn get() = if (isRussian) "Браузер" else "Browser"

    // Battery Profile Strings
    val batteryProfileGaming get() = if (isRussian) "Игровой (Повышенный расход)" else "Gaming (Low Latency)"
    val batteryProfileAnc get() = if (isRussian) "Шумоподавление" else "Noise Cancellation"
    val batteryProfileTransparency get() = if (isRussian) "Прозрачность" else "Transparency"
    val batteryProfilePowerSaving get() = if (isRussian) "Энергосберегающий (ANC Выкл)" else "Power Saving (ANC Off)"
    val batteryProfileCharging get() = if (isRussian) "Зарядка в кейсе (Fast Charge)" else "Charging in case (Fast Charge)"
    val batteryProfileDischarged get() = if (isRussian) "Наушники разряжены" else "Earbuds discharged"
    val batteryProfileNotConnected get() = if (isRussian) "Наушники не подключены" else "Earbuds not connected"
    val batteryFullyCharged get() = if (isRussian) "Заряжено" else "Fully Charged"
    val batteryToHundred get() = if (isRussian) "до 100%" else "to 100%"

    // Analytics Days
    val dayMon get() = if (isRussian) "Понедельник" else "Monday"
    val dayTue get() = if (isRussian) "Вторник" else "Tuesday"
    val dayWed get() = if (isRussian) "Среда" else "Wednesday"
    val dayThu get() = if (isRussian) "Четверг" else "Thursday"
    val dayFri get() = if (isRussian) "Пятница" else "Friday"
    val daySat get() = if (isRussian) "Суббота" else "Saturday"
    val daySun get() = if (isRussian) "Воскресенье" else "Sunday"
    val dayNoData get() = if (isRussian) "Нет данных" else "No Data"
    val dayNoSessions get() = if (isRussian) "Нет сессий" else "No sessions recorded"
    val dayNotDetermined get() = if (isRussian) "Пока не определен" else "Not yet determined"

    // Ambient Noise
    val noiseQuietRoom get() = if (isRussian) "Тихая комната (<45 дБ)" else "Quiet Room (<45 dB)"
    val noiseModerate get() = if (isRussian) "Умеренный шум (45–68 дБ)" else "Moderate Noise (45–68 dB)"
    val noiseLoud get() = if (isRussian) "Шумный транспорт (>68 дБ)" else "Loud Environment (>68 dB)"
    val noiseQuietPlace get() = if (isRussian) "Тихое помещение" else "Quiet Environment"

    // Music Player
    val mediaNowPlaying get() = if (isRussian) "Сейчас играет" else "Now Playing"
    val mediaPermissionTitle get() = if (isRussian) "ПОКАЗЫВАТЬ ТРЕКИ В ПРИЛОЖЕНИИ" else "SHOW TRACKS IN APP"
    val mediaPermissionDesc get() = if (isRussian) "Нажмите, чтобы включить доступ к медиа" else "Tap to grant Notification/Media access"
    val mediaPermissionEnable get() = if (isRussian) "ВКЛЮЧИТЬ" else "ENABLE"
}
