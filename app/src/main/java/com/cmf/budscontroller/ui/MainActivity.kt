package com.cmf.budscontroller.ui

import com.cmf.budscontroller.i18n.AppStrings
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cmf.budscontroller.R
import com.cmf.budscontroller.analytics.BudsUsageInsights
import com.cmf.budscontroller.audio.SpatialMode
import com.cmf.budscontroller.audio.VocalClarity
import com.cmf.budscontroller.protocol.AncLevel
import com.cmf.budscontroller.protocol.AncMode
import com.cmf.budscontroller.protocol.BatteryInfo
import com.cmf.budscontroller.relay.BufferPreset
import com.cmf.budscontroller.relay.RelayMode
import com.cmf.budscontroller.tools.DialClickAction
import com.cmf.budscontroller.tools.DialRotateAction
import com.cmf.budscontroller.tools.RelaxNoiseType

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val btGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter

        checkAndRequestPermissions()

        setContent {
            // Minimalist Dark Palette
            val colorScheme = darkColorScheme(
                primary = Color(0xFFFF5722),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFF1E222B),
                onPrimaryContainer = Color(0xFFF2F2F7),
                secondary = Color(0xFFE5E5EA),
                onSecondary = Color(0xFF141416),
                background = Color(0xFF0D0F14),          // Clean Minimalist Dark Canvas
                onBackground = Color(0xFFF0F0F5),
                surface = Color(0xFF161A22),             // Clean Solid Dark Surface
                onSurface = Color(0xFFF0F0F5),
                surfaceVariant = Color(0xFF161A22),
                onSurfaceVariant = Color(0xFF8E8E93),    // Refined Subtitle
                outline = Color(0x15FFFFFF),             // Subtle Border
                outlineVariant = Color(0x0DFFFFFF)
            )

            MaterialTheme(colorScheme = colorScheme) {
                HiFiStudioBudsApp(
                    viewModel = viewModel,
                    bluetoothAdapter = bluetoothAdapter,
                    onConnectClicked = {
                        checkAndRequestPermissions()
                        viewModel.connectDevice(bluetoothAdapter)
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        permissions.add(Manifest.permission.RECORD_AUDIO)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        } else {
            viewModel.connectDevice(bluetoothAdapter)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!viewModel.deviceStatus.value.isConnected) {
            checkAndRequestPermissions()
            viewModel.connectDevice(bluetoothAdapter)
        }
    }
}

enum class StudioTab(val icon: ImageVector) {
    DEVICE(Icons.Outlined.Headphones),
    STUDIO(Icons.Outlined.GraphicEq),
    INSIGHTS(Icons.Outlined.BarChart),
    FEATURES(Icons.Outlined.Tune),
    BRIDGE(Icons.Outlined.Devices);

