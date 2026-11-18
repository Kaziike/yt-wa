package com.brave.youtubelite.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.brave.youtubelite.MainActivity
import com.brave.youtubelite.R
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground Service chuẩn Android Media Control:
 * - Giao tiếp nội bộ trực tiếp qua CommandListener (không phát Broadcast ra toàn hệ thống).
 * - Hiển thị Notification MediaStyle với icon đơn sắc chuẩn ic_stat_litetube và ảnh bìa thumbnail.
 * - Tự động tạm dừng khi rút tai nghe / ngắt Bluetooth (ACTION_AUDIO_BECOMING_NOISY).
 * - Đảm bảo gọi startForeground() tức thì khi nhận ACTION_START, tránh ForegroundServiceDidNotStartInTimeException.
 */
class MediaService : Service() {

    interface CommandListener {
        fun onMediaPlay()
        fun onMediaPause()
        fun onMediaNext()
        fun onMediaPrevious()
        fun onMediaSeekTo(positionMs: Long)
    }

    companion object {
        const val ACTION_START = "com.brave.youtubelite.service.START"
        const val ACTION_UPDATE = "com.brave.youtubelite.service.UPDATE"
        const val ACTION_STOP = "com.brave.youtubelite.service.STOP"

        const val ACTION_NOTIF_PLAY = "com.brave.youtubelite.service.NOTIF_PLAY"
        const val ACTION_NOTIF_PAUSE = "com.brave.youtubelite.service.NOTIF_PAUSE"
        const val ACTION_NOTIF_NEXT = "com.brave.youtubelite.service.NOTIF_NEXT"
        const val ACTION_NOTIF_PREV = "com.brave.youtubelite.service.NOTIF_PREV"

        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_CHANNEL = "EXTRA_CHANNEL"
        const val EXTRA_IS_PLAYING = "EXTRA_IS_PLAYING"
        const val EXTRA_POSITION_MS = "EXTRA_POSITION_MS"
        const val EXTRA_DURATION_MS = "EXTRA_DURATION_MS"
        const val EXTRA_VIDEO_ID = "EXTRA_VIDEO_ID"

        private const val CHANNEL_ID = "youtube_playback_channel"
        private const val NOTIFICATION_ID = 2024
        private const val REQUEST_CODE_PREV = 2001
        private const val REQUEST_CODE_PLAY_PAUSE = 2002
        private const val REQUEST_CODE_NEXT = 2003
        private const val WAKELOCK_TIMEOUT_MS = 3 * 60 * 60 * 1000L // 3 giờ, tự làm mới khi đổi bài

        private val THUMBNAIL_NAMES = listOf("maxresdefault", "hq720", "mqdefault", "hqdefault")
        private const val THUMBNAIL_MAX_WIDTH = 640

        @Volatile
        var commandListener: CommandListener? = null
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForeground = false
    private var isSessionReleased = true
    private var noisyReceiverRegistered = false

    private var currentTitle = "LiteTube"
    private var currentChannel = "Đang phát trong nền"
    private var isPlaying = true
    private var positionMs = 0L
    private var durationMs = -1L

    private var currentVideoId: String? = null
    private var artBitmap: Bitmap? = null
    private var artVideoId: String? = null
    private var loadingArtVideoId: String? = null
    private val artExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && isPlaying) {
                commandListener?.onMediaPause()
                applyLocalPlayState(false)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LiteTube:PlaybackWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
        ensureMediaSession()
    }

    private fun ensureMediaSession() {
        if (!isSessionReleased && ::mediaSession.isInitialized) return
        mediaSession = MediaSessionCompat(this, "LiteTubeMediaSession").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    commandListener?.onMediaPlay()
                    applyLocalPlayState(true)
                }

                override fun onPause() {
                    commandListener?.onMediaPause()
                    applyLocalPlayState(false)
                }

                override fun onSkipToNext() {
                    commandListener?.onMediaNext()
                }

                override fun onSkipToPrevious() {
                    commandListener?.onMediaPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    positionMs = pos
                    commandListener?.onMediaSeekTo(pos)
                    updatePlaybackState()
                }

