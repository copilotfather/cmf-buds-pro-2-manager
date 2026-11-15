package com.cmf.budscontroller.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cmf.budscontroller.bluetooth.CmfBluetoothManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CmfGameModeTileService : TileService() {

    private val manager = CmfBluetoothManager.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
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
                tile.subtitle = "Не подключено"
            }
        } else {
            tile.state = if (status.isGameModeEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (status.isGameModeEnabled) "Включен (Низкая задержка)" else "Выключен"
            }
        }
        tile.updateTile()
    }
}
