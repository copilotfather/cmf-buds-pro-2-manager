package com.cmf.budscontroller.tools

import android.content.Context
import android.media.AudioManager
import com.cmf.budscontroller.bluetooth.CmfBluetoothManager
import com.cmf.budscontroller.media.CmfMediaTracker
import com.cmf.budscontroller.protocol.AncMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DialRotateAction(val title: String) {
    VOLUME("Громкость (Шаг ±1)"),
    ANC_SHIFTER("Режимы ANC (Шумодав / Прозрачность)"),
    ULTRA_BASS("Уровень Ultra Bass (0 .. 5)"),
    TRACK_SKIP("Перемотка треков (Вперед / Назад)")
}

enum class DialClickAction(val title: String) {
    PLAY_PAUSE("Плей / Пауза трека"),
    TOGGLE_GAME_MODE("Игровой режим (Low Latency)"),
    TOGGLE_MUTE("Заглушить звук"),
    ASSISTANT("Голосовой ассистент")
}

data class SmartDialConfig(
    val rotateAction: DialRotateAction = DialRotateAction.VOLUME,
    val clickAction: DialClickAction = DialClickAction.PLAY_PAUSE,
    val dialHapticFeedback: Boolean = true
)

class SmartDialManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager = CmfBluetoothManager.getInstance()
    private val mediaTracker = CmfMediaTracker.getInstance(context)

    private val _config = MutableStateFlow(SmartDialConfig())
    val config: StateFlow<SmartDialConfig> = _config.asStateFlow()

    suspend fun handleRotateStep(isClockwise: Boolean) {
        when (_config.value.rotateAction) {
            DialRotateAction.VOLUME -> {
                mediaTracker.adjustVolume(if (isClockwise) 1 else -1)
            }
            DialRotateAction.ANC_SHIFTER -> {
                val nextMode = if (isClockwise) AncMode.NOISE_CANCELLATION else AncMode.TRANSPARENCY
                bluetoothManager.setAnc(nextMode)
            }
            DialRotateAction.ULTRA_BASS -> {
                val cur = bluetoothManager.deviceStatus.value.ultraBassLevel
                val next = if (isClockwise) (cur + 1).coerceAtMost(5) else (cur - 1).coerceAtLeast(0)
                bluetoothManager.setUltraBass(next)
            }
            DialRotateAction.TRACK_SKIP -> {
                if (isClockwise) {
                    mediaTracker.skipNext()
                } else {
                    mediaTracker.skipPrevious()
                }
            }
        }
    }

    suspend fun handleClick() {
        when (_config.value.clickAction) {
            DialClickAction.PLAY_PAUSE -> {
                mediaTracker.togglePlayPause()
            }
            DialClickAction.TOGGLE_GAME_MODE -> {
                val cur = bluetoothManager.deviceStatus.value.isGameModeEnabled
                bluetoothManager.setGameMode(!cur)
            }
            DialClickAction.TOGGLE_MUTE -> {
                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (curVol > 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                } else {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                }
            }
            DialClickAction.ASSISTANT -> {}
        }
    }

    fun setRotateAction(action: DialRotateAction) {
        _config.value = _config.value.copy(rotateAction = action)
    }

    fun setClickAction(action: DialClickAction) {
        _config.value = _config.value.copy(clickAction = action)
    }
}
