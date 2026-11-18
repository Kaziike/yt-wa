package com.brave.youtubelite.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import com.brave.youtubelite.databinding.ActivityMainBinding

/**
 * Quản lý chế độ xem video toàn màn hình (HTML5 CustomView) cho cả Player WebView và Browse WebView:
 * - Khôi phục SCREEN_ORIENTATION_UNSPECIFIED khi thoát thay vì khóa cứng màn hình dọc (PORTRAIT).
 * - Đảm bảo giải phóng sạch sẽ nếu đóng cửa sổ PiP khi đang ở trạng thái Fullscreen.
 */
class FullscreenController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val isPlayerPanelVisible: () -> Boolean,
    private val isInPipMode: () -> Boolean,
    private val onFullscreenChanged: () -> Unit
) {

    var isFullscreen: Boolean = false
        private set

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    fun enter(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (view == null) return
        if (customView != null) {
            exit()
        }

        customView = view
        customViewCallback = callback
        isFullscreen = true
        binding.swipeRefreshLayout.isEnabled = false

        binding.webView.visibility = View.GONE
        binding.playerPanel.visibility = View.GONE
        binding.customViewContainer.visibility = View.VISIBLE
        binding.customViewContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        @Suppress("DEPRECATION")
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onFullscreenChanged()
    }

    fun exit() {
        val current = customView ?: return
        isFullscreen = false
        binding.customViewContainer.removeView(current)
        customView = null
        try {
            customViewCallback?.onCustomViewHidden()
        } catch (_: Exception) {}
        customViewCallback = null

        binding.customViewContainer.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        if (isPlayerPanelVisible()) {
            binding.playerPanel.visibility = View.VISIBLE
        }
        if (!isInPipMode()) {
            binding.swipeRefreshLayout.isEnabled = true
        }

        @Suppress("DEPRECATION")
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onFullscreenChanged()
    }
}
