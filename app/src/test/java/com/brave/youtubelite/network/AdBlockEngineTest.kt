package com.brave.youtubelite.network

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class AdBlockEngineTest {

    @Before
    fun setUp() {
        val assetFile = sequenceOf(
            File("src/main/assets/filters/blocked_domains.txt"),
            File("app/src/main/assets/filters/blocked_domains.txt")
        ).firstOrNull { it.exists() }
            ?: error("Không tìm thấy file blocked_domains.txt để chạy Unit Test")

        val snapshot = assetFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
            AdBlockEngine.parseRules(lines)
        }
        AdBlockEngine.setSnapshotForTesting(snapshot)
        AdBlockEngine.isEnabled = true
    }

    @After
    fun tearDown() {
        AdBlockEngine.isEnabled = true
    }

    @Test
    fun blocksExactDomainsAndSubdomains() {
        assertTrue(AdBlockEngine.isBlocked("https://googleads.g.doubleclick.net/pagead/id"))
        assertTrue(AdBlockEngine.isBlocked("https://sub1.sub2.ad.doubleclick.net/pixel"))
        assertTrue(AdBlockEngine.isBlocked("https://www.google-analytics.com/collect"))
        assertTrue(AdBlockEngine.isBlocked("https://video-ad-stats.googlevideo.com/ptracking"))
    }

    @Test
    fun blocksDomainWithPathRulesWithoutBlockingWholeHost() {
        // Các quy tắc dạng domain/path trong blocked_domains.txt trước đây bị bỏ sót hoàn toàn
        assertTrue(AdBlockEngine.isBlocked("https://play.google.com/log?format=json"))
        assertTrue(AdBlockEngine.isBlocked("https://m.youtube.com/api/stats/qoe?event=streamingstats"))
        assertTrue(AdBlockEngine.isBlocked("https://www.youtube.com/api/stats/atr?ns=yt"))
        assertTrue(AdBlockEngine.isBlocked("https://yt3.ggpht.com/ads/promo_banner.jpg"))

        // Đảm bảo KHÔNG chặn nhầm tài nguyên hợp lệ cùng domain
        assertFalse(AdBlockEngine.isBlocked("https://yt3.ggpht.com/ytc/channel_avatar_123.jpg"))
        assertFalse(AdBlockEngine.isBlocked("https://play.google.com/store/apps/details?id=example"))
    }

    @Test
    fun allowsLegitimateYouTubeContentAndStreams() {
        assertFalse(AdBlockEngine.isBlocked("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(AdBlockEngine.isBlocked("https://m.youtube.com/youtubei/v1/player?key=abc"))
        assertFalse(AdBlockEngine.isBlocked("https://rr2---sn-i3b7kn7d.googlevideo.com/videoplayback?itag=140"))
        assertFalse(AdBlockEngine.isBlocked("https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg"))
    }

    @Test
    fun respectsDisabledToggleAndHandlesMalformedUrlsSafely() {
        AdBlockEngine.isEnabled = false
        assertFalse(AdBlockEngine.isBlocked("https://googleads.g.doubleclick.net/pagead/id"))

        AdBlockEngine.isEnabled = true
        assertFalse(AdBlockEngine.isBlocked(""))
        assertFalse(AdBlockEngine.isBlocked("about:blank"))
        assertFalse(AdBlockEngine.isBlocked("javascript:void(0)"))
        assertFalse(AdBlockEngine.isBlocked(":::invalid-url:::"))
    }
}
