package com.brave.youtubelite.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.brave.youtubelite.MainActivity
import com.brave.youtubelite.R
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground Service chuẩn Android Media Control:
 * - Tích hợp MediaSessionCompat để hỗ trợ điều khiển từ màn hình khóa, tai nghe Bluetooth, Android Auto.
 * - Hiển thị Notification chuẩn MediaStyle với các nút Prev, Play/Pause, Next.
 * - Tải ảnh thumbnail của video (i.ytimg.com) làm ảnh bìa cho Notification / Màn hình khóa.
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
        const val EXTRA_VIDEO_ID = "EXTRA_VIDEO_ID"

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

        // Thứ tự ưu tiên ảnh thumbnail: 16:9 nét nhất trước, ảnh 4:3 có viền đen để cuối
        private val THUMBNAIL_NAMES = listOf("maxresdefault", "hq720", "mqdefault", "hqdefault")
        private const val THUMBNAIL_MAX_WIDTH = 640
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForeground = false
    private var isSessionReleased = true

    private var currentTitle = "LiteTube"
    private var currentChannel = "Đang phát âm thanh trong nền"
    private var isPlaying = true
    private var positionMs = 0L
    private var durationMs = -1L

    // Ảnh bìa (thumbnail) của video đang phát
    private var currentVideoId: String? = null
    private var artBitmap: Bitmap? = null
    private var artVideoId: String? = null
    private var loadingArtVideoId: String? = null
    private val artExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LiteTube:PlaybackWakeLock"
        )
        ensureMediaSession()
    }

    /** Tạo (hoặc tạo lại sau khi đã release) MediaSession. */
    private fun ensureMediaSession() {
        if (!isSessionReleased && ::mediaSession.isInitialized) return
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
        isSessionReleased = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPlayback()
            }
            ACTION_START, ACTION_UPDATE -> {
                // ACTION_UPDATE khi service đã dừng thì bỏ qua (tránh tự bật lại notification)
                if (intent.action == ACTION_UPDATE && !isForeground) return START_NOT_STICKY
                ensureMediaSession()
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
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
        if (!videoId.isNullOrEmpty() && videoId != currentVideoId) {
            currentVideoId = videoId
            loadArtwork(videoId)
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

    /** Ảnh bìa hợp lệ chỉ khi đúng video đang phát (tránh hiện ảnh của video trước). */
    private fun currentArt(): Bitmap? =
        if (artBitmap != null && artVideoId != null && artVideoId == currentVideoId) artBitmap else null

    private fun loadArtwork(videoId: String) {
        if (videoId == artVideoId || videoId == loadingArtVideoId) return
        loadingArtVideoId = videoId
        artExecutor.execute {
            val bitmap = fetchThumbnail(videoId)
            mainHandler.post {
                if (loadingArtVideoId == videoId) loadingArtVideoId = null
                if (bitmap == null || isSessionReleased) return@post
                if (videoId != currentVideoId) return@post
                artBitmap = bitmap
                artVideoId = videoId
                updateMetadata()
                updateNotification()
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
                // Ảnh 120x90 là ảnh "không tồn tại" mặc định của YouTube → bỏ qua
                if (decoded.width <= 120) continue
                return if (decoded.width > THUMBNAIL_MAX_WIDTH) {
                    val h = (decoded.height * THUMBNAIL_MAX_WIDTH.toFloat() / decoded.width).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(decoded, THUMBNAIL_MAX_WIDTH, h, true)
                } else {
                    decoded
                }
            } catch (_: Exception) {
                // thử ảnh kế tiếp
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun updateMetadata() {
        if (isSessionReleased) return
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentChannel.ifEmpty { "LiteTube" })
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, currentChannel.ifEmpty { "LiteTube" })
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, if (durationMs > 0) durationMs else -1L)
        currentArt()?.let { art ->
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art)
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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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

    private fun updateNotification() {
        if (isSessionReleased) return
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
        artExecutor.shutdownNow()
        super.onDestroy()
    }
}
