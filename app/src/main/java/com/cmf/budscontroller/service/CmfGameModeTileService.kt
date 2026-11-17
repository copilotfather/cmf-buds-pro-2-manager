package com.cmf.budscontroller.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cmf.budscontroller.bluetooth.CmfBluetoothManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CmfGameModeTileService : TileService() {

    private val manager = CmfBluetoothManager.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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
        val current = manager.deviceStatus.value.isGameModeEnabled
        scope.launch {
            manager.setGameMode(!current)
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
            tile.state = if (status.isGameModeEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val battery = status.battery
                val left = battery.leftPercent.takeIf { it in 0..100 }?.let { "$it%" } ?: "—"
                val right = battery.rightPercent.takeIf { it in 0..100 }?.let { "$it%" } ?: "—"
                val case = battery.casePercent.takeIf { it in 0..100 }?.let { "$it%" } ?: "—"
                tile.subtitle = "L$left R$right C$case"
            }
        }
        tile.updateTile()
    }
}
