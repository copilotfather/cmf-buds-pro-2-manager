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
import kotlinx.coroutines.launch

class CmfAncTileService : TileService() {

    private val manager = CmfBluetoothManager.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
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
                tile.subtitle = status.ancMode.title
            }
        }
        tile.updateTile()
    }
}
