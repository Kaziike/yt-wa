package com.brave.youtubelite

import android.Manifest
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
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

    private lateinit var webView: KeepAliveWebView
    private lateinit var progressBar: ProgressBar
    private lateinit var customViewContainer: FrameLayout

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isFullscreen = false

    private var isVideoPlaying = false
    private var currentTitle = "YouTube Lite"
    private var currentChannel = ""
    private var videoSourceRect: Rect? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Yêu cầu quyền thông báo trên Android 13+ để hiện thanh phát nhạc nền
        requestNotificationPermission()

        // 1. Khởi tạo Engine lọc quảng cáo
        AdBlockEngine.init(applicationContext)

        // 2. Ánh xạ View
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        customViewContainer = findViewById(R.id.customViewContainer)

        // 3. Cấu hình WebView tối ưu hiệu năng và tài nguyên
        setupWebView()

        // 4. Xử lý nút Back của Android hiện đại (OnBackPressedCallback)
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

    @SuppressLint("SetJavaScriptEnabled")
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

        // Kết nối cầu nối JavascriptInterface để đồng bộ với Web
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
     * Cập nhật thông số PiP với sourceRectHint chính xác theo mô hình Brave.
     * Cắt đúng tọa độ của Video để không bị vỡ giao diện cả trang web.
     */
    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val displayWidth = webView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                val displayHeight = webView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

                val targetRect = if (isFullscreen) {
                    Rect(0, 0, displayWidth, displayHeight)
                } else {
                    videoSourceRect ?: Rect(0, 0, displayWidth, (displayWidth * 9) / 16)
                }

                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setSourceRectHint(targetRect)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+: Tự động kích hoạt PiP khi vuốt về Home nếu video đang mở
                    builder.setAutoEnterEnabled(isVideoPlaying || isFullscreen)
                }
                setPictureInPictureParams(builder.build())
            } catch (_: Exception) {}
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isVideoPlaying || isFullscreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val displayWidth = webView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                    val displayHeight = webView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
                    val targetRect = if (isFullscreen) {
                        Rect(0, 0, displayWidth, displayHeight)
                    } else {
                        videoSourceRect ?: Rect(0, 0, displayWidth, (displayWidth * 9) / 16)
                    }

                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .setSourceRectHint(targetRect)
                        .build()
                    enterPictureInPictureMode(params)
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
            // Ẩn tất cả các thành phần web thừa khi đang ở chế độ PiP
            webView.evaluateJavascript("document.documentElement.classList.add('pip-active');", null)
        } else {
            // Khôi phục giao diện bình thường khi phóng to lại app
            webView.evaluateJavascript("document.documentElement.classList.remove('pip-active');", null)
        }
    }

    override fun onStart() {
        super.onStart()
        // Khi quay trở lại app -> dừng notification chạy nền
        stopMediaService()
    }

    override fun onPause() {
        super.onPause()
        // Không pause WebView để luồng âm thanh tiếp tục phát
    }

    override fun onStop() {
        super.onStop()
        val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        // Nếu app rời màn hình mà không ở PiP -> khởi động MediaService để giữ CPU & Audio Focus
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

        // Nhận tọa độ khung hình thực tế của Video từ JavaScript
        @JavascriptInterface
        fun updateVideoBounds(left: Float, top: Float, right: Float, bottom: Float) {
            runOnUiThread {
                val density = resources.displayMetrics.density
                videoSourceRect = Rect(
                    (left * density).toInt(),
                    (top * density).toInt(),
                    (right * density).toInt(),
                    (bottom * density).toInt()
                )
                updatePipParams()
            }
        }
    }
}
