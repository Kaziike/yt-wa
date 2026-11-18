package com.brave.youtubelite.playback

import android.webkit.JavascriptInterface

/**
 * Cầu nối JavascriptInterface độc lập (KHÔNG dùng inner class của Activity để tránh rò rỉ bộ nhớ).
 */
class WebBridge(
    private val fromPlayer: Boolean,
    listener: Listener
) {

    interface Listener {
        fun onOpenWatchLayer(rawUrl: String)
        fun onMinimizePlayer()
        fun onOpenSettings()
        fun onMediaStateReceived(
            fromPlayer: Boolean,
            playing: Boolean,
            videoId: String,
            title: String,
            channel: String,
            posMs: Long,
            durMs: Long,
            videoWidth: Int,
            videoHeight: Int
        )
    }

    @Volatile
    private var listener: Listener? = listener

    fun detach() {
        listener = null
    }

    @JavascriptInterface
    fun openWatchLayer(url: String) {
        if (url.isNotBlank()) {
            listener?.onOpenWatchLayer(url)
        }
    }

    @JavascriptInterface
    fun minimizePlayer() {
        listener?.onMinimizePlayer()
    }

    @JavascriptInterface
    fun openSettings() {
        listener?.onOpenSettings()
    }

    @JavascriptInterface
    fun onMediaState(
        playing: Boolean,
        videoId: String,
        title: String,
        channel: String,
        posMs: Long,
        durMs: Long,
        videoWidth: Int,
        videoHeight: Int
    ) {
        listener?.onMediaStateReceived(
            fromPlayer = fromPlayer,
            playing = playing,
            videoId = videoId,
            title = title,
            channel = channel,
            posMs = posMs,
            durMs = durMs,
            videoWidth = videoWidth,
            videoHeight = videoHeight
        )
    }
}
