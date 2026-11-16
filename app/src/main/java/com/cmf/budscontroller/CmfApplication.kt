package com.cmf.budscontroller

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.cmf.budscontroller.bluetooth.CmfBluetoothManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CmfApplication : Application() {

    private val TAG = "CmfApplication"
    private val appScope = CoroutineScope(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()

        val btManager = CmfBluetoothManager.getInstance()
        appScope.launch {
            btManager.deviceStatus.collect {
                com.cmf.budscontroller.widget.BudsWidget.updateAll(this@CmfApplication)
            }
        }
        val btAdapter = BluetoothAdapter.getDefaultAdapter()

        // 1. Initialize Android system profile proxies (A2DP & Headset)
        btManager.initProfileProxies(this, btAdapter)

        // 2. Read saved preferred device address from SharedPreferences
        val prefs = getSharedPreferences("cmf_prefs", Context.MODE_PRIVATE)
        val savedAddr = prefs.getString("last_device_address", null)
        if (!savedAddr.isNullOrBlank()) {
            btManager.setPreferredAddress(savedAddr)
        }

        // 3. Start background connection watchdog for instant auto-reconnect
        btManager.startWatchdog(this)

        // 4. Register system Bluetooth broadcast receiver for instant event handling
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction("android.bluetooth.device.action.EXTRA_BATTERY_LEVEL")
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return

                // 1. Adapter state change (e.g. user toggles Bluetooth ON in Android QS)
                if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON) {
                        Log.d(TAG, "Bluetooth turned ON: re-initializing proxies and auto-connecting...")
                        val adapter = BluetoothAdapter.getDefaultAdapter()
                        btManager.initProfileProxies(this@CmfApplication, adapter)
                        val dev = btManager.findCmfDevice(adapter, prefs.getString("last_device_address", null))
                        if (dev != null) {
                            appScope.launch {
                                delay(800)
                                btManager.connect(dev, this@CmfApplication)
                            }
                        }
                    }
                    return
                }

                // 2. Per-device actions
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                if (device == null) return
                if (btManager.isExcludedWatchOrNonHeadset(device)) return
                if (!btManager.isCmfOrNothingDevice(device)) return

                when (action) {
                    "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED",
                    "android.bluetooth.device.action.EXTRA_BATTERY_LEVEL" -> {
                        val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                        if (level in 0..100) {
                            btManager.updateSystemBattery(device, level)
                        }
                    }

                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        Log.d(TAG, "ACL connected to ${device.address}, fast-connecting...")
                        btManager.onDeviceAclConnected(device)
                        btManager.checkProfileBattery()
                        appScope.launch {
                            btManager.connect(device, this@CmfApplication)
                        }
                    }

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        Log.d(TAG, "ACL disconnected from ${device.address}")
                        btManager.onDeviceDisconnected(device)
                    }

                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(android.bluetooth.BluetoothProfile.EXTRA_STATE, -1)
                        if (state == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                            Log.d(TAG, "Audio profile connected for ${device.address}")
                            btManager.onDeviceAclConnected(device)
                            btManager.checkProfileBattery()
                            appScope.launch {
                                btManager.connect(device, this@CmfApplication)
                            }
                        } else if (state == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                            if (!btManager.isDeviceConnected(device, this@CmfApplication)) {
                                btManager.onDeviceDisconnected(device)
                            }
                        }
                    }

                    BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT -> {
                        val cmd = intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD)
                        if (cmd == "+IPHONEACCEV") {
                            @Suppress("DEPRECATION")
                            val args = intent.getSerializableExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS) as? Array<*>
                            if (args != null && args.size >= 3) {
                                for (i in 0 until args.size - 1) {
                                    val key = args[i]?.toString() ?: ""
                                    if (key == "1") {
                                        val rawVal = when (val v = args[i + 1]) {
                                            is Int -> v
                                            is String -> v.toIntOrNull() ?: -1
                                            else -> -1
                                        }
                                        if (rawVal in 0..9) {
                                            val percent = (rawVal + 1) * 10
                                            btManager.updateSystemBattery(device, percent)
                                        } else if (rawVal in 10..100) {
                                            btManager.updateSystemBattery(device, rawVal)
                                        }
                                        break
                                    }
                                }
                            }
                        }
                    }

                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                        if (bondState == BluetoothDevice.BOND_BONDED) {
                            Log.d(TAG, "Device bonded: ${device.address}, auto-connecting...")
                            appScope.launch {
                                delay(500)
                                btManager.connect(device, this@CmfApplication)
                            }
                        }
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }
}
