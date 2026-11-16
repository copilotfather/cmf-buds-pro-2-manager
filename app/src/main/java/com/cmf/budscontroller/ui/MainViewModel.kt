package com.cmf.budscontroller.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cmf.budscontroller.analytics.BatteryAnalyticsManager
import com.cmf.budscontroller.analytics.BatteryPrediction
import com.cmf.budscontroller.analytics.HearingProtectionManager
import com.cmf.budscontroller.analytics.HearingStatus
import com.cmf.budscontroller.audio.AudioEffectsManager
import com.cmf.budscontroller.audio.AudioProcessingState
import com.cmf.budscontroller.audio.EqualizerManager
import com.cmf.budscontroller.audio.EqualizerState
import com.cmf.budscontroller.bluetooth.CmfBluetoothManager
import com.cmf.budscontroller.protocol.AncLevel
import com.cmf.budscontroller.protocol.AncMode
import com.cmf.budscontroller.protocol.DeviceStatus
import com.cmf.budscontroller.sensor.AmbientNoiseDetector
import com.cmf.budscontroller.sensor.AmbientNoiseState
import com.cmf.budscontroller.tools.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothManager = CmfBluetoothManager.getInstance()
    val watchBeta = com.cmf.budscontroller.watch.WatchBetaManager(application)
    val devUnlocked = MutableStateFlow(false)
    private var devAttempts = 0
    private var devRetryAfter = 0L

    fun unlockDev(password: String): Boolean {
        if (android.os.SystemClock.elapsedRealtime() < devRetryAfter) return false
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
        val expected = "65a17618b17c902e1d7fb00edc098307bd49e412bbf6834828f0e67029996d81"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val valid = java.security.MessageDigest.isEqual(digest, expected)
        if (valid) { devAttempts = 0; devUnlocked.value = true }
        else if (++devAttempts >= 5) { devRetryAfter = android.os.SystemClock.elapsedRealtime() + 30_000; devAttempts = 0 }
        return valid
    }
    fun lockDev() {
        watchBeta.enable(false)
        devUnlocked.value = false
    }
    private var connectionJob: Job? = null
    val deviceStatus: StateFlow<DeviceStatus> = bluetoothManager.deviceStatus
    val commandStatus = bluetoothManager.commandStatus
    val features = BudsFeatures(application)
    val batteryHistory = features.history
    private var profileJob: Job? = null
    private var comparisonA: List<Int>? = null
    private var comparisonB: List<Int>? = null
    val comparisonSide = MutableStateFlow("A")
    val profileMessage = MutableStateFlow("")

    fun saveProfile(id: String) {
        features.saveProfile(id, deviceStatus.value, eqState.value.bandLevels)
        profileMessage.value = if (com.cmf.budscontroller.i18n.AppStrings.isRussian) "Настройки сохранены" else "Settings saved"
    }

    fun applyProfile(id: String) {
        if (profileJob?.isActive == true) return
        if (!deviceStatus.value.isConnected) {
            profileMessage.value = if (com.cmf.budscontroller.i18n.AppStrings.isRussian) "Подключите наушники" else "Connect earbuds first"
            return
        }
        profileJob = viewModelScope.launch {
            val p = features.profile(id)
            bluetoothManager.setAnc(p.mode)
            bluetoothManager.setUltraBass(p.bass)
            bluetoothManager.setGameMode(p.game)
            equalizerManager.toggleEqualizer(true)
            p.bands.forEachIndexed { i, db -> equalizerManager.setCustomBand(i, db) }
            val result = deviceStatus.value
            val sent = result.ancMode == p.mode && result.ultraBassLevel == p.bass && result.isGameModeEnabled == p.game
            profileMessage.value = if (sent) {
                if (com.cmf.budscontroller.i18n.AppStrings.isRussian) "Профиль отправлен" else "Profile sent"
            } else if (com.cmf.budscontroller.i18n.AppStrings.isRussian) "Не все команды отправлены — проверьте соединение" else "Some commands failed — check connection"
        }
    }

    fun captureComparison(side: String) {
        if (side == "A") comparisonA = eqState.value.bandLevels.toList() else comparisonB = eqState.value.bandLevels.toList()
        comparisonSide.value = side
    }
    fun compare(side: String) {
        val bands = (if (side == "A") comparisonA else comparisonB) ?: return
        equalizerManager.toggleEqualizer(true)
        bands.forEachIndexed { i, db -> equalizerManager.setCustomBand(i, db) }
        comparisonSide.value = side
    }

    // Core Managers
    val hearingManager = HearingProtectionManager(application)
    val hearingStatus: StateFlow<HearingStatus> = hearingManager.status

    val noiseDetector = AmbientNoiseDetector()
    val noiseState: StateFlow<AmbientNoiseState> = noiseDetector.state

    val findMyBudsManager = FindMyBudsManager(application)
    val isChirping: StateFlow<Boolean> = findMyBudsManager.isChirping
    val lastLocation: StateFlow<DisconnectLocation?> = findMyBudsManager.lastLocation

    val audioEffectsManager = AudioEffectsManager(application)
    val audioState: StateFlow<AudioProcessingState> = audioEffectsManager.state

    // New Powerful Feature Managers
    val equalizerManager = EqualizerManager(application)
    val eqState: StateFlow<EqualizerState> = equalizerManager.state

    val smartDialManager = SmartDialManager(application)
    val dialConfig: StateFlow<SmartDialConfig> = smartDialManager.config

    // Advanced Audio Lab (APE/SAC/IWNM/SLG/IBP/ULE интеграция)
    val audioLab = com.cmf.budscontroller.audio.AdvancedAudioLab(application, equalizerManager)
    val labState: StateFlow<com.cmf.budscontroller.audio.AdvancedAudioLabState> = audioLab.state

    val waterEjectorManager = WaterEjectorManager(application)
    val waterState: StateFlow<WaterEjectState> = waterEjectorManager.state

    val sleepTimerManager = SleepTimerManager(application)
    val sleepState: StateFlow<SleepTimerState> = sleepTimerManager.state

    val voiceWhisperManager = VoiceWhisperManager(application)
    val updateManager = com.cmf.budscontroller.update.GitHubUpdateManager(application)
    val updateState = updateManager.updateState
    val whisperSettings: StateFlow<WhisperSettings> = voiceWhisperManager.settings

    // Music & Media Tracker
    val mediaTracker = com.cmf.budscontroller.media.CmfMediaTracker.getInstance(application)
    val mediaState: StateFlow<com.cmf.budscontroller.media.CmfMediaState> = mediaTracker.mediaState

    // 3D Spatial Audio & Ear Tip Diagnostic Engine
    val spatialAudioEngine = com.cmf.budscontroller.audio.SpatialAudioEngine(application)
    val spatialState: StateFlow<com.cmf.budscontroller.audio.SpatialAudioState> = spatialAudioEngine.state

    // Buds Usage Deep Analytics (Volume stats, Daily activity, Case opens, 100% avg duration)
    val usageAnalyticsManager = com.cmf.budscontroller.analytics.BudsUsageAnalyticsManager(application)
    val usageInsights: StateFlow<com.cmf.budscontroller.analytics.BudsUsageInsights> = usageAnalyticsManager.insights

    // USB Audio Relay Bridge (PC <-> Phone <-> CMF Buds)
    val audioRelayManager = com.cmf.budscontroller.relay.CmfAudioRelayManager(application)
    val relayState: StateFlow<com.cmf.budscontroller.relay.AudioRelayState> = audioRelayManager.state

    // High-quality audio mode on buds; Android negotiates the Bluetooth codec.
    val codecManager = com.cmf.budscontroller.audio.BluetoothCodecManager(application)
    val codecState: StateFlow<com.cmf.budscontroller.audio.CodecEngineState> = codecManager.state

    fun applyCodec(codec: com.cmf.budscontroller.audio.CodecType) {
        val dev = bluetoothManager.deviceStatus.value.let {
            if (it.deviceAddress.isNotBlank()) bluetoothManager.findCmfDevice(BluetoothAdapter.getDefaultAdapter(), it.deviceAddress)
            else null
        }
        codecManager.applyCodecToBuds(dev, codec) {}
    }

    fun openDevSettings() {
        codecManager.openSystemDeveloperOptions()
    }

    // Battery Prediction Flow
    private val _batteryPrediction = MutableStateFlow(
        BatteryAnalyticsManager.calculatePrediction(deviceStatus.value)
    )
    val batteryPrediction: StateFlow<BatteryPrediction> = _batteryPrediction.asStateFlow()

    private var hasSpokenGreeting = false

    init {
        audioEffectsManager.initEffects(0)
        equalizerManager.initEqualizer(0)
        usageAnalyticsManager.startTracking(
            isConnectedProvider = { deviceStatus.value.isConnected },
            batteryProvider = { deviceStatus.value.battery }
        )

        // Monitor connection and battery
        viewModelScope.launch {
            // Background check for GitHub updates silently
            updateManager.checkForUpdates()
        }
        viewModelScope.launch {
            var previouslyConnected = false
            deviceStatus.collect { status ->
                features.record(status)
                BatteryAnalyticsManager.recordSample(status.battery)
                val pred = BatteryAnalyticsManager.calculatePrediction(status)
                _batteryPrediction.value = pred
                hearingManager.updateMetrics(status.isConnected)

                if (status.isConnected && !hasSpokenGreeting && (status.battery.leftPercent > 0 || status.battery.rightPercent > 0)) {
                    hasSpokenGreeting = true
                    val left = if (status.battery.leftPercent > 0) status.battery.leftPercent else status.battery.rightPercent
                    val right = if (status.battery.rightPercent > 0) status.battery.rightPercent else status.battery.leftPercent
                    voiceWhisperManager.speakBatteryStatus(
                        left,
                        right,
                        pred.earbudFormattedTime
                    )
                }

                if (!status.isConnected) {
                    hasSpokenGreeting = false
                    if (previouslyConnected) findMyBudsManager.recordDisconnectLocation()
                }
                previouslyConnected = status.isConnected
            }
        }

        // Ticking loop for session and timer refresh every 15 seconds
        viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                hearingManager.updateMetrics(deviceStatus.value.isConnected)
                val pred = BatteryAnalyticsManager.calculatePrediction(deviceStatus.value)
                _batteryPrediction.value = pred
                // Синхронизация Advanced Audio Lab (SLG + IBP)
                val hearing = hearingManager.status.value
                audioLab.updateSafeListening(
                    dailyMinutes = hearing.dailyListeningMinutes,
                    dosePercent = hearing.dailySafeDosePercent,
                    currentVolumePercent = hearing.currentVolumePercent
                )
                audioLab.updateBatteryPrediction(pred.earbudMinutesLeft / 60f)
            }
        }
    }

    fun connectDevice(adapter: BluetoothAdapter?, customDevice: android.bluetooth.BluetoothDevice? = null) {
        if (connectionJob?.isActive == true) return
        if (adapter == null || !adapter.isEnabled) return
        val app = getApplication<Application>()
        val prefs = app.getSharedPreferences("cmf_prefs", android.content.Context.MODE_PRIVATE)

        if (customDevice != null) {
            if (bluetoothManager.isExcludedWatchOrNonHeadset(customDevice) || !bluetoothManager.isCmfOrNothingDevice(customDevice)) {
                return
            }
            prefs.edit().putString("last_device_address", customDevice.address).apply()
            bluetoothManager.setPreferredAddress(customDevice.address)
        }

        val savedAddr = prefs.getString("last_device_address", null)
        bluetoothManager.setPreferredAddress(savedAddr)

        // Try extracting battery from Android headset/A2DP proxy immediately
        bluetoothManager.checkProfileBattery()

        val device = customDevice ?: bluetoothManager.findCmfDevice(adapter, savedAddr) ?: return
        connectionJob = viewModelScope.launch {
            val success = bluetoothManager.connect(device, app)
            if (success) {
                prefs.edit().putString("last_device_address", device.address).apply()
            }
        }
    }

    fun reconnect() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        connectDevice(adapter)
    }

    fun getBondedDevices(adapter: BluetoothAdapter?): List<android.bluetooth.BluetoothDevice> {
        if (adapter == null || !adapter.isEnabled) return emptyList()
        return bluetoothManager.getAllBondedDevices(adapter)
    }

    fun setAncMode(mode: AncMode) {
        val currentLevel = deviceStatus.value.ancLevel
        viewModelScope.launch {
            bluetoothManager.setAnc(mode, currentLevel)
        }
    }

    fun setAncLevel(level: AncLevel) {
        val currentMode = deviceStatus.value.ancMode
        viewModelScope.launch {
            bluetoothManager.setAnc(currentMode, level)
        }
    }

    fun setUltraBass(level: Int) {
        viewModelScope.launch {
            bluetoothManager.setUltraBass(level)
        }
    }

    fun setGameMode(enabled: Boolean) {
        viewModelScope.launch {
            bluetoothManager.setGameMode(enabled)
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            bluetoothManager.refreshStatus()
        }
    }

    // Auto-ANC Toggle
    fun toggleAutoAnc(enabled: Boolean) {
        if (enabled) {
            noiseDetector.startMonitoring { recommendedMode, recommendedLevel ->
                viewModelScope.launch {
                    bluetoothManager.setAnc(recommendedMode, recommendedLevel)
                }
            }
        } else {
            noiseDetector.stopMonitoring()
        }
    }

    // Acoustic Chirp / Find Buds
    fun startChirp(left: Boolean, right: Boolean) {
        if (!deviceStatus.value.isConnected) return
        findMyBudsManager.startAcousticChirp(left, right)
    }

    fun stopChirp() {
        if (deviceStatus.value.isConnected) {
            viewModelScope.launch {
                bluetoothManager.stopFindMyBuds()
            }
        }
        findMyBudsManager.stopAcousticChirp()
    }

    // Audio Tweaks & Equalizer
    fun setChannelBalance(balance: Float) {
        audioEffectsManager.setChannelBalance(balance)
    }

    fun setLoudnessGain(milliBels: Int) {
        audioEffectsManager.setLoudnessGain(milliBels)
    }

    fun setMonoAudio(enabled: Boolean) {
        audioEffectsManager.setMonoAudio(enabled)
    }

    fun setEqPreset(name: String) {
        equalizerManager.applyPreset(name)
    }

    fun setEqBand(band: Int, levelDb: Int) {
        equalizerManager.setCustomBand(band, levelDb)
    }

    fun saveEqPreset(name: String): Boolean {
        return equalizerManager.saveCurrentAsPreset(name)
    }

    fun deleteEqPreset(name: String) {
        equalizerManager.deleteCustomPreset(name)
    }

    fun exportEqPresets(): String {
        return equalizerManager.exportPresetsToJson()
    }

    fun importEqPresets(json: String): Result<Int> {
        return equalizerManager.importPresetsFromJson(json)
    }

    fun toggleEq(enabled: Boolean) {
        equalizerManager.toggleEqualizer(enabled)
    }

    // Water & Dust Eject
    fun startWaterEject() {
        waterEjectorManager.startEjection()
    }

    fun stopWaterEject() {
        waterEjectorManager.stopEjection()
    }

    // Sleep Timer
    fun startSleepTimer(minutes: Int, noise: RelaxNoiseType) {
        sleepTimerManager.startTimer(minutes, noise)
    }

    fun stopSleepTimer() {
        sleepTimerManager.stopTimer()
    }

    // Voice Whisper
    fun toggleWhisperConnect(enabled: Boolean) {
        voiceWhisperManager.toggleAnnounceOnConnect(enabled)
    }

    fun toggleWhisperHours(enabled: Boolean) {
        voiceWhisperManager.toggleAnnounceHourlyTime(enabled)
    }

    fun testWhisperVoice() {
        val b = deviceStatus.value.battery
        val validBuds = listOfNotNull(
            if (b.leftPercent > 0) b.leftPercent else null,
            if (b.rightPercent > 0) b.rightPercent else null
        )
        val budPercent = validBuds.minOrNull() ?: 100
        voiceWhisperManager.speak(
            if (com.cmf.budscontroller.i18n.AppStrings.isRussian) {
                "Тест голоса. Заряд наушников $budPercent процентов. Все системы CMF активны."
            } else {
                "Voice test. Earbud battery is at $budPercent percent. All CMF systems are active."
            }
        )
    }

    // Smart Dial
    fun setDialRotateAction(action: DialRotateAction) {
        smartDialManager.setRotateAction(action)
    }

    fun setDialClickAction(action: DialClickAction) {
        smartDialManager.setClickAction(action)
    }

    fun simulateDialRotate(isClockwise: Boolean) {
        viewModelScope.launch {
            smartDialManager.handleRotateStep(isClockwise)
        }
    }

    fun simulateDialClick() {
        viewModelScope.launch {
            smartDialManager.handleClick()
        }
    }

    fun resetHearingSession() {
        hearingManager.resetSession()
    }

    // Media Controls
    fun togglePlayPause() {
        mediaTracker.togglePlayPause()
    }

    fun skipNextTrack() {
        mediaTracker.skipNext()
    }

    fun skipPreviousTrack() {
        mediaTracker.skipPrevious()
    }

    fun setMediaVolume(percent: Int) {
        mediaTracker.setVolumePercent(percent)
    }

    fun openNotificationSettings(context: android.content.Context) {
        mediaTracker.openNotificationAccessSettings(context)
    }

    fun refreshMediaSessions() {
        mediaTracker.checkAndInitSessions()
    }

    // Spatial 3D Audio & Ear Tip Seal Test
    fun setSpatialMode(mode: com.cmf.budscontroller.audio.SpatialMode) {
        spatialAudioEngine.setSpatialMode(mode)
    }

    fun setSoundstageWidth(width: Int) {
        spatialAudioEngine.setSoundstageWidth(width)
    }

    fun setVocalClarity(clarity: com.cmf.budscontroller.audio.VocalClarity) {
        spatialAudioEngine.setVocalClarity(clarity)
    }

    fun startEarTipSealTest() {
        spatialAudioEngine.startEarTipSealTest()
    }

    // USB Audio Relay Bridge Controls
    fun toggleAudioRelay() {
        if (relayState.value.isRunning) {
            audioRelayManager.stopRelay()
        } else {
            audioRelayManager.startRelay()
        }
    }

    fun setRelayMode(mode: com.cmf.budscontroller.relay.RelayMode) {
        audioRelayManager.setMode(mode)
    }

    fun setRelayBuffer(preset: com.cmf.budscontroller.relay.BufferPreset) {
        audioRelayManager.setBufferPreset(preset)
    }

    // Advanced Audio Lab Controls
    fun setLabBand(index: Int, gainDb: Float) = audioLab.setParametricBand(index, gainDb)
    fun setLabPreset(name: String) = audioLab.applyLabPreset(name)
    fun setLabLeakage(value: Float) = audioLab.setLeakageFactor(value)
    fun sendAncFeedback(kind: String) = audioLab.ancFeedback(kind)
    fun toggleLabLayer(layer: com.cmf.budscontroller.audio.LabNoiseLayer, enabled: Boolean) =
        audioLab.toggleNoiseLayer(layer, enabled)
    fun setLabLayerVolume(layer: com.cmf.budscontroller.audio.LabNoiseLayer, volume: Float) =
        audioLab.setLayerVolume(layer, volume)
    fun setLabMasterVolume(volume: Float) = audioLab.setMixerMasterVolume(volume)
    fun setLabMixerMinutes(minutes: Int) = audioLab.setMixerMinutes(minutes)
    fun startLabMixer() = audioLab.startMixer()
    fun stopLabMixer() = audioLab.stopMixer()
    fun setLabLatencyPreset(preset: com.cmf.budscontroller.audio.LabLatencyPreset) {
        audioLab.setLatencyPreset(preset)
        // ULLE: ULTRA включает игровой режим девайса, QUALITY/BALANCED — обычный
        setGameMode(preset == com.cmf.budscontroller.audio.LabLatencyPreset.ULTRA)
    }

    fun checkForUpdatesManual() {
        viewModelScope.launch {
            updateManager.checkForUpdates()
        }
    }

    fun startUpdateDownload() {
        val url = updateState.value.downloadUrl
        viewModelScope.launch {
            updateManager.downloadAndInstallDirectly(url)
        }
    }

    fun openUpdateInBrowser() {
        val url = updateState.value.downloadUrl
        updateManager.openInBrowser(url)
    }

    override fun onCleared() {
        watchBeta.release()
        super.onCleared()
        audioRelayManager.stopRelay()
        noiseDetector.stopMonitoring()
        findMyBudsManager.stopAcousticChirp()
        audioEffectsManager.release()
        equalizerManager.release()
        waterEjectorManager.stopEjection()
        sleepTimerManager.stopTimer()
        voiceWhisperManager.release()
        spatialAudioEngine.release()
        audioLab.release()
    }
}
