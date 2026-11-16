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

        val cmfKeywords = listOf(
            "cmf", "nothing", "buds", "ear (", "ear(", "ear 1", "ear 2", "ear a",
            "ear open", "ear (open)", "ear (a)", "ear (1)", "ear (2)", "neckband",
            "b163", "b162", "b164", "b159", "b171", "b155", "b157", "b181"
        )
        if (cmfKeywords.any { name.contains(it) }) {
            return true
        }

        val pref = preferredAddress
        if (!pref.isNullOrBlank() && device.address.equals(pref, ignoreCase = true)) {
            return true
        }

        return false
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
        return adapter.bondedDevices?.filter { !isExcludedWatchOrNonHeadset(it) }?.toList() ?: emptyList()
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
                    val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val res = gatt.writeCharacteristic(
                            writeChar,
                            packet,
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        )
                        res == BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        writeChar.value = packet
                        writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
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
