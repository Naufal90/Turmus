package com.offlinep2p.voice.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.offlinep2p.voice.OfflineP2PApp
import com.offlinep2p.voice.R

/**
 * Foreground service that keeps the P2P voice/music session alive when
 * the UI is not visible. Phase 1 provides only the notification and
 * lifecycle skeleton — the audio/network pipeline is wired in later phases.
 *
 * NOTE (per AGENTS.md §23): the service must stop cleanly and must not
 * keep running after the session ends. That is enforced here by
 * stopForeground + stopSelf on ACTION_STOP.
 */
class SessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startInForeground()
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val notification: Notification = NotificationCompat.Builder(this, OfflineP2PApp.CHANNEL_SESSION)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Session running")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // placeholder; replaced when a proper icon exists
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_STOP = "com.offlinep2p.voice.action.STOP"
        private const val NOTIFICATION_ID = 1001
    }
}
