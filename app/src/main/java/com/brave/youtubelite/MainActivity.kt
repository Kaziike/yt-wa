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
import android.util.Log
import android.util.Rational
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
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
        private const val TAG = "MainActivity"
        private const val WEB_LOG_TAG = "WebConsole"
    }

    // Mã JS điều khiển PiP (assets/scriptlets/pip_controller.js), chèn trực tiếp khi cần
    private var pipControllerJs: String = ""

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
        // Bắt buộc dùng RECEIVER_EXPORTED trên Android 13/14 (targetSdk 34) vì nút RemoteAction
        // trên cửa sổ PiP được kích hoạt bởi tiến trình hệ thống (com.android.systemui).
        // Nếu dùng RECEIVER_NOT_EXPORTED, hệ thống sẽ chặn broadcast khiến nút Play/Pause không hoạt động.
        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        requestNotificationPermission()

        AdBlockEngine.init(applicationContext)

        pipControllerJs = try {
            assets.open("scriptlets/pip_controller.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Không đọc được pip_controller.js: ${e.message}")
            ""
        }

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
        val appWebViewClient = AppWebViewClient(applicationContext)
        appWebViewClient.installDocumentStartScripts(webView)
        webView.webViewClient = appWebViewClient

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.d(WEB_LOG_TAG, "${it.message()} [${it.sourceId()}:${it.lineNumber()}]")
                }
                return true
            }
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
        val requestCode = if (isVideoPlaying) 1002 else 1001

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
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

    /**
     * Chèn pip_controller.js (idempotent) rồi gọi window.__bravePip.<method>().
     * Không phụ thuộc vào youtube_defuser.js, nên vẫn hoạt động kể cả khi scriptlet kia lỗi.
     */
    private fun runPipCommand(method: String, onResult: ((String?) -> Unit)? = null) {
        val script = buildString {
            append(pipControllerJs)
            append("\n;(function(){try{")
            append("return window.__bravePip ? String(window.__bravePip.$method()) : 'pip:missing';")
            append("}catch(e){return 'pip:error:' + e;}})();")
        }
        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "PiP $method -> $result")
            onResult?.invoke(result)
        }
    }

    private fun enterPipLayout() {
        progressBar.visibility = View.GONE
        runPipCommand("enter")
    }

    private fun exitPipLayout() {
        runPipCommand("exit")
    }

    // Xử lý khi người dùng bấm nút PLAY trên PiP
    private fun handlePipPlay() {
        isVideoPlaying = true
        updatePipParams()
        runPipCommand("play")
    }

    // Xử lý khi người dùng bấm nút PAUSE trên PiP: mở khóa hook chặn pause rồi dừng video
    private fun handlePipPause() {
        isVideoPlaying = false
        updatePipParams()
        runPipCommand("pause")
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isVideoPlaying || isFullscreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Cô lập thẻ video ngay lập tức (không chờ callback JS, vì lúc callback trả về
                // Activity có thể đã qua trạng thái cho phép vào PiP)
                enterPipLayout()
                try {
                    if (!isInPictureInPictureMode) {
                        buildPipParams()?.let { enterPictureInPictureMode(it) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "enterPictureInPictureMode lỗi: ${e.message}")
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            enterPipLayout()
            // Áp lại ở các nhịp sau khi WebView đã đổi kích thước theo khung PiP
            webView.postDelayed({ if (isInPipModeCompat()) enterPipLayout() }, 150)
            webView.postDelayed({ if (isInPipModeCompat()) enterPipLayout() }, 450)
            updatePipParams()
        } else {
            exitPipLayout()
        }
    }

    private fun isInPipModeCompat(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode

    override fun onResume() {
        super.onResume()
        if (!isInPipModeCompat()) {
            exitPipLayout()
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
