package com.cmf.budscontroller.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
            // Pure Frosted Gray Glass Palette (Liquid Gray Glass & Specular)
            val colorScheme = darkColorScheme(
                primary = Color(0xFFFF5722),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0x352E323D),
                onPrimaryContainer = Color(0xFFF2F2F7),
                secondary = Color(0xFFE2E2E8),
                onSecondary = Color(0xFF141416),
                background = Color(0xFF0D0E12),          // Deep Tinted Glass Canvas
                onBackground = Color(0xFFF0F0F5),
                surface = Color(0x28262933),             // Liquid Gray Frosted Glass
                onSurface = Color(0xFFF0F0F5),
                surfaceVariant = Color(0x38181A20),      // Smoked Translucent Glass
                onSurfaceVariant = Color(0xFF9898A6),    // Refined Specular Gray
                outline = Color(0x40FFFFFF),             // 25% White Specular Hairline
                outlineVariant = Color(0x18FFFFFF)       // 9% White Subtle Divider
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

enum class StudioTab(val label: String, val icon: ImageVector) {
    DEVICE("Обзор", Icons.Outlined.Headphones),
    STUDIO("Звук", Icons.Outlined.GraphicEq),
    INSIGHTS("Аналитика", Icons.Outlined.BarChart),
    FEATURES("Функции", Icons.Outlined.Tune),
    BRIDGE("Мост", Icons.Outlined.Devices)
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
    val codecState by viewModel.codecState.collectAsState()

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

