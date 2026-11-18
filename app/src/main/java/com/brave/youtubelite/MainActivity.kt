package com.brave.youtubelite

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.brave.youtubelite.databinding.ActivityMainBinding
import com.brave.youtubelite.network.AdBlockEngine
import com.brave.youtubelite.network.AppWebViewClient
import com.brave.youtubelite.network.UrlUtils
import com.brave.youtubelite.network.WebAssetRepository
import com.brave.youtubelite.network.WebResourceCacheManager
import com.brave.youtubelite.playback.PlaybackCoordinator
import com.brave.youtubelite.playback.WebBridge
import com.brave.youtubelite.settings.AppSettings
import com.brave.youtubelite.ui.FullscreenController
import com.brave.youtubelite.ui.PipController
import com.brave.youtubelite.ui.PlayerSheetController
import com.brave.youtubelite.ui.SettingsBottomSheet

/**
 * MainActivity tinh gọn (~240 dòng):
 * Chỉ đảm nhiệm điều phối vòng đời Activity, kết nối các Controller chuyên biệt,
 * xử lý Deep Link singleTask (onNewIntent) và dọn dẹp tài nguyên chống rò rỉ bộ nhớ.
 */
class MainActivity : AppCompatActivity(), WebBridge.Listener {

    companion object {
        private const val HOME_URL = "https://m.youtube.com"
        private const val BRIDGE_NAME = "AndroidBridge"
        private const val WEB_LOG_TAG = "WebConsole"
        private const val REQ_POST_NOTIFICATIONS = 101
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var browseClient: AppWebViewClient
    private lateinit var playerClient: AppWebViewClient
    private lateinit var browseBridge: WebBridge
    private lateinit var playerBridge: WebBridge

    private lateinit var playerSheet: PlayerSheetController
    private lateinit var fullscreenController: FullscreenController
    private lateinit var playbackCoordinator: PlaybackCoordinator
    private lateinit var pipController: PipController

    private var browseHomeLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AdBlockEngine.isEnabled = AppSettings.isAdBlockEnabled(this)
        WebResourceCacheManager.isEnabled = AppSettings.isPreloadCacheEnabled(this)
        AdBlockEngine.init(applicationContext)
        WebAssetRepository.init(applicationContext)
        WebResourceCacheManager.init(applicationContext)

        requestNotificationPermission()
        setupControllers()
        setupSwipeRefresh()
        setupBrowseWebView()
        setupPlayerWebView()
        setupBackNavigation()

        handleIncomingIntent(intent, isInitialLaunch = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent, isInitialLaunch = false)
    }

