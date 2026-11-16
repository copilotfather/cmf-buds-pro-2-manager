package com.cmf.budscontroller.watch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

 data class WatchCandidate(val address: String, val name: String, val rssi: Int? = null)
 data class WatchBetaState(
    val enabled: Boolean = false,
    val scanning: Boolean = false,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val devices: List<WatchCandidate> = emptyList(),
    val address: String = "",
    val battery: Int? = null,
    val firmware: String = "",
    val model: String = "",
    val services: List<String> = emptyList(),
    val message: String = "",
    val autoReconnect: Boolean = true,
    val authenticating: Boolean = false,
    val authenticated: Boolean = false,
    val syncing: Boolean = false,
    val notificationsSent: Int = 0,
    val notificationAcknowledged: Boolean = false
 )

/** Beta transport with explicit user-initiated authorization. */
@SuppressLint("MissingPermission")
class WatchBetaManager(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(WatchBetaState())
    val state = _state.asStateFlow()
    private val adapter get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    val control = WatchControlChannel(context, handler, { _state.value }, { _state.value = it }, { reason ->
        disconnect(); message("Ошибка протокола: $reason", "Protocol error: $reason")
    })
    private var reading = false
    fun authenticate() {
        if (reading) { message("Дождитесь чтения стандартных данных", "Wait for standard reads to finish"); return }
        gatt?.takeIf { _state.value.connected }?.let { control.authenticate(it) }
    }
    private val reads = ArrayDeque<BluetoothGattCharacteristic>()
    private var desiredAddress: String? = null
    private var reconnectAttempt = 0
    private val reconnectTask = Runnable {
        val address = desiredAddress
        if (_state.value.enabled && _state.value.autoReconnect && address != null && hasPermissions()) connect(address)
    }
    fun setAutoReconnect(enabled: Boolean) {
        _state.value = _state.value.copy(autoReconnect = enabled)
        if (!enabled) handler.removeCallbacks(reconnectTask)
    }
    private fun scheduleReconnect(address: String) {
        if (!_state.value.enabled || !_state.value.autoReconnect || desiredAddress != address || reconnectAttempt >= 5) return
        val delay = (3000L shl reconnectAttempt.coerceAtMost(4)).coerceAtMost(60_000L)
        reconnectAttempt++
        handler.removeCallbacks(reconnectTask)
        handler.postDelayed(reconnectTask, delay)
    }
    private val scanTimeout = Runnable { stopScan(); message("Поиск завершён", "Scan finished") }
    private val connectionTimeout = Runnable {
        disconnect()
        message("Тайм-аут подключения или чтения. Закройте другое приложение часов и повторите.", "Connection/read timed out. Close other watch apps and retry.")
    }
    private fun message(ru: String, en: String) {
        _state.value = _state.value.copy(message = if (com.cmf.budscontroller.i18n.AppStrings.isRussian) ru else en)
    }
    fun hasPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        return permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }
    private fun isWatch(name: String?): Boolean = name?.trim()?.matches(
        Regex("CMF[\\s_-]*Watch[\\s_-]*Pro[\\s_-]*2(?:[\\s_-]+[0-9a-f]{4})?", RegexOption.IGNORE_CASE)) == true

    fun enable(enabled: Boolean) {
        if (!enabled) { stopScan(); disconnect() }
        _state.value = _state.value.copy(enabled = enabled)
    }
    fun scan() {
        if (!_state.value.enabled) return
        if (!hasPermissions()) { message("Нужны разрешения Bluetooth", "Bluetooth permissions required"); return }
        stopScan()
        try {
            val bt = adapter
            if (bt == null || !bt.isEnabled) { message("Включите Bluetooth", "Enable Bluetooth first"); return }
            val paired = bt.bondedDevices.filter { isWatch(it.name) }.map { WatchCandidate(it.address, it.name ?: "CMF Watch Pro 2") }
            _state.value = _state.value.copy(devices = paired, scanning = true, message = "")
            scanner = bt.bluetoothLeScanner
            if (scanner == null) { _state.value = _state.value.copy(scanning = false); message("BLE недоступен", "BLE unavailable"); return }
            scanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
            handler.postDelayed(scanTimeout, 15_000)
        } catch (e: Exception) { stopScan(); message("Ошибка поиска: ${e.message}", "Scan error: ${e.message}") }
    }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handler.post {
                if (!_state.value.scanning) return@post
                try {
                    val name = result.scanRecord?.deviceName ?: result.device.name
                    if (!isWatch(name)) return@post
                    val found = WatchCandidate(result.device.address, name!!, result.rssi)
                    _state.value = _state.value.copy(devices = (_state.value.devices.filterNot { it.address == found.address } + found).sortedByDescending { it.rssi ?: -150 })
                } catch (_: SecurityException) { stopScan(); message("Разрешение Bluetooth отозвано", "Bluetooth permission revoked") }
            }
        }
        override fun onScanFailed(errorCode: Int) { handler.post { stopScan(); message("Ошибка BLE: $errorCode", "BLE scan failed: $errorCode") } }
    }
    fun stopScan() {
        handler.removeCallbacks(scanTimeout)
        try { scanner?.stopScan(scanCallback) } catch (_: SecurityException) { }
        scanner = null
        _state.value = _state.value.copy(scanning = false)
    }
    fun connect(address: String) {
        if (!_state.value.enabled || !hasPermissions()) return
        val candidate = _state.value.devices.find { it.address == address } ?: return
        if (!isWatch(candidate.name)) return
        stopScan()
        disconnect()
        desiredAddress = address
        control.health.select(address)
        _state.value = _state.value.copy(connecting = true, address = address, message = "")
        try {
            val device = adapter?.getRemoteDevice(address) ?: error("Bluetooth unavailable")
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            handler.postDelayed(connectionTimeout, 20_000)
        } catch (e: Exception) { disconnect(); message("Ошибка подключения: ${e.message}", "Connection failed: ${e.message}") }
    }
    fun disconnect() {
        control.reset()
        reading = false
        desiredAddress = null
        handler.removeCallbacks(reconnectTask)
        handler.removeCallbacks(connectionTimeout)
        val old = gatt
        gatt = null
        reads.clear()
        try { old?.disconnect() } catch (_: SecurityException) { }
        try { old?.close() } catch (_: SecurityException) { }
        _state.value = _state.value.copy(connecting = false, connected = false, authenticated = false, authenticating = false, syncing = false, battery = null, firmware = "", model = "", services = emptyList())
    }
    private fun nextRead(client: BluetoothGatt) {
        if (client !== gatt) return
        while (reads.isNotEmpty()) {
            val characteristic = reads.removeFirst()
            if (client.readCharacteristic(characteristic)) return
        }
        handler.removeCallbacks(connectionTimeout)
        reading = false
        message("GATT подключён. Можно выполнить авторизацию.", "GATT connected. Ready to authenticate.")
    }
    private val callback = object : BluetoothGattCallback() {
        override fun onDescriptorWrite(client: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            handler.post { if (client === gatt) control.descriptorWritten(status) }
        }
        override fun onCharacteristicWrite(client: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            handler.post { if (client === gatt) control.written(status) }
        }
        @Deprecated("Compatibility callback")
        override fun onCharacteristicChanged(client: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) changed(client,characteristic.uuid,characteristic.value?.clone() ?: byteArrayOf())
        }
        override fun onCharacteristicChanged(client: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) = changed(client,characteristic.uuid,value.clone())
        override fun onConnectionStateChange(client: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                if (client !== gatt) { client.close(); return@post }
                try {
                    if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                        val address = desiredAddress
                        disconnect(); message("Часы отключены (GATT $status)", "Watch disconnected (GATT $status)")
                        if (address != null) { desiredAddress = address; scheduleReconnect(address) }
                    } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                        if (!client.discoverServices()) { disconnect(); message("Не удалось прочитать сервисы", "Service discovery failed") }
                    }
                } catch (e: Exception) { disconnect(); message("Ошибка BLE: ${e.message}", "BLE error: ${e.message}") }
            }
        }
        override fun onServicesDiscovered(client: BluetoothGatt, status: Int) {
            handler.post {
                if (client !== gatt) return@post
                try {
                    if (status != BluetoothGatt.GATT_SUCCESS) { disconnect(); message("Ошибка сервисов: $status", "Service discovery error: $status"); return@post }
                    _state.value = _state.value.copy(connecting = false, connected = true, services = client.services.map { it.uuid.toString() })
                    reconnectAttempt = 0
                    reading = true
                    listOf("180f" to "2a19", "180a" to "2a26", "180a" to "2a24").forEach { (service, characteristic) ->
                        client.getService(uuid(service))?.getCharacteristic(uuid(characteristic))?.takeIf { it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 }?.let { reads.add(it) }
                    }
                    nextRead(client)
                } catch (e: Exception) { disconnect(); message("Ошибка чтения: ${e.message}", "Read error: ${e.message}") }
            }
        }
        @Deprecated("Compatibility callback for Android 12 and earlier")
        override fun onCharacteristicRead(client: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33) receive(client, characteristic.uuid, characteristic.value?.clone() ?: byteArrayOf(), status)
        }
        override fun onCharacteristicRead(client: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) = receive(client, characteristic.uuid, value.clone(), status)
    }
    private fun receive(client: BluetoothGatt, id: UUID, bytes: ByteArray, status: Int) {
        handler.post {
            if (client !== gatt) return@post
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = bytes.toString(Charsets.UTF_8).trimEnd('\u0000').take(120)
                _state.value = when (id) {
                    uuid("2a19") -> _state.value.copy(battery = bytes.firstOrNull()?.toInt()?.and(255)?.takeIf { it in 0..100 })
                    uuid("2a26") -> _state.value.copy(firmware = value)
                    uuid("2a24") -> _state.value.copy(model = value)
                    else -> _state.value
                }
            }
            try { nextRead(client) } catch (e: Exception) { disconnect(); message("Ошибка BLE: ${e.message}", "BLE error: ${e.message}") }
        }
    }
    fun release() { stopScan(); disconnect() }
    private fun changed(client: BluetoothGatt, id: UUID, value: ByteArray) {
        handler.post { if (client === gatt) control.receive(id,value) }
    }
    private fun uuid(short: String) = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")
}
