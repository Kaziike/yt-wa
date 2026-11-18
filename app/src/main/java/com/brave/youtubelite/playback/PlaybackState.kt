package com.brave.youtubelite.playback

import android.util.Rational

/**
 * Trạng thái phát đa phương tiện bất biến duy nhất cho toàn bộ tầng Native.
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val videoId: String = "",
    val title: String = "LiteTube",
    val channel: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = -1L,
    val aspectRatio: Rational = Rational(16, 9)
)
