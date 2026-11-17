package com.cmf.budscontroller.ui.components

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.watch.WatchBetaManager

private val WatchOrange = Color(0xFFFF5722)
private val WatchCard = Color(0xFF1C1C1E)

@Composable
fun WatchPage(manager: WatchBetaManager) {
    val state by manager.state.collectAsState()
    val health by manager.control.health.summary.collectAsState()
    val russian = AppStrings.isRussian
    val context = LocalContext.current
    fun t(ru: String, en: String) = if (russian) ru else en
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (manager.hasPermissions()) {
            if (!state.enabled) manager.enable(true)
            manager.scan()
        }
    }
    fun findWatch() {
        if (manager.hasPermissions()) {
            if (!state.enabled) manager.enable(true)
            manager.scan()
        } else {
            val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionLauncher.launch(permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray())
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("CMF WATCH PRO 2", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = WatchOrange)
                Text(t("ТВОИ ЧАСЫ", "YOUR WATCH"), fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.6).sp)
            }
            Box(Modifier.size(42.dp).clip(CircleShape).background(WatchOrange.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.WatchLater, null, tint = WatchOrange, modifier = Modifier.size(23.dp))
            }
        }

        // Product hero and connection state
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WatchCard)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(state.model.ifBlank { "CMF Watch Pro 2" }, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(when {
                            state.authenticated -> t("ПОДКЛЮЧЕНО · АВТОРИЗОВАНО", "CONNECTED · AUTHENTICATED")
                            state.connected -> t("ПОДКЛЮЧЕНО · НУЖНА АВТОРИЗАЦИЯ", "CONNECTED · AUTHENTICATION NEEDED")
                            state.connecting -> t("ПОДКЛЮЧЕНИЕ…", "CONNECTING…")
                            else -> t("НЕ ПОДКЛЮЧЕНО", "NOT CONNECTED")
                        }, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp,
                            color = if (state.connected) Color(0xFF30D158) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(state.battery?.let { "$it%" } ?: "—", color = WatchOrange, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = ::findWatch, enabled = !state.scanning && !state.connecting && !state.connected,
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = WatchOrange), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Outlined.Search, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(if (state.scanning) t("Поиск…", "Scanning…") else t("Найти часы", "Find watch"), fontWeight = FontWeight.Bold)
                    }
                    if (state.connected || state.connecting) OutlinedButton(onClick = { manager.disconnect() }, shape = RoundedCornerShape(14.dp)) { Text(t("Отключить", "Disconnect")) }
                }
                if (state.scanning || state.connecting || state.authenticating) LinearProgressIndicator(Modifier.fillMaxWidth(), color = WatchOrange)
                if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AnimatedVisibility(
            visible = state.devices.isNotEmpty() && !state.connected,
            enter = fadeIn(tween(220)) + expandVertically(tween(260, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(200, easing = FastOutSlowInEasing)),
            label = "watch_devices_visibility"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(t("НАЙДЕННЫЕ ЧАСЫ", "AVAILABLE WATCHES"))
                state.devices.forEach { device ->
                    Card(colors = CardDefaults.cardColors(containerColor = WatchCard), shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { manager.connect(device.address) }, enabled = !state.connecting, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(device.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(device.address, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Outlined.Bluetooth, null, tint = WatchOrange)
                        }
                        }
                    }
                }
            }
        }

        SectionLabel(t("ЗДОРОВЬЕ · СЕГОДНЯ", "HEALTH · TODAY"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WatchMetricCard(Modifier.weight(1f), t("ШАГИ", "STEPS"), health.steps.toString(), WatchOrange)
            WatchMetricCard(Modifier.weight(1f), t("ПУЛЬС", "HEART RATE"), health.heartRate?.let { "$it" } ?: "—", Color(0xFFFF453A), "bpm")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WatchMetricCard(Modifier.weight(1f), "SpO₂", health.spo2?.let { "$it%" } ?: "—", Color(0xFF64D2FF))
            WatchMetricCard(Modifier.weight(1f), t("СОН", "SLEEP"), if (health.sleepMinutes > 0) "${health.sleepMinutes / 60}h ${health.sleepMinutes % 60}m" else "—", Color(0xFFBF5AF2))
        }
        if (state.connected) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = WatchCard)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.FavoriteBorder, null, tint = WatchOrange)
                        Text(t("ЗДОРОВЬЕ И УВЕДОМЛЕНИЯ", "HEALTH & NOTIFICATIONS"), fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = .6.sp)
                    }
                    WatchHealthNotificationsPanel(manager)
                }
            }
        } else {
            Text(t("Подключи часы, чтобы увидеть показатели и управлять синхронизацией.", "Connect your watch to view readings and manage sync."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.firmware.takeIf { it.isNotBlank() }?.let { Text("${t("ПРОШИВКА", "FIRMWARE")}  $it", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 3.dp))
}

@Composable
private fun WatchMetricCard(modifier: Modifier, label: String, value: String, accent: Color, unit: String = "") {
    val animatedAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(220),
        label = "watch_metric_color"
    )
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(WatchCard).border(1.dp, Color.White.copy(alpha = .04f), RoundedCornerShape(20.dp)).animateContentSize().padding(16.dp)) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 })
                },
                label = "watch_metric_value"
            ) { currentValue ->
                Text(currentValue, fontSize = 25.sp, fontWeight = FontWeight.Black, color = animatedAccent, maxLines = 1)
            }
            if (unit.isNotBlank()) Text(unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}
