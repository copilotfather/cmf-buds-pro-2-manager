package com.cmf.budscontroller.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.watch.WatchBetaManager

@Composable
fun WatchHealthNotificationsPanel(manager: WatchBetaManager) {
    val state by manager.state.collectAsState()
    val health by manager.control.health.summary.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("watch_notifications",android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("enabled",false)) }
    var selected by remember { mutableStateOf(prefs.getStringSet("packages",emptySet()).orEmpty().toSet()) }
    var appPackage by remember { mutableStateOf("") }
    var confirmPair by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    fun t(ru: String,en: String) = if (AppStrings.isRussian) ru else en
    HorizontalDivider()
    if (state.connected && !state.authenticated) {
        Text(t("Авторизация нужна для здоровья и уведомлений. Сопряжение может заменить связь с другим приложением часов.", "Authentication is required for health and notifications. Pairing may replace another companion app's binding."))
        Button(onClick = { confirmPair = true }, enabled = !state.authenticating) { Text(t("Авторизация", "Authenticate")) }
    }
    if (state.authenticating) LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(t("Здоровье · сегодня", "Health · today"), style = MaterialTheme.typography.titleMedium)
    Text(t("Шаги", "Steps") + ": ${health.steps} · ${health.distanceMeters} m · ${health.kcal} kcal")
    Text(t("Пульс", "Heart rate") + ": ${health.heartRate ?: "—"} bpm · SpO₂: ${health.spo2 ?: "—"}%")
    Text(t("Стресс", "Stress") + ": ${health.stress ?: "—"} · " + t("Сон", "Sleep") + ": ${health.sleepMinutes} min")
    Text(t("Сохранено записей", "Stored records") + ": ${health.records}")
    if (health.lastSync > 0) Text(t("Синхронизация", "Last sync") + ": " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(health.lastSync)), style = MaterialTheme.typography.bodySmall)
    Text(t("Показатели с часов, не медицинская диагностика. Хранятся локально до 31 дня; тренировки пока не разбираются.", "Watch readings, not medical diagnostics. Stored locally for up to 31 days; workout summaries are not parsed yet."), style = MaterialTheme.typography.bodySmall)
    Button(onClick = { manager.control.sync() }, enabled = state.authenticated && !state.syncing) { Text(if (state.syncing) t("Синхронизация…", "Syncing…") else t("Синхронизировать", "Sync health")) }
    OutlinedButton(onClick = { confirmClear = true }) { Text(t("Удалить локальные данные", "Delete local health data")) }
    Text(t("Уведомления", "Notifications"), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(t("Передавать выбранные приложения", "Forward selected apps"), modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = { enabled = it; prefs.edit().putBoolean("enabled",it).apply() })
    }
    Text(t("Отправляются только новые уведомления при разблокированном телефоне и авторизованных часах. Пропущенные сообщения не сохраняются в очередь.", "Only new notifications are forwarded while the phone is unlocked and watch authenticated. Missed messages are not queued."), style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text(t("Доступ к уведомлениям", "Notification access")) }
    val apps = listOf("com.whatsapp" to "WhatsApp", "org.telegram.messenger" to "Telegram", "com.google.android.gm" to "Gmail", "com.instagram.android" to "Instagram") + selected.filter { it !in setOf("com.whatsapp","org.telegram.messenger","com.google.android.gm","com.instagram.android") }.map { it to it }
    apps.forEach { (pkg,label) ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, modifier = Modifier.weight(1f))
            Checkbox(checked = pkg in selected, onCheckedChange = {
                selected = if (it) selected+pkg else selected-pkg
                prefs.edit().putStringSet("packages",selected).apply()
            })
        }
    }
    OutlinedTextField(value = appPackage, onValueChange = { appPackage = it.take(200) }, label = { Text(t("Пакет другого приложения", "Other app package")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = {
        val pkg = appPackage.trim()
        if (pkg.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)+"))) {
            selected = selected+pkg; prefs.edit().putStringSet("packages",selected).apply(); appPackage = ""
        }
    }) { Text(t("Добавить", "Add app")) }
    Text(t("Отправлено в BLE", "Queued to BLE") + ": ${state.notificationsSent} · ACK: ${if (state.notificationAcknowledged) "✓" else "—"}", style = MaterialTheme.typography.bodySmall)
    if (confirmPair) AlertDialog(onDismissRequest = { confirmPair = false }, title = { Text(t("Сопряжение часов", "Pair watch")) }, text = { Text(t("Закройте другие приложения часов. Подтвердите запрос на часах; после переподключения авторизация потребуется снова.", "Close other watch apps. Confirm the watch prompt; authenticate again after reconnecting.")) }, confirmButton = { TextButton(onClick = { confirmPair = false; manager.authenticate() }) { Text(t("Начать", "Start")) } }, dismissButton = { TextButton(onClick = { confirmPair = false }) { Text(AppStrings.btnCancel) } })
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(t("Удалить историю здоровья?", "Delete health history?")) }, confirmButton = { TextButton(onClick = { manager.control.health.clear(); confirmClear = false }) { Text(t("Удалить", "Delete")) } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(AppStrings.btnCancel) } })
}
