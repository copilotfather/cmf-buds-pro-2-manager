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
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.cmf.budscontroller.ui.components.CmfVibeSelector
import com.cmf.budscontroller.ui.components.CmfSpectrumVisualizer
import com.cmf.budscontroller.ui.theme.CMFTheme
import com.cmf.budscontroller.ui.theme.VibeColorTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val btGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        if (btGranted) viewModel.connectDevice(bluetoothAdapter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(28, 28, 30))
        )
        AppStrings.init(this)

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter
        val preferences = getSharedPreferences("cmf_prefs", Context.MODE_PRIVATE)
        val initialVibeTheme = VibeColorTheme.values().firstOrNull {
            it.name == preferences.getString("vibe_theme", VibeColorTheme.CMF_ORANGE.name)
        } ?: VibeColorTheme.CMF_ORANGE
        val initialComfortableControls = preferences.getBoolean("comfortable_controls", false)

        setContent {
            var vibeTheme by rememberSaveable { mutableStateOf(initialVibeTheme) }
            var comfortableControls by rememberSaveable { mutableStateOf(initialComfortableControls) }
            CMFTheme(vibeTheme = vibeTheme, comfortableControls = comfortableControls) {
                HiFiStudioBudsApp(
                    viewModel = viewModel,
                    bluetoothAdapter = bluetoothAdapter,
                    vibeTheme = vibeTheme,
                    onVibeThemeSelected = { selectedTheme ->
                        vibeTheme = selectedTheme
                        preferences.edit().putString("vibe_theme", selectedTheme.name).apply()
                    },
                    comfortableControls = comfortableControls,
                    onComfortableControlsChanged = { enabled ->
                        comfortableControls = enabled
                        preferences.edit().putBoolean("comfortable_controls", enabled).apply()
                    },
                    onConnectClicked = {
                        checkAndRequestPermissions()
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
        }
    }
}

private fun uiText(russian: String, english: String): String =
    if (AppStrings.isRussian) russian else english

private class EntryStagger {
    private var index = 0
    fun nextDelayMillis(): Int = (index++ * 48).coerceAtMost(336)
}

private val LocalEntryStagger = staticCompositionLocalOf<EntryStagger?> { null }

enum class StudioTab(val icon: ImageVector) {
    DEVICE(Icons.Outlined.Headphones),
    STUDIO(Icons.Outlined.GraphicEq),
    INSIGHTS(Icons.Outlined.BarChart),
    FEATURES(Icons.Outlined.Tune),
    BRIDGE(Icons.Outlined.Devices),
    WATCH(Icons.Outlined.WatchLater);

    val label: String
        get() = when (this) {
            DEVICE -> AppStrings.tabOverview
            STUDIO -> AppStrings.tabSound
            INSIGHTS -> AppStrings.tabStats
            FEATURES -> AppStrings.tabFeatures
            BRIDGE -> AppStrings.tabBridge
            WATCH -> "WATCH"
        }

    val compactLabel: String
        get() = when (this) {
            DEVICE -> uiText("БУДЫ", "BUDS")
            STUDIO -> uiText("ЗВУК", "SOUND")
            INSIGHTS -> uiText("СТАТ", "STATS")
            FEATURES -> uiText("ФУНКЦ", "TOOLS")
            BRIDGE -> uiText("МОСТ", "BRIDGE")
            WATCH -> uiText("ЧАСЫ", "WATCH")
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiFiStudioBudsApp(
    viewModel: MainViewModel,
    bluetoothAdapter: BluetoothAdapter?,
    vibeTheme: VibeColorTheme,
    onVibeThemeSelected: (VibeColorTheme) -> Unit,
    comfortableControls: Boolean,
    onComfortableControlsChanged: (Boolean) -> Unit,
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
    val mediaState by viewModel.mediaState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val firmwareCheck by viewModel.firmwareCheck.collectAsState()

    var currentTab by rememberSaveable { mutableStateOf(StudioTab.DEVICE) }
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
    var showDevDialog by remember { mutableStateOf(false) }
    var showUpdatePopupDialog by remember { mutableStateOf(false) }
    var showFindMyBudsSafetyDialog by remember { mutableStateOf(false) }
    var chirpLeft by remember { mutableStateOf(true) }
    var chirpRight by remember { mutableStateOf(true) }
    var showWaterEjectSafetyDialog by remember { mutableStateOf(false) }

    // Auto-popup when update is detected
    LaunchedEffect(updateState.hasUpdate, updateState.latestVersion) {
        if (updateState.hasUpdate) {
            showUpdatePopupDialog = true
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val compactNavigation = configuration.screenWidthDp < 380 || configuration.fontScale > 1.15f
    val scrollableNavigation = configuration.fontScale > 1.2f
    val haptic = remember { com.cmf.budscontroller.ui.haptic.CmfHapticManager(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
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
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 20.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    letterSpacing = (-0.3).sp,
                                    color = colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (status.isConnected) Color(0xFF30D158) else colorScheme.onSurfaceVariant)
                                    )
                                    AnimatedContent(
                                        targetState = status.isConnected,
                                        transitionSpec = {
                                            (fadeIn(tween(240)) + slideInVertically(tween(280)) { it / 2 }) togetherWith
                                                (fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 2 })
                                        },
                                        label = "connection_label"
                                    ) { connected ->
                                        Text(
                                            text = if (connected) AppStrings.statusConnected else AppStrings.statusReady,
                                            fontSize = 12.sp,
                                            color = if (connected) Color(0xFF30D158) else colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorScheme.surfaceVariant)
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
                                val bluetoothTint by animateColorAsState(
                                    targetValue = if (status.isConnected) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    animationSpec = tween(180),
                                    label = "bluetooth_action_tint"
                                )
                                AnimatedContent(
                                    targetState = status.isConnected,
                                    transitionSpec = {
                                        (fadeIn(tween(130)) + scaleIn(tween(150), initialScale = 0.82f)) togetherWith
                                            (fadeOut(tween(90)) + scaleOut(tween(110), targetScale = 0.82f))
                                    },
                                    label = "bluetooth_action_icon"
                                ) { connected ->
                                    Icon(
                                        imageVector = if (connected) Icons.Outlined.BluetoothConnected else Icons.AutoMirrored.Outlined.BluetoothSearching,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = bluetoothTint
                                    )
                                }
                                Text(
                                    text = if (status.isConnected) {
                                        if (status.connectionType.isNotBlank()) status.connectionType else AppStrings.statusConnected
                                    } else AppStrings.actionFind,
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurface
                                )
                            }
                        }

                        IconButton(onClick = {
                            viewModel.refreshStatus()
                            if (!status.isConnected) onConnectClicked()
                        }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = AppStrings.actionRefresh, tint = colorScheme.onSurfaceVariant)
                        }

                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = AppStrings.actionSettings, tint = colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorScheme.background
                    )
                )
            },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp
            ) {
                Column {
                    HorizontalDivider(color = colorScheme.outlineVariant, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp)
                            .then(if (scrollableNavigation) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudioTab.values().forEach { tab ->
                            val isSelected = currentTab == tab
                            val tabInteraction = remember(tab) { MutableInteractionSource() }
                            val isTabPressed by tabInteraction.collectIsPressedAsState()
                            val tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant
                            val iconScale by animateFloatAsState(
                                targetValue = if (isTabPressed) 0.96f else 1f,
                                animationSpec = tween(150, easing = FastOutSlowInEasing),
                                label = "tab_icon_bounce"
                            )
                            val indicatorWidth by animateDpAsState(
                                targetValue = if (isSelected) 16.dp else 4.dp,
                                animationSpec = tween(180, easing = FastOutSlowInEasing),
                                label = "tab_indicator_morph"
                            )
                            Column(
                                modifier = (if (scrollableNavigation) Modifier.width(64.dp) else Modifier.weight(1f))
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(interactionSource = tabInteraction, indication = null) {
                                        if (currentTab != tab) haptic.click()
                                        currentTab = tab
                                    }
                                    .padding(vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(38.dp).clip(CircleShape)
                                        .background(if (isSelected) colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(21.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }, tint = tint)
                                }
                                Text(
                                    text = if (compactNavigation) tab.compactLabel else tab.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = tint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                                Box(
                                    Modifier.width(indicatorWidth).height(3.dp).clip(CircleShape)
                                        .background(if (isSelected) colorScheme.primary else colorScheme.outlineVariant)
                                )
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
                .consumeWindowInsets(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedContent(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                targetState = currentTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { direction * it / 14 }) togetherWith
                        (fadeOut(tween(120, easing = FastOutLinearInEasing)) +
                            slideOutHorizontally(tween(180, easing = FastOutLinearInEasing)) { -direction * it / 14 })
                },
                label = "tab_page_transition"
            ) { tab ->
                CompositionLocalProvider(LocalEntryStagger provides remember(tab) { EntryStagger() }) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
            when (tab) {
                StudioTab.DEVICE -> {
                    AnimatedVisibility(
                        visible = !status.isConnected,
                        enter = fadeIn(tween(420)) + expandVertically(spring(dampingRatio = 0.76f, stiffness = 300f)) + scaleIn(spring(dampingRatio = 0.72f, stiffness = 320f), initialScale = 0.94f),
                        exit = fadeOut(tween(180)) + shrinkVertically(tween(240)) + scaleOut(tween(220), targetScale = 0.96f),
                        label = "disconnected_banner"
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.BluetoothSearching,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        AppStrings.bannerNotConnectedTitle,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = colorScheme.onSurface
                                    )
                                }
                                Text(
                                    AppStrings.bannerNotConnectedSubtitle,
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp
                                )
                                Button(
                                    onClick = onConnectClicked,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                                ) {
                                    Text(AppStrings.btnConnectNow, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Studio Stage: All 3 devices + Battery
                    StudioProductStage(
                        battery = status.battery,
                        isConnected = status.isConnected,
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
                            title = AppStrings.headerUltraBass,
                            subtitle = if (status.ultraBassLevel > 0) "${AppStrings.ultraBassLevel} ${status.ultraBassLevel}" else AppStrings.ancOff,
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
                                    val segmentColor by animateColorAsState(
                                        targetValue = if (active) colorScheme.primary else colorScheme.outlineVariant,
                                        animationSpec = tween(180),
                                        label = "bass_level_segment"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(segmentColor)
                                            .clickable { viewModel.setUltraBass(if (status.ultraBassLevel == lvl) 0 else lvl) }
                                    )
                                }
                            }
                        }

                        BentoCard(
                            modifier = Modifier.weight(1f),
                            title = AppStrings.headerSmartDial,
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
                            title = AppStrings.headerGameMode,
                            subtitle = when {
                                !status.isLatencyModeAvailable -> uiText("Статус не прочитан", "Status not read")
                                status.isGameModeEnabled -> AppStrings.gameModeActive
                                else -> AppStrings.gameModeInactive
                            },
                            icon = Icons.Outlined.SportsEsports,
                            accentColor = if (status.isGameModeEnabled) Color(0xFF30D158) else colorScheme.onSurfaceVariant,
                            onClick = { viewModel.setGameMode(!status.isGameModeEnabled) }
                        )

                        BentoCard(
                            modifier = Modifier.weight(1f),
                            title = AppStrings.headerSonar,
                            subtitle = if (isChirping) AppStrings.sonarBeeping else AppStrings.sonarPlaySound,
                            icon = Icons.Outlined.LocationOn,
                            accentColor = if (isChirping) Color(0xFFFF453A) else colorScheme.primary,
                            onClick = {
                                if (isChirping) viewModel.stopChirp()
                                else {
                                    chirpLeft = true
                                    chirpRight = true
                                    showFindMyBudsSafetyDialog = true
                                }
                            }
                        )
                    }
                }

                StudioTab.STUDIO -> {
                    com.cmf.budscontroller.ui.components.ComparisonPanel(viewModel)
                    if (status.hardwareModel.equals("B172", ignoreCase = true)) {
                        EarbudListeningPresetCard(
                            selectedPreset = status.earbudListeningPreset,
                            presetAvailable = status.isEarbudListeningPresetAvailable,
                            customEqAvailable = status.isEarbudCustomEqAvailable,
                            customBass = status.earbudCustomBass,
                            customMid = status.earbudCustomMid,
                            customTreble = status.earbudCustomTreble,
                            onSelect = viewModel::setEarbudListeningPreset,
                            onApplyCustomEq = viewModel::setEarbudCustomEq,
                            onRefreshCustomEq = viewModel::refreshEarbudCustomEq
                        )
                        EarbudDeviceSettingsCard(
                            isConnected = status.isConnected,
                            gameModeEnabled = status.isGameModeEnabled,
                            gameModeAvailable = status.isLatencyModeAvailable,
                            inEarEnabled = status.isInEarDetectionEnabled,
                            inEarAvailable = status.isInEarDetectionAvailable,
                            spatialAudioMode = status.spatialAudioMode,
                            spatialHeadTracking = status.isSpatialHeadTrackingEnabled,
                            spatialAvailable = status.isSpatialAudioAvailable,
                            onGameModeChange = viewModel::setGameMode,
                            onInEarChange = viewModel::setInEarDetection,
                            onSpatialModeChange = viewModel::setEarbudSpatialAudio
                        )
                        EarbudGestureCard(
                            isConnected = status.isConnected,
                            isAvailable = status.isGestureConfigAvailable,
                            actions = listOf(
                                status.leftDoubleTapAction,
                                status.leftTripleTapAction,
                                status.leftHoldAction,
                                status.rightDoubleTapAction,
                                status.rightTripleTapAction,
                                status.rightHoldAction
                            ),
                            onChange = viewModel::setEarbudGesture,
                            onRefresh = viewModel::refreshStatus
                        )
                        DualConnectionCard(
                            isConnected = status.isConnected,
                            isAvailable = status.isDualConnectionAvailable,
                            enabled = status.isDualConnectionEnabled,
                            devices = status.rememberedDevices,
                            deviceListAvailable = status.isRememberedDeviceListAvailable,
                            onToggle = viewModel::setDualConnectionEnabled,
                            onDeviceToggle = viewModel::setDualDeviceConnected,
                            onRefresh = viewModel::refreshStatus
                        )
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(uiText("Расширенные настройки наушников", "Advanced earbud settings"), fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (status.hardwareModel.isBlank()) uiText("Определяем ревизию устройства…", "Detecting hardware revision…")
                                    else uiText("Ревизия ${status.hardwareModel}: эти команды подтверждены только для B172.", "Revision ${status.hardwareModel}: these commands are verified for B172 only."),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    // Hi-Res Codec Engine Card (LDAC, LHDC, aptX HD)
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = uiText("ВЫСОКОЕ КАЧЕСТВО ЗВУКА", "HIGH-QUALITY AUDIO"),
                        subtitle = if (codecState.isLdacEnabledOnBuds) uiText("Режим наушников включён • кодек выбирает Android", "Earbud mode on • codec selected by Android") else uiText("Режим наушников выключен", "Earbud mode off"),
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
                                text = if (codecState.isLdacEnabledOnBuds) "HI-RES MODE" else "STANDARD",
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

                    CmfSpectrumVisualizer(isPlaying = mediaState.isPlaying)

                    // Spatial Audio 360 Card
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = uiText("ПРОСТРАНСТВЕННОЕ АУДИО 360°", "360° SPATIAL AUDIO"),
                        subtitle = "${spatialState.mode.title} • ${uiText("Вокал", "Vocal")}: ${spatialState.vocalClarity.title}",
                        icon = Icons.Outlined.SpatialAudio,
                        accentColor = Color(0xFF64D2FF),
                        onClick = { showSpatialDialog = true }
                    )

                    // Balance & Boost Card
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = uiText("БАЛАНС КАНАЛОВ И УСИЛЕНИЕ", "CHANNEL BALANCE & BOOST"),
                        subtitle = "${uiText("Баланс", "Balance")}: ${String.format("%.1f", audioState.channelBalance)} • ${uiText("Усиление", "Boost")}: +${audioState.loudnessBoostMilliBels / 100} dB",
                        icon = Icons.Outlined.SettingsSuggest,
                        accentColor = Color(0xFFBF5AF2),
                        onClick = { showAudioTweaksDialog = true }
                    )
                }

                StudioTab.INSIGHTS -> {
                    com.cmf.budscontroller.ui.components.BatteryHistoryPanel(viewModel)
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
                    com.cmf.budscontroller.ui.components.ProfilePanel(viewModel)
                    com.cmf.budscontroller.ui.components.DiagnosticsPanel(viewModel)
                    FirmwareUpdateCard(
                        currentVersion = status.firmwareVersion,
                        isConnected = status.isConnected,
                        isModelVerified = status.hardwareModel.equals("B172", ignoreCase = true),
                        result = firmwareCheck,
                        onCheck = viewModel::checkBudsFirmware
                    )
                    com.cmf.budscontroller.ui.components.WidgetSettingsPanel()
                    val lastLocation by viewModel.lastLocation.collectAsState()
                    com.cmf.budscontroller.ui.components.FindMyBudsCard(
                        isChirping = isChirping,
                        lastLocation = lastLocation,
                        onStartChirp = { left, right ->
                            chirpLeft = left
                            chirpRight = right
                            showFindMyBudsSafetyDialog = true
                        },
                        onStopChirp = { viewModel.stopChirp() },
                        onClearLocation = { viewModel.clearLastLocation() }
                    )
                    Text(
                        text = uiText("ИНТЕЛЛЕКТУАЛЬНЫЕ ФУНКЦИИ", "SMART FEATURES"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )

                    // Auto ANC
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = uiText("АДАПТИВНЫЙ АВТО-ANC", "ADAPTIVE AUTO ANC"),
                        subtitle = if (noiseState.isAutoAncActive) "${uiText("Активен", "Active")} • ${noiseState.noiseEnvironment} (${noiseState.currentDecibels} ${uiText("дБ", "dB")})" else uiText("Автоматическая калибровка шумоподавления по микрофону", "Automatic noise cancellation tuning via microphone"),
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
                        title = AppStrings.waterEjectTitle,
                        subtitle = if (waterState.isRunning) "${waterState.currentPhase} • [${waterState.progressSeconds}/${waterState.totalSeconds}s]" else AppStrings.waterEjectSubtitle,
                        icon = Icons.Outlined.WaterDrop,
                        accentColor = Color(0xFF0A84FF),
                        onClick = null
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                if (waterState.isRunning) viewModel.stopWaterEject()
                                else showWaterEjectSafetyDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (waterState.isRunning) (if (AppStrings.isRussian) "Остановить очистку" else "Stop Ejection") else (if (AppStrings.isRussian) "Запустить резонансную очистку (18 сек)" else "Start Resonance Ejection (18s)"), fontSize = 12.sp)
                        }
                        AnimatedVisibility(
                            visible = waterState.isRunning,
                            enter = fadeIn(tween(160)) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
                            exit = fadeOut(tween(100)) + shrinkVertically(tween(160, easing = FastOutSlowInEasing)),
                            label = "water_eject_progress_visibility"
                        ) {
                            val targetProgress = if (waterState.totalSeconds > 0) {
                                (waterState.progressSeconds.toFloat() / waterState.totalSeconds).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            val animatedProgress by animateFloatAsState(
                                targetValue = targetProgress,
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                label = "water_eject_progress"
                            )
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(5.dp).clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF0A84FF),
                                trackColor = colorScheme.surfaceVariant
                            )
                        }
                    }

                    // Sleep Timer
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = uiText("ТАЙМЕР СНА И РЕЛАКС", "SLEEP TIMER & RELAX"),
                        subtitle = if (sleepState.isActive) "${uiText("Осталось", "Remaining")}: ${sleepState.minutesRemaining} ${uiText("мин", "min")} • ${sleepState.noiseType.title}" else uiText("Фоновые звуки природы и авто-отключение", "Ambient sounds with auto-stop"),
                        icon = Icons.Outlined.Bedtime,
                        accentColor = Color(0xFF5E5CE6),
                        onClick = { showSleepTimerDialog = true }
                    )

                    // Whisper
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = uiText("ГОЛОСОВОЙ ШЕПОТ (WHISPER)", "VOICE PROMPTS (WHISPER)"),
                        subtitle = uiText("Озвучивание заряда и времени суток в ухо", "Battery and time announcements in your earbuds"),
                        icon = Icons.Outlined.RecordVoiceOver,
                        accentColor = Color(0xFFFFD60A),
                        onClick = { showWhisperDialog = true }
                    )

                    // GitHub Auto-Updater
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = uiText("ОБНОВЛЕНИЕ ПРИЛОЖЕНИЯ (GITHUB OTA)", "APP UPDATE (GITHUB OTA)"),
                        subtitle = if (updateState.isChecking) uiText("Проверка обновлений на GitHub...", "Checking GitHub for updates...")
                        else if (updateState.isDownloading) uiText("Загрузка обновления: ${updateState.downloadProgress}%...", "Downloading update: ${updateState.downloadProgress}%...")
                        else if (updateState.hasUpdate) uiText("Доступна новая версия: v${updateState.latestVersion}! Нажмите для установки", "Version v${updateState.latestVersion} is available. Tap to install")
                        else if (updateState.checkError != null) uiText("Не удалось проверить GitHub. Нажмите, чтобы повторить", "Couldn't check GitHub. Tap to retry")
                        else uiText("У вас актуальная версия (${viewModel.updateManager.getCurrentVersionName()})", "You're up to date (${viewModel.updateManager.getCurrentVersionName()})"),
                        icon = Icons.Outlined.CloudDownload,
                        accentColor = if (updateState.hasUpdate) Color(0xFF0A84FF) else if (updateState.checkError != null) Color(0xFFFF453A) else Color(0xFF30D158),
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
                                val animatedDownloadFraction by animateFloatAsState(
                                    targetValue = (updateState.downloadProgress / 100f).coerceIn(0f, 1f),
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    label = "ota_card_progress"
                                )
                                LinearProgressIndicator(
                                    progress = { animatedDownloadFraction },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFF0A84FF),
                                    trackColor = colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(uiText("Загрузка APK: ${updateState.downloadProgress}%", "Downloading APK: ${updateState.downloadProgress}%"), fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.startUpdateDownload() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
                                    ) {
                                        Text(uiText("Скачать v${updateState.latestVersion}", "Download v${updateState.latestVersion}"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.openUpdateInBrowser() },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(uiText("Браузер", "Browser"), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                StudioTab.BRIDGE -> {
                    Text(
                        text = uiText("ЛОКАЛЬНЫЙ USB АУДИО МОСТ", "LOCAL USB AUDIO BRIDGE"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )

                    // Card 1: Server Switch & Status
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = uiText("ТРАНСЛЯЦИЯ ЗВУКА С ПК (LOW LATENCY)", "PC AUDIO STREAM (LOW LATENCY)"),
                        subtitle = if (relayState.isRunning) "${uiText("Сервер запущен (порт", "Server running (port")} ${com.cmf.budscontroller.relay.CmfAudioRelayManager.RELAY_PORT}) • ${uiText("Задержка", "Latency")} ~${relayState.latencyEstimateMs} ${uiText("мс", "ms")}" else uiText("Передавайте звук с компьютера через USB и ADB", "Stream PC audio over USB with ADB forwarding"),
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
                                Text(uiText("Параметры", "Settings") + ": ${relayState.mode.title}", fontSize = 12.sp)
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
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                        border = null
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = Color(0xFF64D2FF), modifier = Modifier.size(20.dp))
                                Text(uiText("ПОШАГОВАЯ ИНСТРУКЦИЯ ПОДКЛЮЧЕНИЯ", "CONNECTION GUIDE"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF64D2FF))
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
                                    Text(uiText("Включите переключатель выше", "Turn on the switch above"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(uiText("Телефон запустит локальный TCP-сервер на порту ${com.cmf.budscontroller.relay.CmfAudioRelayManager.RELAY_PORT} для приёма PCM-аудио 48 кГц.", "Your phone will start a local TCP server on port ${com.cmf.budscontroller.relay.CmfAudioRelayManager.RELAY_PORT} to receive 48 kHz PCM audio."), fontSize = 11.5.sp, color = colorScheme.onSurfaceVariant)
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
                                    Text(uiText("Подключите телефон по USB", "Connect your phone over USB"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(uiText("Включите USB-отладку, подтвердите компьютер на телефоне и запустите cmf_pc_relay_usb.bat из папки PC_Client. Скрипт настроит ADB-проброс порта ${com.cmf.budscontroller.relay.CmfAudioRelayManager.RELAY_PORT}.", "Enable USB debugging, authorize the computer on your phone, then run cmf_pc_relay_usb.bat from PC_Client. The script forwards port ${com.cmf.budscontroller.relay.CmfAudioRelayManager.RELAY_PORT} through ADB."), fontSize = 11.5.sp, color = colorScheme.onSurfaceVariant)
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
                                    Text(uiText("Готово! Чистый звук ПК в наушниках", "Done! PC audio is playing in your earbuds"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(uiText("Звук с Windows передаётся напрямую в CMF Buds Pro 2 с минимальной задержкой для игр и видео.", "Windows audio streams to your CMF Buds Pro 2 with low latency for games and video."), fontSize = 11.5.sp, color = colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                StudioTab.WATCH -> {
                    com.cmf.budscontroller.ui.components.WatchPage(viewModel.watchBeta)
                }
            }
                }
            }

            }
        }
    }
}

    // --- DIALOGS ---
    if (showCodecDialog) {
        AlertDialog(
            onDismissRequest = { showCodecDialog = false },
            icon = { Icon(Icons.Outlined.HighQuality, contentDescription = null, tint = colorScheme.primary) },
            title = { Text(uiText("Режим высокого качества наушников", "Earbud high-quality audio mode"), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = uiText("Включает режим высокого качества на наушниках. Bluetooth-кодек выбирает Android; для LDAC нужна поддержка телефона и включённый HD Audio в настройках Bluetooth.", "Enables high-quality mode on the earbuds. Android selects the Bluetooth codec; LDAC requires phone support and HD Audio enabled in Bluetooth settings."),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(uiText("Высокое качество", "High quality"), fontWeight = FontWeight.SemiBold)
                        Switch(checked = codecState.isLdacEnabledOnBuds, onCheckedChange = {
                            viewModel.applyCodec(if (it) com.cmf.budscontroller.audio.CodecType.LDAC else com.cmf.budscontroller.audio.CodecType.SBC)
                        })
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = { viewModel.openDevSettings() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(uiText("Настройки Bluetooth телефона", "Phone Bluetooth settings"), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCodecDialog = false }) { Text(AppStrings.doneBtn) } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showDialDialog) {
        AlertDialog(
            onDismissRequest = { showDialDialog = false },
            icon = { Icon(Icons.Outlined.Tune, contentDescription = null, tint = colorScheme.primary) },
            title = { Text(uiText("Колесо Smart Dial", "Smart Dial"), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(uiText("При вращении:", "On rotation:"), style = MaterialTheme.typography.labelLarge)
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

                    Text(uiText("При нажатии:", "On press:"), style = MaterialTheme.typography.labelLarge)
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
            confirmButton = { TextButton(onClick = { showDialDialog = false }) { Text(AppStrings.doneBtn) } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showSpatialDialog) {
        AlertDialog(
            onDismissRequest = { showSpatialDialog = false },
            icon = { Icon(Icons.Outlined.SpatialAudio, contentDescription = null, tint = colorScheme.primary) },
            title = { Text(uiText("Пространственное аудио 360°", "360° Spatial Audio"), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(uiText("Режим сцены:", "Soundstage:"), style = MaterialTheme.typography.labelLarge)
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

                    Text(uiText("Чёткость вокала:", "Vocal clarity:"), style = MaterialTheme.typography.labelLarge)
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
            confirmButton = { TextButton(onClick = { showSpatialDialog = false }) { Text(AppStrings.doneBtn) } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showAudioTweaksDialog) {
        AlertDialog(
            onDismissRequest = { showAudioTweaksDialog = false },
            icon = { Icon(Icons.Outlined.SettingsSuggest, contentDescription = null, tint = colorScheme.primary) },
            title = { Text(uiText("Баланс каналов и громкость", "Channel balance & volume"), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${uiText("Баланс левого / правого канала", "Left / right balance")} (${String.format("%.1f", audioState.channelBalance)}):", style = MaterialTheme.typography.labelMedium)
                    var lastBalanceHaptic by remember(audioState.channelBalance) {
                        mutableIntStateOf(((audioState.channelBalance + 1f) * 10f).roundToInt())
                    }
                    Slider(
                        value = audioState.channelBalance,
                        onValueChange = { value ->
                            val step = ((value + 1f) * 10f).roundToInt()
                            if (step != lastBalanceHaptic) {
                                haptic.tick()
                                lastBalanceHaptic = step
                            }
                            viewModel.setChannelBalance(value)
                        },
                        valueRange = -1.0f..1.0f,
                        steps = 20
                    )

                    HorizontalDivider(thickness = 0.8.dp)

                    Text("${uiText("Усиление громкости", "Loudness boost")} (+${audioState.loudnessBoostMilliBels / 100} dB):", style = MaterialTheme.typography.labelMedium)
                    var lastLoudnessHaptic by remember(audioState.loudnessBoostMilliBels) {
                        mutableIntStateOf((audioState.loudnessBoostMilliBels / 100f).roundToInt())
                    }
                    Slider(
                        value = (audioState.loudnessBoostMilliBels / 100).toFloat(),
                        onValueChange = { value ->
                            val step = value.roundToInt()
                            if (step != lastLoudnessHaptic) {
                                haptic.tick()
                                lastLoudnessHaptic = step
                            }
                            viewModel.setLoudnessGain((value * 100).toInt())
                        },
                        valueRange = 0f..12f,
                        steps = 12
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAudioTweaksDialog = false }) { Text(AppStrings.doneBtn) } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            icon = { Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = colorScheme.primary) },
            title = { Text(uiText("Таймер сна и релакс", "Sleep timer & relax"), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(uiText("Фоновый звук:", "Ambient sound:"), style = MaterialTheme.typography.labelLarge)
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
                            Text(uiText("Остановить таймер", "Stop timer"), color = Color.White)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepTimerDialog = false }) { Text(AppStrings.doneBtn) } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showWhisperDialog) {
        AlertDialog(
            onDismissRequest = { showWhisperDialog = false },
            icon = { Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null, tint = colorScheme.primary) },
            title = { Text(uiText("Голосовые подсказки", "Voice prompts"), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(uiText("Озвучивать заряд при подключении", "Announce battery on connect"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = whisperSettings.announceOnConnect, onCheckedChange = { viewModel.toggleWhisperConnect(it) })
                    }

                    HorizontalDivider(thickness = 0.8.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(uiText("Озвучивать время каждый час", "Announce the time hourly"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = whisperSettings.announceHourlyTime, onCheckedChange = { viewModel.toggleWhisperHours(it) })
                    }

                    FilledTonalButton(
                        onClick = { viewModel.testWhisperVoice() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(uiText("Проверить голос", "Test voice prompt"))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showWhisperDialog = false }) { Text(AppStrings.doneBtn) } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showRelayDialog) {
        AlertDialog(
            onDismissRequest = { showRelayDialog = false },
            icon = { Icon(Icons.Outlined.Computer, contentDescription = null, tint = colorScheme.primary) },
            title = { Text(uiText("Настройки аудиомоста ПК", "PC audio bridge settings"), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(uiText("Режим передачи:", "Transfer mode:"), style = MaterialTheme.typography.labelLarge)
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

                    Text(uiText("Буфер (задержка):", "Buffer (latency):"), style = MaterialTheme.typography.labelLarge)
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
            confirmButton = { TextButton(onClick = { showRelayDialog = false }) { Text(AppStrings.doneBtn) } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showDevicePicker) {
        val bondedDevices = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            } else emptyList()
        } else {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        }

        ).filter { com.cmf.budscontroller.bluetooth.CmfBluetoothManager.getInstance().isCmfOrNothingDevice(it) }
        AlertDialog(
            onDismissRequest = { showDevicePicker = false },
            icon = { Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = colorScheme.primary) },
            title = { Text(AppStrings.selectDeviceTitle, style = MaterialTheme.typography.titleLarge) },
            text = {
                if (bondedDevices.isEmpty()) {
                    Text(
                        AppStrings.noBondedDevices,
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
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(dev.address, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Outlined.Headphones, contentDescription = null, tint = colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDevicePicker = false }) { Text(AppStrings.btnClose) } },
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
                    if (AppStrings.isRussian) "Настройки и О программе" else "Settings & About",
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
                    CmfVibeSelector(
                        activeTheme = vibeTheme,
                        onThemeSelected = onVibeThemeSelected
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                                .heightIn(min = if (comfortableControls) 72.dp else 64.dp)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    uiText("Комфортное управление", "Comfortable controls"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    uiText("Крупные зоны нажатия · 56 dp", "Larger tap targets · 56 dp"),
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = comfortableControls,
                                onCheckedChange = onComfortableControlsChanged
                            )
                        }
                    }

                    // Language Switcher (Instant EN / RU)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(AppStrings.actionLanguage, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(if (AppStrings.isRussian) "Русский" else "English", fontSize = 12.sp, color = colorScheme.primary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !AppStrings.isRussian,
                                    onClick = { AppStrings.setLanguage(context, "en") },
                                    label = { Text("EN") }
                                )
                                FilterChip(
                                    selected = AppStrings.isRussian,
                                    onClick = { AppStrings.setLanguage(context, "ru") },
                                    label = { Text("RU") }
                                )
                            }
                        }
                    }
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
                                        "${uiText("Версия", "Version")}: v${viewModel.updateManager.getCurrentVersionName()}",
                                        fontSize = 12.sp,
                                        color = colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = uiText("Центр управления CMF Buds Pro 2 с прямым управлением через BES/SPP, режимом высокого качества звука и лаконичным интерфейсом.", "A CMF Buds Pro 2 control center with direct BES/SPP control, high-quality audio mode, and a clean interface."),
                                fontSize = 11.5.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Credits & Developer Info
                    Text(uiText("Разработка и авторство", "Credits"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

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
                                Text(uiText("Автор проекта: Dmitry (copilotfather)", "Project author: Dmitry (copilotfather)"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                                Text(uiText("Соавтор разработки: Antigravity AI", "Development co-author: Antigravity AI"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                                Text(uiText("Исходный код проекта", "Project source code"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(uiText("Лицензия MIT • Открытый проект", "MIT License • Open source"), fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(thickness = 0.8.dp)

                    OutlinedButton(
                        onClick = { showSettingsDialog = false; showDevDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("DEV · CMF Watch Pro 2 β") }

                    // Quick Actions
                    Text(uiText("Обновления и система", "Updates & system"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

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
                        Text(uiText("Проверить обновления сейчас", "Check for updates now"), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(AppStrings.btnClose)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showDevDialog) {
        com.cmf.budscontroller.ui.components.DevDialog(viewModel) { showDevDialog = false }
    }

    // Auto-Popup Dialog when an update is available
    if (showUpdatePopupDialog && updateState.hasUpdate) {
        AlertDialog(
            onDismissRequest = { showUpdatePopupDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = Color(0xFF0A84FF),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    uiText("Доступно обновление v${updateState.latestVersion}!", "Update v${updateState.latestVersion} is available!"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        uiText("На GitHub доступна новая версия приложения:", "A new app version is available on GitHub:"),
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
                                uiText("Что нового:", "What's new:"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF0A84FF)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = updateState.releaseNotes.ifBlank { uiText("Исправления ошибок и улучшения.", "Bug fixes and improvements.") },
                                fontSize = 11.5.sp,
                                color = colorScheme.onSurface
                            )
                        }
                    }

                    if (updateState.isDownloading) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val animatedDownloadFraction by animateFloatAsState(
                            targetValue = (updateState.downloadProgress / 100f).coerceIn(0f, 1f),
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            label = "ota_dialog_progress"
                        )
                        LinearProgressIndicator(
                            progress = { animatedDownloadFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF0A84FF),
                            trackColor = colorScheme.surfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (updateState.downloadProgress < 100) uiText("Загрузка обновления...", "Downloading update...") else uiText("Запуск установщика...", "Starting installer..."),
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${updateState.downloadProgress}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0A84FF)
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
                        Text(uiText("Браузер", "Browser"), fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.startUpdateDownload()
                        },
                        enabled = !updateState.isDownloading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (updateState.isDownloading) uiText("Загрузка...", "Downloading...") else uiText("Скачать и обновить", "Download & update"),
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
                        Text(uiText("Позже", "Later"))
                    }
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showFindMyBudsSafetyDialog) {
        AlertDialog(
            onDismissRequest = { showFindMyBudsSafetyDialog = false },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(36.dp)) },
            title = {
                Text(
                    AppStrings.safetyWarningTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF453A)
                )
            },
            text = {
                Text(
                    AppStrings.findBudsSafetyWarning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFindMyBudsSafetyDialog = false
                        viewModel.startChirp(left = chirpLeft, right = chirpRight)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(AppStrings.btnStartSearch, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFindMyBudsSafetyDialog = false }) {
                    Text(AppStrings.btnCancel)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showWaterEjectSafetyDialog) {
        AlertDialog(
            onDismissRequest = { showWaterEjectSafetyDialog = false },
            icon = { Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(36.dp)) },
            title = {
                Text(
                    AppStrings.safetyWarningTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0A84FF)
                )
            },
            text = {
                Text(
                    AppStrings.waterEjectSafetyWarning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWaterEjectSafetyDialog = false
                        viewModel.startWaterEject()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(AppStrings.btnStartWaterEject, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWaterEjectSafetyDialog = false }) {
                    Text(AppStrings.btnCancel)
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
private fun FirmwareUpdateCard(
    currentVersion: String,
    isConnected: Boolean,
    isModelVerified: Boolean,
    result: com.cmf.budscontroller.update.FirmwareCheckResult?,
    onCheck: () -> Unit
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val hasVersion = currentVersion.isNotBlank() && !currentVersion.equals("unknown", ignoreCase = true)
    val checkResult = result?.takeIf { it.currentVersion == currentVersion }
    val isChecking = checkResult?.error == "Checking"
    val title: String
    val detail: String
    val accent: Color
    when {
        !isModelVerified -> {
            title = uiText("Ревизия не подтверждена", "Hardware revision is unverified")
            detail = uiText("Каталог проверяет только подтверждённую ревизию B172. Для остальных используйте Nothing X.", "The catalog checks confirmed B172 data only. Use Nothing X for other revisions.")
            accent = MaterialTheme.colorScheme.onSurfaceVariant
        }
        !hasVersion -> {
            title = uiText("Версия прошивки не получена", "Firmware version unavailable")
            detail = uiText("Подключите наушники по SPP и обновите статус.", "Connect the earbuds over SPP and refresh their status.")
            accent = MaterialTheme.colorScheme.onSurfaceVariant
        }
        isChecking -> {
            title = uiText("Проверяем каталог…", "Checking catalog…")
            detail = uiText("Сверяем только подтверждённые версии.", "Checking confirmed version data only.")
            accent = MaterialTheme.colorScheme.primary
        }
        checkResult?.isUpdateAvailable == true -> {
            title = uiText("Есть подтверждённый минимум ${checkResult.latestVersion}", "Confirmed minimum ${checkResult.latestVersion} is available")
            detail = uiText("Проверьте обновление в официальном приложении Nothing X.", "Check for the update in the official Nothing X app.")
            accent = Color(0xFFFF9F0A)
        }
        checkResult?.isCurrent == true -> {
            title = uiText("Версия не ниже подтверждённого минимума", "Version meets the confirmed minimum")
            detail = uiText("Это не гарантирует, что установлена последняя версия.", "This does not guarantee that this is the latest version.")
            accent = Color(0xFF30D158)
        }
        checkResult != null -> {
            title = uiText("Для этой ревизии нет данных", "No data for this revision")
            detail = uiText("Проверьте прошивку через Nothing X.", "Check the firmware in Nothing X.")
            accent = MaterialTheme.colorScheme.onSurfaceVariant
        }
        else -> {
            title = uiText("Проверка прошивки", "Firmware status")
            detail = uiText("Сверьте версию с каталогом подтверждённых данных.", "Compare the version with the confirmed data catalog.")
            accent = MaterialTheme.colorScheme.primary
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Text(uiText("ПРОШИВКА НАУШНИКОВ", "EARBUD FIRMWARE"), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, color = accent)
            }
            Text("${uiText("Текущая версия", "Current version")}: ${if (hasVersion) currentVersion else "—"}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp)
            checkResult?.error?.takeIf { it != "Checking" }?.let {
                Text(uiText("Не удалось обновить каталог; показаны сохранённые данные, если они есть.", "Catalog refresh failed; cached data is shown when available."), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            }
            checkResult?.sourceUrl?.let { source ->
                TextButton(onClick = { uriHandler.openUri(source) }, contentPadding = PaddingValues(0.dp)) {
                    Text(uiText("Источник версии", "Version source"), fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCheck, enabled = isConnected && hasVersion && isModelVerified && !isChecking) {
                    Text(uiText("Проверить", "Check"))
                }
                OutlinedButton(onClick = { uriHandler.openUri("https://support.cmf.tech/hc/en-us/categories/22758549066001-Buds") }) {
                    Text(uiText("Как обновить", "Update guide"))
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EarbudListeningPresetCard(
    selectedPreset: Int,
    presetAvailable: Boolean,
    customEqAvailable: Boolean,
    customBass: Int,
    customMid: Int,
    customTreble: Int,
    onSelect: (Int) -> Unit,
    onApplyCustomEq: (Int, Int, Int) -> Unit,
    onRefreshCustomEq: () -> Unit
) {
    var bass by remember(selectedPreset, customBass) { mutableFloatStateOf(customBass.toFloat()) }
    var mid by remember(selectedPreset, customMid) { mutableFloatStateOf(customMid.toFloat()) }
    var treble by remember(selectedPreset, customTreble) { mutableFloatStateOf(customTreble.toFloat()) }
    val presetNames = listOf(
        uiText("Dirac", "Dirac"),
        uiText("Рок", "Rock"),
        uiText("Электроника", "Electronic"),
        uiText("Поп", "Pop"),
        uiText("Вокал", "Vocals"),
        uiText("Классика", "Classical"),
        uiText("Своя настройка", "Custom")
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(uiText("ПРЕСЕТ НАУШНИКОВ", "EARBUD PRESET"), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary)
            Text(
                if (selectedPreset in presetNames.indices) presetNames[selectedPreset] else uiText("Не прочитан", "Not read"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                uiText("Переключает встроенную настройку звука на наушниках. Ответ устройства обновит выбранный пресет.", "Changes the earbuds’ built-in sound preset. The device response updates the selected preset."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                presetNames.forEachIndexed { index, name ->
                    FilterChip(
                        selected = selectedPreset == index,
                        onClick = { onSelect(index) },
                        label = { Text(name, maxLines = 1) }
                    )
                }
            }
            AnimatedVisibility(visible = selectedPreset == 6) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!presetAvailable) {
                        Text(
                            uiText("Подключите наушники, чтобы прочитать EQ", "Connect earbuds to read their EQ"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (!customEqAvailable) {
                        Text(
                            uiText("Настройки ещё не прочитаны с устройства.", "Settings have not been read from the device yet."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onRefreshCustomEq) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(uiText("Прочитать EQ", "Read EQ"))
                        }
                    } else {
                        listOf(
                            Triple(uiText("Бас", "Bass"), bass, { value: Float -> bass = value }),
                            Triple(uiText("Середина", "Mid"), mid, { value: Float -> mid = value }),
                            Triple(uiText("Верх", "Treble"), treble, { value: Float -> treble = value })
                        ).forEach { (label, value, onChange) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, modifier = Modifier.width(74.dp), style = MaterialTheme.typography.bodyMedium)
                                Slider(value = value, onValueChange = onChange, valueRange = -6f..6f, steps = 11, modifier = Modifier.weight(1f))
                                Text("${value.roundToInt()} dB", modifier = Modifier.width(52.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        FilledTonalButton(
                            onClick = { onApplyCustomEq(bass.roundToInt(), mid.roundToInt(), treble.roundToInt()) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.GraphicEq, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(uiText("Записать в наушники", "Apply to earbuds"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EarbudDeviceSettingsCard(
    isConnected: Boolean,
    gameModeEnabled: Boolean,
    gameModeAvailable: Boolean,
    inEarEnabled: Boolean,
    inEarAvailable: Boolean,
    spatialAudioMode: Int,
    spatialHeadTracking: Boolean,
    spatialAvailable: Boolean,
    onGameModeChange: (Boolean) -> Unit,
    onInEarChange: (Boolean) -> Unit,
    onSpatialModeChange: (Int, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                uiText("НАСТРОЙКИ В НАУШНИКАХ", "ON-EARBUD SETTINGS"),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            EarbudSettingRow(
                title = uiText("Игровой режим", "Game mode"),
                subtitle = uiText("Уменьшает задержку Bluetooth-аудио", "Reduces Bluetooth audio latency"),
                checked = gameModeEnabled,
                available = gameModeAvailable && isConnected,
                onCheckedChange = onGameModeChange
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            EarbudSettingRow(
                title = uiText("Автопауза при снятии", "Pause when removed"),
                subtitle = uiText("Реакция на снятие наушника", "Detects when an earbud is removed"),
                checked = inEarEnabled,
                available = inEarAvailable && isConnected,
                onCheckedChange = onInEarChange
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(uiText("Пространственный звук", "Spatial audio"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                uiText("Выбор применяется в DSP наушников", "Mode runs in the earbuds’ DSP"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    0 to uiText("Выкл.", "Off"),
                    1 to uiText("С головой", "Head tracked"),
                    2 to uiText("Фиксированный", "Fixed"),
                    3 to uiText("Концерт", "Concert"),
                    4 to uiText("Кино", "Theatre"),
                    5 to uiText("Игра", "Game")
                ).forEach { (mode, title) ->
                    FilterChip(
                        selected = when (mode) {
                            0 -> spatialAudioMode == 0
                            1 -> spatialAudioMode == 1 && spatialHeadTracking
                            2 -> spatialAudioMode == 1 && !spatialHeadTracking
                            else -> spatialAudioMode == mode - 1
                        },
                        onClick = {
                            when (mode) {
                                0 -> onSpatialModeChange(0, false)
                                1 -> onSpatialModeChange(1, true)
                                2 -> onSpatialModeChange(1, false)
                                else -> onSpatialModeChange(mode - 1, false)
                            }
                        },
                        enabled = isConnected && spatialAvailable,
                        label = { Text(title) }
                    )
                }
            }
            if (!gameModeAvailable || !inEarAvailable) {
                Text(
                    uiText("Сначала подключитесь по Bluetooth, чтобы прочитать состояние.", "Connect over Bluetooth to read the current state."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EarbudSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    available: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = available)
    }
}

@Composable
private fun EarbudGestureCard(
    isConnected: Boolean,
    isAvailable: Boolean,
    actions: List<Int>,
    onChange: (Int, Int, Int) -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                uiText("СЕНСОРНЫЕ ЖЕСТЫ", "TOUCH GESTURES"),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                uiText("Настройки отдельно для левого и правого наушника", "Configure the left and right earbuds separately"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isAvailable) {
                Text(
                    uiText("Состояние жестов ещё не прочитано.", "Gesture settings have not been read yet."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onRefresh, enabled = isConnected) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(uiText("Прочитать настройки", "Read settings"))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(uiText("ЛЕВЫЙ", "LEFT"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        GestureActionPicker(2, uiText("2 касания", "Double tap"), 2, actions[0], isConnected, onChange)
                        GestureActionPicker(2, uiText("3 касания", "Triple tap"), 3, actions[1], isConnected, onChange)
                        GestureActionPicker(2, uiText("Удержание", "Hold"), 7, actions[2], isConnected, onChange)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(uiText("ПРАВЫЙ", "RIGHT"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        GestureActionPicker(3, uiText("2 касания", "Double tap"), 2, actions[3], isConnected, onChange)
                        GestureActionPicker(3, uiText("3 касания", "Triple tap"), 3, actions[4], isConnected, onChange)
                        GestureActionPicker(3, uiText("Удержание", "Hold"), 7, actions[5], isConnected, onChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureActionPicker(
    device: Int,
    title: String,
    gesture: Int,
    action: Int,
    enabled: Boolean,
    onChange: (Int, Int, Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = when (gesture) {
        2 -> listOf(2 to uiText("Воспроизведение", "Play / pause"), 8 to uiText("Предыдущий трек", "Previous track"), 9 to uiText("Следующий трек", "Next track"), 11 to uiText("Голосовой помощник", "Voice assistant"), 1 to uiText("Без действия", "No action"))
        3 -> listOf(8 to uiText("Предыдущий трек", "Previous track"), 9 to uiText("Следующий трек", "Next track"), 11 to uiText("Голосовой помощник", "Voice assistant"), 1 to uiText("Без действия", "No action"))
        else -> listOf(10 to uiText("Управление ANC", "Noise control"), 11 to uiText("Голосовой помощник", "Voice assistant"), 1 to uiText("Без действия", "No action"))
    }
    val actionName = options.firstOrNull { it.first == action }?.second
        ?: if (action >= 0) uiText("Действие $action", "Action $action") else uiText("Не прочитано", "Not read")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(actionName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onChange(device, gesture, value)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DualConnectionCard(
    isConnected: Boolean,
    isAvailable: Boolean,
    enabled: Boolean,
    devices: List<com.cmf.budscontroller.protocol.DualConnectionDevice>,
    deviceListAvailable: Boolean,
    onToggle: (Boolean) -> Unit,
    onDeviceToggle: (String, Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    var confirmEnabled by remember { mutableStateOf<Boolean?>(null) }
    var showMemoryResetSteps by remember { mutableStateOf(false) }
    val connectedCount = devices.count { it.isConnected }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                uiText("ДВОЙНОЕ ПОДКЛЮЧЕНИЕ", "DUAL CONNECTION"),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(uiText("Два устройства одновременно", "Two devices at once"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            !isAvailable -> uiText("Состояние не прочитано", "Status not read")
                            enabled -> uiText("Включено • $connectedCount из 2 подключено", "Enabled • $connectedCount of 2 connected")
                            else -> uiText("Выключено", "Disabled")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { confirmEnabled = it },
                    enabled = isConnected && isAvailable
                )
            }
            if (enabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(uiText("ЗАПОМНЕННЫЕ УСТРОЙСТВА", "REMEMBERED DEVICES"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    devices.isNotEmpty() -> devices.forEach { device ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (device.isCurrentDevice) uiText("Текущий телефон", "This phone") else device.name.ifBlank { device.macAddress },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!device.isCurrentDevice) Text(device.macAddress, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = device.isConnected,
                                onCheckedChange = { onDeviceToggle(device.macAddress, it) },
                                enabled = isConnected && deviceListAvailable && (device.isConnected || connectedCount < 2)
                            )
                        }
                    }
                    deviceListAvailable -> Text(uiText("Наушники не вернули список.", "The earbuds returned no saved devices."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Text(uiText("Читаю список с наушников…", "Reading the earbuds’ device list…"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onRefresh, enabled = isConnected) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(uiText("Обновить список", "Refresh list"))
                }
                TextButton(onClick = { showMemoryResetSteps = true }) {
                    Text(uiText("Очистить память устройств…", "Clear saved-device memory…"))
                }
            }
        }
    }
    confirmEnabled?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmEnabled = null },
            title = { Text(uiText("Наушники перезапустятся", "Earbuds will reboot")) },
            text = { Text(uiText("Для переключения двойного подключения наушникам нужен перезапуск. После этого переподключите их, чтобы проверить состояние.", "Changing dual connection restarts the earbuds. Reconnect afterward to verify the setting.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnabled = null
                    onToggle(target)
                }) { Text(uiText("Перезапустить", "Reboot earbuds")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnabled = null }) { Text(uiText("Отмена", "Cancel")) }
            }
        )
    }
    if (showMemoryResetSteps) {
        AlertDialog(
            onDismissRequest = { showMemoryResetSteps = false },
            title = { Text(uiText("Сбросить память наушников?", "Reset earbud memory?")) },
            text = {
                Text(
                    uiText(
                        "Удалить отдельное устройство из списка удалённо нельзя. Для очистки сохранённых подключений и настроек положите оба наушника в открытый кейс и удерживайте кнопку кейса 10 секунд, пока индикатор не мигнёт красным три раза. Это сбросит настройки наушников.",
                        "A single saved device cannot be removed remotely. To clear saved connections and earbud settings, place both earbuds in the open case and hold the case button for 10 seconds until the light flashes red three times. This resets earbud settings."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { showMemoryResetSteps = false }) {
                    Text(uiText("Понятно", "Got it"))
                }
            }
        )
    }
}

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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        border = null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = uiText("СТУДИЙНЫЙ ЭКВАЛАЙЗЕР", "STUDIO EQUALIZER"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (eqState.isEnabled) eqState.activePresetName else uiText("Эквалайзер выключен", "Equalizer is off"),
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
                            contentDescription = uiText("Импорт/экспорт JSON", "Import/export JSON"),
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
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { onPresetSelected("Nothing Clean") }) {
                    Text(uiText("Сброс", "Reset"))
                }
                OutlinedButton(onClick = {
                    eqState.bandLevels.forEachIndexed { index, db -> onBandChanged(index, -db) }
                }) { Text(uiText("Инверсия", "Invert")) }
                OutlinedButton(onClick = {
                    eqState.bandLevels.forEachIndexed { index, db -> onBandChanged(index, (db - 1).coerceAtLeast(-10)) }
                }) { Text("−1 dB") }
                OutlinedButton(onClick = {
                    eqState.bandLevels.forEachIndexed { index, db -> onBandChanged(index, (db + 1).coerceAtMost(10)) }
                }) { Text("+1 dB") }
            }
            Spacer(Modifier.height(12.dp))
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
                    .background(Color(0xFF000000))
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
                            color = Color(0xFF0A84FF),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // 3. Clean Band Nodes
                        points.forEach { pt ->
                            drawCircle(color = Color(0xFF0A84FF), radius = 4.dp.toPx(), center = pt)
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
                    val animatedDb = animatedBands.getOrElse(index) { levelDb.toFloat() }
                    val displayedDb = animatedDb.roundToInt()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (displayedDb > 0) "+$displayedDb" else "$displayedDb",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (displayedDb != 0) colorScheme.primary else colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Custom vertical slider column
                        Box(
                            modifier = Modifier
                                .width(34.dp)
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF000000))
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
                                    text = "${displayedDb}dB",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (displayedDb != 0) colorScheme.primary else colorScheme.onSurfaceVariant
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
                    text = uiText("ПРЕСЕТЫ:", "PRESETS:"),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = "+ ${uiText("Сохранить пресет", "Save preset")}",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0A84FF),
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
                    val chipBg by animateColorAsState(if (isSel) colorScheme.primary else Color(0xFF000000), tween(180))
                    val chipFg by animateColorAsState(
                        targetValue = if (isSel) Color.White else Color(0xFF8E8E93),
                        animationSpec = tween(180),
                        label = "studio_eq_preset_text"
                    )
                    val chipBorder by animateColorAsState(
                        targetValue = if (isSel) colorScheme.primary else Color(0x15FFFFFF),
                        animationSpec = tween(180),
                        label = "studio_eq_preset_border"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(chipBg)
                            .border(1.dp, chipBorder, RoundedCornerShape(12.dp))
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
                                    contentDescription = uiText("Удалить", "Delete"),
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
            title = { Text(uiText("Сохранить пресет", "Save preset"), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiText("Введите имя для текущей конфигурации из 5 полос:", "Enter a name for this 5-band configuration:"), fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        placeholder = { Text(uiText("Мой пресет...", "My preset...")) },
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
                    Text(uiText("Сохранить", "Save"), color = colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(AppStrings.btnCancel)
                }
            }
        )
    }

    // --- JSON Export & Import Dialog (Issue #3) ---
    if (showJsonDialog) {
        AlertDialog(
            onDismissRequest = { showJsonDialog = false },
            title = { Text(uiText("Импорт / экспорт эквалайзера JSON", "EQ JSON import / export"), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = uiText("Скопируйте пресеты в буфер обмена или вставьте JSON из сообщества:", "Copy your presets to the clipboard or paste JSON shared by the community:"),
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
                        placeholder = { Text(uiText("Вставьте JSON сюда...", "Paste JSON here...")) }
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
                            Text(uiText("Скопировать JSON", "Copy JSON"), fontSize = 11.sp)
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
                            Text(uiText("Вставить", "Paste"), fontSize = 11.sp)
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
                    Text(uiText("Применить JSON", "Apply JSON"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJsonDialog = false }) {
                    Text(AppStrings.btnClose)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        border = null
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
                        Box(modifier = Modifier.weight(midRatio).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFF5AC8FA), Color(0xFFFFAB40)))))
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
                    color = Color(0xFF5AC8FA),
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        border = null
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(uiText("АКТИВНОСТЬ ПО ДНЯМ НЕДЕЛИ", "WEEKLY ACTIVITY"), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = colorScheme.onSurfaceVariant)
                    Text("${uiText("Самый активный день", "Top day")}: ${insights.mostActiveDay}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
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
                insights.weeklyActivity.forEachIndexed { index, day ->
                    val hasMins = day.minutes > 0
                    val fraction = if (hasMins) (day.minutes.toFloat() / maxMin).coerceIn(0.12f, 1f) else 0.04f
                    val isPeak = day.minutes == maxMin && hasMins
                    val animatedFraction by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(760, delayMillis = index * 65, easing = FastOutSlowInEasing),
                        label = "weekly_bar_growth"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Text(
                        text = if (hasMins) "${day.minutes}${uiText("м", "m")}" else "-",
                            fontSize = 10.sp,
                            fontWeight = if (isPeak) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isPeak) Color(0xFF0A84FF) else colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(70.dp * animatedFraction.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    if (isPeak) Brush.verticalGradient(listOf(Color(0xFF0A84FF), Color(0xFF5AC8FA)))
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
            .background(Color(0xFF000000))
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
            Text("$minutes ${uiText("мин", "min")}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface, softWrap = false)
        }
    }
}

// -------------------------------------------------------------
// Studio Components (Seamless Showcase, Continuous Noise Slider, Bento Grid)
// -------------------------------------------------------------

@Composable
fun StudioProductStage(
    battery: BatteryInfo,
    isConnected: Boolean,
    earbudTimeFormatted: String,
    totalWithCaseFormatted: String
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(colorScheme.surfaceVariant.copy(alpha = 0.96f), colorScheme.surface)
                )
            )
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
            .padding(vertical = 16.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colorScheme.primary.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.38f),
                    radius = size.width * 0.58f
                ),
                radius = size.width * 0.58f,
                center = Offset(size.width * 0.5f, size.height * 0.38f)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp),
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

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedVisibility(
                visible = isConnected,
                enter = fadeIn(tween(180)) +
                    expandHorizontally(spring(dampingRatio = 0.78f, stiffness = 380f)) +
                    scaleIn(spring(dampingRatio = 0.76f, stiffness = 420f), initialScale = 0.96f),
                exit = fadeOut(tween(120)) +
                    shrinkHorizontally(spring(dampingRatio = 0.86f, stiffness = 500f)) +
                    scaleOut(tween(150), targetScale = 0.97f),
                label = "battery_pills_connection"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorScheme.background.copy(alpha = 0.62f))
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniBatteryTag(name = "L", percent = battery.leftPercent, isCharging = battery.isLeftCharging)
                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0xFF2C2C2E)))
                    MiniBatteryTag(name = AppStrings.batteryCase, percent = battery.casePercent, isCharging = battery.isCaseCharging)
                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0xFF2C2C2E)))
                    MiniBatteryTag(name = "R", percent = battery.rightPercent, isCharging = battery.isRightCharging)
                }
            }

            if (earbudTimeFormatted.isNotBlank() && earbudTimeFormatted != "—") {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("${AppStrings.earbudBatteryPrefix} ~$earbudTimeFormatted", fontSize = 11.sp, color = Color(0xFF8E8E93))
                    Text("${AppStrings.caseBatteryPrefix} ~$totalWithCaseFormatted", fontSize = 11.sp, color = Color(0xFF8E8E93))
                }
            }
        }
    }
}

@Composable
fun MiniBatteryTag(name: String, percent: Int, isCharging: Boolean) {
    val animatedPercent by animateIntAsState(
        targetValue = percent.coerceIn(0, 100),
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "battery_percent"
    )
    val targetLevelColor = when {
        isCharging -> Color(0xFF00E676)
        percent > 50 -> Color(0xFF00E676)
        percent > 20 -> Color(0xFFFFB300)
        percent >= 0 -> Color(0xFFFF334B)
        else -> Color(0xFF8E95A5)
    }
    val levelColor by animateColorAsState(
        targetValue = targetLevelColor,
        animationSpec = tween(260),
        label = "battery_level_color"
    )

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
            text = if (percent in 0..100) "$animatedPercent%" else "—",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Black,
            color = if (isCharging) levelColor else Color.White
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
    val dimens = com.cmf.budscontroller.ui.theme.LocalCmfDimens.current
    val context = LocalContext.current
    val haptic = remember { com.cmf.budscontroller.ui.haptic.CmfHapticManager(context) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        border = null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppStrings.headerNoiseControl,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = colorScheme.onSurfaceVariant
                )
                val modeLabel = when (currentMode) {
                    AncMode.OFF -> AppStrings.ancOff
                    AncMode.TRANSPARENCY -> AppStrings.ancTransparency
                    AncMode.NOISE_CANCELLATION -> "${AppStrings.ancCancellation} (${currentLevel.title})"
                }
                val modeLabelColor by animateColorAsState(
                    targetValue = if (currentMode != AncMode.OFF) colorScheme.primary else colorScheme.onSurfaceVariant,
                    animationSpec = tween(180),
                    label = "noise_mode_label_color"
                )
                AnimatedContent(
                    targetState = modeLabel,
                    transitionSpec = {
                        (fadeIn(tween(140)) + slideInVertically(tween(160)) { it / 3 }) togetherWith
                            (fadeOut(tween(100)) + slideOutVertically(tween(120)) { -it / 3 })
                    },
                    label = "noise_mode_label"
                ) { label ->
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = modeLabelColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                    .background(Color(0xFF000000))
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
                        .height(dimens.controlTouchTarget)
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
                        Triple(AncMode.OFF, AppStrings.ancOff, Icons.AutoMirrored.Outlined.VolumeOff),
                        Triple(AncMode.TRANSPARENCY, AppStrings.ancTransparency, Icons.Outlined.Hearing),
                        Triple(AncMode.NOISE_CANCELLATION, AppStrings.ancCancellation, Icons.Outlined.Shield)
                    )

                    modes.forEach { (mode, title, icon) ->
                        val isSelected = currentMode == mode
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else Color(0xFF8E95A5),
                            animationSpec = tween(220),
                            label = "noise_text_color"
                        )
                        val iconColor by animateColorAsState(
                            targetValue = if (isSelected) Color(0xFF0A84FF) else Color(0xFF6C7385),
                            animationSpec = tween(220),
                            label = "noise_icon_color"
                        )

                        Row(
                            modifier = Modifier
                                .weight(if (mode == AncMode.TRANSPARENCY) 1.25f else 1f)
                                .heightIn(min = dimens.controlTouchTarget)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(
                                    interactionSource = remember(mode) { MutableInteractionSource() },
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
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = AppStrings.ancDepthLabel,
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
                            val chipFg by animateColorAsState(
                                targetValue = if (isSel) Color.White else colorScheme.onSurfaceVariant,
                                animationSpec = tween(180),
                                label = "anc_level_text"
                            )
                            val chipBorder by animateColorAsState(
                                targetValue = if (isSel) colorScheme.primary else colorScheme.outlineVariant,
                                animationSpec = tween(180),
                                label = "anc_level_border"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(if (lvl == AncLevel.ADAPTIVE) 1.22f else 1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(chipBg)
                                    .border(1.dp, chipBorder, RoundedCornerShape(12.dp))
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
    val entrySequence = LocalEntryStagger.current
    val entryDelay = remember(entrySequence) { entrySequence?.nextDelayMillis() ?: 0 }
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(220),
        label = "bento_card_accent"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "card_press_scale"
    )
    val cardOutline by animateColorAsState(
        targetValue = if (isPressed) accentColor.copy(alpha = 0.34f) else colorScheme.outlineVariant.copy(alpha = 0.38f),
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "card_press_outline"
    )

    AnimatedVisibility(
        visible = true,
        modifier = modifier,
        enter = fadeIn(tween(300, delayMillis = entryDelay, easing = FastOutSlowInEasing)) +
            slideInVertically(tween(340, delayMillis = entryDelay, easing = FastOutSlowInEasing)) { it / 12 } +
            scaleIn(tween(300, delayMillis = entryDelay, easing = FastOutSlowInEasing), initialScale = 0.97f),
        label = "card_cascade_entry"
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(spring(dampingRatio = 0.82f, stiffness = 420f))
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .border(1.dp, cardOutline, RoundedCornerShape(20.dp))
                .then(if (onClick != null) Modifier.clickable(interactionSource = interactionSource, indication = null) { onClick() } else Modifier),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                            tint = animatedAccent
                        )
                    }

                    if (onClick != null) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.1.sp,
                    color = colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                if (subtitle.length <= 40) {
                    AnimatedContent(
                        targetState = subtitle,
                        transitionSpec = {
                            (fadeIn(tween(150, easing = FastOutSlowInEasing)) +
                                slideInVertically(tween(180, easing = FastOutSlowInEasing)) { it / 4 }) togetherWith
                                (fadeOut(tween(100, easing = FastOutLinearInEasing)) +
                                    slideOutVertically(tween(130, easing = FastOutLinearInEasing)) { -it / 4 })
                        },
                        label = "bento_short_status"
                    ) { statusText ->
                        Text(
                            text = statusText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }

                if (extraContent != null) {
                    extraContent()
                }
            }
        }
    }
}
