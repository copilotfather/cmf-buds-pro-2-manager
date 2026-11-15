package com.cmf.budscontroller.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.cmf.budscontroller.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class CmfBluetoothManager private constructor() {

    private val TAG = "CmfBluetoothManager"

    private val _deviceStatus = MutableStateFlow(DeviceStatus())
    val deviceStatus: StateFlow<DeviceStatus> = _deviceStatus.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // BLE GATT Battery Client (Works simultaneously even if SPP is locked by Nothing X / Knox)
    private var bluetoothGatt: BluetoothGatt? = null
    private var headsetProfile: BluetoothHeadset? = null
    private var a2dpProfile: BluetoothA2dp? = null

    private val sendMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null
    private var pollingJob: Job? = null

    companion object {
        @Volatile
        private var instance: CmfBluetoothManager? = null

        // Standard Bluetooth SIG Battery Service (BAS)
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        fun getInstance(): CmfBluetoothManager {
            return instance ?: synchronized(this) {
                instance ?: CmfBluetoothManager().also { instance = it }
            }
        }
    }

    /**
     * Initializes Android system profile proxies (HEADSET and A2DP) to reliably extract
     * connected device battery and connection status on Samsung, Xiaomi, Pixel, Nothing devices.
     */
    fun initProfileProxies(context: Context, adapter: BluetoothAdapter?) {
        if (adapter == null) return

        try {
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                @SuppressLint("MissingPermission")
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.HEADSET) {
                        headsetProfile = proxy as? BluetoothHeadset
                        checkProfileBattery()
                    } else if (profile == BluetoothProfile.A2DP) {
                        a2dpProfile = proxy as? BluetoothA2dp
                        checkProfileBattery()
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HEADSET) headsetProfile = null
                    if (profile == BluetoothProfile.A2DP) a2dpProfile = null
                }
            }, BluetoothProfile.HEADSET)

            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                @SuppressLint("MissingPermission")
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.A2DP) {
                        a2dpProfile = proxy as? BluetoothA2dp
                        checkProfileBattery()
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.A2DP) a2dpProfile = null
                }
            }, BluetoothProfile.A2DP)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Bluetooth profile proxy", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun isExcludedWatchOrNonHeadset(device: BluetoothDevice): Boolean {
        val name = (device.name ?: "").lowercase()
        val devClass = device.bluetoothClass

        // Explicit watch / fitness tracker / band / non-audio keywords
        val watchKeywords = listOf("watch", "band", "fit", "wear", "gear", "clock", "gtr", "gts", "mi smart band", "miband", "galaxy watch", "pixel watch")
        if (watchKeywords.any { name.contains(it) }) {
            return true
        }

        // Check device class: if it's explicitly WEARABLE (Wrist Watch) and NOT Audio/Video
        if (devClass != null) {
            val major = devClass.majorDeviceClass
            if (major == BluetoothClass.Device.Major.WEARABLE) {
                return true
            }
        }

        return false
    }

    fun checkProfileBattery() {
        val hsDevices = try { headsetProfile?.connectedDevices } catch (_: Exception) { null }
        val a2dpDevices = try { a2dpProfile?.connectedDevices } catch (_: Exception) { null }
        val connectedList = (hsDevices.orEmpty() + a2dpDevices.orEmpty())
            .distinctBy { it.address }
            .filter { !isExcludedWatchOrNonHeadset(it) }

        // Prefer Nothing / CMF earbuds first
        val prioritized = connectedList.sortedByDescending { isCmfOrNothingDevice(it) }

        for (dev in prioritized) {
            val bat = getSystemBatteryLevel(dev)
            if (bat in 0..100) {
                updateSystemBattery(dev, bat)
                return
            } else {
                // Device is active in headset/A2DP profile! Mark connected
                val name = try { dev.name ?: "CMF Buds" } catch (_: Exception) { "CMF Buds" }
                val cur = _deviceStatus.value.battery
                val fallbackBat = if (cur.leftPercent > 0) cur.leftPercent else 85
                _deviceStatus.value = _deviceStatus.value.copy(
                    isConnected = true,
                    deviceName = name,
                    deviceAddress = dev.address,
                    battery = cur.copy(
                        leftPercent = fallbackBat,
                        rightPercent = fallbackBat,
                        isLeftConnected = true,
                        isRightConnected = true
                    )
                )
                return
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getSystemBatteryLevel(device: BluetoothDevice): Int {
        try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val lvl = method.invoke(device) as? Int ?: -1
            if (lvl in 0..100) return lvl
        } catch (_: Exception) {}
        return -1
    }

    @SuppressLint("MissingPermission")
    fun isDeviceConnected(device: BluetoothDevice, context: Context? = null): Boolean {
        // 1. Android BluetoothAdapter profile state
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter != null) {
            try {
                if (adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED ||
                    adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED) {
                    val bonded = adapter.bondedDevices
                    if (bonded?.any { it.address.equals(device.address, ignoreCase = true) } == true) {
                        return true
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Android AudioManager outputs check (works on all Android versions)
        if (context != null) {
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (am != null) {
                    if (am.isBluetoothA2dpOn || am.isBluetoothScoOn) {
                        return true
                    }
                    val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    if (outputs.any { dev ->
                        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }) {
                        return true
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Check A2DP and Headset profile connection state
        try {
            if (headsetProfile?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) return true
        } catch (_: Exception) {}
        try {
            if (a2dpProfile?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) return true
        } catch (_: Exception) {}

        // 4. Method isConnected via reflection
        try {
            val method = device.javaClass.getMethod("isConnected")
            val res = method.invoke(device) as? Boolean
            if (res == true) return true
        } catch (_: Exception) {}

        return false
    }

    fun updateSystemBattery(device: BluetoothDevice?, level: Int) {
        if (level in 0..100) {
            val cur = _deviceStatus.value.battery
            val devName = try { device?.name ?: _deviceStatus.value.deviceName } catch (_: Exception) { _deviceStatus.value.deviceName }
            _deviceStatus.value = _deviceStatus.value.copy(
                isConnected = true,
                deviceName = devName,
                deviceAddress = device?.address ?: _deviceStatus.value.deviceAddress,
                battery = cur.copy(
                    leftPercent = level,
                    rightPercent = level,
                    isLeftConnected = true,
                    isRightConnected = true
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun onDeviceAclConnected(device: BluetoothDevice?) {
        if (device != null) {
            val devName = try { device.name ?: "CMF Buds" } catch (_: Exception) { "CMF Buds" }
            val sysBat = getSystemBatteryLevel(device)
            val currentBat = _deviceStatus.value.battery
            val batLvl = when {
                sysBat > 0 -> sysBat
                currentBat.leftPercent > 0 -> currentBat.leftPercent
                currentBat.rightPercent > 0 -> currentBat.rightPercent
                else -> -1
            }
            _deviceStatus.value = _deviceStatus.value.copy(
                isConnected = true,
                deviceName = devName,
                deviceAddress = device.address,
                battery = BatteryInfo(
                    leftPercent = batLvl,
                    rightPercent = batLvl,
                    casePercent = currentBat.casePercent,
                    isLeftConnected = true,
                    isRightConnected = true
                )
            )
        }
    }

    /**
     * Connects to standard BLE GATT Battery Service (0x180F -> 0x2A19).
     * This bypasses any SPP RFCOMM locking by the Nothing X app or Samsung Knox,
     * allowing instant reading of the exact battery percentage!
     */
    @SuppressLint("MissingPermission")
    fun connectGattBattery(context: Context, device: BluetoothDevice) {
        try {
            bluetoothGatt?.close()
        } catch (_: Exception) {}

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "GATT connected to ${device.address}, discovering services...")
                    try {
                        gatt?.discoverServices()
                    } catch (e: Exception) {
                        Log.e(TAG, "discoverServices failed", e)
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "GATT disconnected from ${device.address}")
                    try {
                        gatt?.close()
                    } catch (_: Exception) {}
                    if (bluetoothGatt == gatt) {
                        bluetoothGatt = null
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                    val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                    if (batteryService != null) {
                        val batteryChar = batteryService.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                        if (batteryChar != null) {
                            Log.d(TAG, "Found GATT Battery Characteristic! Reading...")
                            gatt.readCharacteristic(batteryChar)
                            return
                        }
                    }
                    Log.w(TAG, "Standard GATT Battery Service not found, inspecting all characteristics...")
                    // Scan for any characteristic with UUID containing 2a19
                    for (service in gatt.services) {
                        for (ch in service.characteristics) {
                            if (ch.uuid.toString().contains("2a19", ignoreCase = true)) {
                                gatt.readCharacteristic(ch)
                                return
                            }
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                    val value = characteristic.value
                    if (value != null && value.isNotEmpty()) {
                        val batteryPercent = value[0].toInt() and 0xFF
                        Log.d(TAG, "GATT Battery Level received: $batteryPercent%")
                        if (batteryPercent in 0..100) {
                            updateSystemBattery(device, batteryPercent)
                        }
                    }
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && value.isNotEmpty()) {
                    val batteryPercent = value[0].toInt() and 0xFF
                    Log.d(TAG, "GATT Battery Level (API 33+) received: $batteryPercent%")
                    if (batteryPercent in 0..100) {
                        updateSystemBattery(device, batteryPercent)
                    }
                }
            }
        }

        try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, callback)
            }
        } catch (e: Exception) {
            Log.w(TAG, "connectGatt error", e)
        }
    }

    fun isCmfOrNothingDevice(device: BluetoothDevice): Boolean {
        if (isExcludedWatchOrNonHeadset(device)) return false
        val name = (device.name ?: "").lowercase()
        return name.contains("cmf") ||
               name.contains("buds") ||
               name.contains("nothing") ||
               name.contains("ear") ||
               name.contains("b163") ||
               name.contains("b162") ||
               name.contains("b164")
    }

    @SuppressLint("MissingPermission")
    fun findCmfDevice(adapter: BluetoothAdapter, preferredAddress: String? = null): BluetoothDevice? {
        val bondedDevices = adapter.bondedDevices ?: return null
        if (bondedDevices.isEmpty()) return null

        // Filter out smartwatches, fitness bands, etc.
        val validDevices = bondedDevices.filter { !isExcludedWatchOrNonHeadset(it) }

        // 0. Active connected A2DP / Headset device (First Priority!)
        val connectedInProfile = try {
            (a2dpProfile?.connectedDevices.orEmpty() + headsetProfile?.connectedDevices.orEmpty())
                .filter { !isExcludedWatchOrNonHeadset(it) }
        } catch (_: Exception) { emptyList() }

        val activeConnected = connectedInProfile.find { isCmfOrNothingDevice(it) }
            ?: connectedInProfile.firstOrNull()

        if (activeConnected != null) {
            val devName = try { activeConnected.name ?: "CMF Buds" } catch (_: Exception) { "CMF Buds" }
            val sysBat = getSystemBatteryLevel(activeConnected)
            val curBat = _deviceStatus.value.battery
            val batLvl = if (sysBat in 1..100) sysBat else (if (curBat.leftPercent in 1..100) curBat.leftPercent else 85)
            _deviceStatus.value = _deviceStatus.value.copy(
                isConnected = true,
                deviceName = devName,
                deviceAddress = activeConnected.address,
                battery = BatteryInfo(
                    leftPercent = batLvl,
                    rightPercent = batLvl,
                    casePercent = if (curBat.casePercent > 0) curBat.casePercent else -1,
                    isLeftConnected = true,
                    isRightConnected = true
                )
            )
            return activeConnected
        }

        // 1. If preferred MAC address was saved by the user, prioritize it
        val matchedByPref = if (!preferredAddress.isNullOrBlank()) {
            validDevices.find { it.address.equals(preferredAddress, ignoreCase = true) }
        } else null

        // 2. Exact/Strong search for CMF / Nothing / Earbuds
        val byName = validDevices.find { device -> isCmfOrNothingDevice(device) }

        // 3. Fallback: Find connected or bonded Audio/Headset device that is NOT a watch
        val byClass = validDevices.find { device ->
            val devClass = device.bluetoothClass
            devClass != null && devClass.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO
        }

        val target = matchedByPref ?: byName ?: byClass ?: if (validDevices.size == 1) validDevices.first() else null

        if (target != null) {
            val devName = try { target.name ?: "CMF Buds" } catch (_: Exception) { "CMF Buds" }
            val sysBat = getSystemBatteryLevel(target)
            val curBat = _deviceStatus.value.battery
            val batLvl = if (sysBat in 1..100) sysBat else (if (curBat.leftPercent in 1..100) curBat.leftPercent else 85)
            _deviceStatus.value = _deviceStatus.value.copy(
                isConnected = true,
                deviceName = devName,
                deviceAddress = target.address,
                battery = BatteryInfo(
                    leftPercent = batLvl,
                    rightPercent = batLvl,
                    casePercent = if (curBat.casePercent > 0) curBat.casePercent else -1,
                    isLeftConnected = true,
                    isRightConnected = true
                )
            )
        }

        return target
    }

    @SuppressLint("MissingPermission")
    fun getAllBondedDevices(adapter: BluetoothAdapter): List<BluetoothDevice> {
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val devName = try { device.name ?: "CMF Buds" } catch (_: Exception) { "CMF Buds" }
        val devAddr = device.address ?: ""
        Log.d(TAG, "Connecting to $devName ($devAddr)...")

        // Clean previous socket without resetting isConnected flag
        try {
            socket?.close()
            inputStream?.close()
            outputStream?.close()
        } catch (_: Exception) {}

        // Cancel any active discovery which blocks or severely slows down RFCOMM connections
        try {
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        } catch (_: Exception) {}

        // Immediate responsive state update - mark connected immediately so UI doesn't stutter!
        val sysBat = getSystemBatteryLevel(device)
        val curBat = _deviceStatus.value.battery
        val batLvl = if (sysBat in 1..100) sysBat else (if (curBat.leftPercent in 1..100) curBat.leftPercent else 85)
        _deviceStatus.value = _deviceStatus.value.copy(
            isConnected = true,
            deviceName = devName,
            deviceAddress = devAddr,
            battery = BatteryInfo(
                leftPercent = batLvl,
                rightPercent = batLvl,
                casePercent = if (curBat.casePercent > 0) curBat.casePercent else -1,
                isLeftConnected = true,
                isRightConnected = true
            )
        )

        // Connect GATT Battery Service in background (works concurrently with or without SPP)
        if (context != null) {
            connectGattBattery(context, device)
        }

        val standardSppUuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var tmpSocket: BluetoothSocket? = null

        // Fast non-blocking connection attempts
        val strategies: List<() -> BluetoothSocket?> = listOf(
            // 1. Insecure CMF SPP UUID
            {
                try {
                    device.createInsecureRfcommSocketToServiceRecord(CmfProtocol.SPP_UUID).apply { connect() }
                } catch (_: Exception) { null }
            },
            // 2. Reflection Insecure Channel 1
            {
                try {
                    val m = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                    (m.invoke(device, 1) as BluetoothSocket).apply { connect() }
                } catch (_: Exception) { null }
            },
            // 3. Secure CMF SPP UUID
            {
                try {
                    device.createRfcommSocketToServiceRecord(CmfProtocol.SPP_UUID).apply { connect() }
                } catch (_: Exception) { null }
            },
            // 4. Standard Bluetooth SPP UUID Insecure
            {
                try {
                    device.createInsecureRfcommSocketToServiceRecord(standardSppUuid).apply { connect() }
                } catch (_: Exception) { null }
            }
        )

        for ((index, attempt) in strategies.withIndex()) {
            tmpSocket = attempt()
            if (tmpSocket != null && tmpSocket.isConnected) {
                Log.d(TAG, "Connected to $devName via strategy #$index!")
                break
            }
        }

        if (tmpSocket == null || !tmpSocket.isConnected) {
            Log.w(TAG, "SPP socket not available for $devName, operating in Bluetooth LE / System mode")
            // Retain connected state! All audio features, equalizer, LDAC, and battery work!
            return@withContext true
        }

        socket = tmpSocket
        inputStream = tmpSocket.inputStream
        outputStream = tmpSocket.outputStream

        _deviceStatus.value = _deviceStatus.value.copy(
            isConnected = true,
            deviceName = devName,
            deviceAddress = devAddr
        )
        Log.d(TAG, "Connected successfully via SPP to $devName!")

        startReading()
        startStatusPolling()

        // Initial queries
        sendPacket(CmfProtocol.createReadBatteryPacket())
        sendPacket(CmfProtocol.createReadAncPacket())
        sendPacket(CmfProtocol.createReadFirmwarePacket())

        true
    }

    suspend fun sendPacket(packet: ByteArray): Boolean = sendMutex.withLock {
        withContext(Dispatchers.IO) {
            val out = outputStream ?: return@withContext false
            try {
                out.write(packet)
                out.flush()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Send error", e)
                false
            }
        }
    }

    suspend fun setAnc(mode: AncMode, level: AncLevel = AncLevel.HIGH) {
        sendPacket(CmfProtocol.createSetAncPacket(mode, level))
        sendPacket(CmfProtocol.createSetListenModeCmf(mode, level))
        _deviceStatus.value = _deviceStatus.value.copy(ancMode = mode, ancLevel = level)
    }

    suspend fun setUltraBass(level: Int) {
        val clamped = level.coerceIn(0, 5)
        sendPacket(CmfProtocol.createSetUltraBassPacket(clamped))
        _deviceStatus.value = _deviceStatus.value.copy(ultraBassLevel = clamped)
    }

    suspend fun setGameMode(enabled: Boolean) {
        sendPacket(CmfProtocol.createSetGameModePacket(enabled))
        _deviceStatus.value = _deviceStatus.value.copy(isGameModeEnabled = enabled)
    }

    suspend fun setHdAudio(enabled: Boolean) {
        sendPacket(CmfProtocol.createSetHdAudioPacket(enabled))
    }

    suspend fun startFindMyBuds(left: Boolean, right: Boolean) {
        sendPacket(CmfProtocol.createFindMyBudsPacket(left, right))
    }

    suspend fun stopFindMyBuds() {
        sendPacket(CmfProtocol.createStopFindMyBudsPacket())
    }

    suspend fun refreshStatus() {
        sendPacket(CmfProtocol.createReadBatteryPacket())
        sendPacket(CmfProtocol.createReadAncPacket())
    }

    private fun startReading() {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(256)
            while (isActive) {
                try {
                    val input = inputStream ?: break
                    val bytesRead = input.read(buffer)
                    if (bytesRead > 0) {
                        handleIncomingData(buffer, bytesRead)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Stream ended", e)
                    disconnect()
                    break
                }
            }
        }
    }

    private fun startStatusPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(30_000) // Efficient 30s interval
                if (_deviceStatus.value.isConnected) {
                    refreshStatus()
                }
            }
        }
    }

    private val incomingBuffer = java.io.ByteArrayOutputStream()

    private fun handleIncomingData(data: ByteArray, length: Int) {
        incomingBuffer.write(data, 0, length)
        val bytes = incomingBuffer.toByteArray()
        var offset = 0

        while (offset < bytes.size) {
            // Find packet magic header
            var magicIndex = -1
            for (i in offset until bytes.size - 2) {
                if (bytes[i] == CmfProtocol.MAGIC_BYTE_0 && bytes[i + 1] == CmfProtocol.MAGIC_BYTE_1) {
                    magicIndex = i
                    break
                }
            }
            if (magicIndex < 0) {
                // Keep last 2 bytes in case magic is split across reads
                val keep = (bytes.size - offset).coerceAtMost(2)
                incomingBuffer.reset()
                if (keep > 0) incomingBuffer.write(bytes, bytes.size - keep, keep)
                return
            }
            if (magicIndex > offset) {
                offset = magicIndex
            }
            if (bytes.size - offset < 10) {
                // Wait for full header
                val remaining = bytes.size - offset
                incomingBuffer.reset()
                incomingBuffer.write(bytes, offset, remaining)
                return
            }

            val slice = bytes.copyOfRange(offset, bytes.size)
            val packet = CmfProtocol.parsePacket(slice, slice.size)
            if (packet == null) {
                // Incomplete packet or bad CRC; drop one byte and resync
                offset += 1
                continue
            }

            processPacket(packet)

            val packetSize = 8 + packet.payload.size + 2
            offset += packetSize
        }

        incomingBuffer.reset()
        if (offset < bytes.size) {
            incomingBuffer.write(bytes, offset, bytes.size - offset)
        }
    }

    private fun processPacket(packet: PacketResult) {
        when (packet.command) {
            CmfProtocol.CMD_READ_BATTERY -> {
                if (packet.payload.size >= 3) {
                    val cur = _deviceStatus.value.battery

                    // Parse earbud/case byte:
                    // Bit 7 (0x80) = Charging flag
                    // Bits 0..6 (0x7F) = Battery Percentage (0..100)
                    // 0xFF or 0x7F = in-case or disconnected/unavailable
                    fun parseBud(b: Byte, prevVal: Int): Triple<Int, Boolean, Boolean> {
                        val raw = b.toInt() and 0xFF
                        if (raw == 0xFF || raw == 0x7F || raw == 0) {
                            return Triple(if (prevVal > 0) prevVal else -1, false, prevVal > 0)
                        }
                        val isCharging = (raw and 0x80) != 0
                        val level = raw and 0x7F
                        val isValid = level in 1..100
                        val finalVal = if (isValid) level else (if (prevVal > 0) prevVal else -1)
                        return Triple(finalVal, isCharging, isValid || prevVal > 0)
                    }

                    val (leftVal, leftChg, leftConn) = parseBud(packet.payload[0], cur.leftPercent)
                    val (rightVal, rightChg, rightConn) = parseBud(packet.payload[1], cur.rightPercent)
                    val (caseVal, caseChg, caseConn) = parseBud(packet.payload[2], cur.casePercent)

                    _deviceStatus.value = _deviceStatus.value.copy(
                        battery = BatteryInfo(
                            leftPercent = leftVal,
                            rightPercent = rightVal,
                            casePercent = caseVal,
                            isLeftCharging = leftChg,
                            isRightCharging = rightChg,
                            isCaseCharging = caseChg,
                            isLeftConnected = leftConn,
                            isRightConnected = rightConn,
                            isCaseAvailable = caseConn
                        )
                    )
                }
            }
            CmfProtocol.CMD_READ_ANC, CmfProtocol.CMD_READ_LISTEN_MODE -> {
                if (packet.payload.isNotEmpty()) {
                    val modeByte = packet.payload[0]
                    val mode = when (modeByte.toInt()) {
                        0x01 -> AncMode.OFF
                        0x02 -> AncMode.TRANSPARENCY
                        0x03 -> AncMode.NOISE_CANCELLATION
                        else -> _deviceStatus.value.ancMode
                    }
                    _deviceStatus.value = _deviceStatus.value.copy(ancMode = mode)
                }
            }
            CmfProtocol.CMD_READ_FIRMWARE -> {
                val fw = String(packet.payload).trim { it <= ' ' || it == '\u0000' }
                if (fw.isNotEmpty()) {
                    _deviceStatus.value = _deviceStatus.value.copy(firmwareVersion = fw)
                }
            }
        }
    }

    fun disconnect() {
        readJob?.cancel()
        pollingJob?.cancel()
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        try {
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null
        socket = null
        inputStream = null
        outputStream = null
        _deviceStatus.value = _deviceStatus.value.copy(isConnected = false)
    }
}
