package com.cmf.budscontroller.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cmf.budscontroller.bluetooth.CmfBluetoothManager
import com.cmf.budscontroller.protocol.AncLevel
import com.cmf.budscontroller.protocol.AncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver exposing full programmatic control for automation apps:
 * Tasker, MacroDroid, Automate, ADB Shell.
 *
 * Example ADB Commands:
 * adb shell am broadcast -a com.cmf.budscontroller.SET_ANC_MODE --es mode "anc" --es level "high"
 * adb shell am broadcast -a com.cmf.budscontroller.SET_GAME_MODE --ez enabled true
 * adb shell am broadcast -a com.cmf.budscontroller.SET_BASS_LEVEL --ei level 5
 */
class CmfAutomationReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        val manager = CmfBluetoothManager.getInstance()

        when (intent.action) {
            "com.cmf.budscontroller.SET_ANC_MODE" -> {
                val modeStr = intent.getStringExtra("mode") ?: "anc"
                val levelStr = intent.getStringExtra("level") ?: "high"

                val mode = when (modeStr.lowercase()) {
                    "off" -> AncMode.OFF
                    "transparency", "trans" -> AncMode.TRANSPARENCY
                    "toggle" -> {
                        when (manager.deviceStatus.value.ancMode) {
                            AncMode.OFF -> AncMode.TRANSPARENCY
                            AncMode.TRANSPARENCY -> AncMode.NOISE_CANCELLATION
                            AncMode.NOISE_CANCELLATION -> AncMode.OFF
                        }
                    }
                    else -> AncMode.NOISE_CANCELLATION
                }

                val level = when (levelStr.lowercase()) {
                    "low" -> AncLevel.LOW
                    "mid", "medium" -> AncLevel.MID
                    "adaptive" -> AncLevel.ADAPTIVE
                    else -> AncLevel.HIGH
                }

                scope.launch {
                    manager.setAnc(mode, level)
                }
            }

            "com.cmf.budscontroller.SET_GAME_MODE" -> {
                val enabled = if (intent.hasExtra("enabled")) {
                    intent.getBooleanExtra("enabled", false)
                } else {
                    !manager.deviceStatus.value.isGameModeEnabled // toggle
                }

                scope.launch {
                    manager.setGameMode(enabled)
                }
            }

            "com.cmf.budscontroller.SET_BASS_LEVEL" -> {
                val level = intent.getIntExtra("level", 3)
                scope.launch {
                    manager.setUltraBass(level)
                }
            }

            "com.cmf.budscontroller.REQUEST_STATUS" -> {
                context?.let { ctx ->
                    val status = manager.deviceStatus.value
                    val reply = Intent("com.cmf.budscontroller.STATUS_RESULT").apply {
                        putExtra("is_connected", status.isConnected)
                        putExtra("left_battery", status.battery.leftPercent)
                        putExtra("right_battery", status.battery.rightPercent)
                        putExtra("case_battery", status.battery.casePercent)
                        putExtra("anc_mode", status.ancMode.name)
                        putExtra("game_mode", status.isGameModeEnabled)
                        putExtra("bass_level", status.ultraBassLevel)
                    }
                    ctx.sendBroadcast(reply)
                }
            }
        }
    }
}
