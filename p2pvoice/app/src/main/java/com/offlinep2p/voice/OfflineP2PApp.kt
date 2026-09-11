package com.offlinep2p.voice

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * Application entrypoint.
 * Sets up the notification channel used by the foreground SessionService
 * (Phase 3+). No networking or audio work happens here.
 */
class OfflineP2PApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createSessionNotificationChannel()
    }

    private fun createSessionNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_SESSION,
            getString(R.string.notification_channel_session),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing offline P2P voice/music session"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_SESSION = "session_channel"
    }
}
