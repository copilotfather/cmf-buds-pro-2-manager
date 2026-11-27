package com.cmf.budscontroller.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.cmf.budscontroller.i18n.AppStrings
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
    private val _commandStatus = MutableStateFlow("")
    val commandStatus: StateFlow<String> = _commandStatus.asStateFlow()
    private var awaitingAnc: AncMode? = null
    private var ancCheckJob: Job? = null
    private var awaitingEarbudPreset: Int? = null
    private var awaitingCustomEq: Triple<Int, Int, Int>? = null
    private var awaitingGameMode: Boolean? = null
    private var awaitingInEarDetection: Boolean? = null
    private var awaitingSpatialAudio: Pair<Int, Boolean>? = null
    private var awaitingGesture: Triple<Int, Int, Int>? = null
    private var awaitingDualConnectionEnabled: Boolean? = null
    private var awaitingDualDevice: Pair<String, Boolean>? = null
    private var dualListPageRequests = 0

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var currentDevice: BluetoothDevice? = null

    // Dual-Engine: BLE GATT Client & Characteristics
    private var bluetoothGatt: BluetoothGatt? = null
    private var gattWriteChar: BluetoothGattCharacteristic? = null
    private var gattNotifyChar: BluetoothGattCharacteristic? = null

    // Android System Profiles
    private var headsetProfile: BluetoothHeadset? = null
    private var a2dpProfile: BluetoothA2dp? = null

    private val sendMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null
    private var pollingJob: Job? = null
    private var watchdogJob: Job? = null

    private var appContext: Context? = null
    private var preferredAddress: String? = null
    private var lastKnownCaseBattery: Int = -1

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

    fun setPreferredAddress(address: String?) {
        preferredAddress = address
    }

    /**
     * Initializes Android system profile proxies (HEADSET and A2DP) to reliably extract
     * connected device battery and connection status on Samsung, Xiaomi, Pixel, Nothing devices.
     */
    fun initProfileProxies(context: Context, adapter: BluetoothAdapter?) {
        appContext = context.applicationContext
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

    /**
     * Continuous background watchdog:
     * - Auto-reconnects when user takes earbuds out of case
     * - Restores dropped SPP RFCOMM streams when device is still active in system A2DP
     * - Sends periodic keep-alive pings to keep earbud firmware channel awake
     */
    fun startWatchdog(context: Context) {
        appContext = context.applicationContext
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            var pingCounter = 0
            while (isActive) {
                delay(7_000) // Efficient 7-second heartbeat
                try {
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                    if (adapter == null || !adapter.isEnabled) continue

                    val current = currentDevice
                    if (current != null) {
                        val isAudioActive = isDeviceConnected(current, appContext)
                        if (isAudioActive) {
                            if (socket == null || socket?.isConnected != true) {
                                Log.d(TAG, "Watchdog: Earbuds active in audio, restoring SPP connection...")
                                tryReconnectSpp(current)
                            } else {
                                pingCounter++
                                if (pingCounter >= 3) { // Every ~21s
                                    pingCounter = 0
                                    sendPacket(CmfProtocol.createReadBatteryPacket())
                                }
                            }
                        } else if (socket == null && !_deviceStatus.value.isConnected) {
                            val activeDev = findActiveAudioDevice()
                            if (activeDev != null) {
                                Log.d(TAG, "Watchdog: Reconnected active device ${activeDev.name}, connecting...")
                                connect(activeDev, appContext)
                            }
                        }
                    } else if (!_deviceStatus.value.isConnected) {
                        val activeDev = findActiveAudioDevice()
                        if (activeDev != null) {
                            Log.d(TAG, "Watchdog: Auto-connecting to active buds ${activeDev.name} (${activeDev.address})")
                            connect(activeDev, appContext)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Watchdog tick error: ${e.message}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun findActiveAudioDevice(): BluetoothDevice? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        if (!adapter.isEnabled) return null

        val bonded = adapter.bondedDevices ?: return null
        val valid = bonded.filter { !isExcludedWatchOrNonHeadset(it) && isCmfOrNothingDevice(it) }

        // 1. Connected in A2DP or Headset profiles
        val connectedProfiles = try {
            (a2dpProfile?.connectedDevices.orEmpty() + headsetProfile?.connectedDevices.orEmpty())
                .distinctBy { it.address }
                .filter { !isExcludedWatchOrNonHeadset(it) && isCmfOrNothingDevice(it) }
        } catch (_: Exception) { emptyList() }

        if (connectedProfiles.isNotEmpty()) {
            val preferred = preferredAddress
            if (!preferred.isNullOrBlank()) {
                val match = connectedProfiles.find { it.address.equals(preferred, ignoreCase = true) }
                if (match != null) return match
            }
            return connectedProfiles.firstOrNull()
        }

        // 2. Preferred address if bonded and verified connected in system
        val pref = preferredAddress
        if (!pref.isNullOrBlank()) {
            val match = valid.find { it.address.equals(pref, ignoreCase = true) }
            if (match != null && isDeviceConnected(match, appContext)) return match
        }

        return null
    }

    @SuppressLint("MissingPermission")
    fun isExcludedWatchOrNonHeadset(device: BluetoothDevice?): Boolean {
        if (device == null) return true
        val name = try { (device.name ?: "").trim().lowercase() } catch (_: Exception) { "" }
        val devClass = try { device.bluetoothClass } catch (_: Exception) { null }

        // Comprehensive watch, fitness tracker, smartwatch, band, and wearable keywords
        val watchKeywords = listOf(
            "watch", "band", "fit", "wear", "gear", "clock", "gtr", "gts", "bip", "t-rex", "pace",
            "mi smart band", "miband", "galaxy watch", "pixel watch", "amazfit", "garmin", "huawei watch",
            "honor watch", "redmi watch", "d105", "ultra", "tracker", "bracelet", "pedometer", "apple watch",
            "fitbit", "coros", "suunto", "polar", "withings", "ticwatch", "fossil", "skagen", "casio",
            "g-shock", "noise", "colorfit", "boat wave", "storm", "fire-boltt", "titan", "fastrack",
            "realme watch", "oppo watch", "oneplus watch", "haylou", "zepp", "smart ring", "oura", "whoop"
        )
        if (watchKeywords.any { name.contains(it) }) {
            return true
        }

        // Check BluetoothClass: WEARABLE, HEALTH, TOY, or COMPUTER
        if (devClass != null) {
            val major = devClass.majorDeviceClass
            if (major == BluetoothClass.Device.Major.WEARABLE ||
                major == BluetoothClass.Device.Major.HEALTH ||
                major == BluetoothClass.Device.Major.TOY ||
                major == BluetoothClass.Device.Major.COMPUTER) {
                return true
            }
            val sub = devClass.deviceClass
            // Subclasses for Wrist Watch (0x0704), Pager (0x0708), Jacket (0x0710), Helmet (0x0714), Glasses (0x0718)
            if (sub == 0x0704 || sub == 0x0708 || sub == 0x0710 || sub == 0x0714 || sub == 0x0718) {
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
            .filter { !isExcludedWatchOrNonHeadset(it) && isCmfOrNothingDevice(it) }

        if (connectedList.isEmpty()) {
            val cur = currentDevice
            if (cur == null || !isDeviceConnected(cur, appContext)) {
                if (_deviceStatus.value.isConnected && (socket == null || socket?.isConnected != true)) {
                    _deviceStatus.value = _deviceStatus.value.copy(
                        isConnected = false,
                        isSppActive = false,
                        isGattActive = false,
                        connectionType = AppStrings.statusDisconnected
                    )
                }
            }
            return
        }

        for (dev in connectedList) {
            val bat = getSystemBatteryLevel(dev)
            if (bat in 0..100) {
                updateSystemBattery(dev, bat)
                return
            } else {
                val name = try { dev.name ?: "CMF Buds" } catch (_: Exception) { "CMF Buds" }
                val cur = _deviceStatus.value.battery
                val fallbackBat = if (cur.leftPercent > 0) cur.leftPercent else -1
                _deviceStatus.value = _deviceStatus.value.copy(
                    isConnected = true,
                    deviceName = name,
                    deviceAddress = dev.address,
                    connectionType = if (socket?.isConnected == true) "SPP + BLE" else "A2DP Hi-Fi",
                    battery = cur.copy(
                        leftPercent = fallbackBat,
                        rightPercent = fallbackBat,
                        casePercent = if (cur.casePercent > 0) cur.casePercent else lastKnownCaseBattery,
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
        // Never treat excluded watch or non-earbud as connected
        if (isExcludedWatchOrNonHeadset(device)) return false

        // 1. Hidden reflection method device.isConnected() - direct ACL state in Android OS
        try {
            val method = device.javaClass.getMethod("isConnected")
            val res = method.invoke(device) as? Boolean
            if (res == true) return true
        } catch (_: Exception) {}

        // 2. A2DP profile connection state for this specific device
        try {
            if (a2dpProfile?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) return true
            if (a2dpProfile?.connectedDevices?.any { it.address.equals(device.address, ignoreCase = true) } == true) return true
        } catch (_: Exception) {}

        // 3. Headset profile connection state for this specific device
        try {
            if (headsetProfile?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) return true
            if (headsetProfile?.connectedDevices?.any { it.address.equals(device.address, ignoreCase = true) } == true) return true
        } catch (_: Exception) {}

        // 4. Active RFCOMM SPP socket check
        if (socket?.isConnected == true && currentDevice?.address.equals(device.address, ignoreCase = true)) {
            return true
        }

        return false
    }

    fun onDeviceDisconnected(device: BluetoothDevice?) {
        val currentAddr = _deviceStatus.value.deviceAddress
        if (device == null || currentAddr.isBlank() || device.address.equals(currentAddr, ignoreCase = true)) {
            Log.d(TAG, "Device ${device?.address} disconnected, resetting connection status")
            disconnect()
        }
    }

    fun updateSystemBattery(device: BluetoothDevice?, level: Int) {
        if (device != null && (isExcludedWatchOrNonHeadset(device) || !isCmfOrNothingDevice(device))) return
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
                    casePercent = if (cur.casePercent > 0) cur.casePercent else lastKnownCaseBattery,
                    isLeftConnected = true,
                    isRightConnected = true
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun onDeviceAclConnected(device: BluetoothDevice?) {
        if (device == null || isExcludedWatchOrNonHeadset(device) || !isCmfOrNothingDevice(device)) return
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
            connectionType = if (socket?.isConnected == true) "SPP + BLE" else "A2DP Hi-Fi",
            battery = BatteryInfo(
                leftPercent = batLvl,
                rightPercent = batLvl,
                casePercent = if (currentBat.casePercent > 0) currentBat.casePercent else lastKnownCaseBattery,
                isLeftConnected = true,
                isRightConnected = true
            )
        )
    }

    /**
     * Connects to BLE GATT Client with dual-channel support:
     * 1. Standard Battery Service (0x180F -> 0x2A19) with real-time notifications
     * 2. Nothing / CMF Custom GATT Protocol Service (aeac4a03-...) for direct command dispatch
     *    even if RFCOMM SPP is locked by Nothing X / Knox!
     */
    @SuppressLint("MissingPermission")
    fun connectGattClient(context: Context, device: BluetoothDevice) {
        try {
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null
        gattWriteChar = null
        gattNotifyChar = null

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "GATT connected to ${device.address}, requesting priority and MTU...")
                    try {
                        gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            gatt?.requestMtu(512)
                        } else {
                            gatt?.discoverServices()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "GATT init failed", e)
                        gatt?.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "GATT disconnected from ${device.address}")
                    try {
                        gatt?.close()
                    } catch (_: Exception) {}
                    if (bluetoothGatt == gatt) {
                        bluetoothGatt = null
                        gattWriteChar = null
                        gattNotifyChar = null
                        val sppActive = socket?.isConnected == true
                        val audioActive = isDeviceConnected(device, appContext)
                        _deviceStatus.value = _deviceStatus.value.copy(
                            isGattActive = false,
                            isConnected = sppActive || audioActive,
                            connectionType = if (sppActive) "SPP Direct" else if (audioActive) "A2DP Hi-Fi" else AppStrings.statusDisconnected
                        )
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                Log.d(TAG, "GATT MTU changed: $mtu, discovering services...")
                try {
                    gatt?.discoverServices()
                } catch (e: Exception) {
                    Log.w(TAG, "discoverServices error", e)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                    Log.d(TAG, "GATT services discovered (${gatt.services.size} services)")

                    // 1. Setup Battery Service
                    val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                    val batteryChar = batteryService?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                        ?: gatt.services.flatMap { it.characteristics }.find {
                            it.uuid.toString().contains("2a19", ignoreCase = true)
                        }

                    if (batteryChar != null) {
                        Log.d(TAG, "Found GATT Battery Characteristic! Reading and enabling notifications...")
                        gatt.readCharacteristic(batteryChar)
                        enableCharacteristicNotifications(gatt, batteryChar)
                    }

                    // 2. Setup CMF / Nothing Custom Protocol Service
                    val cmfService = gatt.getService(CmfProtocol.GATT_CMF_SERVICE_UUID)
                    if (cmfService != null) {
                        // Find Write characteristic
                        gattWriteChar = cmfService.getCharacteristic(CmfProtocol.GATT_CMF_TX_CHAR_UUID)
                            ?: cmfService.characteristics.find {
                                (it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                            }

                        // Find Notify characteristic
                        gattNotifyChar = cmfService.getCharacteristic(CmfProtocol.GATT_CMF_RX_CHAR_UUID)
                            ?: cmfService.characteristics.find {
                                (it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0
                            }

                        if (gattNotifyChar != null) {
                            Log.d(TAG, "Enabling notifications on CMF GATT Rx Characteristic...")
                            enableCharacteristicNotifications(gatt, gattNotifyChar!!)
                        }
                    }

                    _deviceStatus.value = _deviceStatus.value.copy(
                        isGattActive = true,
                        connectionType = if (socket?.isConnected == true) "SPP + BLE" else "BLE GATT"
                    )
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                    val value = characteristic.value ?: return
                    handleGattCharacteristicData(characteristic.uuid, value, device)
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    handleGattCharacteristicData(characteristic.uuid, value, device)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                if (characteristic != null) {
                    val value = characteristic.value ?: return
                    handleGattCharacteristicData(characteristic.uuid, value, device)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleGattCharacteristicData(characteristic.uuid, value, device)
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

    private fun handleGattCharacteristicData(uuid: UUID, value: ByteArray, device: BluetoothDevice) {
        if (value.isEmpty()) return
        if (uuid == BATTERY_LEVEL_CHAR_UUID || uuid.toString().contains("2a19", ignoreCase = true)) {
            val batteryPercent = value[0].toInt() and 0xFF
            Log.d(TAG, "GATT Battery Level received: $batteryPercent%")
            if (batteryPercent in 0..100) {
                updateSystemBattery(device, batteryPercent)
            }
        } else if (uuid == CmfProtocol.GATT_CMF_RX_CHAR_UUID || uuid.toString().contains("aeac", ignoreCase = true)) {
            Log.d(TAG, "GATT CMF Packet received (${value.size} bytes)")
            handleIncomingData(value, value.size)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableCharacteristicNotifications(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        try {
            gatt.setCharacteristicNotification(ch, true)
            val descriptor = ch.getDescriptor(CmfProtocol.CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (descriptor != null) {
                val value = if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, value)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = value
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable notifications on ${ch.uuid}", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun isCmfOrNothingDevice(device: BluetoothDevice?): Boolean {
        if (device == null) return false
        if (isExcludedWatchOrNonHeadset(device)) return false
        val name = try { (device.name ?: "").trim().lowercase() } catch (_: Exception) { "" }
        if (name.isBlank()) return false

        return name.replace(Regex("[^a-z0-9]"), "") == "cmfbudspro2"
    }

    @SuppressLint("MissingPermission")
    fun findCmfDevice(adapter: BluetoothAdapter, preferredAddress: String? = null): BluetoothDevice? {
        if (preferredAddress != null) {
            this.preferredAddress = preferredAddress
        }
        val bondedDevices = adapter.bondedDevices ?: return null
        if (bondedDevices.isEmpty()) return null

        val validDevices = bondedDevices.filter { !isExcludedWatchOrNonHeadset(it) && isCmfOrNothingDevice(it) }
        if (validDevices.isEmpty()) return null

        // 0. Active connected A2DP / Headset device (Top Priority)
        val connectedInProfile = try {
            (a2dpProfile?.connectedDevices.orEmpty() + headsetProfile?.connectedDevices.orEmpty())
                .distinctBy { it.address }
                .filter { !isExcludedWatchOrNonHeadset(it) && isCmfOrNothingDevice(it) }
        } catch (_: Exception) { emptyList() }

        val activePref = if (!preferredAddress.isNullOrBlank()) {
            connectedInProfile.find { it.address.equals(preferredAddress, ignoreCase = true) }
        } else null
        val activeCmf = connectedInProfile.firstOrNull()
        val activeConnected = activePref ?: activeCmf

        if (activeConnected != null) {
            return activeConnected
        }

        // 1. Preferred MAC address saved by user
        val matchedByPref = if (!preferredAddress.isNullOrBlank()) {
            validDevices.find { it.address.equals(preferredAddress, ignoreCase = true) }
        } else null

        // 2. Strong search for CMF / Nothing / Earbuds
        val byName = validDevices.firstOrNull()

        return matchedByPref ?: byName
    }

    @SuppressLint("MissingPermission")
    fun getAllBondedDevices(adapter: BluetoothAdapter): List<BluetoothDevice> {
        return adapter.bondedDevices?.filter { isCmfOrNothingDevice(it) }?.toList() ?: emptyList()
    }

    /**
     * Connects with multi-strategy fast-failover (SPP RFCOMM + BLE GATT):
     * - Cancels active discovery to prevent RFCOMM timeouts
     * - In parallel connects BLE GATT for battery & control
     * - Runs 8 fast connection strategies guarded with 1800ms timeouts
     * - Falls back cleanly to A2DP/GATT mode if SPP is locked by OEM stack
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        if (!isCmfOrNothingDevice(device)) return@withContext false
        val devName = try { device.name ?: "CMF Buds" } catch (_: Exception) { "CMF Buds" }
        val devAddr = device.address ?: ""
        currentDevice = device
        preferredAddress = devAddr
        Log.d(TAG, "Connecting to $devName ($devAddr)...")

        // Clean previous socket cleanly
        try {
            socket?.close()
            inputStream?.close()
            outputStream?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null

        // Cancel discovery to maximize radio bandwidth
        try {
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        } catch (_: Exception) {}

        // Warm up SDP cache
        try {
            device.fetchUuidsWithSdp()
        } catch (_: Exception) {}

        // Connect GATT in parallel
        val ctx = context ?: appContext
        if (ctx != null) {
            connectGattClient(ctx, device)
        }

        val isSystemConnected = isDeviceConnected(device, ctx)
        var tmpSocket: BluetoothSocket? = null

        // 8 Fast failover RFCOMM connection strategies guarded with 1800ms timeout
        val strategies: List<Pair<String, suspend () -> BluetoothSocket?>> = listOf(
            "CMF Insecure SPP" to {
                withTimeoutOrNull(1800L) {
                    try {
                        val s = device.createInsecureRfcommSocketToServiceRecord(CmfProtocol.SPP_UUID)
                        s.connect()
                        s
                    } catch (_: Exception) { null }
                }
            },
            "Reflection Insecure Ch 1" to {
                withTimeoutOrNull(1800L) {
                    try {
                        val m = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                        val s = m.invoke(device, 1) as BluetoothSocket
                        s.connect()
                        s
                    } catch (_: Exception) { null }
                }
            },
            "Reflection Insecure Ch 2" to {
                withTimeoutOrNull(1800L) {
                    try {
                        val m = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                        val s = m.invoke(device, 2) as BluetoothSocket
                        s.connect()
                        s
                    } catch (_: Exception) { null }
                }
            },
            "Reflection Insecure Ch 4" to {
                withTimeoutOrNull(1800L) {
                    try {
                        val m = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                        val s = m.invoke(device, 4) as BluetoothSocket
                        s.connect()
                        s
                    } catch (_: Exception) { null }
                }
            },
            "CMF Secure SPP" to {
                withTimeoutOrNull(1800L) {
                    try {
                        val s = device.createRfcommSocketToServiceRecord(CmfProtocol.SPP_UUID)
                        s.connect()
                        s
                    } catch (_: Exception) { null }
                }
            },
            "Standard SPP Insecure" to {
                withTimeoutOrNull(1800L) {
                    try {
                        val s = device.createInsecureRfcommSocketToServiceRecord(CmfProtocol.STANDARD_SPP_UUID)
                        s.connect()
                        s
                    } catch (_: Exception) { null }
                }
            },
            "Standard SPP Secure" to {
                withTimeoutOrNull(1800L) {
                    try {
                        val s = device.createRfcommSocketToServiceRecord(CmfProtocol.STANDARD_SPP_UUID)
                        s.connect()
                        s
                    } catch (_: Exception) { null }
                }
            },
            "Reflection Secure Ch 1" to {
                withTimeoutOrNull(1800L) {
                    try {
                        val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        val s = m.invoke(device, 1) as BluetoothSocket
                        s.connect()
                        s
                    } catch (_: Exception) { null }
                }
            }
        )

        for ((name, attempt) in strategies) {
            val sock = attempt()
            if (sock != null && sock.isConnected) {
                Log.d(TAG, "Connected to $devName via strategy: $name!")
                tmpSocket = sock
                break
            }
        }

        val isSppConnected = tmpSocket != null && tmpSocket.isConnected
        val isGattConnected = _deviceStatus.value.isGattActive

        if (!isSppConnected && !isSystemConnected && !isGattConnected) {
            Log.w(TAG, "Device $devName is unreachable via SPP, GATT or system audio")
            currentDevice = null
            _deviceStatus.value = _deviceStatus.value.copy(
                isConnected = false,
                isSppActive = false,
                isGattActive = false,
                connectionType = AppStrings.statusDisconnected
            )
            return@withContext false
        }

        val sysBat = getSystemBatteryLevel(device)
        val curBat = _deviceStatus.value.battery
        val batLvl = if (sysBat in 1..100) sysBat else (if (curBat.leftPercent in 1..100) curBat.leftPercent else -1)

        val connType = when {
            isSppConnected && isGattConnected -> "SPP + BLE"
            isSppConnected -> "SPP Direct"
            isGattConnected -> "BLE GATT"
            else -> "A2DP Hi-Fi"
        }

        _deviceStatus.value = _deviceStatus.value.copy(
            isConnected = true,
            deviceName = devName,
            deviceAddress = devAddr,
            earbudListeningPreset = -1,
            isEarbudListeningPresetAvailable = false,
            isEarbudCustomEqAvailable = false,
            earbudCustomBass = 0,
            earbudCustomMid = 0,
            earbudCustomTreble = 0,
            isLatencyModeAvailable = false,
            isInEarDetectionAvailable = false,
            isSpatialAudioAvailable = false,
            spatialAudioMode = -1,
            isSpatialHeadTrackingEnabled = false,
            isGestureConfigAvailable = false,
            isDualConnectionAvailable = false,
            isRememberedDeviceListAvailable = false,
            rememberedDevices = emptyList(),
            hardwareModel = "",
            connectionType = connType,
            isSppActive = isSppConnected,
            isGattActive = isGattConnected,
            battery = BatteryInfo(
                leftPercent = batLvl,
                rightPercent = batLvl,
                casePercent = if (curBat.casePercent > 0) curBat.casePercent else lastKnownCaseBattery,
                isLeftConnected = true,
                isRightConnected = true
            )
        )

        if (isSppConnected) {
            socket = tmpSocket
            inputStream = tmpSocket!!.inputStream
            outputStream = tmpSocket.outputStream
            startReading()
            startStatusPolling()

            sendPacket(CmfProtocol.createReadBatteryPacket())
            sendPacket(CmfProtocol.createReadAncPacket())
            sendPacket(CmfProtocol.createReadDeviceModelPacket())
            sendPacket(CmfProtocol.createReadFirmwarePacket())
        } else {
            Log.d(TAG, "Operating $devName in BLE GATT / System A2DP mode ($connType)")
            startStatusPolling()
        }

        true
    }

    /**
     * Dual-Transport Send Packet:
     * Dispatches packet through RFCOMM SPP if connected;
     * if SPP is unavailable or fails, automatically dispatches via BLE GATT write!
     */
    suspend fun sendPacket(packet: ByteArray): Boolean = sendMutex.withLock {
        withContext(Dispatchers.IO) {
            // 1. Try SPP RFCOMM
            val out = outputStream
            if (out != null) {
                try {
                    out.write(packet)
                    out.flush()
                    return@withContext true
                } catch (e: Exception) {
                    Log.w(TAG, "SPP send failed, falling back to BLE GATT: ${e.message}")
                }
            }

            // 2. Fallback to BLE GATT Write
            val gatt = bluetoothGatt
            val writeChar = gattWriteChar
            if (gatt != null && writeChar != null) {
                try {
                    val writeType = if ((writeChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    } else {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                    val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val res = gatt.writeCharacteristic(
                            writeChar,
                            packet,
                            writeType
                        )
                        res == BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        writeChar.value = packet
                        writeChar.writeType = writeType
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(writeChar)
                    }
                    if (success) {
                        Log.d(TAG, "Packet successfully sent via BLE GATT write")
                        return@withContext true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "BLE GATT write failed: ${e.message}")
                }
            }

            false
        }
    }

    suspend fun setAnc(mode: AncMode, level: AncLevel = AncLevel.HIGH) {
        // 1. Immediate optimistic UI update so buttons respond instantly with zero lag
        _deviceStatus.value = _deviceStatus.value.copy(ancMode = mode, ancLevel = level)

        val sent = sendPacket(CmfProtocol.createSetAncPacket(mode, level))

        if (sent) {
            awaitingAnc = mode
            val pendingStatus = if (AppStrings.isRussian) "ANC: ожидаю ответ наушников" else "ANC: waiting for earbud response"
            _commandStatus.value = pendingStatus
            ancCheckJob?.cancel()
            ancCheckJob = scope.launch {
                delay(150)
                sendPacket(CmfProtocol.createReadAncPacket())
                delay(3000)
                if (awaitingAnc == mode) {
                    awaitingAnc = null
                    if (_commandStatus.value == pendingStatus) {
                        _commandStatus.value = if (AppStrings.isRussian) "ANC: нет подтверждения от наушников" else "ANC: no earbud confirmation"
                    }
                }
            }
        } else {
            _commandStatus.value = if (AppStrings.isRussian) "ANC: команда не отправлена" else "ANC: command not sent"
        }
    }

    suspend fun setUltraBass(level: Int) {
        if (!supportsB172Settings()) {
            _commandStatus.value = if (AppStrings.isRussian) "Бас: код модели не подтверждён" else "Bass: model not verified"
            return
        }
        val clamped = level.coerceIn(0, 5)
        if (!sendPacket(CmfProtocol.createSetUltraBassPacket(clamped))) {
            _commandStatus.value = if (AppStrings.isRussian) "Бас: команда не отправлена" else "Bass: command not sent"
            return
        }
        val pendingStatus = if (AppStrings.isRussian) "Бас: ожидаю подтверждение наушников" else "Bass: waiting for earbud readback"
        _commandStatus.value = pendingStatus
        delay(150)
        sendPacket(CmfProtocol.createReadBassPacket())
        delay(1800)
        if (_commandStatus.value == pendingStatus) {
            _commandStatus.value = if (AppStrings.isRussian) "Бас: наушники не подтвердили изменение" else "Bass: earbuds did not confirm the change"
        }
    }

    suspend fun setEarbudListeningPreset(preset: Int) {
        if (!supportsB172Settings()) {
            _commandStatus.value = if (AppStrings.isRussian) "Пресет наушников: модель не подтверждена" else "Earbud preset: model not verified"
            return
        }
        if (preset !in 0..6) return
        if (!sendPacket(CmfProtocol.createSetEarbudListeningPresetPacket(preset))) {
            _commandStatus.value = if (AppStrings.isRussian) "Пресет наушников: команда не отправлена" else "Earbud preset: command not sent"
            return
        }
        awaitingEarbudPreset = preset
        val pendingStatus = if (AppStrings.isRussian) "Пресет наушников: ожидаю подтверждение" else "Earbud preset: waiting for confirmation"
        _commandStatus.value = pendingStatus
        delay(150)
        sendPacket(CmfProtocol.createReadEarbudListeningPresetPacket())
        delay(1800)
        if (awaitingEarbudPreset == preset) {
            awaitingEarbudPreset = null
            if (_commandStatus.value == pendingStatus) {
                _commandStatus.value = if (AppStrings.isRussian) "Пресет наушников: нет подтверждения" else "Earbud preset: no confirmation"
            }
        }
    }

    suspend fun setEarbudCustomEq(bass: Int, mid: Int, treble: Int) {
        if (!supportsB172Settings()) {
            _commandStatus.value = if (AppStrings.isRussian) "EQ наушников: модель не подтверждена" else "Earbud EQ: model not verified"
            return
        }
        val expected = Triple(bass.coerceIn(-6, 6), mid.coerceIn(-6, 6), treble.coerceIn(-6, 6))
        if (!sendPacket(CmfProtocol.createSetEarbudCustomEqPacket(expected.first, expected.second, expected.third))) {
            _commandStatus.value = if (AppStrings.isRussian) "EQ наушников: команда не отправлена" else "Earbud EQ: command not sent"
            return
        }
        awaitingCustomEq = expected
        val pendingStatus = if (AppStrings.isRussian) "EQ наушников: ожидаю подтверждение" else "Earbud EQ: waiting for confirmation"
        _commandStatus.value = pendingStatus
        delay(150)
        sendPacket(CmfProtocol.createReadEarbudCustomEqPacket())
        delay(1800)
        if (awaitingCustomEq == expected) {
            awaitingCustomEq = null
            if (_commandStatus.value == pendingStatus) {
                _commandStatus.value = if (AppStrings.isRussian) "EQ наушников: нет подтверждения" else "Earbud EQ: no confirmation"
            }
        }
    }

    suspend fun refreshEarbudCustomEq() {
        if (!supportsB172Settings()) return
        if (!sendPacket(CmfProtocol.createReadEarbudCustomEqPacket())) {
            _commandStatus.value = if (AppStrings.isRussian) "EQ наушников: чтение не отправлено" else "Earbud EQ: read request not sent"
        }
    }

    suspend fun setGameMode(enabled: Boolean) {
        if (!supportsB172Settings()) {
            _commandStatus.value = if (AppStrings.isRussian) "Игровой режим: модель не подтверждена" else "Game mode: model not verified"
            return
        }
        val sent = sendPacket(CmfProtocol.createSetLatencyPacket(enabled))
        awaitingGameMode = if (sent) enabled else null
        _commandStatus.value = if (sent) {
            if (AppStrings.isRussian) "Игровой режим: ожидаю подтверждение" else "Game mode: waiting for confirmation"
        } else if (AppStrings.isRussian) "Игровой режим: команда не отправлена" else "Game mode: command not sent"
        if (sent) {
            delay(150)
            sendPacket(CmfProtocol.createReadLatencyPacket())
            delay(1800)
            if (awaitingGameMode == enabled) {
                awaitingGameMode = null
                if (AppStrings.isRussian) _commandStatus.value = "Игровой режим: наушники не подтвердили изменение"
                else _commandStatus.value = "Game mode: earbuds did not confirm the change"
            }
        }
    }

    suspend fun setInEarDetection(enabled: Boolean) {
        if (!supportsB172Settings()) {
            _commandStatus.value = if (AppStrings.isRussian) "Автопауза: модель не подтверждена" else "In-ear detection: model not verified"
            return
        }
        val sent = sendPacket(CmfProtocol.createSetInEarPacket(enabled))
        awaitingInEarDetection = if (sent) enabled else null
        _commandStatus.value = if (sent) {
            if (AppStrings.isRussian) "Автопауза: ожидаю подтверждение" else "In-ear detection: waiting for confirmation"
        } else if (AppStrings.isRussian) "Автопауза: команда не отправлена" else "In-ear detection: command not sent"
        if (sent) {
            delay(150)
            sendPacket(CmfProtocol.createReadInEarPacket())
            delay(1800)
            if (awaitingInEarDetection == enabled) {
                awaitingInEarDetection = null
                if (AppStrings.isRussian) _commandStatus.value = "Автопауза: наушники не подтвердили изменение"
                else _commandStatus.value = "In-ear detection: earbuds did not confirm the change"
            }
        }
    }

    suspend fun setEarbudSpatialAudio(mode: Int, headTracking: Boolean) {
        if (!supportsB172Settings()) return
        val expected = mode.coerceIn(0, 4) to (headTracking && mode != 0)
        val sent = sendPacket(CmfProtocol.createSetSpatialAudioPacket(expected.first, expected.second))
        awaitingSpatialAudio = if (sent) expected else null
        _commandStatus.value = if (sent) {
            if (AppStrings.isRussian) "Пространственный звук: ожидаю подтверждение" else "Spatial audio: waiting for confirmation"
        } else if (AppStrings.isRussian) "Пространственный звук: команда не отправлена" else "Spatial audio: command not sent"
        if (sent) {
            delay(150)
            sendPacket(CmfProtocol.createReadSpatialAudioPacket())
            delay(1800)
            if (awaitingSpatialAudio == expected) {
                awaitingSpatialAudio = null
                _commandStatus.value = if (AppStrings.isRussian) "Пространственный звук: нет подтверждения" else "Spatial audio: no confirmation"
            }
        }
    }

    suspend fun setEarbudGesture(device: Int, gesture: Int, action: Int) {
        if (!supportsB172Settings()) return
        val expected = Triple(device, gesture, action)
        val sent = runCatching {
            sendPacket(CmfProtocol.createSetEarbudGesturePacket(device, gesture, action))
        }.getOrDefault(false)
        awaitingGesture = if (sent) expected else null
        _commandStatus.value = if (sent) {
            if (AppStrings.isRussian) "Жест наушников: ожидаю подтверждение" else "Earbud gesture: waiting for confirmation"
        } else if (AppStrings.isRussian) "Жест наушников: команда не отправлена" else "Earbud gesture: command not sent"
        if (sent) {
            delay(150)
            sendPacket(CmfProtocol.createReadEarbudGesturesPacket())
            delay(1800)
            if (awaitingGesture == expected) {
                awaitingGesture = null
                _commandStatus.value = if (AppStrings.isRussian) "Жест наушников: нет подтверждения" else "Earbud gesture: no confirmation"
            }
        }
    }

    suspend fun setDualConnectionEnabled(enabled: Boolean) {
        if (!supportsB172Settings()) return
        val sent = sendPacket(CmfProtocol.createSetDualConnectionPacket(enabled))
        awaitingDualConnectionEnabled = if (sent) enabled else null
        _commandStatus.value = if (sent) {
            if (AppStrings.isRussian) "Двойное подключение: наушники перезапускаются; переподключитесь для проверки" else "Dual connection: earbuds reboot; reconnect to verify"
        } else if (AppStrings.isRussian) "Двойное подключение: команда не отправлена" else "Dual connection: command not sent"
        if (sent) {
            delay(3000)
            if (awaitingDualConnectionEnabled == enabled) {
                awaitingDualConnectionEnabled = null
                _commandStatus.value = if (AppStrings.isRussian) "Двойное подключение: переподключитесь, чтобы прочитать состояние" else "Dual connection: reconnect to read its state"
            }
        }
    }

    suspend fun setDualDeviceConnected(macAddress: String, connect: Boolean) {
        if (!supportsB172Settings()) return
        val packet = runCatching { CmfProtocol.createSetDualDeviceConnectionPacket(macAddress, connect) }.getOrNull()
            ?: return
        if (!sendPacket(packet)) {
            _commandStatus.value = if (AppStrings.isRussian) "Запомненное устройство: команда не отправлена" else "Remembered device: command not sent"
            return
        }
        awaitingDualDevice = macAddress.replace('-', ':').uppercase() to connect
        delay(250)
        refreshDualDeviceList()
        delay(1800)
        if (awaitingDualDevice != null) {
            awaitingDualDevice = null
            _commandStatus.value = if (AppStrings.isRussian) "Запомненное устройство: нет подтверждения" else "Remembered device: no confirmation"
        }
    }

    private suspend fun refreshDualDeviceList() {
        dualListPageRequests = 1
        _deviceStatus.value = _deviceStatus.value.copy(rememberedDevices = emptyList(), isRememberedDeviceListAvailable = false)
        sendPacket(CmfProtocol.createReadDualDeviceListPacket(0))
    }

    suspend fun setHdAudio(enabled: Boolean): Boolean =
        sendPacket(CmfProtocol.createSetHdAudioPacket(enabled))

    suspend fun startFindMyBuds(left: Boolean, right: Boolean) {
        sendPacket(CmfProtocol.createFindMyBudsPacket(left, right))
    }

    suspend fun stopFindMyBuds() {
        sendPacket(CmfProtocol.createStopFindMyBudsPacket())
    }

    suspend fun refreshStatus() {
        val dev = currentDevice
        if (dev != null) {
            val bat = getSystemBatteryLevel(dev)
            if (bat in 1..100) {
                updateSystemBattery(dev, bat)
            }
        }
        checkProfileBattery()
        sendPacket(CmfProtocol.createReadBatteryPacket())
        sendPacket(CmfProtocol.createReadAncPacket())
        if (supportsB172Settings()) requestB172Settings()
    }

    private fun supportsB172Settings(): Boolean =
        _deviceStatus.value.hardwareModel.equals("B172", ignoreCase = true)

    private suspend fun requestB172Settings() {
        sendPacket(CmfProtocol.createReadEarbudListeningPresetPacket())
        sendPacket(CmfProtocol.createReadBassPacket())
        sendPacket(CmfProtocol.createReadLatencyPacket())
        sendPacket(CmfProtocol.createReadInEarPacket())
        sendPacket(CmfProtocol.createReadSpatialAudioPacket())
        sendPacket(CmfProtocol.createReadEarbudGesturesPacket())
        sendPacket(CmfProtocol.createReadDualConnectionPacket())
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
                    Log.w(TAG, "SPP stream ended", e)
                    val dev = currentDevice
                    val stillConnected = dev != null && isDeviceConnected(dev, appContext)
                    try {
                        inputStream?.close()
                        outputStream?.close()
                        socket?.close()
                    } catch (_: Exception) {}
                    socket = null
                    inputStream = null
                    outputStream = null
                    _deviceStatus.value = _deviceStatus.value.copy(isSppActive = false)

                    if (stillConnected) {
                        Log.d(TAG, "Device is still connected via A2DP/Headset. Retaining connected state.")
                        _deviceStatus.value = _deviceStatus.value.copy(
                            connectionType = if (bluetoothGatt != null) "BLE GATT" else "A2DP Hi-Fi"
                        )
                        scope.launch {
                            delay(3000)
                            if (_deviceStatus.value.isConnected && socket == null && currentDevice != null) {
                                tryReconnectSpp(currentDevice!!)
                            }
                        }
                    } else {
                        disconnect()
                    }
                    break
                }
            }
        }
    }

    private suspend fun tryReconnectSpp(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        if (socket?.isConnected == true) return@withContext
        try {
            val s = withTimeoutOrNull(2000L) {
                try {
                    val sock = device.createInsecureRfcommSocketToServiceRecord(CmfProtocol.SPP_UUID)
                    sock.connect()
                    sock
                } catch (_: Exception) {
                    try {
                        val m = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                        val sock = m.invoke(device, 1) as BluetoothSocket
                        sock.connect()
                        sock
                    } catch (_: Exception) { null }
                }
            }
            if (s != null && s.isConnected) {
                socket = s
                inputStream = s.inputStream
                outputStream = s.outputStream
                Log.d(TAG, "Silently reconnected SPP to ${device.address}")
                _deviceStatus.value = _deviceStatus.value.copy(
                    isSppActive = true,
                    connectionType = if (bluetoothGatt != null) "SPP + BLE" else "SPP Direct"
                )
                startReading()
                refreshStatus()
            }
        } catch (_: Exception) {}
    }

    private fun startStatusPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(20_000) // Fast 20s interval
                if (_deviceStatus.value.isConnected) {
                    refreshStatus()
                }
            }
        }
    }

    private val incomingBuffer = java.io.ByteArrayOutputStream()

    @Synchronized
    private fun handleIncomingData(data: ByteArray, length: Int) {
        if (length <= 0 || length > data.size) return
        incomingBuffer.write(data, 0, length)
        val bytes = incomingBuffer.toByteArray()
        var offset = 0

        while (offset < bytes.size) {
            var magicIndex = -1
            for (i in offset until bytes.size - 2) {
                if (bytes[i] == CmfProtocol.MAGIC_BYTE_0 && bytes[i + 1] == CmfProtocol.MAGIC_BYTE_1) {
                    magicIndex = i
                    break
                }
            }
            if (magicIndex < 0) {
                val keep = (bytes.size - offset).coerceAtMost(2)
                incomingBuffer.reset()
                if (keep > 0) incomingBuffer.write(bytes, bytes.size - keep, keep)
                return
            }
            if (magicIndex > offset) {
                offset = magicIndex
            }
            if (bytes.size - offset < 8) {
                val remaining = bytes.size - offset
                incomingBuffer.reset()
                incomingBuffer.write(bytes, offset, remaining)
                return
            }

            if (bytes[offset + 2] != CmfProtocol.PROTOCOL_VER) {
                offset += 1
                continue
            }

            val payloadLength = bytes[offset + 5].toInt() and 0xFF
            val packetLength = 8 + payloadLength + 2
            if (bytes.size - offset < packetLength) {
                val remaining = bytes.size - offset
                incomingBuffer.reset()
                incomingBuffer.write(bytes, offset, remaining)
                return
            }

            val slice = bytes.copyOfRange(offset, offset + packetLength)
            val packet = CmfProtocol.parsePacket(slice, slice.size)
            if (packet == null) {
                offset += 1
                continue
            }

            processPacket(packet)

            offset += packetLength
        }

        incomingBuffer.reset()
        if (offset < bytes.size) {
            incomingBuffer.write(bytes, offset, bytes.size - offset)
        }
    }

    private fun processPacket(packet: PacketResult) {
        when (packet.command) {
            CmfProtocol.CMD_READ_DEVICE_MODEL -> {
                if (packet.payload.size >= 2) {
                    val modelId = ((packet.payload[1].toInt() and 0xFF) shl 8) or
                        (packet.payload[0].toInt() and 0xFF)
                    val normalizedModel = modelId.toString(16).uppercase()
                    _deviceStatus.value = _deviceStatus.value.copy(hardwareModel = normalizedModel)
                    if (normalizedModel == "B172") {
                        scope.launch { requestB172Settings() }
                    }
                }
            }
            CmfProtocol.CMD_READ_BATTERY -> {
                if (packet.payload.size >= 3) {
                    val cur = _deviceStatus.value.battery

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
                    val (caseVal, caseChg, caseConn) = parseBud(packet.payload[2], if (cur.casePercent > 0) cur.casePercent else lastKnownCaseBattery)

                    if (caseVal > 0) {
                        lastKnownCaseBattery = caseVal
                    }

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
                            isCaseAvailable = caseConn || lastKnownCaseBattery > 0
                        )
                    )
                }
            }
            CmfProtocol.CMD_READ_ANC -> {
                if (packet.payload.isNotEmpty()) {
                    val (mode, level) = parseAncPayload(packet.command, packet.payload)
                    if (mode != null) {
                        _deviceStatus.value = _deviceStatus.value.copy(
                            ancMode = mode,
                            ancLevel = level ?: _deviceStatus.value.ancLevel
                        )
                        awaitingAnc?.let { expected ->
                            _commandStatus.value = if (expected == mode) {
                                if (AppStrings.isRussian) "ANC: подтверждён ответом наушников" else "ANC: confirmed by earbud response"
                            } else {
                                if (AppStrings.isRussian) "ANC: активен режим ${mode.title}" else "ANC: active mode ${mode.title}"
                            }
                            awaitingAnc = null
                            ancCheckJob?.cancel()
                        }
                    }
                }
            }
            CmfProtocol.CMD_READ_LISTEN_MODE -> {
                if (packet.payload.isNotEmpty()) {
                    val preset = packet.payload[0].toInt() and 0xFF
                    if (preset in 0..6) {
                        _deviceStatus.value = _deviceStatus.value.copy(
                            earbudListeningPreset = preset,
                            isEarbudListeningPresetAvailable = true
                        )
                        awaitingEarbudPreset?.let { expected ->
                            _commandStatus.value = if (preset == expected) {
                                if (AppStrings.isRussian) "Пресет наушников: подтверждён" else "Earbud preset: confirmed"
                            } else {
                                if (AppStrings.isRussian) "Активный пресет наушников: $preset" else "Active earbud preset: $preset"
                            }
                            awaitingEarbudPreset = null
                        }
                        if (preset == 6) scope.launch {
                            delay(100)
                            sendPacket(CmfProtocol.createReadEarbudCustomEqPacket())
                        }
                    }
                }
            }
            CmfProtocol.CMD_READ_CUSTOM_EQ -> {
                CmfProtocol.parseEarbudCustomEq(packet.payload)?.let { (bass, mid, treble) ->
                    _deviceStatus.value = _deviceStatus.value.copy(
                        isEarbudCustomEqAvailable = true,
                        earbudCustomBass = bass,
                        earbudCustomMid = mid,
                        earbudCustomTreble = treble
                    )
                    awaitingCustomEq?.let { expected ->
                        _commandStatus.value = if (expected == Triple(bass, mid, treble)) {
                            if (AppStrings.isRussian) "EQ наушников: подтверждён" else "Earbud EQ: confirmed"
                        } else {
                            if (AppStrings.isRussian) "EQ наушников: прочитаны фактические значения" else "Earbud EQ: actual values read"
                        }
                        awaitingCustomEq = null
                    }
                }
            }
            CmfProtocol.CMD_READ_LATENCY -> {
                if (packet.payload.isNotEmpty()) {
                    val enabled = packet.payload[0].toInt() == 1
                    _deviceStatus.value = _deviceStatus.value.copy(isGameModeEnabled = enabled, isLatencyModeAvailable = true)
                    awaitingGameMode = null
                    _commandStatus.value = if (AppStrings.isRussian) "Игровой режим: ${if (enabled) "включён" else "выключен"} подтверждён наушниками" else "Game mode: ${if (enabled) "enabled" else "disabled"} confirmed by earbuds"
                }
            }
            CmfProtocol.CMD_READ_IN_EAR -> {
                if (packet.payload.size >= 3) {
                    val enabled = packet.payload[2].toInt() != 0
                    _deviceStatus.value = _deviceStatus.value.copy(isInEarDetectionEnabled = enabled, isInEarDetectionAvailable = true)
                    awaitingInEarDetection = null
                    _commandStatus.value = if (AppStrings.isRussian) "Автопауза: ${if (enabled) "включена" else "выключена"} подтверждена наушниками" else "In-ear detection: ${if (enabled) "enabled" else "disabled"} confirmed by earbuds"
                }
            }
            CmfProtocol.CMD_READ_SPATIAL -> {
                if (packet.payload.isNotEmpty()) {
                    val mode = packet.payload[0].toInt() and 0xFF
                    val headTracking = packet.payload.getOrNull(1)?.toInt() == 1
                    _deviceStatus.value = _deviceStatus.value.copy(
                        spatialAudioMode = if (mode in 0..4) mode else -1,
                        isSpatialHeadTrackingEnabled = headTracking,
                        isSpatialAudioAvailable = mode in 0..4
                    )
                    awaitingSpatialAudio?.let { expected ->
                        _commandStatus.value = if (expected == (mode to headTracking)) {
                            if (AppStrings.isRussian) "Пространственный звук: подтверждён" else "Spatial audio: confirmed"
                        } else if (AppStrings.isRussian) "Пространственный звук: прочитан фактический режим" else "Spatial audio: actual mode read back"
                        awaitingSpatialAudio = null
                    }
                }
            }
            CmfProtocol.CMD_READ_GESTURES -> {
                CmfProtocol.parseEarbudGestures(packet.payload)?.let { mappings ->
                    var updated = _deviceStatus.value
                    for (mapping in mappings) {
                        if (mapping.button != 1 || mapping.device !in 2..3) continue
                        updated = when (mapping.device to mapping.gesture) {
                            2 to 2 -> updated.copy(leftDoubleTapAction = mapping.action)
                            2 to 3 -> updated.copy(leftTripleTapAction = mapping.action)
                            2 to 7 -> updated.copy(leftHoldAction = mapping.action)
                            3 to 2 -> updated.copy(rightDoubleTapAction = mapping.action)
                            3 to 3 -> updated.copy(rightTripleTapAction = mapping.action)
                            3 to 7 -> updated.copy(rightHoldAction = mapping.action)
                            else -> updated
                        }
                    }
                    _deviceStatus.value = updated.copy(isGestureConfigAvailable = true)
                    awaitingGesture?.let { expected ->
                        val actual = mappings.firstOrNull {
                            it.device == expected.first && it.button == 1 && it.gesture == expected.second
                        }?.action
                        _commandStatus.value = if (actual == expected.third) {
                            if (AppStrings.isRussian) "Жест наушников: подтверждён" else "Earbud gesture: confirmed"
                        } else {
                            if (AppStrings.isRussian) "Жест наушников: прочитано фактическое действие" else "Earbud gesture: actual action read back"
                        }
                        awaitingGesture = null
                    }
                }
            }
            CmfProtocol.CMD_READ_DUAL_CONNECTION -> {
                if (packet.payload.isNotEmpty()) {
                    val enabled = packet.payload[0].toInt() == 1
                    _deviceStatus.value = _deviceStatus.value.copy(
                        isDualConnectionEnabled = enabled,
                        isDualConnectionAvailable = true
                    )
                    awaitingDualConnectionEnabled = null
                    if (enabled) scope.launch { refreshDualDeviceList() }
                    _commandStatus.value = if (AppStrings.isRussian) "Двойное подключение: ${if (enabled) "включено" else "выключено"} прочитано" else "Dual connection: ${if (enabled) "enabled" else "disabled"} read back"
                }
            }
            CmfProtocol.CMD_READ_DUAL_DEVICE_LIST -> {
                CmfProtocol.parseDualConnectionDeviceList(packet.payload)?.let { page ->
                    val current = _deviceStatus.value.rememberedDevices
                    val merged = (current + page).distinctBy { it.macAddress }
                    _deviceStatus.value = _deviceStatus.value.copy(rememberedDevices = merged)
                    val added = merged.size > current.size
                    if (added && dualListPageRequests < 20) {
                        dualListPageRequests++
                        scope.launch {
                            delay(120)
                            sendPacket(CmfProtocol.createReadDualDeviceListPacket(merged.size))
                        }
                    } else {
                        _deviceStatus.value = _deviceStatus.value.copy(isRememberedDeviceListAvailable = true)
                    }
                    awaitingDualDevice?.let { expected ->
                        val device = merged.firstOrNull { it.macAddress.equals(expected.first, ignoreCase = true) }
                        if (device != null && device.isConnected == expected.second) {
                            _commandStatus.value = if (AppStrings.isRussian) "Запомненное устройство: изменение подтверждено" else "Remembered device: change confirmed"
                            awaitingDualDevice = null
                        }
                    }
                }
            }
            CmfProtocol.CMD_READ_FIRMWARE -> {
                val fw = String(packet.payload).trim { it <= ' ' || it == '\u0000' }
                if (fw.isNotEmpty()) {
                    _deviceStatus.value = _deviceStatus.value.copy(firmwareVersion = fw)
                }
            }
            CmfProtocol.CMD_READ_BASS -> {
                if (packet.payload.size >= 2) {
                    val enabled = packet.payload[0].toInt() != 0
                    val rawLevel = packet.payload[1].toInt() and 0xFF
                    val level = if (enabled && rawLevel > 0) ((rawLevel + 1) / 2).coerceIn(1, 5) else 0
                    _deviceStatus.value = _deviceStatus.value.copy(ultraBassLevel = level)
                    _commandStatus.value = if (AppStrings.isRussian) "Бас: подтверждён уровень $level" else "Bass: confirmed level $level"
                }
            }
        }
    }

    private fun parseAncPayload(cmd: Int, payload: ByteArray): Pair<AncMode?, AncLevel?> {
        if (payload.isEmpty()) return Pair(null, null)

        // Case A: 3-byte format [subId, modeByte, levelByte], e.g. [0x01, 0x03, 0x03]
        if (payload.size >= 2 && payload[0] == 0x01.toByte() && payload[1].toInt() in 1..3) {
            val mode = when (payload[1].toInt()) {
                0x01 -> AncMode.OFF
                0x02 -> AncMode.TRANSPARENCY
                0x03 -> AncMode.NOISE_CANCELLATION
                else -> null
            }
            val level = if (payload.size >= 3) {
                when (payload[2].toInt()) {
                    0x01 -> AncLevel.LOW
                    0x02 -> AncLevel.MID
                    0x03 -> AncLevel.HIGH
                    0x04 -> AncLevel.ADAPTIVE
                    else -> null
                }
            } else null
            return Pair(mode, level)
        }

        // Case B: Listen mode byte (0x00=Off, 0x01=Transparency, 0x02..0x05=ANC with level)
        val b0 = payload[0].toInt() and 0xFF
        if (cmd == CmfProtocol.CMD_READ_LISTEN_MODE || b0 == 0x00 || b0 in 0x04..0x05) {
            return when (b0) {
                0x00 -> Pair(AncMode.OFF, null)
                0x01 -> Pair(AncMode.TRANSPARENCY, null)
                0x02 -> Pair(AncMode.NOISE_CANCELLATION, AncLevel.LOW)
                0x03 -> Pair(AncMode.NOISE_CANCELLATION, AncLevel.MID)
                0x04 -> Pair(AncMode.NOISE_CANCELLATION, AncLevel.HIGH)
                0x05 -> Pair(AncMode.NOISE_CANCELLATION, AncLevel.ADAPTIVE)
                else -> Pair(null, null)
            }
        }

        // Case C: Single byte ANC (0x01=Off, 0x02=Transparency, 0x03=ANC)
        return when (b0) {
            0x01 -> Pair(AncMode.OFF, null)
            0x02 -> Pair(AncMode.TRANSPARENCY, null)
            0x03 -> Pair(AncMode.NOISE_CANCELLATION, null)
            else -> Pair(null, null)
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
        gattWriteChar = null
        gattNotifyChar = null
        socket = null
        inputStream = null
        outputStream = null
        currentDevice = null
        _deviceStatus.value = _deviceStatus.value.copy(
            isConnected = false,
            isSppActive = false,
            isGattActive = false,
            connectionType = AppStrings.statusDisconnected
        )
    }
}