    val colorScheme = MaterialTheme.colorScheme

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
                                letterSpacing = (-0.4).sp,
                                color = colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (status.isConnected) Color(0xFF30D158) else Color(0xFF8E8E93))
                                )
                                Text(
                                    text = if (status.isConnected) "Подключено • Hi-Res LDAC" else "Готов к подключению",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (status.isConnected) Color(0xFF30D158) else colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colorScheme.surfaceVariant)
                            .border(1.dp, colorScheme.outline, RoundedCornerShape(16.dp))
                            .clickable {
                                if (status.isConnected) showDevicePicker = true
                                else onConnectClicked()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (status.isConnected) Icons.Outlined.BluetoothConnected else Icons.Outlined.BluetoothSearching,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (status.isConnected) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (status.isConnected) "Устройства" else "Найти",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(onClick = {
                        viewModel.refreshStatus()
                        if (!status.isConnected) onConnectClicked()
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Обновить", tint = colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color(0x60FFFFFF),
                                Color(0x18FFFFFF),
                                Color(0x35FFFFFF)
                            )
                        ),
                        RoundedCornerShape(28.dp)
                    ),
                shape = RoundedCornerShape(28.dp),
                color = Color(0x33282C38),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        val animatedBg by animateColorAsState(
                            targetValue = if (isSelected) Color(0x40FFFFFF) else Color.Transparent,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                        )
                        val animatedBorder by animateColorAsState(
                            targetValue = if (isSelected) Color(0x55FFFFFF) else Color.Transparent,
                            animationSpec = tween(200)
                        )
                        val animatedFg by animateColorAsState(
                            targetValue = if (isSelected) Color.White else Color(0xFF9898A6),
                            animationSpec = tween(200)
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(animatedBg)
                                .border(1.dp, animatedBorder, RoundedCornerShape(20.dp))
                                .clickable { currentTab = tab }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(19.dp),
                                tint = animatedFg
                            )
                            if (isSelected) {
                                Text(
                                    text = tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = animatedFg
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

                    // Studio-grade Interactive Multi-band EQ
                    StudioMasterEqualizerCard(
                        eqState = eqState,
                        onToggle = { viewModel.toggleEq(it) },
                        onPresetSelected = { viewModel.setEqPreset(it) },
                        onBandChanged = { band, db -> viewModel.setEqBand(band, db) }
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
                        title = "ОЧИСТКА ОТ ВОДЫ И ПЫЛИ (WATER EJECT)",
                        subtitle = if (waterState.isRunning) "Выталкивание влаги... (${waterState.progressSeconds}с)" else "Резонансные звуковые волны 140–180 Гц",
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
                            Text(if (waterState.isRunning) "Остановить очистку" else "Запустить вибро-волну", fontSize = 12.sp)
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

                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "ТРАНСЛЯЦИЯ ЗВУКА С ПК (LOW LATENCY)",
                        subtitle = if (relayState.isRunning) "Сервер запущен (Порт 59100) • Задержка ~${relayState.latencyEstimateMs} мс" else "Слушайте звук с компьютера на наушниках через Wi-Fi/USB",
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
                                Text("Настройки: ${relayState.mode.title}", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = relayState.isRunning,
                                onCheckedChange = { viewModel.toggleAudioRelay() }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // --- DIALOGS ---
    if (showCodecDialog) {
        AlertDialog(
            onDismissRequest = { showCodecDialog = false },
            icon = { Icon(Icons.Outlined.HighQuality, contentDescription = null, tint = colorScheme.primary) },
            title = { Text("Выбор Bluetooth аудиокодека", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Выберите кодек для передачи звука на CMF Buds Pro 2. Изменение отправляет аппаратно-программный пакет на наушники:",
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
            shape = RoundedCornerShape(26.dp)
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
            shape = RoundedCornerShape(26.dp)
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
            shape = RoundedCornerShape(26.dp)
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
            shape = RoundedCornerShape(26.dp)
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
            shape = RoundedCornerShape(26.dp)
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
            shape = RoundedCornerShape(26.dp)
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
            shape = RoundedCornerShape(26.dp)
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
            shape = RoundedCornerShape(26.dp)
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
    onBandChanged: (Int, Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val bandLabels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(colorScheme.outline, colorScheme.outlineVariant)))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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

                Switch(
                    checked = eqState.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.scale(0.85f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Visual Hi-Fi Smooth Response Curve Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorScheme.surface)
                    .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val midY = h / 2f

                    // Zero dB reference grid line
                    drawLine(
                        color = Color(0x33FFFFFF),
                        start = Offset(0f, midY),
                        end = Offset(w, midY),
                        strokeWidth = 1.dp.toPx()
                    )

                    val points = eqState.bandLevels.mapIndexed { idx, db ->
                        val x = if (eqState.bandLevels.size > 1) {
                            (idx.toFloat() / (eqState.bandLevels.size - 1)) * w
                        } else w / 2f
                        val y = midY - (db.toFloat() / 10f) * (midY * 0.85f)
                        Offset(x, y)
                    }

                    // Smooth Bezier Curve Path
                    val curvePath = Path()
                    if (points.isNotEmpty()) {
                        curvePath.moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val controlX1 = (p0.x + p1.x) / 2f
                            val controlY1 = p0.y
                            val controlX2 = (p0.x + p1.x) / 2f
                            val controlY2 = p1.y
                            curvePath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
                        }

                        drawPath(
                            path = curvePath,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFFF5722),
                                    Color(0xFFFF9F0A),
                                    Color(0xFFFF375F)
                                )
                            ),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Glowing Points
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
                                .background(colorScheme.surface)
                                .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
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
                                    onClick = { onBandChanged(index, (levelDb + 1).coerceAtMost(10)) },
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
                                    onClick = { onBandChanged(index, (levelDb - 1).coerceAtLeast(-10)) },
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

            // Presets Horizontal Carousel Chips
            Text(
                text = "ПРЕДУСТАНОВКИ:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                eqState.presets.take(4).forEach { preset ->
                    val isSel = eqState.activePresetName == preset.name
                    val chipBg by animateColorAsState(if (isSel) colorScheme.primary else colorScheme.surface, tween(180))
                    val chipFg = if (isSel) Color.White else colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(chipBg)
                            .border(1.dp, if (isSel) colorScheme.primary else colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable { onPresetSelected(preset.name) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.name.split(" ").first(),
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = chipFg
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Deep User Usage Analytics Dashboard Component
// -------------------------------------------------------------

@Composable
fun BudsDeepAnalyticsDashboard(insights: BudsUsageInsights) {
    val colorScheme = MaterialTheme.colorScheme

    Text(
        text = "АНАЛИТИКА И СТАТИСТИКА ИСПОЛЬЗОВАНИЯ",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )

    // 1. Volume Time Distribution (На какой громкости сколько слушаю)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x301E212A)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0x65FFFFFF),
                    Color(0x20FFFFFF),
                    Color(0x0CFFFFFF),
                    Color(0x28FFFFFF)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("РАСПРЕДЕЛЕНИЕ ГРОМКОСТИ", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = colorScheme.onSurfaceVariant)
                    Text("Время по уровням звука", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Всего: ${insights.volumeStats.totalMinutes} мин", fontSize = 11.sp, color = colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multicolored Segmented Progress Bar
            val total = insights.volumeStats.totalMinutes.coerceAtLeast(1).toFloat()
            val lowRatio = insights.volumeStats.lowVolumeMinutes / total
            val midRatio = insights.volumeStats.midVolumeMinutes / total
            val highRatio = insights.volumeStats.highVolumeMinutes / total

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                Box(modifier = Modifier.weight(lowRatio.coerceAtLeast(0.05f)).fillMaxHeight().background(Color(0xFF30D158)))
                Spacer(modifier = Modifier.width(2.dp))
                Box(modifier = Modifier.weight(midRatio.coerceAtLeast(0.05f)).fillMaxHeight().background(Color(0xFFFF9F0A)))
                Spacer(modifier = Modifier.width(2.dp))
                Box(modifier = Modifier.weight(highRatio.coerceAtLeast(0.05f)).fillMaxHeight().background(Color(0xFFFF453A)))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Volume Tier Detail Tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VolumeStatPill(
                    title = "Тихая (0-30%)",
                    minutes = insights.volumeStats.lowVolumeMinutes,
                    color = Color(0xFF30D158),
                    modifier = Modifier.weight(1f)
                )
                VolumeStatPill(
                    title = "Средняя (31-70%)",
                    minutes = insights.volumeStats.midVolumeMinutes,
                    color = Color(0xFFFF9F0A),
                    modifier = Modifier.weight(1f)
                )
                VolumeStatPill(
                    title = "Громкая (71-100%)",
                    minutes = insights.volumeStats.highVolumeMinutes,
                    color = Color(0xFFFF453A),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 2. Weekly Activity Histogram (Как часто я слушаю музыку и в какие дни)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x301E212A)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0x65FFFFFF),
                    Color(0x20FFFFFF),
                    Color(0x0CFFFFFF),
                    Color(0x28FFFFFF)
                )
            )
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
            val maxMin = insights.weeklyActivity.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 180
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom
            ) {
                insights.weeklyActivity.forEach { day ->
                    val fraction = (day.minutes.toFloat() / maxMin).coerceIn(0.12f, 1f)
                    val isPeak = day.minutes == maxMin

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            text = "${day.minutes}м",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPeak) colorScheme.primary else colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    if (isPeak) Brush.verticalGradient(listOf(colorScheme.primary, Color(0xFFFF9F0A)))
                                    else Brush.verticalGradient(listOf(Color(0xFF2C2C36), Color(0xFF1E1E26)))
                                )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = day.dayName,
                            fontSize = 11.sp,
                            fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal,
                            color = if (isPeak) colorScheme.onSurface else colorScheme.onSurfaceVariant
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
            title = "ОТКРЫТИЙ КЕЙСА",
            subtitle = "${insights.caseOpensToday} раз сегодня",
            icon = Icons.Outlined.SensorDoor,
            accentColor = Color(0xFF64D2FF)
        ) {
            Text(
                text = "Всего за всё время: ${insights.totalCaseOpens}",
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Average Full Battery Hours Tile
        BentoCard(
            modifier = Modifier.weight(1f),
            title = "АВТОНОМНОСТЬ 100%",
            subtitle = "~${insights.averageFullBatteryHours} часов в ср.",
            icon = Icons.Outlined.BatteryChargingFull,
            accentColor = Color(0xFF30D158)
        ) {
            Text(
                text = "Здоровье батареи: ${insights.batteryHealthEstimate}%",
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
    minutes: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colorScheme.surface)
            .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, fontSize = 9.sp, color = colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text("${minutes} мин", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
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
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF221A18),
                        Color(0xFF131317),
                        Color(0xFF0E0E12)
                    ),
                    radius = 800f
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color(0x33FFFFFF),
                        Color(0x0AFFFFFF)
                    )
                ),
                RoundedCornerShape(32.dp)
            )
            .padding(vertical = 20.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
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
                    contentDescription = "CMF Case with Smart Dial",
                    modifier = Modifier
                        .fillMaxHeight(0.95f)
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

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x60000000))
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniBatteryTag(name = "L", percent = battery.leftPercent, isCharging = battery.isLeftCharging)
                Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0x22FFFFFF)))
                MiniBatteryTag(name = "КЕЙС", percent = battery.casePercent, isCharging = battery.isCaseCharging)
                Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0x22FFFFFF)))
                MiniBatteryTag(name = "R", percent = battery.rightPercent, isCharging = battery.isRightCharging)
            }

            if (earbudTimeFormatted.isNotBlank() && earbudTimeFormatted != "—") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Наушники: ~$earbudTimeFormatted", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                    Text("С кейсом: ~$totalWithCaseFormatted", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun MiniBatteryTag(name: String, percent: Int, isCharging: Boolean) {
    val text = if (percent in 0..100) "$percent%" else "—"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (isCharging) {
            Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF30D158))
        }
        Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8E8E98))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isCharging) Color(0xFF30D158) else Color.White)
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x301E212A)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0x65FFFFFF),
                    Color(0x20FFFFFF),
                    Color(0x0CFFFFFF),
                    Color(0x28FFFFFF)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colorScheme.background)
                    .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NoiseSliderOption(
                    title = "Выкл",
                    icon = Icons.AutoMirrored.Outlined.VolumeOff,
                    isSelected = currentMode == AncMode.OFF,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeSelected(AncMode.OFF) }
                )
                NoiseSliderOption(
                    title = "Прозрачность",
                    icon = Icons.Outlined.Hearing,
                    isSelected = currentMode == AncMode.TRANSPARENCY,
                    modifier = Modifier.weight(1.3f),
                    onClick = { onModeSelected(AncMode.TRANSPARENCY) }
                )
                NoiseSliderOption(
                    title = "Шумодав",
                    icon = Icons.Outlined.Shield,
                    isSelected = currentMode == AncMode.NOISE_CANCELLATION,
                    modifier = Modifier.weight(1.2f),
                    onClick = { onModeSelected(AncMode.NOISE_CANCELLATION) }
                )
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
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(chipBg)
                                    .border(1.dp, if (isSel) colorScheme.primary else colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .clickable { onLevelSelected(lvl) }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lvl.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = chipFg
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
                    Modifier.clickable(
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x301E212A)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0x65FFFFFF), // High specular top rim
                    Color(0x20FFFFFF),
                    Color(0x0CFFFFFF),
                    Color(0x28FFFFFF)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surface)
                        .border(1.dp, colorScheme.outlineVariant, CircleShape),
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
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 2,
                lineHeight = 18.sp
            )

            if (extraContent != null) {
                extraContent()
            }
        }
    }
}