    val label: String
        get() = when (this) {
            DEVICE -> AppStrings.tabOverview
            STUDIO -> AppStrings.tabSound
            INSIGHTS -> AppStrings.tabStats
            FEATURES -> AppStrings.tabFeatures
            BRIDGE -> AppStrings.tabBridge
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiFiStudioBudsApp(
    viewModel: MainViewModel,
    bluetoothAdapter: BluetoothAdapter?,
    onConnectClicked: () -> Unit
) {
    val status by viewModel.deviceStatus.collectAsState()
    val batteryPred by viewModel.batteryPrediction.collectAsState()
    val dialConfig by viewModel.dialConfig.collectAsState()
    val eqState by viewModel.eqState.collectAsState()
    val spatialState by viewModel.spatialState.collectAsState()
    val audioState by viewModel.audioState.collectAsState()
    val relayState by viewModel.relayState.collectAsState()
    val noiseState by viewModel.noiseState.collectAsState()
    val isChirping by viewModel.isChirping.collectAsState()
    val waterState by viewModel.waterState.collectAsState()
    val sleepState by viewModel.sleepState.collectAsState()
    val whisperSettings by viewModel.whisperSettings.collectAsState()
    val insights by viewModel.usageInsights.collectAsState()
    val hearingStatus by viewModel.hearingStatus.collectAsState()
    val codecState by viewModel.codecState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    var currentTab by remember { mutableStateOf(StudioTab.DEVICE) }
    var showDevicePicker by remember { mutableStateOf(false) }

    // Dialogs
    var showCodecDialog by remember { mutableStateOf(false) }
    var showDialDialog by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    var showSpatialDialog by remember { mutableStateOf(false) }
    var showAudioTweaksDialog by remember { mutableStateOf(false) }
    var showRelayDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showWhisperDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showUpdatePopupDialog by remember { mutableStateOf(false) }

    // Auto-popup when update is detected
    LaunchedEffect(updateState.hasUpdate, updateState.latestVersion) {
        if (updateState.hasUpdate) {
            showUpdatePopupDialog = true
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = remember { com.cmf.budscontroller.ui.haptic.CmfHapticManager(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F14))
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "CMF Buds Pro 2",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    letterSpacing = (-0.3).sp,
                                    color = Color.White
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (status.isConnected) Color(0xFF34C759) else Color(0xFF636366))
                                    )
                                    Text(
                                        text = if (status.isConnected) AppStrings.statusConnectedLdac else AppStrings.statusReady,
                                        fontSize = 12.sp,
                                        color = if (status.isConnected) Color(0xFF34C759) else Color(0xFF636366)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1C1F28))
                                .clickable {
                                    onConnectClicked()
                                    showDevicePicker = true
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (status.isConnected) Icons.Outlined.BluetoothConnected else Icons.AutoMirrored.Outlined.BluetoothSearching,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (status.isConnected) Color(0xFFFF5722) else Color(0xFF636366)
                                )
                                Text(
                                    text = if (status.isConnected) {
                                        if (status.connectionType.isNotBlank()) status.connectionType else "Подключено"
                                    } else "Найти",
                                    fontSize = 12.sp,
                                    color = Color(0xFFE5E5EA)
                                )
                            }
                        }

                        IconButton(onClick = {
                            viewModel.refreshStatus()
                            if (!status.isConnected) onConnectClicked()
                        }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Обновить", tint = colorScheme.onSurfaceVariant)
                        }

                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Настройки", tint = colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF161A22),
                tonalElevation = 0.dp
            ) {
                val tabs = StudioTab.values()
                val selectedIndex = tabs.indexOf(currentTab)

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    val totalWidth = maxWidth
                    val tabWidth = totalWidth / tabs.size.toFloat()

                    val targetOffset = tabWidth * selectedIndex
                    val animatedOffset by animateDpAsState(
                        targetValue = targetOffset,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                        label = "bottom_bar_offset"
                    )

                    Box(
                        modifier = Modifier
                            .offset(x = animatedOffset)
                            .width(tabWidth)
                            .height(40.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF252830))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEach { tab ->
                            val isSelected = currentTab == tab
                            val iconColor by animateColorAsState(
                                targetValue = if (isSelected) Color(0xFFFF5722) else Color(0xFF636366),
                                animationSpec = tween(200),
                                label = "tab_icon_color"
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (isSelected) Color.White else Color(0xFF636366),
                                animationSpec = tween(200),
                                label = "tab_text_color"
                            )

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { currentTab = tab }
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(19.dp),
                                    tint = iconColor
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = tab.label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (currentTab) {
                StudioTab.DEVICE -> {
                    // Studio Stage: All 3 devices + Battery
                    StudioProductStage(
                        battery = status.battery,
                        earbudTimeFormatted = batteryPred.earbudFormattedTime,
                        totalWithCaseFormatted = batteryPred.totalWithCaseFormattedTime
                    )

                    // Hi-Fi Noise Control Capsule
                    StudioNoiseControlBar(
                        currentMode = status.ancMode,
                        currentLevel = status.ancLevel,
                        onModeSelected = { viewModel.setAncMode(it) },
                        onLevelSelected = { viewModel.setAncLevel(it) }
                    )

                    // Bento Grid: Bass & Smart Dial
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BentoCard(
                            modifier = Modifier.weight(1f),
                            title = "УЛЬТРА БАС",
                            subtitle = if (status.ultraBassLevel > 0) "Уровень ${status.ultraBassLevel}" else "Выключено",
                            icon = Icons.Outlined.Equalizer,
                            accentColor = colorScheme.primary,
                            onClick = {
                                val nextLvl = (status.ultraBassLevel + 1) % 6
                                viewModel.setUltraBass(nextLvl)
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                (1..5).forEach { lvl ->
                                    val active = lvl <= status.ultraBassLevel
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (active) colorScheme.primary else colorScheme.outlineVariant)
                                    )
                                }
                            }
                        }

                        BentoCard(
                            modifier = Modifier.weight(1f),
                            title = "SMART DIAL",
                            subtitle = dialConfig.rotateAction.title,
                            icon = Icons.Outlined.Tune,
                            accentColor = Color(0xFFFF9F0A),
                            onClick = { showDialDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Outlined.TouchApp, contentDescription = null, modifier = Modifier.size(13.dp), tint = colorScheme.onSurfaceVariant)
                                Text(dialConfig.clickAction.title, fontSize = 11.sp, color = colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }

                    // Bento Row: Game Mode & Sonar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BentoCard(
                            modifier = Modifier.weight(1f),
                            title = "ИГРОВОЙ РЕЖИМ",
                            subtitle = if (status.isGameModeEnabled) "Включен (Низкий пинг)" else "Выключен",
                            icon = Icons.Outlined.SportsEsports,
                            accentColor = if (status.isGameModeEnabled) Color(0xFF30D158) else colorScheme.onSurfaceVariant,
                            onClick = { viewModel.setGameMode(!status.isGameModeEnabled) }
                        )

                        BentoCard(
                            modifier = Modifier.weight(1f),
                            title = "ПОИСК НАУШНИКОВ",
                            subtitle = if (isChirping) "Звучит сигнал..." else "Подать сигнал",
                            icon = Icons.Outlined.LocationOn,
                            accentColor = if (isChirping) Color(0xFFFF453A) else colorScheme.primary,
                            onClick = {
                                if (isChirping) viewModel.stopChirp()
                                else viewModel.startChirp(left = true, right = true)
                            }
                        )
                    }
                }

                StudioTab.STUDIO -> {
                    // Hi-Res Codec Engine Card (LDAC, LHDC, aptX HD)
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "BLUETOOTH АУДИОКОДЕК ВЫСОКОГО РАЗРЕШЕНИЯ",
                        subtitle = "${codecState.activeCodec.title} • ${codecState.sampleRate} / ${codecState.bitsPerSample}",
                        icon = Icons.Outlined.GraphicEq,
                        accentColor = Color(0xFFFF9F0A),
                        onClick = { showCodecDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = codecState.bitrateMode,
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colorScheme.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "HI-RES WIRELESS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                            }
                        }
                    }

                    // Studio-grade Interactive Multi-band EQ with Morphing & JSON Export/Import (Issue #3 & #38)
                    StudioMasterEqualizerCard(
                        eqState = eqState,
                        onToggle = { viewModel.toggleEq(it) },
                        onPresetSelected = { viewModel.setEqPreset(it) },
                        onBandChanged = { band, db -> viewModel.setEqBand(band, db) },
                        onSavePreset = { viewModel.saveEqPreset(it) },
                        onDeletePreset = { viewModel.deleteEqPreset(it) },
                        onExportPresets = { viewModel.exportEqPresets() },
                        onImportPresets = { viewModel.importEqPresets(it) },
                        haptic = haptic
                    )

                    // Spatial Audio 360 Card
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "ПРОСТРАНСТВЕННОЕ АУДИО 360°",
                        subtitle = "${spatialState.mode.title} • Вокал: ${spatialState.vocalClarity.title}",
                        icon = Icons.Outlined.SpatialAudio,
                        accentColor = Color(0xFF64D2FF),
                        onClick = { showSpatialDialog = true }
                    )

                    // Balance & Boost Card
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "БАЛАНС КАНАЛОВ И УСИЛЕНИЕ",
                        subtitle = "Баланс: ${String.format("%.1f", audioState.channelBalance)} • Буст: +${audioState.loudnessBoostMilliBels / 100} dB",
                        icon = Icons.Outlined.SettingsSuggest,
                        accentColor = Color(0xFFBF5AF2),
                        onClick = { showAudioTweaksDialog = true }
                    )
                }

                StudioTab.INSIGHTS -> {
                    // Deep User Analytics Dashboard
                    BudsDeepAnalyticsDashboard(insights = insights)

                    Spacer(modifier = Modifier.height(14.dp))

                    // WHO Hearing Protection Card
                    com.cmf.budscontroller.ui.components.HearingHealthCard(
                        hearingStatus = hearingStatus,
                        onResetSession = { viewModel.resetHearingSession() }
                    )
                }

                StudioTab.FEATURES -> {
                    Text(
                        text = "ИНТЕЛЛЕКТУАЛЬНЫЕ ФУНКЦИИ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )

                    // Auto ANC
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "АДАПТИВНЫЙ АВТО-ANC",
                        subtitle = if (noiseState.isAutoAncActive) "Активен • ${noiseState.noiseEnvironment} (${noiseState.currentDecibels} дБ)" else "Автоматическая калибровка шумоподавления по микрофону",
                        icon = Icons.Outlined.AutoMode,
                        accentColor = colorScheme.primary,
                        onClick = { viewModel.toggleAutoAnc(!noiseState.isAutoAncActive) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Switch(
                                checked = noiseState.isAutoAncActive,
                                onCheckedChange = { viewModel.toggleAutoAnc(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }

                    // Water Eject
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "ОЧИСТКА ОТ ВОДЫ И ПЫЛИ (SONIC WATER EJECT)",
                        subtitle = if (waterState.isRunning) "${waterState.currentPhase} • [${waterState.progressSeconds}/${waterState.totalSeconds}с]" else "3-этапный акустический срыв натяжения (130-170 Гц + AM-импульсы + 9.8 кГц)",
                        icon = Icons.Outlined.WaterDrop,
                        accentColor = Color(0xFF0A84FF),
                        onClick = {
                            if (waterState.isRunning) viewModel.stopWaterEject()
                            else viewModel.startWaterEject()
                        }
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                if (waterState.isRunning) viewModel.stopWaterEject()
                                else viewModel.startWaterEject()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (waterState.isRunning) "Остановить очистку" else "Запустить резонансную очистку (18 сек)", fontSize = 12.sp)
                        }
                    }

                    // Sleep Timer
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "ТАЙМЕР СНА И РЕЛАКС",
                        subtitle = if (sleepState.isActive) "Осталось: ${sleepState.minutesRemaining} мин • ${sleepState.noiseType.title}" else "Фоновые звуки природы и авто-отключение",
                        icon = Icons.Outlined.Bedtime,
                        accentColor = Color(0xFF5E5CE6),
                        onClick = { showSleepTimerDialog = true }
                    )

                    // Whisper
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "ГОЛОСОВОЙ ШЕПОТ (WHISPER)",
                        subtitle = "Озвучивание заряда и времени суток в ухо",
                        icon = Icons.Outlined.RecordVoiceOver,
                        accentColor = Color(0xFFFFD60A),
                        onClick = { showWhisperDialog = true }
                    )

                    // GitHub Auto-Updater
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "ОБНОВЛЕНИЕ ПРИЛОЖЕНИЯ (GITHUB OTA)",
                        subtitle = if (updateState.isChecking) "Проверка обновлений на GitHub..."
                        else if (updateState.isDownloading) "Загрузка обновления: ${updateState.downloadProgress}%..."
                        else if (updateState.hasUpdate) "Доступна новая версия: v${updateState.latestVersion}! Нажмите для установки"
                        else "У вас актуальная версия (${viewModel.updateManager.getCurrentVersionName()})",
                        icon = Icons.Outlined.CloudDownload,
                        accentColor = if (updateState.hasUpdate) Color(0xFFFF5722) else Color(0xFF30D158),
                        onClick = {
                            if (updateState.hasUpdate) {
                                if (!updateState.isDownloading) viewModel.startUpdateDownload()
                            } else {
                                viewModel.checkForUpdatesManual()
                            }
                        }
                    ) {
                        if (updateState.hasUpdate) {
                            Spacer(modifier = Modifier.height(10.dp))
                            if (updateState.isDownloading) {
                                LinearProgressIndicator(
                                    progress = { updateState.downloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFFFF5722),
                                    trackColor = colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Загрузка APK: ${updateState.downloadProgress}%", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.startUpdateDownload() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                                    ) {
                                        Text("Скачать v${updateState.latestVersion}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.openUpdateInBrowser() },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Браузер", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                StudioTab.BRIDGE -> {
                    Text(
                        text = "БЕСПРОВОДНОЙ АУДИО МОСТ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )

                    // Card 1: Server Switch & Status
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "ТРАНСЛЯЦИЯ ЗВУКА С ПК (LOW LATENCY)",
                        subtitle = if (relayState.isRunning) "Сервер запущен (Порт ${com.cmf.budscontroller.relay.CmfAudioRelayManager.RELAY_PORT}) • Задержка ~${relayState.latencyEstimateMs} мс" else "Слушайте звук с компьютера на наушниках через Wi-Fi/USB",
                        icon = Icons.Outlined.Computer,
                        accentColor = colorScheme.primary
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showRelayDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Параметры: ${relayState.mode.title}", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = relayState.isRunning,
                                onCheckedChange = { viewModel.toggleAudioRelay() }
                            )
                        }
                    }

                    // Card 2: Interactive Step-by-Step Setup Guide
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.verticalGradient(listOf(Color(0x15FFFFFF), Color(0x08FFFFFF)))
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = Color(0xFF64D2FF), modifier = Modifier.size(20.dp))
                                Text("ПОШАГОВАЯ ИНСТРУКЦИЯ ПОДКЛЮЧЕНИЯ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF64D2FF))
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            // Step 1
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier.size(22.dp).clip(CircleShape).background(colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorScheme.primary)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Включите тумблер выше", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Телефон запустит локальный TCP сервер на порту ${com.cmf.budscontroller.relay.CmfAudioRelayManager.RELAY_PORT} и подготовит аудио-декодер к приёму PCM-потока 48 кГц.", fontSize = 11.5.sp, color = colorScheme.onSurfaceVariant)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Step 2
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier.size(22.dp).clip(CircleShape).background(colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("2", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorScheme.primary)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Выберите способ передачи (USB или Wi-Fi)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("• По USB (Ultra Low-Latency ~10-15 мс): подключите провод и запустите cmf_pc_relay_usb.bat в папке PC_Client.\n• По Wi-Fi: запустите cmf_pc_relay.bat и укажите локальный IP вашего телефона.", fontSize = 11.5.sp, color = colorScheme.onSurfaceVariant)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Step 3
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier.size(22.dp).clip(CircleShape).background(colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("3", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorScheme.primary)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Готово! Чистый звук ПК в наушниках", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Звук с Windows передаётся напрямую без сжатия в наушники CMF Buds Pro 2 с минимальной задержкой для игр и видео.", fontSize = 11.5.sp, color = colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

    // --- DIALOGS ---
    if (showCodecDialog) {
        AlertDialog(
            onDismissRequest = { showCodecDialog = false },
            icon = { Icon(Icons.Outlined.HighQuality, contentDescription = null, tint = colorScheme.primary) },
            title = { Text("Аппаратный аудиокодек наушников", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "В чип CMF Buds Pro 2 аппаратно зашиты кодеки LDAC, AAC и SBC. При выборе кодек активируется непосредственно в прошивке наушников и синхронизируется с телефоном:",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )

                    codecState.availableCodecs.forEach { codec ->
                        val isSelected = codecState.activeCodec == codec
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.applyCodec(codec)
                                    showCodecDialog = false
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceVariant
                            ),
                            border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(colorScheme.primary, colorScheme.primary))) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(codec.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        if (codec.isHiRes) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF30D158).copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("HI-RES", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF30D158))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(codec.description, fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                                    Text("${codec.sampleRate} • ${codec.bitrateInfo}", fontSize = 10.sp, color = colorScheme.primary)
                                }

                                RadioButton(selected = isSelected, onClick = {
                                    viewModel.applyCodec(codec)
                                    showCodecDialog = false
                                })
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = { viewModel.openDevSettings() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Системные настройки кодека", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCodecDialog = false }) { Text("Готово") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showDialDialog) {
        AlertDialog(
            onDismissRequest = { showDialDialog = false },
            icon = { Icon(Icons.Outlined.Tune, contentDescription = null, tint = colorScheme.primary) },
            title = { Text("Колесико Smart Dial", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Вращение колесика:", style = MaterialTheme.typography.labelLarge)
                    DialRotateAction.values().forEach { action ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setDialRotateAction(action) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = dialConfig.rotateAction == action, onClick = { viewModel.setDialRotateAction(action) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(action.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    HorizontalDivider(thickness = 0.8.dp)

                    Text("Нажатие на колесико:", style = MaterialTheme.typography.labelLarge)
                    DialClickAction.values().forEach { action ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setDialClickAction(action) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = dialConfig.clickAction == action, onClick = { viewModel.setDialClickAction(action) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(action.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialDialog = false }) { Text("Готово") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showSpatialDialog) {
        AlertDialog(
            onDismissRequest = { showSpatialDialog = false },
            icon = { Icon(Icons.Outlined.SpatialAudio, contentDescription = null, tint = colorScheme.primary) },
            title = { Text("Пространственное аудио 360°", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Режим сцены:", style = MaterialTheme.typography.labelLarge)
                    SpatialMode.values().forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setSpatialMode(mode) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = spatialState.mode == mode, onClick = { viewModel.setSpatialMode(mode) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(mode.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.8.dp)

                    Text("Четкость вокала:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        VocalClarity.values().forEach { vc ->
                            FilterChip(
                                selected = spatialState.vocalClarity == vc,
                                onClick = { viewModel.setVocalClarity(vc) },
                                label = { Text(vc.title, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSpatialDialog = false }) { Text("Готово") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showAudioTweaksDialog) {
        AlertDialog(
            onDismissRequest = { showAudioTweaksDialog = false },
            icon = { Icon(Icons.Outlined.SettingsSuggest, contentDescription = null, tint = colorScheme.primary) },
            title = { Text("Баланс каналов и громкость", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Баланс левый / правый (${String.format("%.1f", audioState.channelBalance)}):", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = audioState.channelBalance,
                        onValueChange = { viewModel.setChannelBalance(it) },
                        valueRange = -1.0f..1.0f,
                        steps = 20
                    )

                    HorizontalDivider(thickness = 0.8.dp)

                    Text("Аппаратное усиление звука (+${audioState.loudnessBoostMilliBels / 100} dB):", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = (audioState.loudnessBoostMilliBels / 100).toFloat(),
                        onValueChange = { viewModel.setLoudnessGain((it * 100).toInt()) },
                        valueRange = 0f..12f,
                        steps = 12
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAudioTweaksDialog = false }) { Text("Готово") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            icon = { Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = colorScheme.primary) },
            title = { Text("Таймер сна и релакс", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Тип шума для засыпания:", style = MaterialTheme.typography.labelLarge)
                    RelaxNoiseType.values().forEach { nt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.startSleepTimer(minutes = 30, noise = nt) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = sleepState.noiseType == nt && sleepState.isActive, onClick = { viewModel.startSleepTimer(minutes = 30, noise = nt) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(nt.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (sleepState.isActive) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { viewModel.stopSleepTimer() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Остановить таймер", color = Color.White)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepTimerDialog = false }) { Text("Готово") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showWhisperDialog) {
        AlertDialog(
            onDismissRequest = { showWhisperDialog = false },
            icon = { Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null, tint = colorScheme.primary) },
            title = { Text("Голосовой шепот (Whisper)", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Озвучивать заряд при надевании", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = whisperSettings.announceOnConnect, onCheckedChange = { viewModel.toggleWhisperConnect(it) })
                    }

                    HorizontalDivider(thickness = 0.8.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Озвучивать время каждый час", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = whisperSettings.announceHourlyTime, onCheckedChange = { viewModel.toggleWhisperHours(it) })
                    }

                    FilledTonalButton(
                        onClick = { viewModel.testWhisperVoice() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Проверить голос (Тест)")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showWhisperDialog = false }) { Text("Готово") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showRelayDialog) {
        AlertDialog(
            onDismissRequest = { showRelayDialog = false },
            icon = { Icon(Icons.Outlined.Computer, contentDescription = null, tint = colorScheme.primary) },
            title = { Text("Параметры ПК Моста", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Режим передачи:", style = MaterialTheme.typography.labelLarge)
                    RelayMode.values().forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setRelayMode(mode) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = relayState.mode == mode, onClick = { viewModel.setRelayMode(mode) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(mode.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(mode.description, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.8.dp)

                    Text("Буферизация (Задержка):", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        BufferPreset.values().forEach { bp ->
                            FilterChip(
                                selected = relayState.bufferPreset == bp,
                                onClick = { viewModel.setRelayBuffer(bp) },
                                label = { Text(bp.title, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRelayDialog = false }) { Text("Готово") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showDevicePicker) {
        val bondedDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            } else emptyList()
        } else {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        }

        AlertDialog(
            onDismissRequest = { showDevicePicker = false },
            icon = { Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = colorScheme.primary) },
            title = { Text("Выберите устройство", style = MaterialTheme.typography.titleLarge) },
            text = {
                if (bondedDevices.isEmpty()) {
                    Text(
                        "Нет сопряжённых Bluetooth устройств. Подключите CMF Buds Pro 2 в настройках телефона.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(bondedDevices) { dev ->
                            val name = dev.name ?: "Bluetooth Device"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showDevicePicker = false
                                        val prefs = context.getSharedPreferences("cmf_prefs", Context.MODE_PRIVATE)
                                        prefs.edit().putString("last_device_address", dev.address).apply()
                                        viewModel.connectDevice(bluetoothAdapter, dev)
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                                        Text(dev.address, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Outlined.Headphones, contentDescription = null, tint = colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDevicePicker = false }) { Text("Закрыть") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showSettingsDialog) {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null, tint = colorScheme.primary) },
            title = {
                Text(
                    "Настройки и О программе",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // App Information & Version
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.cmf_case_dial),
                                    contentDescription = null,
                                    modifier = Modifier.size(42.dp)
                                )
                                Column {
                                    Text(
                                        "CMF Buds Pro 2 Manager",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        "Версия: v${viewModel.updateManager.getCurrentVersionName()}",
                                        fontSize = 12.sp,
                                        color = colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Автономный центр управления для CMF Buds Pro 2 от Nothing. Прямое аппаратное BES/SPP управление, Sony LDAC Hi-Res и скандинавский минимализм.",
                                fontSize = 11.5.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Credits & Developer Info
                    Text("Разработка и Авторство", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { uriHandler.openUri("https://github.com/copilotfather") },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Автор проекта: Dmitry (copilotfather)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("github.com/copilotfather", fontSize = 11.sp, color = colorScheme.primary)
                            }
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp), tint = colorScheme.primary)
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Соавтор разработки: Antigravity AI", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Google DeepMind Agentic Assistant", fontSize = 11.sp, color = Color(0xFF00E5FF))
                            }
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF00E5FF))
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { uriHandler.openUri("https://github.com/copilotfather/cmf-buds-pro-2-manager") },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Исходный код репозитория", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("MIT License • Открытый проект", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(thickness = 0.8.dp)

                    // Quick Actions
                    Text("Обновления и Система", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

                    FilledTonalButton(
                        onClick = {
                            viewModel.checkForUpdatesManual()
                            showSettingsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Проверить обновления сейчас", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Закрыть")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Auto-Popup Dialog when an update is available
    if (showUpdatePopupDialog && updateState.hasUpdate) {
        AlertDialog(
            onDismissRequest = { showUpdatePopupDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = Color(0xFFFF5722),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Доступно обновление v${updateState.latestVersion}!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Найдена свежая версия приложения на GitHub:",
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Что нового:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFFF5722)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = updateState.releaseNotes.ifBlank { "Оптимизация под Poco X7, улучшенный Sonic Water Eject, меню настроек и скользящие стекляшки." },
                                fontSize = 11.5.sp,
                                color = colorScheme.onSurface
                            )
                        }
                    }

                    if (updateState.isDownloading) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { updateState.downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFFFF5722),
                            trackColor = colorScheme.surfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (updateState.downloadProgress < 100) "Загрузка обновления..." else "Запуск установщика...",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${updateState.downloadProgress}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5722)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.openUpdateInBrowser()
                            showUpdatePopupDialog = false
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Браузер", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.startUpdateDownload()
                        },
                        enabled = !updateState.isDownloading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (updateState.isDownloading) "Загрузка..." else "Скачать и обновить",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            dismissButton = {
                if (!updateState.isDownloading) {
                    TextButton(onClick = { showUpdatePopupDialog = false }) {
                        Text("Позже")
                    }
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// -------------------------------------------------------------
// Interactive Studio Master Equalizer Component (Graph + Sliders)
// -------------------------------------------------------------

@Composable
fun StudioMasterEqualizerCard(
    eqState: com.cmf.budscontroller.audio.EqualizerState,
    onToggle: (Boolean) -> Unit,
    onPresetSelected: (String) -> Unit,
    onBandChanged: (Int, Int) -> Unit,
    onSavePreset: (String) -> Boolean = { false },
    onDeletePreset: (String) -> Unit = {},
    onExportPresets: () -> String = { "" },
    onImportPresets: (String) -> Unit = {},
    haptic: com.cmf.budscontroller.ui.haptic.CmfHapticManager? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val effectiveHaptic = haptic ?: remember { com.cmf.budscontroller.ui.haptic.CmfHapticManager(context) }
    val bandLabels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    var showSaveDialog by remember { mutableStateOf(false) }
    var showJsonDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var jsonInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(Color(0x15FFFFFF), Color(0x08FFFFFF))))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "СТУДИЙНЫЙ ЭКВАЛАЙЗЕР",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (eqState.isEnabled) eqState.activePresetName else "Эквалайзер выключен",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (eqState.isEnabled) colorScheme.primary else colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            jsonInput = onExportPresets()
                            showJsonDialog = true
                            haptic?.click()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = "JSON Импорт/Экспорт",
                            tint = Color(0xFF64D2FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Switch(
                        checked = eqState.isEnabled,
                        onCheckedChange = {
                            haptic?.click()
                            onToggle(it)
                        },
                        modifier = Modifier.scale(0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Smooth Morphing Kinetic Frequency Curve
            val b0 by animateFloatAsState(targetValue = eqState.bandLevels.getOrElse(0) { 0 }.toFloat(), animationSpec = spring(stiffness = 300f, dampingRatio = 0.72f), label = "eq_b0")
            val b1 by animateFloatAsState(targetValue = eqState.bandLevels.getOrElse(1) { 0 }.toFloat(), animationSpec = spring(stiffness = 300f, dampingRatio = 0.72f), label = "eq_b1")
            val b2 by animateFloatAsState(targetValue = eqState.bandLevels.getOrElse(2) { 0 }.toFloat(), animationSpec = spring(stiffness = 300f, dampingRatio = 0.72f), label = "eq_b2")
            val b3 by animateFloatAsState(targetValue = eqState.bandLevels.getOrElse(3) { 0 }.toFloat(), animationSpec = spring(stiffness = 300f, dampingRatio = 0.72f), label = "eq_b3")
            val b4 by animateFloatAsState(targetValue = eqState.bandLevels.getOrElse(4) { 0 }.toFloat(), animationSpec = spring(stiffness = 300f, dampingRatio = 0.72f), label = "eq_b4")
            val animatedBands = listOf(b0, b1, b2, b3, b4)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0D0F14))
                    .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val midY = h / 2f

                    // Zero dB reference grid line
                    drawLine(
                        color = Color(0x18FFFFFF),
                        start = Offset(0f, midY),
                        end = Offset(w, midY),
                        strokeWidth = 1.dp.toPx()
                    )

                    val points = animatedBands.mapIndexed { idx, dbFloat ->
                        val x = if (animatedBands.size > 1) {
                            (idx.toFloat() / (animatedBands.size - 1)) * w
                        } else w / 2f
                        val y = midY - (dbFloat / 10f) * (midY * 0.85f)
                        Offset(x, y)
                    }

                    // Smooth Bezier Curve Path
                    val curvePath = Path()
                    val fillPath = Path()
                    if (points.isNotEmpty()) {
                        curvePath.moveTo(points.first().x, points.first().y)
                        fillPath.moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val controlX1 = (p0.x + p1.x) / 2f
                            val controlY1 = p0.y
                            val controlX2 = (p0.x + p1.x) / 2f
                            val controlY2 = p1.y
                            curvePath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
                            fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
                        }

                        // Close fill path down to baseline
                        fillPath.lineTo(points.last().x, h)
                        fillPath.lineTo(points.first().x, h)
                        fillPath.close()

                        // 1. Subtle translucent fill
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0x20FF5722),
                                    Color.Transparent
                                )
                            )
                        )

                        // 2. Clean precise curve
                        drawPath(
                            path = curvePath,
                            color = Color(0xFFFF5722),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // 3. Clean Band Nodes
                        points.forEach { pt ->
                            drawCircle(color = Color(0xFFFF5722), radius = 4.dp.toPx(), center = pt)
                            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 5 Discrete Frequency Sliders
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                eqState.bandLevels.forEachIndexed { index, levelDb ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (levelDb > 0) "+$levelDb" else "$levelDb",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (levelDb != 0) colorScheme.primary else colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Custom vertical slider column
                        Box(
                            modifier = Modifier
                                .width(34.dp)
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0D0F14))
                                .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconButton(
                                    onClick = {
                                        haptic?.tick()
                                        onBandChanged(index, (levelDb + 1).coerceAtMost(10))
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                                }

                                Text(
                                    text = "${levelDb}dB",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (levelDb != 0) colorScheme.primary else colorScheme.onSurfaceVariant
                                )

                                IconButton(
                                    onClick = {
                                        haptic?.tick()
                                        onBandChanged(index, (levelDb - 1).coerceAtLeast(-10))
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = bandLabels.getOrElse(index) { "Band $index" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Presets Horizontal Carousel Chips with Custom Save button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ПРЕДУСТАНОВКИ:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = "+ Сохранить пресет",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF5722),
                    modifier = Modifier
                        .clickable {
                            presetNameInput = ""
                            showSaveDialog = true
                            haptic?.click()
                        }
                        .padding(4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                eqState.presets.forEach { preset ->
                    val isSel = eqState.activePresetName == preset.name
                    val chipBg by animateColorAsState(if (isSel) colorScheme.primary else Color(0xFF0D0F14), tween(180))
                    val chipFg = if (isSel) Color.White else Color(0xFF8E8E93)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(chipBg)
                            .border(1.dp, if (isSel) colorScheme.primary else Color(0x15FFFFFF), RoundedCornerShape(12.dp))
                            .clickable {
                                effectiveHaptic.click()
                                onPresetSelected(preset.name)
                            }
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = preset.name,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = chipFg
                            )
                            if (preset.isCustom) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Удалить",
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clickable {
                                            haptic?.click()
                                            onDeletePreset(preset.name)
                                        },
                                    tint = chipFg.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Save Custom Preset Dialog ---
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Сохранить пресет", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Введите имя для текущей конфигурации 5 полос:", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        placeholder = { Text("Мой пресет...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (presetNameInput.isNotBlank()) {
                            onSavePreset(presetNameInput.trim())
                            showSaveDialog = false
                            haptic?.heavyClick()
                        }
                    }
                ) {
                    Text("Сохранить", color = colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // --- JSON Export & Import Dialog (Issue #3) ---
    if (showJsonDialog) {
        AlertDialog(
            onDismissRequest = { showJsonDialog = false },
            title = { Text("JSON Экспорт / Импорт эквалайзера", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Вы можете скопировать ваши пресеты в буфер обмена или вставить JSON из сообщества:",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = { jsonInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp),
                        placeholder = { Text("Вставьте JSON здесь...") }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("CMF_EQ_Presets", jsonInput)
                                cm.setPrimaryClip(clip)
                                haptic?.click()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Скопировать JSON", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = cm.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text?.toString() ?: ""
                                    jsonInput = text
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Вставить", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportPresets(jsonInput)
                        showJsonDialog = false
                        haptic?.heavyClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                ) {
                    Text("Применить JSON", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJsonDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// Deep User Usage Analytics Dashboard Component
// -------------------------------------------------------------

@Composable
fun BudsDeepAnalyticsDashboard(insights: BudsUsageInsights) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = remember { com.cmf.budscontroller.ui.haptic.CmfHapticManager(context) }

    Text(
        text = AppStrings.headerDeepAnalytics,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )

    // 1. Volume Time Distribution (На какой громкости сколько слушаю)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(listOf(Color(0x15FFFFFF), Color(0x08FFFFFF)))
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(AppStrings.volDistributionTitle, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = colorScheme.onSurfaceVariant)
                    Text(AppStrings.volLevelsTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("${AppStrings.volTotal} ${insights.volumeStats.totalMinutes} ${AppStrings.minUnit}", fontSize = 11.sp, color = colorScheme.primary, fontWeight = FontWeight.SemiBold, softWrap = false)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multicolored Segmented Progress Bar with high-glow neon colors
            val total = insights.volumeStats.totalMinutes.toFloat()
            val hasData = total > 0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E2330))
            ) {
                if (hasData) {
                    val lowRatio = insights.volumeStats.lowVolumeMinutes / total
                    val midRatio = insights.volumeStats.midVolumeMinutes / total
                    val highRatio = insights.volumeStats.highVolumeMinutes / total

                    if (lowRatio > 0f) {
                        Box(modifier = Modifier.weight(lowRatio).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF69F0AE)))))
                    }
                    if (midRatio > 0f) {
                        if (lowRatio > 0f) Spacer(modifier = Modifier.width(2.dp))
                        Box(modifier = Modifier.weight(midRatio).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFFFF9100), Color(0xFFFFAB40)))))
                    }
                    if (highRatio > 0f) {
                        if (lowRatio > 0f || midRatio > 0f) Spacer(modifier = Modifier.width(2.dp))
                        Box(modifier = Modifier.weight(highRatio).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFFFF1744), Color(0xFFFF5252)))))
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0x30FFFFFF)))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Volume Tier Detail Tiles with Neon Highlights
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VolumeStatPill(
                    title = AppStrings.volQuiet,
                    range = "0-30%",
                    minutes = insights.volumeStats.lowVolumeMinutes,
                    color = Color(0xFF00E676),
                    modifier = Modifier.weight(1f)
                )
                VolumeStatPill(
                    title = AppStrings.volMid,
                    range = "31-70%",
                    minutes = insights.volumeStats.midVolumeMinutes,
                    color = Color(0xFFFF9100),
                    modifier = Modifier.weight(1f)
                )
                VolumeStatPill(
                    title = AppStrings.volLoud,
                    range = "71-100%",
                    minutes = insights.volumeStats.highVolumeMinutes,
                    color = Color(0xFFFF1744),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 2. Weekly Activity Histogram (Как часто я слушаю музыку и в какие дни)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(listOf(Color(0x15FFFFFF), Color(0x08FFFFFF)))
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("АКТИВНОСТЬ ПО ДНЯМ НЕДЕЛИ", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = colorScheme.onSurfaceVariant)
                    Text("Топ день: ${insights.mostActiveDay}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                }
                Text(insights.favoriteHour, fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bar Histogram
            val maxMin = insights.weeklyActivity.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 60
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom
            ) {
                insights.weeklyActivity.forEach { day ->
                    val hasMins = day.minutes > 0
                    val fraction = if (hasMins) (day.minutes.toFloat() / maxMin).coerceIn(0.12f, 1f) else 0.04f
                    val isPeak = day.minutes == maxMin && hasMins

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            text = if (hasMins) "${day.minutes}м" else "-",
                            fontSize = 10.sp,
                            fontWeight = if (isPeak) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isPeak) Color(0xFFFF5722) else colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    if (isPeak) Brush.verticalGradient(listOf(Color(0xFFFF5722), Color(0xFFFF9100)))
                                    else if (hasMins) Brush.verticalGradient(listOf(Color(0xFF00E5FF), Color(0xFF0091EA)))
                                    else Brush.verticalGradient(listOf(Color(0xFF262C3D), Color(0xFF181C26)))
                                )
                                .border(
                                    0.8.dp,
                                    if (isPeak) Color(0xFFFFAB40) else if (hasMins) Color(0xFF80D8FF) else Color(0x20FFFFFF),
                                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = day.dayName,
                            fontSize = 11.5.sp,
                            fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPeak) Color.White else colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // 3. Bento Row: Case Opens & Average Full Battery Duration
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Case Opens Tile
        BentoCard(
            modifier = Modifier.weight(1f),
            title = AppStrings.caseOpensTitle,
            subtitle = if (insights.caseOpensToday > 0) "${insights.caseOpensToday} ${AppStrings.todayLabel}" else "0 ${AppStrings.todayLabel}",
            icon = Icons.Outlined.SensorDoor,
            accentColor = Color(0xFF00E5FF)
        ) {
            Text(
                text = "${AppStrings.totalLabel} ${insights.totalCaseOpens}",
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Average Full Battery Hours Tile
        BentoCard(
            modifier = Modifier.weight(1f),
            title = AppStrings.batteryLifeTitle,
            subtitle = if (insights.averageFullBatteryHours > 0f) "~${insights.averageFullBatteryHours} h" else "Calibrating...",
            icon = Icons.Outlined.BatteryChargingFull,
            accentColor = Color(0xFF00E676)
        ) {
            Text(
                text = "${AppStrings.batteryHealthLabel} ${insights.batteryHealthEstimate}%",
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun VolumeStatPill(
    title: String,
    range: String,
    minutes: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = remember { com.cmf.budscontroller.ui.haptic.CmfHapticManager(context) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0D0F14))
            .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface, maxLines = 1, softWrap = false)
            Text(range, fontSize = 8.sp, color = colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            Spacer(modifier = Modifier.height(3.dp))
            Text("${minutes} мин", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface, softWrap = false)
        }
    }
}

// -------------------------------------------------------------
// Studio Components (Seamless Showcase, Continuous Noise Slider, Bento Grid)
// -------------------------------------------------------------

@Composable
fun StudioProductStage(
    battery: BatteryInfo,
    earbudTimeFormatted: String,
    totalWithCaseFormatted: String
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF161A22))
            .padding(vertical = 20.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.cmf_earbud_left),
                        contentDescription = "Left Earbud",
                        modifier = Modifier
                            .fillMaxHeight(0.82f)
                            .weight(1f),
                        contentScale = ContentScale.Fit
                    )

                    Image(
                        painter = painterResource(id = R.drawable.cmf_case_dial),
                        contentDescription = "CMF Case",
                        modifier = Modifier
                            .fillMaxHeight(0.94f)
                            .weight(1.3f),
                        contentScale = ContentScale.Fit
                    )

                    Image(
                        painter = painterResource(id = R.drawable.cmf_earbud_right),
                        contentDescription = "Right Earbud",
                        modifier = Modifier
                            .fillMaxHeight(0.82f)
                            .weight(1f),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0D0F14))
                    .padding(vertical = 10.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniBatteryTag(name = "L", percent = battery.leftPercent, isCharging = battery.isLeftCharging)
                Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0xFF2C2C2E)))
                MiniBatteryTag(name = "КЕЙС", percent = battery.casePercent, isCharging = battery.isCaseCharging)
                Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0xFF2C2C2E)))
                MiniBatteryTag(name = "R", percent = battery.rightPercent, isCharging = battery.isRightCharging)
            }

            if (earbudTimeFormatted.isNotBlank() && earbudTimeFormatted != "—") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Наушники: ~$earbudTimeFormatted", fontSize = 11.sp, color = Color(0xFF8E8E93))
                    Text("С кейсом: ~$totalWithCaseFormatted", fontSize = 11.sp, color = Color(0xFF8E8E93))
                }
            }
        }
    }
}

@Composable
fun MiniBatteryTag(name: String, percent: Int, isCharging: Boolean) {
    val text = if (percent in 0..100) "$percent%" else "—"
    val levelColor = when {
        isCharging -> Color(0xFF00E676)
        percent > 50 -> Color(0xFF00E676)
        percent > 20 -> Color(0xFFFFB300)
        percent >= 0 -> Color(0xFFFF334B)
        else -> Color(0xFF8E95A5)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isCharging) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = Color(0xFF00E676)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(levelColor)
                    .border(0.5.dp, Color(0x60FFFFFF), CircleShape)
            )
        }
        Text(
            text = name,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFB0B7C6),
            letterSpacing = 0.5.sp
        )
        Text(
            text = text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Black,
            color = if (isCharging) Color(0xFF00E676) else Color.White
        )
    }
}

@Composable
fun StudioNoiseControlBar(
    currentMode: AncMode,
    currentLevel: AncLevel,
    onModeSelected: (AncMode) -> Unit,
    onLevelSelected: (AncLevel) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = remember { com.cmf.budscontroller.ui.haptic.CmfHapticManager(context) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(listOf(Color(0x15FFFFFF), Color(0x08FFFFFF)))
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ШУМОПОДАВЛЕНИЕ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = when (currentMode) {
                        AncMode.OFF -> "Пассивное"
                        AncMode.TRANSPARENCY -> "Прозрачность"
                        AncMode.NOISE_CANCELLATION -> "Активно (${currentLevel.title})"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (currentMode != AncMode.OFF) colorScheme.primary else colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Animated sliding glass indicator bar
            val selectedIndex = when (currentMode) {
                AncMode.OFF -> 0
                AncMode.TRANSPARENCY -> 1
                AncMode.NOISE_CANCELLATION -> 2
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0D0F14))
                    .padding(3.dp)
            ) {
                val totalWidth = maxWidth
                // Weights: 1f, 1.25f, 1f -> sum = 3.25f
                val unitWidth = totalWidth / 3.25f
                val (targetOffset, targetWidth) = when (selectedIndex) {
                    0 -> Pair(0.dp, unitWidth)
                    1 -> Pair(unitWidth, unitWidth * 1.25f)
                    else -> Pair(unitWidth * 2.25f, unitWidth)
                }

                val animatedOffset by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 380f
                    ),
                    label = "glass_slider_offset"
                )
                val animatedWidth by animateDpAsState(
                    targetValue = targetWidth,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 380f
                    ),
                    label = "glass_slider_width"
                )

                // The sliding indicator
                Box(
                    modifier = Modifier
                        .offset(x = animatedOffset)
                        .width(animatedWidth)
                        .height(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF252830))
                )

                // 3 Options placed on top of the sliding glass track
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val modes = listOf(
                        Triple(AncMode.OFF, "Выкл", Icons.AutoMirrored.Outlined.VolumeOff),
                        Triple(AncMode.TRANSPARENCY, "Прозрачность", Icons.Outlined.Hearing),
                        Triple(AncMode.NOISE_CANCELLATION, "Шумодав", Icons.Outlined.Shield)
                    )

                    modes.forEach { (mode, title, icon) ->
                        val isSelected = currentMode == mode
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else Color(0xFF8E95A5),
                            animationSpec = tween(220),
                            label = "noise_text_color"
                        )
                        val iconColor by animateColorAsState(
                            targetValue = if (isSelected) Color(0xFFFF5722) else Color(0xFF6C7385),
                            animationSpec = tween(220),
                            label = "noise_icon_color"
                        )

                        Row(
                            modifier = Modifier
                                .weight(if (mode == AncMode.TRANSPARENCY) 1.25f else 1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    haptic.tick()
                                    onModeSelected(mode)
                                },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = iconColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = title,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = currentMode == AncMode.NOISE_CANCELLATION) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "Глубина отсечки шума:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AncLevel.values().forEach { lvl ->
                            val isSel = currentLevel == lvl
                            val chipBg by animateColorAsState(if (isSel) colorScheme.primary else colorScheme.surface, tween(180))
                            val chipFg = if (isSel) Color.White else colorScheme.onSurfaceVariant

                            Box(
                                modifier = Modifier
                                    .weight(if (lvl == AncLevel.ADAPTIVE) 1.22f else 1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(chipBg)
                                    .border(1.dp, if (isSel) colorScheme.primary else colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptic.tick()
                                        onLevelSelected(lvl)
                                    }
                                    .padding(vertical = 9.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lvl.title,
                                    fontSize = if (lvl == AncLevel.ADAPTIVE) 9.sp else 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = chipFg,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoiseSliderOption(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = remember { com.cmf.budscontroller.ui.haptic.CmfHapticManager(context) }

    val bg = if (isSelected) colorScheme.surfaceVariant else Color.Transparent
    val borderCol = if (isSelected) colorScheme.outline else Color.Transparent
    val fg = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = fg)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else fg
        )
    }
}

@Composable
fun BentoCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    extraContent: (@Composable () -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(listOf(Color(0x15FFFFFF), Color(0x08FFFFFF)))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = accentColor
                    )
                }

                if (onClick != null) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF3A3A3C)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF8E8E93)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE5E5EA),
                maxLines = 2,
                lineHeight = 18.sp
            )

            if (extraContent != null) {
                extraContent()
            }
        }
    }
}
