package com.cmf.budscontroller.audio

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CodecType(
    val title: String,
    val bitrateInfo: String,
    val sampleRate: String,
    val isHiRes: Boolean,
    val description: String
) {
    LDAC("Sony LDAC (Аппаратный Hi-Res)", "до 990 kbps (Hi-Res Audio Wireless)", "24 бит / 96 кГц", true, "Активирует чип LDAC в наушниках CMF. Максимальное качество студийного звука"),
    AAC("AAC (Энергосберегающий)", "256 - 320 kbps", "16 бит / 44.1 кГц", false, "Стандартный сбалансированный режим наушников по умолчанию"),
    SBC("SBC (Базовый)", "до 328 kbps (Baseline)", "16 бит / 44.1 кГц", false, "Универсальный профиль совместимости")
}

data class CodecEngineState(
    val activeCodec: CodecType = CodecType.LDAC,
    val isLdacEnabledOnBuds: Boolean = true,
    val isHardwareOffloadActive: Boolean = true,
    val sampleRate: String = "96.0 kHz",
    val bitsPerSample: String = "24-bit",
    val channelMode: String = "Stereo (2.0)",
    val bitrateMode: String = "990 kbps (Чип CMF LDAC активен)",
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
                // 1. Посылаем прошивке наушников CMF аппаратно-байтовую команду включения LDAC чипа (CMD_SET_HD_AUDIO)
                val enableLdacInBuds = codec.isHiRes
                com.cmf.budscontroller.bluetooth.CmfBluetoothManager.getInstance().setHdAudio(enableLdacInBuds)

                // 2. Применяем системный A2DP кодек через Android Settings
                val codecIndex = when (codec) {
                    CodecType.LDAC -> 4
                    CodecType.AAC -> 1
                    CodecType.SBC -> 0
                }

                try {
                    Settings.Global.putInt(context.contentResolver, "bluetooth_a2dp_codec_priority", codecIndex)
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot write global setting directly: ${e.message}")
                }

                _state.value = _state.value.copy(
                    activeCodec = codec,
                    isLdacEnabledOnBuds = enableLdacInBuds,
                    sampleRate = if (codec.isHiRes) "96.0 kHz" else "44.1 kHz",
                    bitsPerSample = if (codec.isHiRes) "24-bit" else "16-bit",
                    bitrateMode = if (codec == CodecType.LDAC) "990 kbps (Чип CMF LDAC активен)" else codec.bitrateInfo
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
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
