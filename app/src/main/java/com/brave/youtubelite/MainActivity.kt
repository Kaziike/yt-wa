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
        private const val ACTION_PIP_PLAY_PAUSE = "com.brave.youtubelite.PIP_PLAY_PAUSE"
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

    // Receiver lắng nghe nút Bấm Play/Pause ngay trên cửa sổ nổi PiP
    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_PLAY_PAUSE) {
                togglePlayPause()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Đăng ký nhận sự kiện nút bấm từ cửa sổ nổi PiP
        val filter = IntentFilter(ACTION_PIP_PLAY_PAUSE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pipActionReceiver, filter)
        }

        // Xin quyền thông báo trên Android 13+
        requestNotificationPermission()

        // 1. Khởi tạo Engine lọc quảng cáo
        AdBlockEngine.init(applicationContext)

        // 2. Ánh xạ View
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        customViewContainer = findViewById(R.id.customViewContainer)

        // 3. Cấu hình WebView
        setupWebView()

        // 4. Xử lý nút Back
        setupBackPressedHandler()

        // 5. Tải YouTube Mobile
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

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // ĐẢM BẢO BÀN PHÍM HOẠT ĐỘNG: Bật focusable và bắt touch để kích hoạt bàn phím ảo
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
     * Cập nhật thông số PiP với tỉ lệ chuẩn 16:9 và nút điều khiển Native
     */
    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))

                val iconRes = if (isVideoPlaying) {
                    android.R.drawable.ic_media_pause
                } else {
                    android.R.drawable.ic_media_play
                }
                val actionTitle = if (isVideoPlaying) "Tạm dừng" else "Phát"

                val intent = Intent(ACTION_PIP_PLAY_PAUSE).setPackage(packageName)
                val pendingIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    intent,
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
                }
                setPictureInPictureParams(builder.build())
            } catch (_: Exception) {}
        }
    }

    private fun togglePlayPause() {
        webView.evaluateJavascript(
            """
            (function() {
                const v = document.querySelector('video');
                if (v) {
                    if (v.paused) v.play(); else v.pause();
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
                try {
                    webView.evaluateJavascript("document.documentElement.classList.add('pip-active');") {
                        val params = PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9))
                            .build()
                        enterPictureInPictureMode(params)
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
            webView.evaluateJavascript("document.documentElement.classList.add('pip-active');", null)
            updatePipParams()
        } else {
            webView.evaluateJavascript("document.documentElement.classList.remove('pip-active');", null)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInPictureInPictureMode) {
            webView.evaluateJavascript("document.documentElement.classList.remove('pip-active');", null)
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
        val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        if (isVideoPlaying && !inPip) {
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
                stopMediaService()
                updatePipParams()
            }
        }
    }
}