    private fun setupControllers() {
        playerSheet = PlayerSheetController(
            binding = binding,
            resources = resources,
            onRequestTogglePlayPause = { playbackCoordinator.togglePlayPause() },
            onRequestResync = { playbackCoordinator.runPipCommand("resync()") },
            onPlayerClosed = {
                playbackCoordinator.resetOnPlayerClosed()
                pipController.updateParams()
            }
        )

        fullscreenController = FullscreenController(
            activity = this,
            binding = binding,
            isPlayerPanelVisible = { playerSheet.isVisible },
            isInPipMode = { pipController.isInPipMode() },
            onFullscreenChanged = { pipController.updateParams() }
        )

        playbackCoordinator = PlaybackCoordinator(
            context = this,
            browseWebView = binding.webView,
            playerWebView = binding.playerWebView,
            isPlayerLayerActive = { playerSheet.isVisible },
            isInPipMode = { pipController.isInPipMode() },
            onStateChanged = { state, shouldUpdatePip ->
                playerSheet.applyPlaybackState(state)
                if (shouldUpdatePip) {
                    pipController.updateParams()
                }
            }
        )

        pipController = PipController(
            activity = this,
            playerSheet = playerSheet,
            fullscreenController = fullscreenController,
            playback = playbackCoordinator
        )
        pipController.register()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.parseColor("#212121"))
        binding.swipeRefreshLayout.setColorSchemeColors(
            Color.parseColor("#FF0000"),
            Color.parseColor("#FF5722")
        )
        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.webView.reload()
        }
        binding.swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            binding.webView.scrollY > 0
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebSettings(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            offscreenPreRaster = false
            userAgentString = userAgentString.replace("; wv", "")
        }
        view.setLayerType(View.LAYER_TYPE_NONE, null)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
    }

    private fun setupBrowseWebView() {
        configureWebSettings(binding.webView)
        browseBridge = WebBridge(fromPlayer = false, listener = this)
        binding.webView.addJavascriptInterface(browseBridge, BRIDGE_NAME)

        browseClient = AppWebViewClient(applicationContext, isBrowseMode = true).apply {
            onInterceptWatchUrl = { normalizedWatchUrl ->
                openWatchVideo(normalizedWatchUrl)
                true
            }
            onPageFinishedCallback = {
                runOnUiThread { binding.swipeRefreshLayout.isRefreshing = false }
            }
        }
        browseClient.installDocumentStartScripts(binding.webView)
        binding.webView.webViewClient = browseClient

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100 && !pipController.isInPipMode()) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = newProgress
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                fullscreenController.enter(view, callback)
            }

            override fun onHideCustomView() {
                fullscreenController.exit()
            }
        }
    }

    private fun setupPlayerWebView() {
        configureWebSettings(binding.playerWebView)
        playerBridge = WebBridge(fromPlayer = true, listener = this)
        binding.playerWebView.addJavascriptInterface(playerBridge, BRIDGE_NAME)

        playerClient = AppWebViewClient(applicationContext, isBrowseMode = false)
        playerClient.installDocumentStartScripts(binding.playerWebView)
        binding.playerWebView.webViewClient = playerClient

        binding.playerWebView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.d(WEB_LOG_TAG, "${it.message()} [${it.sourceId()}:${it.lineNumber()}]")
                }
                return true
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                fullscreenController.enter(view, callback)
            }

            override fun onHideCustomView() {
                fullscreenController.exit()
            }
        }
    }

    /**
     * Điều phối thông minh URL từ Intent / Deep Link:
     * - Luôn giữ trang chủ YouTube ở Browse WebView tầng dưới.
     * - Nếu link đầu vào là video (/watch hoặc youtu.be), mở thẳng trên lớp Player nổi để giữ trọn tính năng MiniPlayer!
     */
    private fun handleIncomingIntent(incomingIntent: Intent?, isInitialLaunch: Boolean) {
        val rawUrl = incomingIntent?.dataString
        val watchUrl = UrlUtils.normalizeWatchUrl(rawUrl)

        if (watchUrl != null) {
            if (!browseHomeLoaded) {
                browseHomeLoaded = true
                binding.webView.loadUrl(HOME_URL)
            }
            openWatchVideo(watchUrl)
            return
        }

        val parsed = UrlUtils.parseHttpUrl(rawUrl)
        if (parsed != null && UrlUtils.isYouTubeHost(parsed.host)) {
            browseHomeLoaded = true
            if (playerSheet.state == PlayerSheetController.PanelState.EXPANDED) {
                playerSheet.collapse()
            }
            binding.webView.loadUrl(rawUrl!!)
        } else if (isInitialLaunch && !browseHomeLoaded) {
            browseHomeLoaded = true
            binding.webView.loadUrl(HOME_URL)
        }
    }

    private fun openWatchVideo(rawOrNormalizedUrl: String) {
        val safeWatchUrl = UrlUtils.normalizeWatchUrl(rawOrNormalizedUrl) ?: return
        runOnUiThread {
            if (fullscreenController.isFullscreen) {
                fullscreenController.exit()
            }
            playbackCoordinator.resetForNewVideo(safeWatchUrl)
            playerSheet.open(safeWatchUrl)
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    fullscreenController.isFullscreen -> fullscreenController.exit()
                    playerSheet.state == PlayerSheetController.PanelState.EXPANDED -> playerSheet.collapse()
                    playerSheet.state == PlayerSheetController.PanelState.COLLAPSED -> playerSheet.close()
                    binding.webView.canGoBack() -> binding.webView.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun syncAllSettingsToWeb() {
        browseClient.updateSettingsBootstrap(binding.webView)
        playerClient.updateSettingsBootstrap(binding.playerWebView)
        browseClient.syncRuntimeSettingsAndStyles(binding.webView)
        playerClient.syncRuntimeSettingsAndStyles(binding.playerWebView)
        playbackCoordinator.syncWebViewKeepAlive()
        pipController.updateParams()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_POST_NOTIFICATIONS
                )
            }
        }
    }

    // ==============================================================
    // WebBridge.Listener callbacks (chuyển về UI Thread an toàn)
    // ==============================================================
    override fun onOpenWatchLayer(rawUrl: String) {
        openWatchVideo(rawUrl)
    }

    override fun onOpenSettings() {
        runOnUiThread {
            SettingsBottomSheet.show(
                activity = this,
                onSettingsChanged = { syncAllSettingsToWeb() },
                onClearWebViewsCache = {
                    binding.webView.clearCache(true)
                    binding.playerWebView.clearCache(true)
                },
                onReloadActiveWebView = {
                    if (playerSheet.isVisible) {
                        binding.playerWebView.reload()
                    } else {
                        binding.webView.reload()
                    }
                }
            )
        }
    }

    override fun onMediaStateReceived(
        fromPlayer: Boolean,
        playing: Boolean,
        videoId: String,
        title: String,
        channel: String,
        posMs: Long,
        durMs: Long,
        videoWidth: Int,
        videoHeight: Int
    ) {
        runOnUiThread {
            playbackCoordinator.handleMediaStateFromWeb(
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

    // ==============================================================
    // Activity Lifecycle & PiP / Configuration Callbacks
    // ==============================================================
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        playerSheet.onConfigurationChanged()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pipController.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipController.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun onStart() {
        super.onStart()
        if (!pipController.isInPipMode() && !pipController.isSwitchingToAudioOnly) {
            playbackCoordinator.onForegroundEntered()
        }
    }

    override fun onResume() {
        super.onResume()
        pipController.onActivityResumed()
    }

    override fun onStop() {
        super.onStop()
        pipController.onActivityStopped()
    }

    override fun onDestroy() {
        pipController.unregister()
        playerSheet.release()
        playbackCoordinator.release()
        browseBridge.detach()
        playerBridge.detach()
        browseClient.release()
        playerClient.release()
        binding.webView.safeDestroy(BRIDGE_NAME)
        binding.playerWebView.safeDestroy(BRIDGE_NAME)
        super.onDestroy()
    }
}
