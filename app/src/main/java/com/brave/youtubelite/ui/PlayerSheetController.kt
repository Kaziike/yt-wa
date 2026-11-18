package com.brave.youtubelite.ui

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.brave.youtubelite.databinding.ActivityMainBinding
import com.brave.youtubelite.playback.PlaybackState
import kotlin.math.abs

/**
 * Quản lý chuyên biệt Tầng Phát Video Trượt (Sliding Video Player Sheet & MiniPlayer):
 * - Xử lý trạng thái HIDDEN / EXPANDED / COLLAPSED.
 * - Sửa lỗi cử chỉ cảm ứng (tách biệt ACTION_UP và ACTION_CANCEL, kẹp biên translationY >= 0).
 * - Tự động đo chiều cao thanh tab đáy thực tế của trang bên dưới thay vì gán cứng 48dp.
 * - Tự động căn chỉnh lại tỷ lệ & kích thước khi xoay màn hình (onConfigurationChanged) hoặc thoát PiP
 *   thông qua OnLayoutChangeListener thay vì postDelayed(450ms) cảm tính.
 */
class PlayerSheetController(
    private val binding: ActivityMainBinding,
    private val resources: Resources,
    private val onRequestTogglePlayPause: () -> Unit,
    private val onRequestResync: () -> Unit,
    private val onPlayerClosed: () -> Unit
) {

    enum class PanelState {
        HIDDEN, EXPANDED, COLLAPSED
    }

    var state: PanelState = PanelState.HIDDEN
        private set

    private var wasCollapsedBeforePip = false
    private var lastMiniIconPlaying: Boolean? = null
    private var lastBottomNavPx: Int = dpToPx(48f).toInt()
    private var pendingLayoutListener: View.OnLayoutChangeListener? = null

    init {
        setupGestures()
    }

    val isVisible: Boolean
        get() = state != PanelState.HIDDEN

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        binding.btnMinimizePlayer.setOnClickListener {
            collapse()
        }

        var dragStartY = 0f
        var isDragging = false
        binding.playerDragArea.setOnTouchListener { _, event ->
            if (state != PanelState.EXPANDED) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - dragStartY
                    if (deltaY > dpToPx(12f) || isDragging) {
                        isDragging = true
                        binding.playerPanel.translationY = deltaY.coerceAtLeast(0f)
                    }
                    isDragging
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        val deltaY = event.rawY - dragStartY
                        isDragging = false
                        if (deltaY > dpToPx(80f)) collapse() else expand(fromDragSnapBack = true)
                    } else {
                        collapse()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // ACTION_CANCEL (ví dụ vào PiP hoặc thông báo hệ thống): trả về EXPANDED, KHÔNG thu nhỏ nhầm!
                    if (isDragging) {
                        isDragging = false
                        binding.playerPanel.animate()
                            .translationY(0f)
                            .setDuration(150)
                            .start()
                    }
                    true
                }
                else -> false
            }
        }

        var miniStartX = 0f
        var miniStartY = 0f
        var miniSwipedUp = false
        binding.miniPlayerBar.setOnTouchListener { _, event ->
            if (state != PanelState.COLLAPSED) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    miniStartX = event.rawX
                    miniStartY = event.rawY
                    miniSwipedUp = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - miniStartY
                    if (!miniSwipedUp && dy < -dpToPx(20f)) {
                        miniSwipedUp = true
                        expand()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!miniSwipedUp) {
                        val dx = abs(event.rawX - miniStartX)
                        val dy = abs(event.rawY - miniStartY)
                        if (dx < dpToPx(14f) && dy < dpToPx(14f)) {
                            expand()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    miniSwipedUp = false
                    false
                }
                else -> false
            }
        }

        binding.btnMiniPlayerPlayPause.setOnClickListener {
            onRequestTogglePlayPause()
        }

        binding.btnMiniPlayerClose.setOnClickListener {
            close()
        }
    }

    fun open(watchUrl: String) {
        clearPendingLayoutListener()
        state = PanelState.EXPANDED
        wasCollapsedBeforePip = false

        val params = binding.playerPanel.layoutParams as FrameLayout.LayoutParams
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        params.gravity = Gravity.TOP
        params.bottomMargin = 0
        binding.playerPanel.layoutParams = params
        binding.playerPanel.setBackgroundColor(Color.BLACK)

        binding.playerPanel.animate().cancel()
        binding.playerPanel.visibility = View.VISIBLE
        binding.playerPanel.translationY = 0f

        resetPlayerWebViewTransform()
        unlockPlayerWebViewSize()

        binding.tvMiniPlayerTitle.text = "LiteTube"
        binding.tvMiniPlayerChannel.text = ""
        binding.miniPlayerBar.visibility = View.GONE
        binding.btnMinimizePlayer.visibility = View.VISIBLE
        binding.playerDragArea.visibility = View.VISIBLE

        binding.playerWebView.loadUrl(watchUrl)
    }

    fun collapse() {
        if (state == PanelState.COLLAPSED || state == PanelState.HIDDEN) return
        clearPendingLayoutListener()
        state = PanelState.COLLAPSED

        val fullW = binding.mainContainer.width
        val fullH = binding.mainContainer.height
        lockPlayerWebViewSize(fullW, fullH)

        binding.miniPlayerBar.visibility = View.VISIBLE
        binding.miniPlayerBar.alpha = 1f
        binding.btnMinimizePlayer.visibility = View.GONE
        binding.playerDragArea.visibility = View.GONE

        // Đo đồng thời chiều cao thanh tab đáy của trang Browse và khung video thật của Player
        binding.webView.evaluateJavascript(
            "(function(){return window.__liteTube?String(window.__liteTube.getBottomBarHeight()):'48';})()"
        ) { navHeightRes ->
            val navCssPx = navHeightRes?.trim()?.trim('"')?.toFloatOrNull() ?: 48f
            lastBottomNavPx = (navCssPx * resources.displayMetrics.density)
                .coerceIn(0f, dpToPx(64f))
                .toInt()

            binding.playerWebView.evaluateJavascript(
                "(function(){return window.__liteTube?window.__liteTube.measureVideoRect():'';})()"
            ) { rectRes ->
                if (state != PanelState.COLLAPSED) return@evaluateJavascript
                animateToMini(fullW, fullH, lastBottomNavPx.toFloat(), parseVideoRect(rectRes), animate = true)
            }
        }
    }

    fun expand(fromDragSnapBack: Boolean = false) {
        if (state == PanelState.HIDDEN) return
        if (state == PanelState.EXPANDED && !fromDragSnapBack) return
        clearPendingLayoutListener()
        val wasCollapsed = state == PanelState.COLLAPSED
        state = PanelState.EXPANDED

        val fullW = binding.mainContainer.width
        val fullH = binding.mainContainer.height
        val miniH = dpToPx(64f)

        if (wasCollapsed) {
            lockPlayerWebViewSize(fullW, fullH)
            val startTransY = (fullH - lastBottomNavPx - miniH).coerceAtLeast(0f)
            binding.playerPanel.animate().cancel()
            val params = binding.playerPanel.layoutParams as FrameLayout.LayoutParams
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.TOP
            params.bottomMargin = 0
            binding.playerPanel.layoutParams = params
            binding.playerPanel.translationY = startTransY
            binding.playerPanel.setBackgroundColor(Color.BLACK)
        }

        binding.miniPlayerBar.visibility = View.GONE
        binding.btnMinimizePlayer.visibility = View.VISIBLE
        binding.playerDragArea.visibility = View.VISIBLE

        binding.playerPanel.animate()
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (state != PanelState.EXPANDED) return@withEndAction
                unlockPlayerWebViewSize()
                onRequestResync()
            }
            .start()

        binding.playerWebView.pivotX = 0f
        binding.playerWebView.pivotY = 0f
        binding.playerWebView.animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationX(0f)
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun close() {
        if (state == PanelState.HIDDEN) return
        clearPendingLayoutListener()
        state = PanelState.HIDDEN
        wasCollapsedBeforePip = false
        lastMiniIconPlaying = null

        val targetY = binding.mainContainer.height.toFloat()
        binding.playerPanel.animate().cancel()
        binding.playerPanel.animate()
            .translationY(targetY)
            .setDuration(200)
            .withEndAction {
                binding.playerPanel.visibility = View.GONE
                val params = binding.playerPanel.layoutParams as FrameLayout.LayoutParams
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                params.gravity = Gravity.TOP
                params.bottomMargin = 0
                binding.playerPanel.layoutParams = params
                binding.playerPanel.translationY = 0f
                binding.playerPanel.setBackgroundColor(Color.BLACK)

                resetPlayerWebViewTransform()
                unlockPlayerWebViewSize()
                binding.playerWebView.loadUrl("about:blank")
                onPlayerClosed()
            }
            .start()
    }

    fun applyPlaybackState(playback: PlaybackState) {
        if (binding.tvMiniPlayerTitle.text.toString() != playback.title) {
            binding.tvMiniPlayerTitle.text = playback.title
        }
        if (binding.tvMiniPlayerChannel.text.toString() != playback.channel) {
            binding.tvMiniPlayerChannel.text = playback.channel
        }
        if (lastMiniIconPlaying != playback.isPlaying) {
            lastMiniIconPlaying = playback.isPlaying
            binding.btnMiniPlayerPlayPause.setImageResource(
                if (playback.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
        }
    }

    /**
     * Chuẩn bị giao diện trước khi bước vào cửa sổ PiP:
     * Nếu đang ở MiniPlayer, tạm thời đưa playerPanel về full-screen và ẩn Browse để PiP chỉ hiển thị video.
     */
    fun prepareForPip() {
        clearPendingLayoutListener()
        binding.progressBar.visibility = View.GONE
        binding.swipeRefreshLayout.isEnabled = false

        if (state == PanelState.COLLAPSED) {
            wasCollapsedBeforePip = true
            binding.swipeRefreshLayout.visibility = View.GONE

            val params = binding.playerPanel.layoutParams as FrameLayout.LayoutParams
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.TOP
            params.bottomMargin = 0
            binding.playerPanel.animate().cancel()
            binding.playerPanel.layoutParams = params
            binding.playerPanel.translationY = 0f
            binding.playerPanel.setBackgroundColor(Color.BLACK)

            binding.miniPlayerBar.visibility = View.GONE
            binding.btnMinimizePlayer.visibility = View.GONE
            binding.playerDragArea.visibility = View.GONE

            resetPlayerWebViewTransform()
            unlockPlayerWebViewSize()
        } else if (state == PanelState.EXPANDED) {
            binding.btnMinimizePlayer.visibility = View.GONE
            binding.playerDragArea.visibility = View.GONE
        }
    }

    /**
     * Khôi phục giao diện sau khi thoát khỏi cửa sổ PiP bằng sự kiện kích thước thật của mainContainer.
     */
    fun restoreFromPip(restoreMiniIfWasCollapsed: Boolean, isFullscreen: Boolean) {
        clearPendingLayoutListener()
        binding.swipeRefreshLayout.visibility = View.VISIBLE
        if (!isFullscreen) {
            binding.swipeRefreshLayout.isEnabled = true
        }

        if (wasCollapsedBeforePip) {
            wasCollapsedBeforePip = false
            state = PanelState.EXPANDED
            binding.btnMinimizePlayer.visibility = View.VISIBLE
            binding.playerDragArea.visibility = View.VISIBLE

            if (restoreMiniIfWasCollapsed && !isFullscreen) {
                waitForFullWindowThen {
                    if (state == PanelState.EXPANDED) {
                        collapse()
                    }
                }
            }
        } else if (state == PanelState.EXPANDED) {
            binding.btnMinimizePlayer.visibility = View.VISIBLE
            binding.playerDragArea.visibility = View.VISIBLE
        }
    }

    /**
     * Tính toán lại kích thước khóa và vị trí thu nhỏ của MiniPlayer khi xoay màn hình / chia đôi màn hình.
     */
    fun onConfigurationChanged() {
        if (state != PanelState.COLLAPSED) return
        clearPendingLayoutListener()
        val listener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                val newW = right - left
                val newH = bottom - top
                if (newW <= 0 || newH <= 0) return
                if (newW == (oldRight - oldLeft) && newH == (oldBottom - oldTop)) return
                binding.mainContainer.removeOnLayoutChangeListener(this)
                pendingLayoutListener = null
                if (state == PanelState.COLLAPSED) {
                    lockPlayerWebViewSize(newW, newH)
                    binding.playerWebView.evaluateJavascript(
                        "(function(){return window.__liteTube?window.__liteTube.measureVideoRect():'';})()"
                    ) { rectRes ->
                        if (state == PanelState.COLLAPSED) {
                            animateToMini(newW, newH, lastBottomNavPx.toFloat(), parseVideoRect(rectRes), animate = false)
                        }
                    }
                }
            }
        }
        pendingLayoutListener = listener
        binding.mainContainer.addOnLayoutChangeListener(listener)
    }

    private fun waitForFullWindowThen(action: () -> Unit) {
        val minExpandedHeightPx = dpToPx(360f).toInt()
        if (binding.mainContainer.height >= minExpandedHeightPx) {
            binding.mainContainer.post { action() }
            return
        }
        val listener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                if (bottom - top >= minExpandedHeightPx) {
                    binding.mainContainer.removeOnLayoutChangeListener(this)
                    pendingLayoutListener = null
                    binding.mainContainer.post { action() }
                }
            }
        }
        pendingLayoutListener = listener
        binding.mainContainer.addOnLayoutChangeListener(listener)
    }

    private fun clearPendingLayoutListener() {
        pendingLayoutListener?.let { binding.mainContainer.removeOnLayoutChangeListener(it) }
        pendingLayoutListener = null
    }

    private fun animateToMini(
        fullW: Int,
        fullH: Int,
        bottomNavH: Float,
        rect: FloatArray?,
        animate: Boolean
    ) {
        val miniH = dpToPx(64f)
        val boxW = dpToPx(104f)
        val boxH = dpToPx(58f)
        val boxLeft = dpToPx(4f)
        val targetY = (fullH - bottomNavH - miniH).coerceAtLeast(0f)

        var vLeft = 0f
        var vTop = 0f
        var vW = fullW.toFloat().coerceAtLeast(1f)
        var vH = vW * 9f / 16f
        if (rect != null) {
            val ratio = if (rect[4] > 0f) fullW / rect[4] else resources.displayMetrics.density
            vLeft = rect[0] * ratio
            vTop = rect[1] * ratio
            vW = (rect[2] * ratio).coerceAtLeast(1f)
            vH = (rect[3] * ratio).coerceAtLeast(1f)
        }

        val scale = minOf(boxW / vW, boxH / vH)
        val tx = boxLeft + (boxW - vW * scale) / 2f - vLeft * scale
        val ty = (miniH - vH * scale) / 2f - vTop * scale

        val applyCollapsedBounds = {
            if (state == PanelState.COLLAPSED) {
                val params = binding.playerPanel.layoutParams as FrameLayout.LayoutParams
                params.height = miniH.toInt()
                params.gravity = Gravity.BOTTOM
                params.bottomMargin = bottomNavH.toInt()
                binding.playerPanel.layoutParams = params
                binding.playerPanel.translationY = 0f
                binding.playerPanel.setBackgroundColor(Color.parseColor("#212121"))
            }
        }

        binding.playerWebView.pivotX = 0f
        binding.playerWebView.pivotY = 0f

        if (animate) {
            binding.playerPanel.animate()
                .translationY(targetY)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { applyCollapsedBounds() }
                .start()

            binding.playerWebView.animate()
                .scaleX(scale)
                .scaleY(scale)
                .translationX(tx)
                .translationY(ty)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            applyCollapsedBounds()
            binding.playerWebView.scaleX = scale
            binding.playerWebView.scaleY = scale
            binding.playerWebView.translationX = tx
            binding.playerWebView.translationY = ty
        }
    }

    private fun parseVideoRect(result: String?): FloatArray? {
        return try {
            val raw = result?.trim()?.trim('"') ?: return null
            if (raw.isEmpty() || raw == "null") return null
            val parts = raw.split(",").map { it.trim().toFloat() }
            if (parts.size < 5 || parts[2] < 20f || parts[3] < 20f) null else parts.toFloatArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun lockPlayerWebViewSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val lp = binding.playerWebView.layoutParams as FrameLayout.LayoutParams
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
            binding.playerWebView.layoutParams = lp
        }
    }

    private fun unlockPlayerWebViewSize() {
        val lp = binding.playerWebView.layoutParams as FrameLayout.LayoutParams
        if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT || lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            binding.playerWebView.layoutParams = lp
        }
    }

    private fun resetPlayerWebViewTransform() {
        binding.playerWebView.animate().cancel()
        binding.playerWebView.pivotX = 0f
        binding.playerWebView.pivotY = 0f
        binding.playerWebView.scaleX = 1f
        binding.playerWebView.scaleY = 1f
        binding.playerWebView.translationX = 0f
        binding.playerWebView.translationY = 0f
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    fun release() {
        clearPendingLayoutListener()
        binding.playerPanel.animate().cancel()
        binding.playerWebView.animate().cancel()
    }
}
