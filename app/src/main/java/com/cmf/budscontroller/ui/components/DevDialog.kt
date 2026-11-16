package com.cmf.budscontroller.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.ui.MainViewModel

@Composable
fun DevDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val unlocked by vm.devUnlocked.collectAsState()
    val watch by vm.watchBeta.state.collectAsState()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var permissionError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun text(ru: String, en: String) = if (AppStrings.isRussian) ru else en
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionError = !vm.watchBeta.hasPermissions()
        if (!permissionError && unlocked && watch.enabled) vm.watchBeta.scan()
    }
    fun scan() {
        if (vm.watchBeta.hasPermissions()) vm.watchBeta.scan()
        else permissionLauncher.launch(if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }
    AlertDialog(
        onDismissRequest = { vm.watchBeta.stopScan(); onDismiss() },
        title = { Text("DEV · CMF Watch Pro 2 β") },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!unlocked) {
                    Text(text("Введите пароль разработчика", "Enter developer password"))
                    OutlinedTextField(value = password, onValueChange = { password = it.take(128); error = false },
                        label = { Text(text("Пароль", "Password")) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), isError = error,
                        modifier = Modifier.fillMaxWidth())
                    if (error) Text(text("Неверный пароль. После пяти ошибок подождите 30 секунд.", "Incorrect password. After five attempts wait 30 seconds."), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { error = !vm.unlockDev(password); password = "" }) { Text(text("Войти", "Unlock")) }
                } else {
                    Text(text("Бета: BLE, авторизация, здоровье и уведомления. Совместимость с прошивкой требует проверки на часах.", "Beta: BLE, authentication, health and notifications. Firmware compatibility requires testing on the watch."))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text("Включить бета-поддержку", "Enable beta support"), modifier = Modifier.weight(1f))
                        Switch(checked = watch.enabled, onCheckedChange = { vm.watchBeta.enable(it) })
                    }
                    if (watch.enabled) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text("Переподключать при потере связи", "Reconnect after connection loss"), modifier = Modifier.weight(1f))
                            Switch(checked = watch.autoReconnect, onCheckedChange = { vm.watchBeta.setAutoReconnect(it) })
                        }
                        Text(text("Закройте другое приложение часов, если оно удерживает BLE-соединение. На Android 11 и ниже для поиска включите геолокацию телефона.", "Close other watch apps if they hold the BLE connection. Android 11 and earlier require phone location enabled for discovery."), style = MaterialTheme.typography.bodySmall)
                        if (permissionError) Text(text("Разрешите Bluetooth/геолокацию в настройках приложения.", "Allow Bluetooth/location in app settings."), color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { if (watch.scanning) vm.watchBeta.stopScan() else scan() }, enabled = !watch.connecting && !watch.connected) {
                                Text(if (watch.scanning) text("Стоп", "Stop") else text("Поиск", "Scan"))
                            }
                            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Bluetooth") }
                        }
                        if (watch.scanning || watch.connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
                        watch.devices.forEach { device ->
                            OutlinedButton(onClick = { vm.watchBeta.connect(device.address) }, enabled = !watch.connecting && !watch.connected, modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Text(device.name)
                                    Text("${device.address}${device.rssi?.let { " · $it dBm" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (!watch.scanning && watch.devices.isEmpty()) Text(text("Нажмите «Поиск», включив часы рядом с телефоном.", "Tap Scan with your watch nearby and powered on."))
                        Text(when {
                            watch.connecting -> text("Подключение и чтение сервисов…", "Connecting and discovering services…")
                            watch.authenticated -> text("Часы авторизованы", "Watch authenticated")
                            watch.connected -> text("BLE подключено · нужна авторизация", "BLE connected · authentication required")
                            else -> text("Часы отключены", "Watch disconnected")
                        })
                        if (watch.message.isNotBlank()) Text(watch.message, style = MaterialTheme.typography.bodySmall)
                        if (watch.connected) {
                            Text(text("Заряд", "Battery") + ": ${watch.battery?.let { "$it%" } ?: text("Недоступен", "Unavailable")}")
                            Text(text("Модель", "Model") + ": ${watch.model.ifBlank { "—" }}")
                            Text(text("Прошивка", "Firmware") + ": ${watch.firmware.ifBlank { "—" }}")
                            Text(text("GATT-сервисы", "GATT services") + " (${watch.services.size})", style = MaterialTheme.typography.labelLarge)
                            watch.services.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (watch.connected || watch.connecting) OutlinedButton(onClick = { vm.watchBeta.disconnect() }) { Text(text("Отключить", "Disconnect")) }
                        WatchHealthNotificationsPanel(vm.watchBeta)
                    }
                    TextButton(onClick = { vm.lockDev() }) { Text(text("Заблокировать DEV", "Lock DEV")) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.watchBeta.stopScan(); onDismiss() }) { Text(AppStrings.btnClose) } }
    )
}
