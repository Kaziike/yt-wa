package com.brave.youtubelite.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.brave.youtubelite.MainActivity
import com.brave.youtubelite.R

/**
 * Foreground Service chuẩn Android Media Control:
 * - Tích hợp MediaSessionCompat để hỗ trợ điều khiển từ màn hình khóa, tai nghe Bluetooth, Android Auto.
 * - Hiển thị Notification chuẩn MediaStyle với các nút Prev, Play/Pause, Next.
 * - Duy trì CPU WakeLock khi đang phát để âm thanh không bị ngắt khi tắt màn hình.
 */
class MediaService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_STOP = "ACTION_STOP"

        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_CHANNEL = "EXTRA_CHANNEL"
        const val EXTRA_IS_PLAYING = "EXTRA_IS_PLAYING"
        const val EXTRA_POSITION_MS = "EXTRA_POSITION_MS"
        const val EXTRA_DURATION_MS = "EXTRA_DURATION_MS"

        const val ACTION_MEDIA_PLAY = "com.brave.youtubelite.MEDIA_PLAY"
        const val ACTION_MEDIA_PAUSE = "com.brave.youtubelite.MEDIA_PAUSE"
        const val ACTION_MEDIA_NEXT = "com.brave.youtubelite.MEDIA_NEXT"
        const val ACTION_MEDIA_PREV = "com.brave.youtubelite.MEDIA_PREV"
        const val ACTION_MEDIA_SEEK = "com.brave.youtubelite.MEDIA_SEEK"
        const val EXTRA_SEEK_POS_MS = "EXTRA_SEEK_POS_MS"

        private const val CHANNEL_ID = "youtube_playback_channel"
        private const val NOTIFICATION_ID = 2024
        private const val REQUEST_CODE_PREV = 2001
        private const val REQUEST_CODE_PLAY_PAUSE = 2002
        private const val REQUEST_CODE_NEXT = 2003
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForeground = false
    private var isSessionReleased = false

    private var currentTitle = "LiteTube"
    private var currentChannel = "Đang phát âm thanh trong nền"
    private var isPlaying = true
    private var positionMs = 0L
    private var durationMs = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LiteTube:PlaybackWakeLock"
        )

        mediaSession = MediaSessionCompat(this, "LiteTubeMediaSession").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    sendBroadcast(Intent(ACTION_MEDIA_PLAY).setPackage(packageName))
                    isPlaying = true
                    updatePlaybackState()
                    updateNotification()
                    acquireWakeLock()
                }

                override fun onPause() {
                    sendBroadcast(Intent(ACTION_MEDIA_PAUSE).setPackage(packageName))
                    isPlaying = false
                    updatePlaybackState()
                    updateNotification()
                    releaseWakeLock()
                }

                override fun onSkipToNext() {
                    sendBroadcast(Intent(ACTION_MEDIA_NEXT).setPackage(packageName))
                }

                override fun onSkipToPrevious() {
                    sendBroadcast(Intent(ACTION_MEDIA_PREV).setPackage(packageName))
                }

                override fun onSeekTo(pos: Long) {
                    sendBroadcast(Intent(ACTION_MEDIA_SEEK).apply {
                        putExtra(EXTRA_SEEK_POS_MS, pos)
                        setPackage(packageName)
                    })
                    positionMs = pos
                    updatePlaybackState()
                }

                override fun onStop() {
                    sendBroadcast(Intent(ACTION_MEDIA_PAUSE).setPackage(packageName))
                    stopPlayback()
                }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPlayback()
            }
            ACTION_START, ACTION_UPDATE -> {
                handlePlaybackIntent(intent)
            }
            ACTION_MEDIA_PLAY -> {
                isPlaying = true
                acquireWakeLock()
                updatePlaybackState()
                updateNotification()
            }
            ACTION_MEDIA_PAUSE -> {
                isPlaying = false
                releaseWakeLock()
                updatePlaybackState()
                updateNotification()
            }
        }
        return START_NOT_STICKY
    }

    private fun handlePlaybackIntent(intent: Intent) {
        intent.getStringExtra(EXTRA_TITLE)?.let {
            if (it.isNotEmpty()) currentTitle = it
        }
        intent.getStringExtra(EXTRA_CHANNEL)?.let {
            if (it.isNotEmpty()) currentChannel = it
        }
        if (intent.hasExtra(EXTRA_IS_PLAYING)) {
            isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying)
        } else if (intent.action == ACTION_START) {
            isPlaying = true
        }
        if (intent.hasExtra(EXTRA_POSITION_MS)) {
            positionMs = intent.getLongExtra(EXTRA_POSITION_MS, positionMs)
        }
        if (intent.hasExtra(EXTRA_DURATION_MS)) {
            durationMs = intent.getLongExtra(EXTRA_DURATION_MS, durationMs)
        }

        if (isPlaying) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }

        updateMetadata()
        updatePlaybackState()
        updateNotification()
    }

    private fun updateMetadata() {
        if (isSessionReleased) return
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentChannel.ifEmpty { "LiteTube" })
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, currentChannel.ifEmpty { "LiteTube" })
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, if (durationMs > 0) durationMs else -1L)
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun updatePlaybackState() {
        if (isSessionReleased) return
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackSpeed = if (isPlaying) 1.0f else 0.0f

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, positionMs, playbackSpeed)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 1) Previous
        val prevIntent = Intent(ACTION_MEDIA_PREV).setPackage(packageName)
        val prevPendingIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_PREV,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_previous,
            "Trước",
            prevPendingIntent
        ).build()

        // 2) Play / Pause
        val playPauseIntent = Intent(if (isPlaying) ACTION_MEDIA_PAUSE else ACTION_MEDIA_PLAY).setPackage(packageName)
        val playPausePendingIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_PLAY_PAUSE,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseAction = NotificationCompat.Action.Builder(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Tạm dừng" else "Phát",
            playPausePendingIntent
        ).build()

        // 3) Next
        val nextIntent = Intent(ACTION_MEDIA_NEXT).setPackage(packageName)
        val nextPendingIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_NEXT,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_next,
            "Tiếp theo",
            nextPendingIntent
        ).build()

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(currentTitle)
            .setContentText(currentChannel.ifEmpty { "LiteTube" })
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(style)
            .build()
    }

    private fun updateNotification() {
        try {
            val notification = buildNotification()
            if (!isForeground) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForeground = true
            } else {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(2 * 60 * 60 * 1000L) // Giữ tối đa 2 giờ phát liên tục
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseMediaSession() {
        if (!isSessionReleased) {
            try {
                if (::mediaSession.isInitialized) {
                    mediaSession.isActive = false
                    mediaSession.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isSessionReleased = true
        }
    }

    private fun stopPlayback() {
        try {
            releaseWakeLock()
            if (isForeground) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                isForeground = false
            }
            releaseMediaSession()
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "Phát nhạc nền LiteTube",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Duy trì âm thanh LiteTube khi khóa màn hình"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(chan)
        }
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}
