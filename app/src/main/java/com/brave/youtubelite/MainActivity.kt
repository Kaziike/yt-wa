package com.brave.youtubelite

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.brave.youtubelite.network.AdBlockEngine
import com.brave.youtubelite.network.AppWebViewClient
import com.brave.youtubelite.service.MediaService
import com.brave.youtubelite.view.KeepAliveWebView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val ACTION_PIP_PLAY = "com.brave.youtubelite.PIP_PLAY"
        private const val ACTION_PIP_PAUSE = "com.brave.youtubelite.PIP_PAUSE"
    }

    private lateinit var webView: KeepAliveWebView
    private lateinit var progressBar: ProgressBar
    private lateinit var customViewContainer: FrameLayout

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isFullscreen = false

    private var isVideoPlaying = false
    private var currentTitle = "YouTube Lite"
    private var currentChannel = ""

    // Tỉ lệ khung hình thật của video (cập nhật từ JS) để cửa sổ PiP khớp video
    private var videoAspectRatio = Rational(16, 9)

    // Receiver lắng nghe nút Play và Pause riêng biệt trên PiP
    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_PLAY -> handlePipPlay()
                ACTION_PIP_PAUSE -> handlePipPause()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_PLAY)
            addAction(ACTION_PIP_PAUSE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pipActionReceiver, filter)
        }

        requestNotificationPermission()

        AdBlockEngine.init(applicationContext)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        customViewContainer = findViewById(R.id.customViewContainer)

        setupWebView()
        setupBackPressedHandler()

        val targetUrl = intent?.dataString ?: "https://m.youtube.com"
        webView.loadUrl(targetUrl)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        val settings = webView.settings
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false

            setCacheMode(WebSettings.LOAD_DEFAULT)
            mediaPlaybackRequiresUserGesture = false
            offscreenPreRaster = false

            userAgentString = settings.userAgentString.replace("; wv", "")
        }

        // Không ép LAYER_TYPE_HARDWARE cho WebView: cửa sổ đã bật hardwareAccelerated,
        // việc vẽ WebView vào một offscreen layer riêng có thể khiến khung hình video
        // không được cập nhật khi WebView bị thu nhỏ (PiP) -> màn hình đen nhưng vẫn có tiếng.
        webView.setLayerType(View.LAYER_TYPE_NONE, null)

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus(View.FOCUS_DOWN)
        webView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                    if (!v.hasFocus()) {
                        v.requestFocus()
                    }
                }
            }
            false
        }

        webView.addJavascriptInterface(MediaBridge(), "AndroidBridge")
        webView.webViewClient = AppWebViewClient(applicationContext)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    onHideCustomView()
                    return
                }

                customView = view
                customViewCallback = callback
                isFullscreen = true

                webView.visibility = View.GONE
                customViewContainer.visibility = View.VISIBLE
                customViewContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                updatePipParams()
            }

            override fun onHideCustomView() {
                if (customView == null) return

                isFullscreen = false
                customViewContainer.removeView(customView)
                customView = null
                customViewCallback?.onCustomViewHidden()

                customViewContainer.visibility = View.GONE
                webView.visibility = View.VISIBLE

                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                updatePipParams()
            }
        }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullscreen) {
                    webView.webChromeClient?.onHideCustomView()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /**
     * Android chỉ chấp nhận tỉ lệ PiP trong khoảng 1:2.39 .. 2.39:1,
     * ngoài khoảng này setAspectRatio sẽ ném IllegalArgumentException.
     */
    private fun clampedPipAspectRatio(): Rational {
        val ratio = videoAspectRatio.toFloat()
        if (ratio.isNaN() || ratio <= 0f) return Rational(16, 9)
        val maxRatio = 2.39f
        val minRatio = 1f / 2.39f
        return when {
            ratio > maxRatio -> Rational(239, 100)
            ratio < minRatio -> Rational(100, 239)
            else -> videoAspectRatio
        }
    }

    private fun buildPipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(clampedPipAspectRatio())

        val actionIntent = if (isVideoPlaying) {
            Intent(ACTION_PIP_PAUSE).setPackage(packageName)
        } else {
            Intent(ACTION_PIP_PLAY).setPackage(packageName)
        }

        val iconRes = if (isVideoPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val actionTitle = if (isVideoPlaying) "Tạm dừng" else "Phát"

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val action = RemoteAction(
            Icon.createWithResource(this, iconRes),
            actionTitle,
            actionTitle,
            pendingIntent
        )
        builder.setActions(listOf(action))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(isVideoPlaying || isFullscreen)
            // Nội dung là video -> tắt seamless resize để hệ thống không crossfade/nhòe khi đổi kích thước
            builder.setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                buildPipParams()?.let { setPictureInPictureParams(it) }
            } catch (_: Exception) {}
        }
    }

    // Xử lý khi người dùng bấm nút PLAY trên PiP: Gọi play()
    private fun handlePipPlay() {
        isVideoPlaying = true
        updatePipParams()
        webView.evaluateJavascript(
            """
            (function() {
                const v = (window.__braveMainVideo && window.__braveMainVideo()) || document.querySelector('video');
                if (v) {
                    v.play().catch(function() {});
                }
            })();
            """.trimIndent(),
            null
        )
    }

    // Xử lý khi người dùng bấm nút PAUSE trên PiP: Cho phép pause tường minh
    private fun handlePipPause() {
        isVideoPlaying = false
        updatePipParams()
        webView.evaluateJavascript(
            """
            (function() {
                window.__isUserExplicitPause = true;
                const v = (window.__braveMainVideo && window.__braveMainVideo()) || document.querySelector('video');
                if (v) {
                    v.pause();
                }
            })();
            """.trimIndent(),
            null
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isVideoPlaying || isFullscreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Cô lập thẻ video ngay lập tức (không chờ callback JS, vì lúc callback trả về
                // Activity có thể đã qua trạng thái cho phép vào PiP)
                progressBar.visibility = View.GONE
                webView.evaluateJavascript("if (window.__enterPipMode) window.__enterPipMode();", null)
                try {
                    if (!isInPictureInPictureMode) {
                        buildPipParams()?.let { enterPictureInPictureMode(it) }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            progressBar.visibility = View.GONE
            webView.evaluateJavascript("if (window.__enterPipMode) window.__enterPipMode();", null)
            updatePipParams()
        } else {
            webView.evaluateJavascript("if (window.__exitPipMode) window.__exitPipMode();", null)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInPictureInPictureMode) {
            webView.evaluateJavascript("if (window.__exitPipMode) window.__exitPipMode();", null)
        }
    }

    override fun onStart() {
        super.onStart()
        stopMediaService()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Dù đang ở PiP hay bình thường, nếu màn hình tắt mà video đang phát -> luôn chạy MediaService để giữ âm thanh!
        if (isVideoPlaying) {
            startMediaService()
        }
    }

    private fun startMediaService() {
        try {
            val intent = Intent(this, MediaService::class.java).apply {
                action = MediaService.ACTION_START
                putExtra(MediaService.EXTRA_TITLE, currentTitle)
                putExtra(MediaService.EXTRA_CHANNEL, currentChannel)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {}
    }

    private fun stopMediaService() {
        try {
            val intent = Intent(this, MediaService::class.java).apply {
                action = MediaService.ACTION_STOP
            }
            startService(intent)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(pipActionReceiver)
        } catch (_: Exception) {}
        stopMediaService()
        webView.destroy()
        super.onDestroy()
    }

    inner class MediaBridge {
        @JavascriptInterface
        fun onVideoPlaying(title: String, channel: String) {
            runOnUiThread {
                isVideoPlaying = true
                if (title.isNotEmpty()) currentTitle = title
                if (channel.isNotEmpty()) currentChannel = channel
                updatePipParams()
            }
        }

        @JavascriptInterface
        fun onVideoPaused() {
            runOnUiThread {
                isVideoPlaying = false
                updatePipParams()
            }
        }

        @JavascriptInterface
        fun onVideoSize(width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            runOnUiThread {
                val newRatio = Rational(width, height)
                if (newRatio != videoAspectRatio) {
                    videoAspectRatio = newRatio
                    updatePipParams()
                }
            }
        }
    }
}
