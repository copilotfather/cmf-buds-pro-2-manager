package com.cmf.budscontroller.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class CmfMediaNotificationListenerService : NotificationListenerService() {

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
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        CmfMediaTracker.getInstance(applicationContext).checkAndInitSessions()
    }
}
