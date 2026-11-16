package com.cmf.budscontroller.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class CmfMediaNotificationListenerService : NotificationListenerService() {
    private val forwarded = linkedMapOf<String,String>()

    private val TAG = "CmfMediaListener"

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification Listener connected!")
        CmfMediaTracker.getInstance(applicationContext).checkAndInitSessions()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification Listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        // Refresh active media sessions when media notifications update
        CmfMediaTracker.getInstance(applicationContext).checkAndInitSessions()
        forwardToWatch(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        CmfMediaTracker.getInstance(applicationContext).checkAndInitSessions()
        sbn?.key?.let { forwarded.remove(it) }
    }

    private fun forwardToWatch(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        val prefs = getSharedPreferences("watch_notifications",MODE_PRIVATE)
        if (!prefs.getBoolean("enabled",false) || sbn.packageName !in prefs.getStringSet("packages",emptySet()).orEmpty()) return
        val notification = sbn.notification
        if (sbn.isOngoing || notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0 || notification.visibility == android.app.Notification.VISIBILITY_SECRET) return
        val keyguard = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (keyguard.isKeyguardLocked) return
        val title = notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty().take(256)
        val body = (notification.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)
            ?: notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT))?.toString().orEmpty().take(2048)
        if (title.isBlank() && body.isBlank()) return
        val fingerprint = "$title\u0000$body"
        if (forwarded[sbn.key] == fingerprint) return
        val send = com.cmf.budscontroller.watch.WatchNotificationBridge.send ?: return
        forwarded[sbn.key] = fingerprint
        if (forwarded.size > 100) forwarded.remove(forwarded.keys.first())
        send(sbn.packageName,title,body)
    }
}
