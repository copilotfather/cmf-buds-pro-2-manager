package com.cmf.budscontroller.relay

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.util.Log
import com.cmf.budscontroller.i18n.AppStrings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

enum class RelayMode {
    PC_TO_EARBUDS,
    MIC_TO_PC,
    DUPLEX;

    val title: String
        get() = when (this) {
            PC_TO_EARBUDS -> AppStrings.relayModePcToBuds
            MIC_TO_PC -> AppStrings.relayModeMicToPc
            DUPLEX -> AppStrings.relayModeDuplex
        }

    val description: String
        get() = when (this) {
            PC_TO_EARBUDS -> AppStrings.relayModePcToBudsDesc
            MIC_TO_PC -> AppStrings.relayModeMicToPcDesc
            DUPLEX -> AppStrings.relayModeDuplexDesc
        }
}

enum class BufferPreset(val bufferFactor: Int, val latencyMs: Int) {
    ULTRA_LOW(1, 10),
    BALANCED(2, 25),
    STABLE(4, 50);

    val title: String
        get() = when (this) {
            ULTRA_LOW -> if (AppStrings.isRussian) "ULTRA (10мс)" else "ULTRA (10ms)"
            BALANCED -> if (AppStrings.isRussian) "БАЛАНС (25мс)" else "BALANCED (25ms)"
            STABLE -> if (AppStrings.isRussian) "СТАБИЛЬНО (50мс)" else "STABLE (50ms)"
        }
}

data class AudioRelayState(
    val isRunning: Boolean = false,
    val mode: RelayMode = RelayMode.DUPLEX,
    val clientConnected: Boolean = false,
    val latencyEstimateMs: Int = 12,
    val sampleRate: Int = 48000,
    val bufferPreset: BufferPreset = BufferPreset.ULTRA_LOW,
    val kbpsSpeed: Float = 0f,
    val volumeGain: Float = 1.0f,
    val statusMessage: String = if (AppStrings.isRussian) "Готов к подключению USB" else "Ready for USB connection"
)

class CmfAudioRelayManager(private val context: Context) {

    private val TAG = "CmfAudioRelay"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(AudioRelayState())
    val state: StateFlow<AudioRelayState> = _state.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null

    private var serverJob: Job? = null
    private var playJob: Job? = null
    private var recordJob: Job? = null

    companion object {
        const val RELAY_PORT = 4242
        const val SAMPLE_RATE = 48000
    }

    fun setMode(mode: RelayMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun setBufferPreset(preset: BufferPreset) {
        _state.value = _state.value.copy(
            bufferPreset = preset,
            latencyEstimateMs = preset.latencyMs
        )
    }

    fun setVolumeGain(gain: Float) {
        val clamped = gain.coerceIn(0.5f, 2.5f)
        _state.value = _state.value.copy(volumeGain = clamped)
        audioTrack?.setVolume(clamped)
    }

    fun startRelay() {
        if (_state.value.isRunning) return

        _state.value = _state.value.copy(
            isRunning = true,
            statusMessage = if (AppStrings.isRussian) "Ожидание подключения ПК (порт $RELAY_PORT)..." else "Waiting for PC connection (port $RELAY_PORT)..."
        )

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(RELAY_PORT).apply {
                    reuseAddress = true
                }
                Log.d(TAG, "Audio Relay Server listening on port $RELAY_PORT")

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    Log.d(TAG, "PC Client connected from ${socket.inetAddress}")
                    clientSocket = socket

                    _state.value = _state.value.copy(
                        clientConnected = true,
                        statusMessage = if (AppStrings.isRussian) "USB Мост активен: ПК ➔ Телефон ➔ Наушники" else "USB Bridge Active: PC ➔ Phone ➔ Earbuds"
                    )

                    handleClientSession(socket)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Server socket error", e)
                }
            } finally {
                stopRelay()
            }
        }
    }

    private suspend fun handleClientSession(socket: Socket) = withContext(Dispatchers.IO) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        // Init Low-Latency AudioTrack for PC audio -> CMF Earbuds
        initAudioTrack()

        // Init AudioRecord for CMF Earbuds mic -> PC
        if (_state.value.mode != RelayMode.PC_TO_EARBUDS) {
            initAudioRecord()
        }

        // Job 1: Play incoming audio from PC to Earbuds
        playJob = launch {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val chunk = ByteArray((minBuf / 2) * _state.value.bufferPreset.bufferFactor)
            var bytesTotal = 0L
            var lastTimer = System.currentTimeMillis()

            try {
                while (isActive && !socket.isClosed) {
                    val read = input.read(chunk)
                    if (read <= 0) break

                    audioTrack?.write(chunk, 0, read)
                    bytesTotal += read

                    val now = System.currentTimeMillis()
                    if (now - lastTimer >= 1000) {
                        val kbps = (bytesTotal * 8f) / (1024f * ((now - lastTimer) / 1000f))
                        _state.value = _state.value.copy(kbpsSpeed = kbps)
                        bytesTotal = 0L
                        lastTimer = now
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Playback stream ended: ${e.message}")
            }
        }

        // Job 2: Stream Earbuds Mic to PC over USB
        if (_state.value.mode != RelayMode.PC_TO_EARBUDS) {
            recordJob = launch {
                val micBuf = ByteArray(1024)
                try {
                    audioRecord?.startRecording()
                    while (isActive && !socket.isClosed) {
                        val read = audioRecord?.read(micBuf, 0, micBuf.size) ?: -1
                        if (read > 0) {
                            output.write(micBuf, 0, read)
                            output.flush()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Mic stream ended: ${e.message}")
                }
            }
        }

        playJob?.join()
        recordJob?.cancel()

        _state.value = _state.value.copy(
            clientConnected = false,
            statusMessage = if (AppStrings.isRussian) "ПК отключен" else "PC Disconnected"
        )
    }

    private fun initAudioTrack() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val bufferSize = minBuf * _state.value.bufferPreset.bufferFactor

        audioTrack?.release()
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build().apply {
                play()
            }
    }

    @SuppressLint("MissingPermission")
    private fun initAudioRecord() {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        try {
            audioRecord?.release()
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord init error", e)
        }
    }

    fun stopRelay() {
        playJob?.cancel()
        recordJob?.cancel()
        serverJob?.cancel()

        try {
            clientSocket?.close()
            serverSocket?.close()
        } catch (_: Exception) {}

        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}

        audioTrack = null
        audioRecord = null
        clientSocket = null
        serverSocket = null

        _state.value = _state.value.copy(
            isRunning = false,
            clientConnected = false,
            kbpsSpeed = 0f,
            statusMessage = if (AppStrings.isRussian) "Переадресация остановлена" else "Relay stopped"
        )
    }
}
