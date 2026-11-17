package com.cmf.budscontroller.watch

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import com.cmf.budscontroller.i18n.AppStrings
import java.util.UUID

@SuppressLint("MissingPermission")
class WatchControlChannel(private val context: Context, private val handler: Handler,
    private val state: () -> WatchBetaState, private val update: (WatchBetaState) -> Unit,
    private val fail: (String) -> Unit) {
    val health = WatchHealthStore(context)
    private var client: BluetoothGatt? = null
    private var commandWrite: BluetoothGattCharacteristic? = null
    private var shellWrite: BluetoothGattCharacteristic? = null
    private val subscriptions = ArrayDeque<BluetoothGattCharacteristic>()
    private val writes = ArrayDeque<Pair<BluetoothGattCharacteristic,ByteArray>>()
    private var writing = false
    private var shellInput = ""
    private var secretRequested = false
    private val timeout = Runnable { fail("Authentication timed out") }
    private val writeTimeout = Runnable { fail("GATT write timed out") }
    private val syncTimeout = Runnable {
        val saved = health.flush()
        val message = when {
            !saved && AppStrings.isRussian -> "Тайм-аут синхронизации; локально сохранить данные не удалось"
            !saved -> "Sync timed out; local data could not be saved"
            AppStrings.isRussian -> "Тайм-аут синхронизации; частичные данные сохранены"
            else -> "Sync timed out; partial data saved"
        }
        update(state().copy(syncing = false, message = message))
    }
    private val session: WatchSession = WatchSession(
        send = { enqueue(commandWrite ?: error("Missing command channel"),it) },
        onReady = {
            handler.removeCallbacks(timeout)
            update(state().copy(authenticating = false, authenticated = true, message = "Authenticated"))
            WatchNotificationBridge.send = { app,title,body -> handler.post { forward(app,title,body) } }
        },
        onData = { a,b,payload ->
            if (a == 0xffff && b == 5 && payload.isNotEmpty()) {
                if (payload[0] == 1.toByte() && state().syncing) fetchSecond()
                if (payload[0] == 2.toByte() && state().syncing) {
                    handler.removeCallbacks(syncTimeout)
                    val saved = health.flush(true)
                    val message = when {
                        saved && AppStrings.isRussian -> "Синхронизация здоровья завершена"
                        saved -> "Health sync complete"
                        AppStrings.isRussian -> "Не удалось безопасно сохранить данные здоровья"
                        else -> "Health data could not be saved securely"
                    }
                    update(state().copy(syncing = false, message = message))
                }
            } else if (b == 1 && state().syncing) health.accept(a,payload)
            if (a == 0x65 && b == 3) update(state().copy(notificationAcknowledged = true))
        }
    )
    private fun fetchSecond(): Unit = session.command(0xffff,0x9057)
    fun reset() {
        WatchNotificationBridge.send = null
        handler.removeCallbacks(timeout); handler.removeCallbacks(writeTimeout); handler.removeCallbacks(syncTimeout)
        session.reset(); health.flush(); writes.clear(); subscriptions.clear()
        client = null; commandWrite = null; shellWrite = null; writing = false; shellInput = ""; secretRequested = false
    }
    fun authenticate(gatt: BluetoothGatt) {
        if (state().authenticating || state().authenticated) return
        try {
            client = gatt
            fun find(id: String) = gatt.services.flatMap { it.characteristics }.firstOrNull { it.uuid == UUID.fromString(id) }
            commandWrite = find("0000fff2-0000-1000-8000-00805f9b34fb")
            shellWrite = find("77d4ff01-2fe2-2334-0d35-9ccd078f529c")
            val commandRead = find("0000fff1-0000-1000-8000-00805f9b34fb")
            val shellRead = find("77d4ff02-2fe2-2334-0d35-9ccd078f529c")
            require(commandWrite != null && shellWrite != null && commandRead != null && shellRead != null) { "Required watch services unavailable" }
            update(state().copy(authenticating = true, message = "Confirm pairing on watch"))
            subscriptions.add(commandRead); subscriptions.add(shellRead)
            handler.postDelayed(timeout,60000)
            subscribeNext()
        } catch(e: Exception) { fail(e.message ?: "Authentication failed") }
    }
    private fun subscribeNext() {
        val gatt = client ?: return
        if (subscriptions.isEmpty()) {
            if (!secretRequested) {
                secretRequested = true
                enqueue(shellWrite ?: error("Missing shell channel"), "AT GETSECRET\r\n".toByteArray(Charsets.US_ASCII))
            }
            return
        }
        val characteristic = subscriptions.removeFirst()
        require(gatt.setCharacteristicNotification(characteristic,true))
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) ?: error("Missing CCCD")
        val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        if (Build.VERSION.SDK_INT >= 33) require(gatt.writeDescriptor(descriptor,value) == BluetoothStatusCodes.SUCCESS)
        else { descriptor.value = value; require(gatt.writeDescriptor(descriptor)) }
    }
    fun descriptorWritten(status: Int) {
        try { require(status == BluetoothGatt.GATT_SUCCESS); subscribeNext() } catch(e: Exception) { fail(e.message ?: "Subscription failed") }
    }
    private fun enqueue(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        require(writes.size < 256) { "Write queue full" }
        writes.add(characteristic to value); writeNext()
    }
    private fun writeNext() {
        if (writing || writes.isEmpty()) return
        val gatt = client ?: return
        val (characteristic,value) = writes.removeFirst()
        writing = true
        val type = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val success = if(Build.VERSION.SDK_INT >= 33) gatt.writeCharacteristic(characteristic,value,type) == BluetoothStatusCodes.SUCCESS
            else { characteristic.writeType = type; characteristic.value = value; gatt.writeCharacteristic(characteristic) }
        require(success) { "GATT write rejected" }
        handler.postDelayed(writeTimeout,8000)
    }
    fun written(status: Int) {
        handler.removeCallbacks(writeTimeout)
        try { require(status == BluetoothGatt.GATT_SUCCESS); writing = false; writeNext() } catch(e: Exception) { fail(e.message ?: "Write failed") }
    }
    fun receive(id: UUID, value: ByteArray) {
        try {
            if (id.toString() == "77d4ff02-2fe2-2334-0d35-9ccd078f529c" && state().authenticating) {
                shellInput += value.toString(Charsets.US_ASCII).filter { it == '\r' || it == '\n' || it in ' '..'~' }
                require(shellInput.length <= 1024)
                val match = Regex("GETSECRET:([0-9a-fA-F]{32}),OK(?:\\r?\\n|$)").find(shellInput)
                if (match != null) {
                    val secret = match.groupValues[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    shellInput = ""; session.pair(secret); secret.fill(0)
                }
            } else if(id.toString() == "0000fff1-0000-1000-8000-00805f9b34fb") session.receive(value)
        } catch(e: Exception) { fail(e.message ?: "Protocol error") }
    }
    fun sync() {
        if (!state().authenticated || state().syncing) return
        try {
            update(state().copy(syncing = true, message = "Syncing health data"))
            handler.postDelayed(syncTimeout,240000)
            session.command(0xffff,0x8005)
        } catch(e: Exception) { fail(e.message ?: "Sync failed") }
    }
    private var lastNotification = 0L
    private fun forward(app: String,title: String,body: String) {
        val prefs = context.getSharedPreferences("watch_notifications",Context.MODE_PRIVATE)
        if(!state().authenticated || !prefs.getBoolean("enabled",false) || app !in prefs.getStringSet("packages",emptySet()).orEmpty()) return
        val now = android.os.SystemClock.elapsedRealtime()
        if(now-lastNotification < 1000 || writes.size > 160) return
        lastNotification = now
        try {
            val icon = when(app) { "com.whatsapp" -> 8; "org.telegram.messenger" -> 12; "com.instagram.android" -> 18; "com.google.android.gm" -> 27; else -> 255 }
            session.notification(icon,title,body)
            update(state().copy(notificationsSent = state().notificationsSent+1,notificationAcknowledged = false))
        } catch(e: Exception) { fail(e.message ?: "Notification failed") }
    }
}
