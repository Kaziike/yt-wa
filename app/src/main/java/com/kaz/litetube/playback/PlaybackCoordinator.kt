package com.kaz.litetube.playback

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import com.kaz.litetube.network.UrlUtils
import com.kaz.litetube.network.WebAssetRepository
import com.kaz.litetube.service.MediaService
import com.kaz.litetube.settings.AppSettings
import com.kaz.litetube.view.KeepAliveWebView

/**
 * Trình điều phối trung tâm giữa Browse WebView, Player WebView và MediaService:
 * - Lưu giữ nguồn trạng thái duy nhất (PlaybackState).
 * - Đảm bảo dừng âm thanh Shorts ở tầng Browse ngay khi tầng Player mở lên (chống phát chồng 2 luồng tiếng).
 * - Thực thi lệnh điều khiển WebView siêu nhẹ (~80 bytes IPC), chỉ nạp bổ sung script nếu trang thiếu.
 * - Quản lý thông minh cờ keepAliveInBackground của cả 2 WebView để tiết kiệm tối đa CPU/GPU/Pin.
 */
class PlaybackCoordinator(
    context: Context,
    private val browseWebView: KeepAliveWebView,
    private val playerWebView: KeepAliveWebView,
    private val isPlayerLayerActive: () -> Boolean,
    private val isInPipMode: () -> Boolean,
    private val onStateChanged: (PlaybackState, Boolean) -> Unit
) : MediaService.CommandListener {

    companion object {
        private const val TAG = "PlaybackCoordinator"
    }

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    var state: PlaybackState = PlaybackState()
        private set

    private var isMediaServiceActive = false
    private var isAppInBackground = false

    init {
        MediaService.commandListener = this
    }

    private fun activeWebView(): KeepAliveWebView =
        if (isPlayerLayerActive()) playerWebView else browseWebView

    fun resetForNewVideo(watchUrl: String) {
        silenceBrowseWebView()
        val vid = UrlUtils.extractVideoId(watchUrl)
        val prevRatio = state.aspectRatio
        state = PlaybackState(
            isPlaying = false,
            videoId = vid,
            title = "LiteTube",
            channel = "",
            positionMs = 0L,
            durationMs = -1L,
            aspectRatio = prevRatio
        )
        syncWebViewKeepAlive()
        onStateChanged(state, true)
    }

    fun resetOnPlayerClosed() {
        val wasPlaying = state.isPlaying
        state = PlaybackState()
        stopMediaService()
        syncWebViewKeepAlive()
        onStateChanged(state, wasPlaying)
    }

    /**
     * Tạm dừng mọi video (ví dụ YouTube Shorts) đang chạy trên Browse WebView khi mở lớp Player đè lên.
     */
    fun silenceBrowseWebView() {
        browseWebView.evaluateJavascript(
            "(function(){try{if(window.__bravePip)window.__bravePip.pause();" +
                "else document.querySelectorAll('video').forEach(function(v){v.pause();});}catch(e){}})();",
            null
        )
    }

    /**
     * Gọi lệnh trên window.__bravePip.<callExpr> với payload tối thiểu; tự phục hồi nếu thiếu script.
     */
    fun runPipCommand(callExpr: String, onResult: ((String?) -> Unit)? = null) {
        val target = activeWebView()
        val fastScript = "(function(){try{return window.__bravePip?String(window.__bravePip.$callExpr):'pip:missing';}catch(e){return 'pip:error:'+e;}})();"
        target.evaluateJavascript(fastScript) { result ->
            val unquoted = result?.trim()?.trim('"') ?: ""
            if (unquoted == "pip:missing") {
                val recoveryScript = buildString {
                    append(WebAssetRepository.coreBridgeJs)
                    append('\n')
                    append(WebAssetRepository.pipControllerJs)
                    append("\n;(function(){try{return window.__bravePip?String(window.__bravePip.")
                    append(callExpr)
                    append("):'pip:missing';}catch(e){return 'pip:error:'+e;}})();")
                }
                target.evaluateJavascript(recoveryScript) { retryRes ->
                    Log.d(TAG, "PiP (recovered) $callExpr -> $retryRes")
                    onResult?.invoke(retryRes)
                }
            } else {
                onResult?.invoke(result)
            }
        }
    }

    fun handleMediaStateFromWeb(
        fromPlayer: Boolean,
        playing: Boolean,
        videoId: String,
        title: String,
        channel: String,
        posMs: Long,
        durMs: Long,
        videoWidth: Int,
        videoHeight: Int
    ) {
        val accept = if (isPlayerLayerActive()) fromPlayer else !fromPlayer
        if (!accept) return

        val prev = state
        val playingChanged = playing != prev.isPlaying

        val cleanVid = videoId.trim().ifEmpty {
            if (fromPlayer) UrlUtils.extractVideoId(playerWebView.url) else prev.videoId
        }
        val cleanTitle = title.trim().let {
            if (it.isNotEmpty() && it != "LiteTube" && it != "YouTube") it else prev.title
        }
        val cleanChannel = channel.trim().ifEmpty { prev.channel }
        val newPos = if (posMs >= 0L) posMs else prev.positionMs
        val newDur = if (durMs > 0L) durMs else prev.durationMs
        val newRatio = if (videoWidth > 0 && videoHeight > 0) {
            Rational(videoWidth, videoHeight)
        } else {
            prev.aspectRatio
        }
        val ratioChanged = newRatio != prev.aspectRatio

        state = PlaybackState(
            isPlaying = playing,
            videoId = cleanVid,
            title = cleanTitle,
            channel = cleanChannel,
            positionMs = newPos,
            durationMs = newDur,
            aspectRatio = newRatio
        )

        syncWebViewKeepAlive()
        onStateChanged(state, playingChanged || ratioChanged)

        if (!isMediaServiceActive && isInPipMode() && (playing || isPlayerLayerActive())) {
            startMediaService()
        } else if (isMediaServiceActive) {
            updateMediaService()
        }
    }

    fun onForegroundEntered() {
        if (!isAppInBackground && !isMediaServiceActive) return
        isAppInBackground = false
        runPipCommand("setBackground(false)")
        if (!isInPipMode()) {
            stopMediaService()
        }
        syncWebViewKeepAlive()
    }

    fun onBackgroundEntered(keepAudioAlive: Boolean) {
        isAppInBackground = true
        runPipCommand("setBackground(true)")
        if (keepAudioAlive && state.isPlaying) {
            startMediaService()
        } else if (!keepAudioAlive) {
            stopMediaService()
        }
        syncWebViewKeepAlive()
    }

    fun onPipEntered() {
        runPipCommand("enter()")
        if (!isMediaServiceActive && (state.isPlaying || isPlayerLayerActive())) {
            startMediaService()
        } else if (isMediaServiceActive) {
            updateMediaService()
        }
        syncWebViewKeepAlive()
    }

    fun onPipExitedToApp() {
        runPipCommand("exit()")
        syncWebViewKeepAlive()
    }

    fun onPipSwitchedToAudioOnly() {
        isAppInBackground = true
        runPipCommand("enterAudioOnlyFromPip()")
        state = state.copy(isPlaying = true)
        startMediaService()
        syncWebViewKeepAlive()
        onStateChanged(state, true)
    }

    fun onPipDismissedAndStop() {
        runPipCommand("pause()")
        runPipCommand("exit()")
        val changed = state.isPlaying
        state = state.copy(isPlaying = false)
        stopMediaService()
        syncWebViewKeepAlive()
        onStateChanged(state, changed)
    }

    fun play() {
        val changed = !state.isPlaying
        state = state.copy(isPlaying = true)
        runPipCommand("play()")
        syncWebViewKeepAlive()
        onStateChanged(state, changed)
        updateMediaService()
    }

    fun pause() {
        val changed = state.isPlaying
        state = state.copy(isPlaying = false)
        runPipCommand("pause()")
        syncWebViewKeepAlive()
        onStateChanged(state, changed)
        updateMediaService()
    }

    fun togglePlayPause() {
        if (state.isPlaying) pause() else play()
    }

    /**
     * Cập nhật chế độ giữ sống nền (KeepAlive) đúng cho từng WebView:
     * - Browse WebView chỉ giữ sống nền khi KHÔNG mở Player Layer và đang thực sự phát (Shorts) kèm bật Background Play.
     * - Player WebView chỉ giữ sống nền khi đang mở Player Layer và đang ở PiP hoặc phát nền hợp lệ.
     */
    fun syncWebViewKeepAlive() {
        val bgEnabled = AppSettings.isBackgroundPlayEnabled(appContext)
        val pip = isInPipMode()
        val playerActive = isPlayerLayerActive()

        val keepPlayerAlive = playerActive && (pip || (bgEnabled && state.isPlaying))
        val keepBrowseAlive = !playerActive && (pip || (bgEnabled && state.isPlaying))

        playerWebView.updateKeepAlive(keepPlayerAlive)
        browseWebView.updateKeepAlive(keepBrowseAlive)
    }

    fun startMediaService() {
        try {
            val vid = state.videoId.ifEmpty { UrlUtils.extractVideoId(playerWebView.url) }
            if (vid.isNotEmpty() && vid != state.videoId) {
                state = state.copy(videoId = vid)
            }
            val intent = Intent(appContext, MediaService::class.java).apply {
                action = MediaService.ACTION_START
                putExtra(MediaService.EXTRA_TITLE, state.title)
                putExtra(MediaService.EXTRA_CHANNEL, state.channel)
                putExtra(MediaService.EXTRA_IS_PLAYING, state.isPlaying)
                putExtra(MediaService.EXTRA_POSITION_MS, state.positionMs)
                putExtra(MediaService.EXTRA_DURATION_MS, state.durationMs)
                putExtra(MediaService.EXTRA_VIDEO_ID, state.videoId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            isMediaServiceActive = true
        } catch (e: Exception) {
            Log.w(TAG, "Không thể khởi động MediaService: ${e.message}")
        }
    }

    fun updateMediaService() {
        if (!isMediaServiceActive) return
        try {
            val intent = Intent(appContext, MediaService::class.java).apply {
                action = MediaService.ACTION_UPDATE
                putExtra(MediaService.EXTRA_TITLE, state.title)
                putExtra(MediaService.EXTRA_CHANNEL, state.channel)
                putExtra(MediaService.EXTRA_IS_PLAYING, state.isPlaying)
                putExtra(MediaService.EXTRA_POSITION_MS, state.positionMs)
                putExtra(MediaService.EXTRA_DURATION_MS, state.durationMs)
                putExtra(MediaService.EXTRA_VIDEO_ID, state.videoId)
            }
            appContext.startService(intent)
        } catch (_: Exception) {}
    }

    fun stopMediaService() {
        if (!isMediaServiceActive) return
        try {
            val intent = Intent(appContext, MediaService::class.java).apply {
                action = MediaService.ACTION_STOP
            }
            appContext.startService(intent)
        } catch (_: Exception) {}
        isMediaServiceActive = false
    }

    override fun onMediaPlay() {
        mainHandler.post { play() }
    }

    override fun onMediaPause() {
        mainHandler.post { pause() }
    }

    override fun onMediaNext() {
        mainHandler.post { runPipCommand("next()") }
    }

    override fun onMediaPrevious() {
        mainHandler.post { runPipCommand("previous()") }
    }

    override fun onMediaSeekTo(positionMs: Long) {
        mainHandler.post {
            state = state.copy(positionMs = positionMs)
            runPipCommand("seekTo($positionMs)")
        }
    }

    fun release() {
        if (MediaService.commandListener === this) {
            MediaService.commandListener = null
        }
        mainHandler.removeCallbacksAndMessages(null)
        stopMediaService()
    }
}