                override fun onStop() {
                    commandListener?.onMediaPause()
                    stopPlayback()
                }
            })
        }
        isSessionReleased = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                ensureMediaSession()
                handlePlaybackIntent(intent, forceEnsureForeground = true)
            }
            ACTION_UPDATE -> {
                if (!isForeground) return START_NOT_STICKY
                ensureMediaSession()
                handlePlaybackIntent(intent, forceEnsureForeground = false)
            }
            ACTION_STOP -> {
                stopPlayback()
            }
            ACTION_NOTIF_PLAY -> {
                if (!isForeground) return START_NOT_STICKY
                commandListener?.onMediaPlay()
                applyLocalPlayState(true)
            }
            ACTION_NOTIF_PAUSE -> {
                if (!isForeground) return START_NOT_STICKY
                commandListener?.onMediaPause()
                applyLocalPlayState(false)
            }
            ACTION_NOTIF_NEXT -> {
                if (!isForeground) return START_NOT_STICKY
                commandListener?.onMediaNext()
            }
            ACTION_NOTIF_PREV -> {
                if (!isForeground) return START_NOT_STICKY
                commandListener?.onMediaPrevious()
            }
        }
        return START_NOT_STICKY
    }

    private fun applyLocalPlayState(playing: Boolean) {
        isPlaying = playing
        syncWakeLockAndNoisyReceiver()
        updatePlaybackState()
        updateNotification(forceForeground = false)
    }

    private fun handlePlaybackIntent(intent: Intent, forceEnsureForeground: Boolean) {
        intent.getStringExtra(EXTRA_TITLE)?.let {
            if (it.isNotEmpty()) currentTitle = it
        }
        intent.getStringExtra(EXTRA_CHANNEL)?.let {
            if (it.isNotEmpty()) currentChannel = it
        }
        if (intent.hasExtra(EXTRA_IS_PLAYING)) {
            isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying)
        } else if (forceEnsureForeground) {
            isPlaying = true
        }
        if (intent.hasExtra(EXTRA_POSITION_MS)) {
            positionMs = intent.getLongExtra(EXTRA_POSITION_MS, positionMs)
        }
        if (intent.hasExtra(EXTRA_DURATION_MS)) {
            durationMs = intent.getLongExtra(EXTRA_DURATION_MS, durationMs)
        }
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
        if (!videoId.isNullOrEmpty() && videoId != currentVideoId) {
            currentVideoId = videoId
            loadArtwork(videoId)
            // Làm mới thời gian giữ WakeLock mỗi khi chuyển sang video mới đang phát
            if (isPlaying) refreshWakeLock()
        }

        syncWakeLockAndNoisyReceiver()
        updateMetadata()
        updatePlaybackState()
        updateNotification(forceForeground = forceEnsureForeground)
    }

    private fun syncWakeLockAndNoisyReceiver() {
        if (isPlaying) {
            acquireWakeLock()
            registerNoisyReceiver()
        } else {
            releaseWakeLock()
            unregisterNoisyReceiver()
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        try {
            ContextCompat.registerReceiver(
                this,
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            noisyReceiverRegistered = true
        } catch (_: Exception) {}
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        try {
            unregisterReceiver(becomingNoisyReceiver)
        } catch (_: Exception) {}
        noisyReceiverRegistered = false
    }

    private fun currentArt(): Bitmap? =
        if (artBitmap != null && artVideoId != null && artVideoId == currentVideoId) artBitmap else null

    private fun loadArtwork(videoId: String) {
        if (videoId == artVideoId || videoId == loadingArtVideoId) return
        loadingArtVideoId = videoId
        artExecutor.execute {
            val bitmap = fetchThumbnail(videoId)
            mainHandler.post {
                if (loadingArtVideoId == videoId) loadingArtVideoId = null
                if (bitmap == null || isSessionReleased || videoId != currentVideoId) {
                    return@post
                }
                artBitmap = bitmap
                artVideoId = videoId
                updateMetadata()
                updateNotification(forceForeground = false)
            }
        }
    }

    private fun fetchThumbnail(videoId: String): Bitmap? {
        for (name in THUMBNAIL_NAMES) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("https://i.ytimg.com/vi/$videoId/$name.jpg").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 7000
                    instanceFollowRedirects = true
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) continue
                val decoded = conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: continue
                if (decoded.width <= 120) {
                    decoded.recycle()
                    continue
                }
                return if (decoded.width > THUMBNAIL_MAX_WIDTH) {
                    val h = (decoded.height * THUMBNAIL_MAX_WIDTH.toFloat() / decoded.width).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(decoded, THUMBNAIL_MAX_WIDTH, h, true)
                    if (scaled !== decoded) {
                        decoded.recycle()
                    }
                    scaled
                } else {
                    decoded
                }
            } catch (_: Exception) {
                // thử mức phân giải tiếp theo
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun updateMetadata() {
        if (isSessionReleased) return
        val subtitle = currentChannel.ifEmpty { "LiteTube" }
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, if (durationMs > 0) durationMs else -1L)
        currentArt()?.let { art ->
            // Chỉ gắn 1 trường ALBUM_ART để tránh nhân 3 kích thước Parcel qua Binder sang SystemUI
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
        }
        mediaSession.setMetadata(builder.build())
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
        val speed = if (isPlaying) 1.0f else 0.0f

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, positionMs, speed)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    private fun buildServiceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MediaService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

        val prevAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_previous,
            "Trước",
            buildServiceActionPendingIntent(ACTION_NOTIF_PREV, REQUEST_CODE_PREV)
        ).build()

        val playPauseAction = NotificationCompat.Action.Builder(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Tạm dừng" else "Phát",
            buildServiceActionPendingIntent(
                if (isPlaying) ACTION_NOTIF_PAUSE else ACTION_NOTIF_PLAY,
                REQUEST_CODE_PLAY_PAUSE
            )
        ).build()

        val nextAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_next,
            "Tiếp theo",
            buildServiceActionPendingIntent(ACTION_NOTIF_NEXT, REQUEST_CODE_NEXT)
        ).build()

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_litetube)
            .setContentTitle(currentTitle)
            .setContentText(currentChannel.ifEmpty { "LiteTube" })
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(style)
        currentArt()?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    private fun updateNotification(forceForeground: Boolean) {
        if (isSessionReleased && !forceForeground) return
        try {
            ensureMediaSession()
            val notification = buildNotification()
            if (!isForeground || forceForeground) {
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
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)
            }
        } catch (_: Exception) {}
    }

    private fun refreshWakeLock() {
        try {
            wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun releaseMediaSession() {
        if (!isSessionReleased) {
            try {
                if (::mediaSession.isInitialized) {
                    mediaSession.isActive = false
                    mediaSession.release()
                }
            } catch (_: Exception) {}
            isSessionReleased = true
        }
    }

    private fun stopPlayback() {
        try {
            unregisterNoisyReceiver()
            releaseWakeLock()
            if (isForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
            }
            releaseMediaSession()
            artBitmap = null
            artVideoId = null
            currentVideoId = null
            loadingArtVideoId = null
            stopSelf()
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "Phát nhạc nền LiteTube",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Duy trì âm thanh LiteTube khi khóa màn hình hoặc ở chế độ PiP"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(chan)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        stopPlayback()
        artExecutor.shutdownNow()
        super.onDestroy()
    }
}
