package com.cmf.budscontroller.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Random
import kotlin.math.*

/**
 * ADVANCED AUDIO LAB
 * Kotlin-порт технологий из CMF_Buds_Pro_2_Audio_Tech:
 *  - APE: 8-полосный параметрический EQ с пресетами и маппингом на 5-полосный девайс-EQ
 *  - SAC: умная калибровка ANC (утечка, объём канала, автотюн по фидбеку)
 *  - IWNM: иммерсивный микшер шума (7 слоёв, fade, sleep-таймер)
 *  - SLG: безопасное прослушивание (доза, риск, лимит громкости)
 *  - IBP: прогноз батареи (часы/минуты)
 *  - ULLE: пресеты задержки (gaming/audio/movie)
 *  - ASA: режимы пространственного звука
 */
enum class LabNoiseLayer(val title: String) {
    RAIN("Дождь"), WIND("Ветер"), FIRE("Огонь"),
    OCEAN("Океан"), BIRDS("Птицы"), CITY("Город"), FOREST("Лес")
}

enum class LabLatencyPreset(val title: String, val subtitle: String) {
    ULTRA("ULTRA 10MS", "Минимальная задержка для игр"),
    BALANCED("BALANCED 25MS", "Баланс звука и задержки"),
    QUALITY("QUALITY 50MS", "Максимальная стабильность")
}

enum class LabRiskLevel(val title: String) {
    OK("НОРМА"), WARNING("ВНИМАНИЕ"), DANGER("ОПАСНО")
}

data class LabAncCalibration(
    val isCalibrated: Boolean = false,
    val leakageFactor: Float = 0.15f,       // 0 = герметично, 1 = открыто
    val canalVolume: Float = 1.0f,          // компенсация объёма канала
    val demandOffset: Float = 0f,           // автотюн по фидбеку
    val confidence: Float = 0.5f,
    val recommendedStrength: Int = 50       // 0..100
)

data class LabBand(val freq: Int, val gainDb: Float = 0f, val isShelf: Boolean = false)

data class AdvancedAudioLabState(
    // APE
    val parametricBands: List<LabBand> = listOf(
        LabBand(63), LabBand(160), LabBand(400), LabBand(1000),
        LabBand(2500), LabBand(6000), LabBand(10000, isShelf = true),
        LabBand(14000, isShelf = true)
    ),
    val activeLabPreset: String = "FLAT",
    val labPresets: List<Pair<String, List<Float>>> = listOf(
        "FLAT" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "BASSHEAD" to listOf(8f, 6f, 3f, 0f, -1f, 1f, 3f, 4f),
        "VOCAL" to listOf(-3f, -1f, 2f, 5f, 4f, 2f, 1f, 0f),
        "CINEMA" to listOf(4f, 2f, 0f, 1f, 3f, 4f, 5f, 3f),
        "NIGHT" to listOf(-2f, -1f, 0f, 1f, -1f, -4f, -6f, -8f)
    ),
    // SAC
    val ancCalibration: LabAncCalibration = LabAncCalibration(),
    // IWNM
    val isMixerActive: Boolean = false,
    val layerEnabled: Map<LabNoiseLayer, Boolean> = LabNoiseLayer.entries.associateWith { it == LabNoiseLayer.RAIN },
    val layerVolume: Map<LabNoiseLayer, Float> = LabNoiseLayer.entries.associateWith { 0.6f },
    val mixerMasterVolume: Float = 0.8f,
    val mixerMinutesRemaining: Int = 0,
    val mixerSelectedMinutes: Int = 45,
    // SLG
    val dailyDosePercent: Int = 0,
    val riskLevel: LabRiskLevel = LabRiskLevel.OK,
    val recommendedVolumeCap: Int = 100,
    // IBP
    val batteryHoursLeft: Float = 0f,
    val batteryTimeFormatted: String = "--",
    // ULLE
    val latencyPreset: LabLatencyPreset = LabLatencyPreset.BALANCED,
    // ASA
    val spatialEnabled: Boolean = false
)

