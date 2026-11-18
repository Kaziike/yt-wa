package com.brave.youtubelite.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlAndCacheValidationTest {

    @Test
    fun normalizesValidWatchAndShortUrlsAndRejectsMaliciousTargets() {
        assertEquals(
            "https://m.youtube.com/watch?v=abc123XYZ",
            UrlUtils.normalizeWatchUrl("/watch?v=abc123XYZ")
        )
        assertEquals(
            "https://m.youtube.com/watch?v=abc123XYZ&list=PL999",
            UrlUtils.normalizeWatchUrl("https://youtu.be/abc123XYZ?list=PL999&si=tracking")
        )
        assertEquals(
            "https://m.youtube.com/watch?v=abc123XYZ",
            UrlUtils.normalizeWatchUrl("https://www.youtube.com/watch?v=abc123XYZ")
        )

        // Shorts giữ nguyên ở tầng Browse, không mở bằng Player Sheet
        assertNull(UrlUtils.normalizeWatchUrl("https://m.youtube.com/shorts/shortId123"))

        // Chặn tuyệt đối các scheme độc hại hoặc domain giả mạo
        assertNull(UrlUtils.normalizeWatchUrl("javascript:alert(1)"))
        assertNull(UrlUtils.normalizeWatchUrl("file:///data/data/com.brave.youtubelite/prefs.xml"))
        assertNull(UrlUtils.normalizeWatchUrl("https://evil-phishing.com/watch?v=abc123XYZ"))
    }

    @Test
    fun unwrapsYouTubeRedirectSafely() {
        val redirectUrl = "https://m.youtube.com/redirect?event=video_description&q=https%3A%2F%2Fgithub.com%2FKaziike%2Fyt-wa"
        assertEquals("https://github.com/Kaziike/yt-wa", UrlUtils.unwrapYouTubeRedirect(redirectUrl))

        val maliciousRedirect = "https://m.youtube.com/redirect?q=javascript%3Aalert(1)"
        assertNull(UrlUtils.unwrapYouTubeRedirect(maliciousRedirect))
    }

    @Test
    fun preventsCaptivePortalHtmlFromPoisoningStaticCache() {
        val jsUrl = "https://m.youtube.com/s/player/123456/player-plasma-ias-phone-vi_VN.vflset/base.js"
        val fontUrl = "https://fonts.gstatic.com/s/roboto/v30/KFOmCnqEu92Fr1Mu4mxK.woff2"

        assertTrue(WebResourceCacheManager.isCompatibleMimeType(jsUrl, "application/javascript"))
        assertTrue(WebResourceCacheManager.isCompatibleMimeType(jsUrl, "text/javascript"))
        assertFalse(WebResourceCacheManager.isCompatibleMimeType(jsUrl, "text/html"))

        assertTrue(WebResourceCacheManager.isCompatibleMimeType(fontUrl, "font/woff2"))
        assertFalse(WebResourceCacheManager.isCompatibleMimeType(fontUrl, "text/html; charset=UTF-8"))
    }

    @Test
    fun normalizesImmutableStaticAssetCacheKeys() {
        val k1 = WebResourceCacheManager.getCacheKey("https://s.ytimg.com/yts/jsbin/s/mobile/app.js?v=1")
        val k2 = WebResourceCacheManager.getCacheKey("https://s.ytimg.com/yts/jsbin/s/mobile/app.js?v=2")
        assertEquals(k1, k2)
    }
}
