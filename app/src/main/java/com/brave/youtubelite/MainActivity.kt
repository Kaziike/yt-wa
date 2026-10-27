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
import android.graphics.Color
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
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.brave.youtubelite.network.AdBlockEngine
import com.brave.youtubelite.network.AppWebViewClient
import com.brave.youtubelite.network.WebResourceCacheManager
import com.brave.youtubelite.service.MediaService
import com.brave.youtubelite.settings.AppSettings
import com.brave.youtubelite.view.KeepAliveWebView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

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
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isFullscreen = false

    private var isVideoPlaying = false
    private var isMiniPlayerActive = false
    private var currentTitle = "YouTube Lite"
    private var currentChannel = ""
    private var positionMs: Long = 0L
    private var durationMs: Long = -1L
    private var isMediaServiceActive = false

    // Tỉ lệ khung hình thật của video (cập nhật từ JS) để cửa sổ PiP khớp video
    private var videoAspectRatio = Rational(16, 9)

    // Receiver lắng nghe nút Play/Pause trên PiP và Media Controls từ MediaService / Notification / LockScreen
    private val mediaActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_PLAY, MediaService.ACTION_MEDIA_PLAY -> handlePipPlay()
                ACTION_PIP_PAUSE, MediaService.ACTION_MEDIA_PAUSE -> handlePipPause()
                MediaService.ACTION_MEDIA_NEXT -> runPipCommand("next")
                MediaService.ACTION_MEDIA_PREV -> runPipCommand("previous")
                MediaService.ACTION_MEDIA_SEEK -> {
                    val pos = intent.getLongExtra(MediaService.EXTRA_SEEK_POS_MS, 0L)
                    runPipCommand("seekTo($pos)")
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Đồng bộ cài đặt ban đầu vào Engine
        AdBlockEngine.isEnabled = AppSettings.isAdBlockEnabled(this)
        WebResourceCacheManager.isEnabled = AppSettings.isPreloadCacheEnabled(this)

        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_PLAY)
            addAction(ACTION_PIP_PAUSE)
            addAction(MediaService.ACTION_MEDIA_PLAY)
            addAction(MediaService.ACTION_MEDIA_PAUSE)
            addAction(MediaService.ACTION_MEDIA_NEXT)
            addAction(MediaService.ACTION_MEDIA_PREV)
            addAction(MediaService.ACTION_MEDIA_SEEK)
        }
        // Dùng RECEIVER_EXPORTED để nhận PendingIntent từ SystemUI (PiP & Notification)
        ContextCompat.registerReceiver(
            this,
            mediaActionReceiver,
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
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.parseColor("#212121"))
        swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#FF0000"), Color.parseColor("#FF5722"))
        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }
        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            webView.scrollY > 0
        }

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

        // Không ép LAYER_TYPE_HARDWARE cho WebView để tránh lỗi màn hình đen trong PiP
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
        appWebViewClient.onOpenSettings = {
            runOnUiThread {
                showSettingsDialog()
            }
        }
        appWebViewClient.onPageFinishedCallback = {
            runOnUiThread {
                swipeRefreshLayout.isRefreshing = false
            }
        }
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
                    swipeRefreshLayout.isRefreshing = false
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                enterFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }
    }

    private fun enterFullscreen(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (customView != null) {
            exitFullscreen()
            return
        }

        customView = view
        customViewCallback = callback
        isFullscreen = true
        swipeRefreshLayout.isEnabled = false

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

    private fun exitFullscreen() {
        if (customView == null) return

        isFullscreen = false
        customViewContainer.removeView(customView)
        customView = null
        customViewCallback?.onCustomViewHidden()

        customViewContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
        if (!isInPipModeCompat()) {
            swipeRefreshLayout.isEnabled = true
        }

        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        updatePipParams()
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullscreen) {
                    exitFullscreen()
                    return
                }

                val currentUrl = webView.url ?: ""
                val isWatchPage = currentUrl.contains("/watch")

                // 1. Nếu video đang phát hoặc đang ở URL /watch và chưa minimize:
                // gọi window.__braveMiniPlayer.minimize() để thu nhỏ về MiniPlayer trước khi thoát trang/app!
                if (!isMiniPlayerActive && (isVideoPlaying || isWatchPage)) {
                    webView.evaluateJavascript(
                        "(function(){ if (window.__braveMiniPlayer && typeof window.__braveMiniPlayer.minimize === 'function') { return window.__braveMiniPlayer.minimize(); } return 'none'; })();"
                    ) { result ->
                        Log.d(TAG, "Back pressed -> minimize result: $result")
                    }
                    return
                }

                // 2. Nếu MiniPlayer đang mở và người dùng ấn Back: cho phép back trang lịch sử web hoặc đóng MiniPlayer.
                if (isMiniPlayerActive) {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        webView.evaluateJavascript(
                            "(function(){ if (window.__braveMiniPlayer && typeof window.__braveMiniPlayer.close === 'function') { window.__braveMiniPlayer.close(); } })();",
                            null
                        )
                    }
                    return
                }

                // 3. Xử lý mặc định
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /**
     * Hiển thị BottomSheet Cài đặt ứng dụng
     */
    private fun showSettingsDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        dialog.setContentView(dialogView)

        val switchAdBlock = dialogView.findViewById<SwitchMaterial>(R.id.switchAdBlock)
        val switchBackgroundPlay = dialogView.findViewById<SwitchMaterial>(R.id.switchBackgroundPlay)
        val switchAutoPip = dialogView.findViewById<SwitchMaterial>(R.id.switchAutoPip)
        val switchPreloadCache = dialogView.findViewById<SwitchMaterial>(R.id.switchPreloadCache)
        val tvCacheSize = dialogView.findViewById<TextView>(R.id.tvCacheSize)
        val btnClearCache = dialogView.findViewById<MaterialButton>(R.id.btnClearCache)
        val btnReloadInSettings = dialogView.findViewById<MaterialButton>(R.id.btnReloadInSettings)
        val btnCloseSettings = dialogView.findViewById<MaterialButton>(R.id.btnCloseSettings)

        // Khởi tạo trạng thái Switch từ AppSettings
        switchAdBlock.isChecked = AppSettings.isAdBlockEnabled(this)
        switchBackgroundPlay.isChecked = AppSettings.isBackgroundPlayEnabled(this)
        switchAutoPip.isChecked = AppSettings.isAutoPipEnabled(this)
        switchPreloadCache.isChecked = AppSettings.isPreloadCacheEnabled(this)

        fun updateCacheDisplay() {
            val totalBytes = WebResourceCacheManager.getCacheSizeBytes(this) + AppSettings.getCacheSizeBytes(this)
            val mb = totalBytes.toDouble() / (1024.0 * 1024.0)
            tvCacheSize.text = getString(R.string.setting_cache_size_format, mb)
        }

        updateCacheDisplay()

        switchAdBlock.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setAdBlockEnabled(this, isChecked)
            AdBlockEngine.isEnabled = isChecked
            syncSettingsToWeb()
        }

        switchBackgroundPlay.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setBackgroundPlayEnabled(this, isChecked)
            syncSettingsToWeb()
        }

        switchAutoPip.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setAutoPipEnabled(this, isChecked)
            syncSettingsToWeb()
            updatePipParams()
        }

        switchPreloadCache.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setPreloadCacheEnabled(this, isChecked)
            WebResourceCacheManager.isEnabled = isChecked
            syncSettingsToWeb()
        }

        btnClearCache.setOnClickListener {
            WebResourceCacheManager.clearCache(this)
            AppSettings.clearAppCache(this)
            webView.clearCache(true)
            updateCacheDisplay()
        }

        btnReloadInSettings.setOnClickListener {
            dialog.dismiss()
            webView.reload()
        }

        btnCloseSettings.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun syncSettingsToWeb() {
        val settingsJson = AppSettings.toJson(this)
        webView.evaluateJavascript(
            "if (window.__bravePip && window.__bravePip.applySettings) window.__bravePip.applySettings($settingsJson); else window.__braveSettings = $settingsJson;",
            null
        )
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
            val autoPip = AppSettings.isAutoPipEnabled(this)
            builder.setAutoEnterEnabled((isVideoPlaying || isFullscreen) && autoPip)
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
     * Chèn pip_controller.js rồi gọi window.__bravePip.<method>().
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
        swipeRefreshLayout.isEnabled = false
        runPipCommand("enter")
    }

    private fun exitPipLayout() {
        if (!isFullscreen) {
            swipeRefreshLayout.isEnabled = true
        }
        runPipCommand("exit")
    }

    private fun handlePipPlay() {
        isVideoPlaying = true
        updatePipParams()
        runPipCommand("play")
        updateMediaService(isPlaying = true)
    }

    private fun handlePipPause() {
        isVideoPlaying = false
        updatePipParams()
        runPipCommand("pause")
        updateMediaService(isPlaying = false)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val autoPip = AppSettings.isAutoPipEnabled(this)
        if ((isVideoPlaying || isFullscreen) && autoPip) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            swipeRefreshLayout.isEnabled = false
            enterPipLayout()
            webView.postDelayed({ if (isInPipModeCompat()) enterPipLayout() }, 150)
            webView.postDelayed({ if (isInPipModeCompat()) enterPipLayout() }, 450)
            updatePipParams()
        } else {
            exitPipLayout()
            if (!isFullscreen) {
                swipeRefreshLayout.isEnabled = true
            }
        }
    }

    private fun isInPipModeCompat(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode

    override fun onResume() {
        super.onResume()
        if (!isInPipModeCompat()) {
            exitPipLayout()
            if (!isFullscreen) {
                swipeRefreshLayout.isEnabled = true
            }
            runPipCommand("setBackground(false)")
        }
    }

    override fun onStart() {
        super.onStart()
        runPipCommand("setBackground(false)")
        // Dừng MediaService khi người dùng mở lại Activity
        stopMediaService()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        runPipCommand("setBackground(true)")
        // Khi màn hình tắt hoặc app vào background, chỉ chạy MediaService nếu bật Background Play
        val bgPlay = AppSettings.isBackgroundPlayEnabled(this)
        if (isVideoPlaying && bgPlay) {
            startMediaService()
        }
    }

    private fun startMediaService() {
        try {
            val intent = Intent(this, MediaService::class.java).apply {
                action = MediaService.ACTION_START
                putExtra(MediaService.EXTRA_TITLE, currentTitle)
                putExtra(MediaService.EXTRA_CHANNEL, currentChannel)
                putExtra(MediaService.EXTRA_IS_PLAYING, isVideoPlaying)
                putExtra(MediaService.EXTRA_POSITION_MS, positionMs)
                putExtra(MediaService.EXTRA_DURATION_MS, durationMs)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isMediaServiceActive = true
        } catch (_: Exception) {}
    }

    private fun updateMediaService(isPlaying: Boolean? = null) {
        if (!isMediaServiceActive) return
        try {
            val intent = Intent(this, MediaService::class.java).apply {
                action = MediaService.ACTION_UPDATE
                putExtra(MediaService.EXTRA_TITLE, currentTitle)
                putExtra(MediaService.EXTRA_CHANNEL, currentChannel)
                putExtra(MediaService.EXTRA_IS_PLAYING, isPlaying ?: isVideoPlaying)
                putExtra(MediaService.EXTRA_POSITION_MS, positionMs)
                putExtra(MediaService.EXTRA_DURATION_MS, durationMs)
            }
            startService(intent)
        } catch (_: Exception) {}
    }

    private fun stopMediaService() {
        try {
            val intent = Intent(this, MediaService::class.java).apply {
                action = MediaService.ACTION_STOP
            }
            startService(intent)
            isMediaServiceActive = false
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(mediaActionReceiver)
        } catch (_: Exception) {}
        stopMediaService()
        webView.destroy()
        super.onDestroy()
    }

    inner class MediaBridge {
        @JavascriptInterface
        fun openSettings() {
            runOnUiThread {
                showSettingsDialog()
            }
        }

        @JavascriptInterface
        fun onVideoPlaying(title: String, channel: String) {
            runOnUiThread {
                isVideoPlaying = true
                if (title.isNotEmpty()) currentTitle = title
                if (channel.isNotEmpty()) currentChannel = channel
                updatePipParams()
                updateMediaService(isPlaying = true)
            }
        }

        @JavascriptInterface
        fun onVideoPaused() {
            runOnUiThread {
                isVideoPlaying = false
                updatePipParams()
                updateMediaService(isPlaying = false)
            }
        }

        @JavascriptInterface
        fun onMediaStateChanged(playing: Boolean, title: String, channel: String, posMs: Long, durMs: Long) {
            runOnUiThread {
                isVideoPlaying = playing
                if (title.isNotEmpty()) currentTitle = title
                if (channel.isNotEmpty()) currentChannel = channel
                if (posMs >= 0) positionMs = posMs
                if (durMs > 0) durationMs = durMs
                updatePipParams()
                updateMediaService()
            }
        }

        @JavascriptInterface
        fun onMiniPlayerStateChanged(isMinimized: Boolean) {
            runOnUiThread {
                isMiniPlayerActive = isMinimized
                Log.d(TAG, "onMiniPlayerStateChanged: isMinimized=$isMinimized")
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
