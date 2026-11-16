package com.cmf.budscontroller.audio

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.cmf.budscontroller.i18n.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CodecType(
    val sampleRate: String,
    val isHiRes: Boolean
) {
    LDAC("24-bit / 96 kHz", true),
    AAC("16-bit / 44.1 kHz", false),
    SBC("16-bit / 44.1 kHz", false);

    val title: String
        get() = when (this) {
            LDAC -> AppStrings.codecLdacTitle
            AAC -> AppStrings.codecAacTitle
            SBC -> AppStrings.codecSbcTitle
        }

    val bitrateInfo: String
        get() = when (this) {
            LDAC -> if (AppStrings.isRussian) "до 990 kbps (Hi-Res Audio Wireless)" else "up to 990 kbps (Hi-Res Audio Wireless)"
            AAC -> "256 - 320 kbps"
            SBC -> if (AppStrings.isRussian) "до 328 kbps (Baseline)" else "up to 328 kbps (Baseline)"
        }

    val description: String
        get() = when (this) {
            LDAC -> AppStrings.codecLdacDesc
            AAC -> AppStrings.codecAacDesc
            SBC -> AppStrings.codecSbcDesc
        }
}

data class CodecEngineState(
    val activeCodec: CodecType = CodecType.LDAC,
    val isLdacEnabledOnBuds: Boolean = false,
    val isHardwareOffloadActive: Boolean = false,
    val sampleRate: String = "Android selects",
    val bitsPerSample: String = "when connected",
    val channelMode: String = "Stereo (2.0)",
    val bitrateMode: String = "LDAC depends on phone support",
    val availableCodecs: List<CodecType> = listOf(
        CodecType.LDAC,
        CodecType.AAC,
        CodecType.SBC
    )
)

class BluetoothCodecManager(private val context: Context) {

    private val TAG = "BluetoothCodecManager"
    private val _state = MutableStateFlow(CodecEngineState())
    val state: StateFlow<CodecEngineState> = _state.asStateFlow()

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

    fun applyCodecToBuds(device: BluetoothDevice?, codec: CodecType, onCompleted: (Boolean) -> Unit) {
        scope.launch {
            try {
                val enableLdacInBuds = codec.isHiRes
                val sent = com.cmf.budscontroller.bluetooth.CmfBluetoothManager.getInstance().setHdAudio(enableLdacInBuds)
                if (!sent) {
                    onCompleted(false)
                    return@launch
                }

                _state.value = _state.value.copy(
                    isLdacEnabledOnBuds = enableLdacInBuds,
                    bitrateMode = if (enableLdacInBuds) "Hi-res включён • кодек выбирает Android" else "Стандартное качество • кодек выбирает Android"
                )

                onCompleted(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch codec: ${e.message}", e)
                onCompleted(false)
            }
        }
    }

    fun openSystemDeveloperOptions() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Log.w(TAG, "Unable to open Bluetooth settings")
        }
    }
}
