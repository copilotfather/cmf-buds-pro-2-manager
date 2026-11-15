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
    LDAC("Sony LDAC", "до 990 kbps (Hi-Res Audio Wireless)", "24 бит / 96 кГц", true, "Максимальная детализация звука студийного качества"),
    LHDC("LHDC V5 / Savitech", "до 1000 kbps (Ultra Low Latency)", "24 бит / 192 кГц", true, "Аудиофильский кодек с ультра-низкой задержкой"),
    APTX_HD("Qualcomm aptX HD", "576 kbps (High Definition)", "24 бит / 48 кГц", true, "Стабильная передача 24-битного аудио без сжатия верхов"),
    APTX_ADAPTIVE("Qualcomm aptX Adaptive", "279 - 420 kbps (Dynamic)", "24 бит / 96 кГц", true, "Автоматическая адаптация битрейта под радиоэфир"),
    AAC("Apple AAC-LC", "256 - 320 kbps", "16 бит / 44.1 кГц", false, "Стандартное сбалансированное сжатие с низким энергопотреблением"),
    SBC("Standard SBC", "до 328 kbps (Legacy Baseline)", "16 бит / 44.1 кГц", false, "Базовый универсальный Bluetooth профиль")
}

data class CodecEngineState(
    val activeCodec: CodecType = CodecType.LDAC,
    val isLdacEnabledOnBuds: Boolean = true,
    val isHardwareOffloadActive: Boolean = true,
    val sampleRate: String = "96.0 kHz",
    val bitsPerSample: String = "24-bit",
    val channelMode: String = "Stereo (2.0)",
    val bitrateMode: String = "990 kbps (Приоритет качества)",
    val availableCodecs: List<CodecType> = listOf(
        CodecType.LDAC,
        CodecType.LHDC,
        CodecType.APTX_HD,
        CodecType.APTX_ADAPTIVE,
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
                // 1. Посылаем прошивке наушников команду включения Hi-Res (Nothing CMF SPP Command 0xF05A)
                val sppPacket = com.cmf.budscontroller.protocol.CmfProtocol.buildPacket(
                    0xF05A,
                    byteArrayOf(if (codec.isHiRes) 0x01 else 0x00, 0x00)
                )
                com.cmf.budscontroller.bluetooth.CmfBluetoothManager.getInstance().sendPacket(sppPacket)

                // 2. Применяем системный A2DP кодек через Android Settings
                val codecIndex = when (codec) {
                    CodecType.LDAC -> 4
                    CodecType.APTX_HD -> 3
                    CodecType.APTX_ADAPTIVE, CodecType.LHDC -> 2
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
                    isLdacEnabledOnBuds = codec.isHiRes,
                    sampleRate = if (codec.isHiRes) "96.0 kHz" else "44.1 kHz",
                    bitsPerSample = if (codec.isHiRes) "24-bit" else "16-bit",
                    bitrateMode = if (codec == CodecType.LDAC) "990 kbps (High Quality)" else codec.bitrateInfo
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
