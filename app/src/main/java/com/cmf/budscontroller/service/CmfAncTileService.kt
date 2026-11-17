package com.cmf.budscontroller.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cmf.budscontroller.bluetooth.CmfBluetoothManager
import com.cmf.budscontroller.protocol.AncLevel
import com.cmf.budscontroller.protocol.AncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CmfAncTileService : TileService() {

    private val manager = CmfBluetoothManager.getInstance()
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var statusJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        statusJob?.cancel()
        statusJob = scope.launch {
            manager.deviceStatus.collect { updateTile() }
        }
    }

    override fun onStopListening() {
        statusJob?.cancel()
        statusJob = null
        scope.coroutineContext.cancelChildren()
        super.onStopListening()
    }

    override fun onDestroy() {
        statusJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val currentStatus = manager.deviceStatus.value
        val nextMode = when (currentStatus.ancMode) {
            AncMode.OFF -> AncMode.TRANSPARENCY
            AncMode.TRANSPARENCY -> AncMode.NOISE_CANCELLATION
            AncMode.NOISE_CANCELLATION -> AncMode.OFF
        }

        scope.launch {
            manager.setAnc(nextMode, AncLevel.HIGH)
            updateTile()
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val status = manager.deviceStatus.value

        if (!status.isConnected) {
            tile.state = Tile.STATE_UNAVAILABLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = com.cmf.budscontroller.i18n.AppStrings.statusDisconnected
            }
        } else {
            tile.state = when (status.ancMode) {
                AncMode.OFF -> Tile.STATE_INACTIVE
                AncMode.TRANSPARENCY, AncMode.NOISE_CANCELLATION -> Tile.STATE_ACTIVE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val battery = status.battery
                val left = battery.leftPercent.takeIf { it in 0..100 }?.let { "$it%" } ?: "—"
                val right = battery.rightPercent.takeIf { it in 0..100 }?.let { "$it%" } ?: "—"
                val case = battery.casePercent.takeIf { it in 0..100 }?.let { "$it%" } ?: "—"
                val mode = when (status.ancMode) {
                    AncMode.OFF -> "OFF"
                    AncMode.TRANSPARENCY -> "AMB"
                    AncMode.NOISE_CANCELLATION -> "ANC"
                }
                tile.subtitle = "$mode · L$left R$right C$case"
            }
        }
        tile.updateTile()
    }
}
