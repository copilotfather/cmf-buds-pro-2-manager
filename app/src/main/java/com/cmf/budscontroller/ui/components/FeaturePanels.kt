package com.cmf.budscontroller.ui.components

import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.ui.MainViewModel
import com.cmf.budscontroller.widget.BudsWidget
import kotlin.math.abs

private fun tr(ru: String, en: String) = if (AppStrings.isRussian) ru else en

@Composable
private fun FeatureSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun ProfilePanel(vm: MainViewModel) {
    val message by vm.profileMessage.collectAsState()
    FeatureSection(tr("Профили звука", "Listening profiles")) {
        Text(
            tr(
                "Сохраняйте наборы ANC, баса, игрового режима и телефонного EQ.",
                "Save combinations of ANC, bass, game mode and phone EQ."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        listOf("metro" to tr("Метро", "Metro"), "work" to tr("Работа", "Work"), "games" to tr("Игры", "Games"), "sleep" to tr("Сон", "Sleep"))
            .chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (id, name) ->
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(onClick = { vm.applyProfile(id) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                        Text(tr("Включить", "Apply"), maxLines = 1)
                                    }
                                    OutlinedButton(onClick = { vm.saveProfile(id) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                        Text(tr("Сохранить", "Save"), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        if (message.isNotBlank()) {
            androidx.compose.animation.AnimatedContent(
                targetState = message,
                transitionSpec = { androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
                label = "profile_feedback"
            ) { currentMessage ->
                Text(currentMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun DiagnosticsPanel(vm: MainViewModel) {
    val status by vm.deviceStatus.collectAsState()
    val command by vm.commandStatus.collectAsState()
    val context = LocalContext.current
    val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    val output = audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type in listOf(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_BLE_HEADSET) &&
            it.productName.toString().lowercase().replace(Regex("[^a-z0-9]"), "") == "cmfbudspro2"
    }
    FeatureSection(tr("Диагностика подключения", "Connection diagnostics")) {
        Text(if (status.isConnected) status.deviceName else tr("Наушники не подключены", "Earbuds disconnected"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Audio" to output, "SPP" to status.isSppActive, "BLE" to status.isGattActive).forEach { (label, active) ->
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(if (active) Color(0xFF30D158) else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                            Text(if (active) tr("Активен", "Active") else tr("Нет", "Off"), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        if (command.isNotBlank()) {
            val commandColor = when {
                command.contains("подтвержд", ignoreCase = true) || command.contains("confirmed", ignoreCase = true) -> Color(0xFF30D158)
                command.contains("нет подтверждения", ignoreCase = true) || command.contains("no confirmation", ignoreCase = true) || command.contains("не подтвердили", ignoreCase = true) || command.contains("did not confirm", ignoreCase = true) || command.contains("не отправлена", ignoreCase = true) || command.contains("not sent", ignoreCase = true) -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            Surface(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                color = commandColor.copy(alpha = 0.10f),
                shape = MaterialTheme.shapes.small
            ) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(commandColor, CircleShape))
                    androidx.compose.animation.AnimatedContent(
                        targetState = command,
                        transitionSpec = { androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
                        label = "command_feedback"
                    ) { currentCommand ->
                        Text(currentCommand, style = MaterialTheme.typography.bodySmall, color = commandColor)
                    }
                }
            }
        }
        Text(when {
            !status.isConnected -> tr("Включите Bluetooth и подключите CMF Buds Pro 2 в настройках телефона.", "Enable Bluetooth and pair CMF Buds Pro 2 in phone settings.")
            !status.isSppActive && !status.isGattActive -> tr("Доступен системный статус. Канал управления ещё не установлен; повторите подключение.", "System status available. Control transport is not ready; retry connection.")
            else -> tr("Канал управления доступен. Применение команд зависит от прошивки наушников.", "Control transport available. Command support depends on earbud firmware.")
        })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.reconnect() }) { Text(tr("Подключить", "Connect")) }
            OutlinedButton(onClick = { vm.refreshStatus() }) { Text(tr("Обновить", "Refresh")) }
        }
    }
}

@Composable
fun ComparisonPanel(vm: MainViewModel) {
    var savedA by remember { mutableStateOf(false) }
    var savedB by remember { mutableStateOf(false) }
    val side by vm.comparisonSide.collectAsState()
    FeatureSection(tr("Сравнение эквалайзера A/B", "EQ A/B comparison")) {
        Text(tr("Сохраните две кривые и переключайте их во время прослушивания.", "Capture two curves and switch while listening."))
        Text(tr("Обработка Android зависит от аудиоплеера и поддержки аудиоэффектов телефоном.", "Android processing depends on the player and phone audio-effects support."), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("A", "B").forEach { target ->
                Column(Modifier.weight(1f)) {
                    OutlinedButton(onClick = {
                        vm.captureComparison(target)
                        if (target == "A") savedA = true else savedB = true
                    }) { Text(tr("Запомнить", "Capture") + " $target") }
                    FilterChip(selected = side == target, enabled = if (target == "A") savedA else savedB,
                        onClick = { vm.compare(target) }, label = { Text(tr("Слушать", "Listen") + " $target") })
                }
            }
        }
    }
}

@Composable
fun BatteryHistoryPanel(vm: MainViewModel) {
    val history by vm.batteryHistory.collectAsState()
    val status by vm.deviceStatus.collectAsState()
    val b = status.battery
    FeatureSection(tr("Заряд за 7 дней", "7-day battery history")) {
        if (history.isEmpty()) Text(tr("История появится после подключения наушников.", "History starts when earbuds connect."))
        else {
            val leftColor = MaterialTheme.colorScheme.primary
            val rightColor = androidx.compose.ui.graphics.Color(0xFFFF9F0A)
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                repeat(5) { i -> val y = size.height * i / 4f
                    drawLine(androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f), Offset(0f,y), Offset(size.width,y)) }
                val first = history.first().time
                val span = (history.last().time - first).coerceAtLeast(1).toFloat()
                listOf(true to leftColor, false to rightColor).forEach { (left, color) ->
                    history.zipWithNext().forEach { (a,b) ->
                        val av = if (left) a.left else a.right
                        val bv = if (left) b.left else b.right
                        if (av in 0..100 && bv in 0..100 && b.time - a.time < 1_800_000) {
                            drawLine(color, Offset((a.time-first)/span*size.width, (1-av/100f)*size.height), Offset((b.time-first)/span*size.width, (1-bv/100f)*size.height), 3.dp.toPx())
                        }
                    }
                    history.forEach { s -> val v = if (left) s.left else s.right
                        if (v in 0..100) drawCircle(color, 2.dp.toPx(), Offset((s.time-first)/span*size.width,(1-v/100f)*size.height)) }
                }
            }
            Text(tr("Синий: левый · Оранжевый: правый · 0–100%", "Blue: left · Orange: right · 0–100%"), style = MaterialTheme.typography.bodySmall)
            val format = remember { java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault()) }
            Text("${format.format(java.util.Date(history.first().time))} — ${format.format(java.util.Date(history.last().time))}", style = MaterialTheme.typography.bodySmall)
            Text(tr("Последний заряд кейса", "Last case battery") + ": ${history.last().case.takeIf { it in 0..100 }?.let { "$it%" } ?: "—"}")
        }
        if (status.isConnected && b.leftPercent in 0..100 && b.rightPercent in 0..100 && abs(b.leftPercent - b.rightPercent) >= 15) {
            Text(tr("Разница между наушниками ≥15%. Проверьте контакты зарядки и посадку в кейсе.", "Earbud difference ≥15%. Check charging contacts and seating in the case."), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun WidgetSettingsPanel() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cmf_widgets", android.content.Context.MODE_PRIVATE) }
    var light by remember { mutableStateOf(prefs.getBoolean("light", false)) }
    var accent by remember { mutableStateOf(prefs.getInt("accent", 0xFFFF5555.toInt())) }
    var third by remember { mutableStateOf(prefs.getString("third", "bass") ?: "bass") }
    fun save() {
        prefs.edit().putBoolean("light",light).putInt("accent",accent).putString("third",third).apply()
        BudsWidget.updateAll(context)
    }
    FeatureSection(tr("Оформление виджетов", "Widget appearance")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tr("Светлый стиль", "Light style"))
            Switch(checked = light, onCheckedChange = { light = it; save() })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0xFFFF5555.toInt() to tr("Красный", "Red"), 0xFF0A84FF.toInt() to tr("Синий", "Blue"), 0xFF30D158.toInt() to tr("Зелёный", "Green")).forEach { (color,label) ->
                FilterChip(selected = accent == color, onClick = { accent = color; save() }, label = { Text(label) })
            }
        }
        Text(tr("Третья кнопка пульта", "Third control button"))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("bass" to "Bass", "transparency" to tr("Прозрачность", "Transparency"), "refresh" to tr("Обновить", "Refresh")).forEach { (key,label) ->
                FilterChip(selected = third == key, onClick = { third = key; save() }, label = { Text(label) })
            }
        }
        Text(tr("Настройки применяются ко всем виджетам. Размер меняется удержанием виджета на рабочем столе.", "Settings apply to all widgets. Long-press a home screen widget to resize it."), style = MaterialTheme.typography.bodySmall)
    }
}
