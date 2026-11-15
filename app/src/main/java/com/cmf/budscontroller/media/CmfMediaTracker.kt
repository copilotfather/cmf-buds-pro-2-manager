package com.cmf.budscontroller.media

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class CmfMediaState(
    val title: String = "Нет активного трека",
    val artist: String = "Подключите плеер",
    val album: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val appName: String = "",
    val hasListenerPermission: Boolean = false,
    val volumePercent: Int = 50,
    val albumArt: Bitmap? = null
)

class CmfMediaTracker private constructor(private val context: Context) {

    private val TAG = "CmfMediaTracker"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _mediaState = MutableStateFlow(CmfMediaState())
    val mediaState: StateFlow<CmfMediaState> = _mediaState.asStateFlow()

    private var activeController: MediaController? = null
    private var sessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateFromController(activeController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateFromController(activeController)
        }

        override fun onSessionDestroyed() {
            activeController = null
            findActiveSession()
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action.contains("metachanged") || action.contains("playstatechanged")) {
                val track = intent.getStringExtra("track") ?: intent.getStringExtra("title")
                val artist = intent.getStringExtra("artist")
                val isPlaying = intent.getBooleanExtra("playing", false) || audioManager.isMusicActive

                if (!track.isNullOrEmpty()) {
                    _mediaState.value = _mediaState.value.copy(
                        title = track,
                        artist = artist ?: "Неизвестный исполнитель",
                        isPlaying = isPlaying
                    )
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: CmfMediaTracker? = null

        fun getInstance(context: Context): CmfMediaTracker {
            return instance ?: synchronized(this) {
                instance ?: CmfMediaTracker(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        registerBroadcastReceiver()
        startPeriodicSync()
        checkAndInitSessions()
    }

    fun isNotificationAccessGranted(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledPackages.contains(context.packageName)
    }

    fun openNotificationAccessSettings(ctx: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(ctx, CmfMediaNotificationListenerService::class.java).flattenToString()
                    )
                }
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(fallbackIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot open settings", e)
            }
        }
    }

    fun checkAndInitSessions() {
        val granted = isNotificationAccessGranted()
        _mediaState.value = _mediaState.value.copy(
            hasListenerPermission = granted,
            volumePercent = getCurrentVolumePercent()
        )

        if (granted) {
            try {
                val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val component = ComponentName(context, CmfMediaNotificationListenerService::class.java)

                sessionListener?.let { mediaSessionManager.removeOnActiveSessionsChangedListener(it) }

                sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                    handleActiveSessions(controllers)
                }

                mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener!!, component)
                handleActiveSessions(mediaSessionManager.getActiveSessions(component))
            } catch (e: SecurityException) {
                Log.w(TAG, "MediaSessionManager access waiting for permission", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MediaSessionManager", e)
            }
        }
    }

    private fun handleActiveSessions(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = null
            _mediaState.value = _mediaState.value.copy(
                isPlaying = audioManager.isMusicActive
            )
            return
        }

        // Prefer currently playing session
        val playingController = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.first()

        if (activeController != playingController) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = playingController
            playingController.registerCallback(controllerCallback)
        }

        updateFromController(playingController)
    }

    private fun findActiveSession() {
        if (!isNotificationAccessGranted()) return
        try {
            val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(context, CmfMediaNotificationListenerService::class.java)
            handleActiveSessions(mediaSessionManager.getActiveSessions(component))
        } catch (_: Exception) {}
    }

    private fun updateFromController(controller: MediaController?) {
        val state = controller?.playbackState
        val meta = controller?.metadata
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING || audioManager.isMusicActive

        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: if (isPlaying) "Воспроизведение звука" else "Нет активного трека"

        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: if (isPlaying) "Аудиопоток CMF" else "Плеер на паузе"

        val album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = state?.position ?: 0L

        val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        var appName = ""
        controller?.packageName?.let { pkg ->
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(pkg, 0)
                appName = pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                appName = pkg.substringAfterLast('.')
            }
        }

        _mediaState.value = _mediaState.value.copy(
            title = title,
            artist = artist,
            album = album,
            isPlaying = isPlaying,
            durationMs = if (duration > 0) duration else 0L,
            positionMs = if (position > 0) position else 0L,
            appName = appName.uppercase(),
            hasListenerPermission = isNotificationAccessGranted(),
            albumArt = art,
            volumePercent = getCurrentVolumePercent()
        )
    }

    private fun startPeriodicSync() {
        scope.launch {
            while (isActive) {
                delay(1000)
                val granted = isNotificationAccessGranted()
                val isMusicOn = audioManager.isMusicActive
                val vol = getCurrentVolumePercent()

                if (activeController != null) {
                    val pState = activeController?.playbackState
                    val pos = pState?.position ?: 0L
                    _mediaState.value = _mediaState.value.copy(
                        isPlaying = pState?.state == PlaybackState.STATE_PLAYING || isMusicOn,
                        positionMs = pos,
                        hasListenerPermission = granted,
                        volumePercent = vol
                    )
                } else {
                    _mediaState.value = _mediaState.value.copy(
                        isPlaying = isMusicOn,
                        hasListenerPermission = granted,
                        volumePercent = vol
                    )
                    if (granted) {
                        findActiveSession()
                    }
                }
            }
        }
    }

    // Media Controls
    fun togglePlayPause() {
        val ctrl = activeController
        if (ctrl != null) {
            val isPlaying = ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
            if (isPlaying) {
                ctrl.transportControls.pause()
            } else {
                ctrl.transportControls.play()
            }
        } else {
            // Universal Fallback via Media Key Event
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun play() {
        val ctrl = activeController
        if (ctrl != null) {
            ctrl.transportControls.play()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    fun pause() {
        val ctrl = activeController
        if (ctrl != null) {
            ctrl.transportControls.pause()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        }
    }

    fun skipNext() {
        val ctrl = activeController
        if (ctrl != null) {
            ctrl.transportControls.skipToNext()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    fun skipPrevious() {
        val ctrl = activeController
        if (ctrl != null) {
            ctrl.transportControls.skipToPrevious()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    fun seekTo(positionMs: Long) {
        activeController?.transportControls?.seekTo(positionMs)
    }

    fun setVolumePercent(percent: Int) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((percent.coerceIn(0, 100) / 100f) * maxVol).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        _mediaState.value = _mediaState.value.copy(volumePercent = getCurrentVolumePercent())
    }

    fun adjustVolume(delta: Int) {
        val dir = if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
        _mediaState.value = _mediaState.value.copy(volumePercent = getCurrentVolumePercent())
    }

    private fun getCurrentVolumePercent(): Int {
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return ((cur.toFloat() / max.toFloat()) * 100).toInt()
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)
    }

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction("com.android.music.metachanged")
            addAction("com.android.music.playstatechanged")
            addAction("com.spotify.music.metadatachanged")
            addAction("com.spotify.music.playbackstatechanged")
            addAction("com.sec.android.app.music.metachanged")
            addAction("fm.last.android.metachanged")
            addAction("com.amazon.mp3.metachanged")
            addAction("com.miui.player.metachanged")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(broadcastReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot register broadcast receiver", e)
        }
    }
}