class AdvancedAudioLab(
    private val context: Context,
    private val equalizerManager: EqualizerManager
) {
    private val TAG = "AdvancedAudioLab"
    private val _state = MutableStateFlow(AdvancedAudioLabState())
    val state: StateFlow<AdvancedAudioLabState> = _state.asStateFlow()

    // ---------------- APE: 8-band parametric EQ ----------------
    fun setParametricBand(index: Int, gainDb: Float) {
        val bands = _state.value.parametricBands.toMutableList()
        if (index !in bands.indices) return
        bands[index] = bands[index].copy(gainDb = gainDb.coerceIn(-12f, 12f))
        // Схлопываем 8 полос в 5 девайсных (60/230/910/3600/14000 Гц) по лог-среднему соседних
        val five = foldEightToFive(bands.map { it.gainDb })
        _state.value = _state.value.copy(parametricBands = bands, activeLabPreset = "КАСТОМ")
        applyToDevice(five)
    }

    fun applyLabPreset(name: String) {
        val preset = _state.value.labPresets.find { it.first == name } ?: return
        val bands = _state.value.parametricBands.mapIndexed { i, b -> b.copy(gainDb = preset.second.getOrElse(i) { 0f }) }
        _state.value = _state.value.copy(parametricBands = bands, activeLabPreset = name)
        applyToDevice(foldEightToFive(preset.second))
    }

    private fun foldEightToFive(eight: List<Float>): List<Int> {
        val groups = listOf(intArrayOf(0), intArrayOf(1), intArrayOf(2, 3), intArrayOf(4, 5), intArrayOf(6, 7))
        return groups.map { idx ->
            val values: List<Float> = idx.map { eight.getOrNull(it) }.filterNotNull()
            // Взвешенное среднее с усилением роли центра группы
            val avg: Float = if (values.size == 2) values[0] * 0.45f + values[1] * 0.55f
                             else values.firstOrNull() ?: 0f
            round(avg.toDouble()).toInt().coerceIn(-10, 10)
        }
    }

    private fun applyToDevice(fiveBands: List<Int>) {
        fiveBands.forEachIndexed { i, db ->
            equalizerManager.setCustomBand(i, db)
        }
    }

    // ---------------- SAC: Smart ANC Calibration ----------------
    fun calibrateAnc(ambientDb: Float) {
        // Упрощённая модель SAC: чем выше шум — тем сильнее утечка меняет эффект.
        val s = _state.value.ancCalibration
        val leakage = s.leakageFactor.coerceIn(0f, 1f)
        val canal = (1f - leakage).coerceIn(0.3f, 1.6f)
        // Спрос: 45 дБ -> 0, 85 дБ -> 1
        val demand = ((ambientDb - 45f) / 40f).coerceIn(0f, 1f)
        val lfComp = if (leakage > 0.5f) 0.7f else 1f // при утечке режем НЧ ANC
        val strength = (demand * canal * lfComp * (1f + s.demandOffset)).coerceIn(0f, 1f) * 100f
        val confidence = (0.45f + 0.55f * (1f - abs(leakage - 0.15f))).coerceIn(0f, 1f)
        _state.value = _state.value.copy(
            ancCalibration = s.copy(
                isCalibrated = true, canalVolume = canal,
                recommendedStrength = round(strength).toInt(), confidence = confidence
            )
        )
    }

    fun setLeakageFactor(value: Float) {
        val s = _state.value.ancCalibration.copy(leakageFactor = value.coerceIn(0f, 1f))
        _state.value = _state.value.copy(ancCalibration = s)
        calibrateAnc(estimateAmbientFromStrength())
    }

    /** Фидбек пользователя: "too much" / "too little" / "perfect" */
    fun ancFeedback(kind: String) {
        val s = _state.value
        val step = 0.08f
        val newOffset = when (kind) {
            "more" -> (s.ancCalibration.demandOffset + step).coerceIn(-0.5f, 0.5f)
            "less" -> (s.ancCalibration.demandOffset - step).coerceIn(-0.5f, 0.5f)
            else -> s.ancCalibration.demandOffset * 0.5f // "perfect" — затухание
        }
        _state.value = s.copy(ancCalibration = s.ancCalibration.copy(demandOffset = newOffset))
        calibrateAnc(estimateAmbientFromStrength())
    }

    private fun estimateAmbientFromStrength(): Float {
        return 45f + (_state.value.ancCalibration.recommendedStrength / 100f) * 40f
    }

    // ---------------- IWNM: Immersive White Noise Mixer ----------------
    fun toggleNoiseLayer(layer: LabNoiseLayer, enabled: Boolean) {
        val s = _state.value
        _state.value = s.copy(layerEnabled = s.layerEnabled + (layer to enabled))
        if (s.isMixerActive) mixer.requestRestart()
    }

    fun setLayerVolume(layer: LabNoiseLayer, volume: Float) {
        val s = _state.value
        _state.value = s.copy(layerVolume = s.layerVolume + (layer to volume.coerceIn(0f, 1f)))
    }

    fun setMixerMasterVolume(volume: Float) {
        _state.value = _state.value.copy(mixerMasterVolume = volume.coerceIn(0f, 1f))
    }

    fun setMixerMinutes(minutes: Int) {
        _state.value = _state.value.copy(mixerSelectedMinutes = minutes.coerceIn(5, 480))
    }

    fun startMixer() {
        if (_state.value.isMixerActive) return
        _state.value = _state.value.copy(isMixerActive = true, mixerMinutesRemaining = _state.value.mixerSelectedMinutes)
        mixer.start()
        startMixerTicker()
    }

    fun stopMixer() {
        _state.value = _state.value.copy(isMixerActive = false, mixerMinutesRemaining = 0)
        mixer.stop()
        tickerJob?.cancel()
    }

    private var tickerJob: Job? = null
    private fun startMixerTicker() {
        tickerJob?.cancel()
        tickerJob = GlobalScope.launch(Dispatchers.Default) {
            while (isActive && _state.value.isMixerActive) {
                delay(60_000)
                val remaining = _state.value.mixerMinutesRemaining - 1
                if (remaining <= 0) {
                    // Плавное угасание перед остановкой
                    mixer.fadeOutAndStop()
                    _state.value = _state.value.copy(isMixerActive = false, mixerMinutesRemaining = 0)
                    break
                }
                _state.value = _state.value.copy(mixerMinutesRemaining = remaining)
            }
        }
    }

    private val mixer = NoiseMixerEngine()

    // ---------------- SLG: Safe Listening Guardian ----------------
    fun updateSafeListening(dailyMinutes: Int, dosePercent: Int, currentVolumePercent: Int) {
        val level = when {
            dosePercent >= 100 -> LabRiskLevel.DANGER
            dosePercent >= 50 -> LabRiskLevel.WARNING
            else -> LabRiskLevel.OK
        }
        // Рекомендуемый лимит громкости: плавное снижение, никогда не повышение
        val cap = when (level) {
            LabRiskLevel.DANGER -> (currentVolumePercent * 0.6f).toInt().coerceAtLeast(30)
            LabRiskLevel.WARNING -> (currentVolumePercent * 0.8f).toInt().coerceAtLeast(40)
            LabRiskLevel.OK -> 100
        }
        _state.value = _state.value.copy(
            dailyDosePercent = dosePercent.coerceIn(0, 999),
            riskLevel = level,
            recommendedVolumeCap = cap,
            // IBP: лёгкая коррекция прогноза от дозы не требуется — берём из BatteryAnalyticsManager
            batteryHoursLeft = _state.value.batteryHoursLeft
        )
    }

    // ---------------- IBP: Battery Prediction ----------------
    fun updateBatteryPrediction(hoursLeft: Float) {
        val h = floor(hoursLeft).toInt().coerceAtLeast(0)
        val m = ((hoursLeft - h) * 60).roundToInt().coerceIn(0, 59)
        _state.value = _state.value.copy(
            batteryHoursLeft = hoursLeft,
            batteryTimeFormatted = String.format("%dh %02dm", h, m)
        )
    }

    // ---------------- ULLE: Latency preset ----------------
    fun setLatencyPreset(preset: LabLatencyPreset) {
        _state.value = _state.value.copy(latencyPreset = preset)
    }

    // ---------------- ASA: Spatial passthrough ----------------
    fun setSpatialEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(spatialEnabled = enabled)
    }

    fun release() {
        mixer.stop()
        tickerJob?.cancel()
    }

    // ==================================================================
    // NoiseMixerEngine: 7 слоёв, плавные фейды, AudioTrack 44.1kHz mono
    // ==================================================================
    private inner class NoiseMixerEngine {
        private var track: AudioTrack? = null
        private var job: Job? = null
        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        @Volatile private var masterGain = 0.8f
        @Volatile private var targetMaster = 0.8f
        @Volatile private var running = false
        @Volatile private var restartRequested = false

        private val rng = Random()
        // Состояние фильтров/генераторов (персистентно между чанками — бесшовность)
        private val brownState = HashMap<LabNoiseLayer, Float>()
        private val pinkHistory = HashMap<LabNoiseLayer, FloatArray>()
        private val lfoPhase = HashMap<LabNoiseLayer, Float>()
        private var chirpPhase = 0f
        private var chirpCountdown = 0
        private var crackleCountdown = 0

        fun start() {
            if (running) return
            running = true
            restartRequested = false
            masterGain = 0f; targetMaster = _state.value.mixerMasterVolume
            job = scope.launch {
                try {
                    val sampleRate = 44100
                    val minBuf = AudioTrack.getMinBufferSize(
                        sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                    )
                    track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(maxOf(minBuf, sampleRate))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                    track?.play()
                    val chunk = 4410 // 100 ms
                    val buf = ShortArray(chunk)
                    val f = FloatArray(chunk)
                    while (isActive && running) {
                        if (restartRequested) { restartRequested = false; masterGain = 0f; targetMaster = _state.value.mixerMasterVolume }
                        renderChunk(f, sampleRate)
                        targetMaster = _state.value.mixerMasterVolume
                        masterGain += (targetMaster - masterGain) * 0.02f // плавный fade
                        for (i in f.indices) buf[i] = (f[i] * masterGain * 32000f).toInt().coerceIn(-32000, 32000).toShort()
                        track?.write(buf, 0, buf.size)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "mixer error", e)
                } finally {
                    stopInternal()
                }
            }
        }

        fun requestRestart() { if (running) restartRequested = true }

        fun fadeOutAndStop() {
            targetMaster = 0f
            scope.launch {
                delay(2500)
                stop()
            }
        }

        fun stop() {
            running = false
            job?.cancel()
            stopInternal()
        }

        private fun stopInternal() {
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
            track = null
        }

        private fun renderChunk(out: FloatArray, sr: Int) {
            out.fill(0f)
            val s = _state.value
            if (!s.isMixerActive) return
            for (layer in LabNoiseLayer.entries) {
                if (s.layerEnabled[layer] != true) continue
                val vol = s.layerVolume[layer] ?: 0.6f
                val g = vol * 0.25f // суммарный запас против клиппинга
                when (layer) {
                    LabNoiseLayer.RAIN -> addPink(out, layer, g * 0.9f, sr)
                    LabNoiseLayer.WIND -> addWind(out, layer, g * 1.1f, sr)
                    LabNoiseLayer.FIRE -> addFire(out, layer, g, sr)
                    LabNoiseLayer.OCEAN -> addOcean(out, layer, g * 1.2f, sr)
                    LabNoiseLayer.BIRDS -> addBirds(out, layer, g, sr)
                    LabNoiseLayer.CITY -> addCity(out, layer, g * 1.1f, sr)
                    LabNoiseLayer.FOREST -> addPink(out, layer, g * 0.7f, sr)
                }
            }
        }

        private fun addBrown(out: FloatArray, layer: LabNoiseLayer, gain: Float, leak: Float = 0.02f) {
            var b = brownState[layer] ?: 0f
            for (i in out.indices) {
                b += (rng.nextGaussian().toFloat()) * 0.12f
                b *= (1f - leak)
                out[i] += b * gain * 3f
            }
            brownState[layer] = b.coerceIn(-6f, 6f)
        }

        private fun addPink(out: FloatArray, layer: LabNoiseLayer, gain: Float, sr: Int) {
            var hist = pinkHistory[layer] ?: FloatArray(4)
            if (hist.size != 4) hist = FloatArray(4)
            for (i in out.indices) {
                val w = rng.nextGaussian().toFloat()
                // Дешёвый Paul Kellet pink filter
                hist[0] = 0.99765f * hist[0] + w * 0.0990460f
                hist[1] = 0.96300f * hist[1] + w * 0.2965164f
                hist[2] = 0.57000f * hist[2] + w * 1.0526913f
                out[i] += (hist[0] + hist[1] + hist[2] + w * 0.1848f) * gain * 0.35f
            }
            pinkHistory[layer] = hist
        }

        private fun addWind(out: FloatArray, layer: LabNoiseLayer, gain: Float, sr: Int) {
            var phase = lfoPhase[layer] ?: 0f
            var b = brownState[layer] ?: 0f
            for (i in out.indices) {
                phase += 2f * PI.toFloat() / (sr * 7f) // 7-секундный цикл порывов
                if (phase > 2f * PI) phase -= 2f * PI.toFloat()
                val gust = 0.55f + 0.45f * sin(phase)
                b += rng.nextGaussian().toFloat() * 0.1f
                b *= 0.985f
                out[i] += b * gain * 3f * gust
            }
            lfoPhase[layer] = phase
            brownState[layer] = b.coerceIn(-6f, 6f)
        }

        private fun addFire(out: FloatArray, layer: LabNoiseLayer, gain: Float, sr: Int) {
            var b = brownState[layer] ?: 0f
            for (i in out.indices) {
                b += rng.nextGaussian().toFloat() * 0.08f
                b *= 0.97f
                var sample = b * gain * 2.4f
                if (crackleCountdown <= 0) {
                    crackleCountdown = sr / 4 + rng.nextInt(sr) // треск 0.25..1.25 c
                }
                crackleCountdown--
                if (crackleCountdown < 400) {
                    val burst = (rng.nextGaussian().toFloat()) * gain * 2f * (1f - crackleCountdown / 400f)
                    sample += burst
                }
                out[i] += sample
            }
            brownState[layer] = b.coerceIn(-6f, 6f)
        }

        private fun addOcean(out: FloatArray, layer: LabNoiseLayer, gain: Float, sr: Int) {
            var phase = lfoPhase[layer] ?: 0f
            var b = brownState[layer] ?: 0f
            for (i in out.indices) {
                phase += 2f * PI.toFloat() / (sr * 11f) // волна каждые ~11 c
                if (phase > 2f * PI) phase -= 2f * PI.toFloat()
                val swell = 0.35f + 0.65f * max(0f, sin(phase))
                b += rng.nextGaussian().toFloat() * 0.1f
                b *= 0.99f
                out[i] += b * gain * 3f * swell
            }
            lfoPhase[layer] = phase
            brownState[layer] = b.coerceIn(-6f, 6f)
        }

        private fun addBirds(out: FloatArray, layer: LabNoiseLayer, gain: Float, sr: Int) {
            for (i in out.indices) {
                var sample = 0f
                if (chirpCountdown <= 0) {
                    if (rng.nextInt(400) == 0) chirpCountdown = sr / 6 // чирик ~166 мс
                    else chirpCountdown = 1
                }
                chirpCountdown--
                if (chirpCountdown > 0 && rng.nextInt(20) == 0) {
                    val sweepFreq = 1800f + rng.nextFloat() * 2400f
                    chirpPhase += 2f * PI.toFloat() * sweepFreq / sr
                    if (chirpPhase > 2f * PI) chirpPhase -= 2f * PI.toFloat()
                    sample = sin(chirpPhase) * gain * 2.5f
                } else {
                    chirpPhase *= 0.995f
                }
                out[i] += sample
            }
        }

        private fun addCity(out: FloatArray, layer: LabNoiseLayer, gain: Float, sr: Int) {
            var b = brownState[layer] ?: 0f
            var hum = lfoPhase[layer] ?: 0f
            for (i in out.indices) {
                b += rng.nextGaussian().toFloat() * 0.07f
                b *= 0.99f
                hum += 2f * PI.toFloat() * 110f / sr // сетевой гул 110 Гц
                if (hum > 2f * PI) hum -= 2f * PI.toFloat()
                val horn = if (rng.nextInt(sr * 8) == 0) 0.6f else 0f // редкие гудки
                out[i] += (b * 2.2f + sin(hum) * 0.5f + horn * rng.nextGaussian().toFloat()) * gain
            }
            brownState[layer] = b.coerceIn(-6f, 6f)
            lfoPhase[layer] = hum
        }
    }
}
