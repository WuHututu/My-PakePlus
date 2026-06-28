package com.app.pakeplus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder
import android.widget.Toast

class MediaPlaybackService : Service() {
    companion object {
        const val ACTION_START = "com.app.pakeplus.action.MEDIA_PLAYBACK_START"
        const val ACTION_STOP = "com.app.pakeplus.action.MEDIA_PLAYBACK_STOP"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_SESSION_TOKEN = "extra_session_token"
        private const val CHANNEL_ID = "splayer_media_playback"
        private const val NOTIFICATION_ID = 1024
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPlaybackService()
                return START_NOT_STICKY
            }

            ACTION_START, null -> startPlaybackService(intent)
        }
        return START_STICKY
    }

    private fun startPlaybackService(intent: Intent?) {
        createChannel()
        val notification = buildNotification(intent)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to keep playback service alive", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun stopPlaybackService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SPlayer playback",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(intent: Intent?): Notification {
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "SPlayer" }
        val artist = intent?.getStringExtra(EXTRA_ARTIST).orEmpty().ifBlank { "Playing" }
        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_SESSION_TOKEN, MediaSession.Token::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_SESSION_TOKEN)
        }
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        val notificationBuilder = builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(openIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
        if (token != null) {
            notificationBuilder.setStyle(
                Notification.MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(0)
            )
        }
        return notificationBuilder.build()
    }
}

