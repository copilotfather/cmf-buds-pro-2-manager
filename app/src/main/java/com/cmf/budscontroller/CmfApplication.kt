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
import com.cmf.budscontroller.bluetooth.CmfBluetoothManager

class CmfApplication : Application() {

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()

        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        CmfBluetoothManager.getInstance().initProfileProxies(this, btAdapter)

        // Register system bluetooth event receiver to capture real-time battery and connection events from Android OS
        val filter = IntentFilter().apply {
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction("android.bluetooth.device.action.EXTRA_BATTERY_LEVEL")
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                if (action == "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" ||
                    action == "android.bluetooth.device.action.EXTRA_BATTERY_LEVEL") {
                    val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                    if (level in 0..100) {
                        CmfBluetoothManager.getInstance().updateSystemBattery(device, level)
                    }
                } else if (action == BluetoothDevice.ACTION_ACL_CONNECTED ||
                    action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED ||
                    action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
                    CmfBluetoothManager.getInstance().onDeviceAclConnected(device)
                    CmfBluetoothManager.getInstance().checkProfileBattery()
                    if (context != null && device != null) {
                        CmfBluetoothManager.getInstance().connectGattBattery(context, device)
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
