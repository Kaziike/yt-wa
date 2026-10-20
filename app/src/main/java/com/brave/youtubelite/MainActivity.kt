package com.brave.youtubelite

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.brave.youtubelite.network.AdBlockEngine
import com.brave.youtubelite.network.AppWebViewClient

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var customViewContainer: FrameLayout

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isFullscreen = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false

            // Bộ nhớ cache tối ưu để giảm tải mạng
            setCacheMode(WebSettings.LOAD_DEFAULT)

            // Cho phép video tự động phát
            mediaPlaybackRequiresUserGesture = false

            // Giảm tải render ngoài màn hình để tiết kiệm RAM & CPU
            offscreenPreRaster = false

            // Tùy biến User-Agent nhẹ chuẩn Chrome Mobile
            userAgentString = settings.userAgentString.replace("; wv", "")
        }

        // Bật tăng tốc phần cứng
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Gán WebViewClient tùy biến (chứa bộ lọc và Scriptlet của Brave)
        webView.webViewClient = AppWebViewClient(applicationContext)

        // Gán WebChromeClient để hỗ trợ Fullscreen Video và thanh tải trang
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

                // Ẩn WebView, hiện khung Fullscreen
                webView.visibility = View.GONE
                customViewContainer.visibility = View.VISIBLE
                customViewContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                // Ẩn thanh trạng thái hệ thống và xoay ngang màn hình
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            override fun onHideCustomView() {
                if (customView == null) return

                isFullscreen = false
                customViewContainer.removeView(customView)
                customView = null
                customViewCallback?.onCustomViewHidden()

                customViewContainer.visibility = View.GONE
                webView.visibility = View.VISIBLE

                // Phục hồi màn hình đứng
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullscreen) {
                    // Nếu đang xem video toàn màn hình -> thoát toàn màn hình trước
                    webView.webChromeClient?.onHideCustomView()
                } else if (webView.canGoBack()) {
                    // Nếu có lịch sử duyệt trang -> quay lại trang trước
                    webView.goBack()
                } else {
                    // Thoát app
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // Tự động vào chế độ Picture-in-Picture (PiP) khi người dùng bấm Home
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                // Không hỗ trợ PiP trên một số dòng máy
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
        }
    }

    override fun onPause() {
        super.onPause()
        // Giữ video tiếp tục chạy khi tắt màn hình hoặc chuyển app
        // (Không gọi webView.onPause() để hỗ trợ Background Playback)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
