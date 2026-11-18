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
 * - Hỗ trợ kéo từ trên xuống trên TOÀN BỘ khu vực video (và thanh topbar) thông qua DraggablePlayerLayout,
 *   hoặc chạm vào Logo YouTube bên trái thanh topbar để thu nhỏ về MiniPlayer.
 * - Bảo toàn nguyên vẹn thao tác chạm điều khiển video, tua ngang seekbar và cuộn danh sách gợi ý bên dưới.
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

    var isInPipMode: Boolean = false
        private set

    private var wasCollapsedBeforePip = false
    private var lastMiniIconPlaying: Boolean? = null
    private var lastBottomNavPx: Int = dpToPx(48f).toInt()
    private var measuredVideoBottomPx: Float = 0f
    private var pendingLayoutListener: View.OnLayoutChangeListener? = null
    private var pipViewportListener: View.OnLayoutChangeListener? = null

    init {
        setupGestures()
    }

    val isVisible: Boolean
        get() = state != PanelState.HIDDEN

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        // Kéo vuốt xuống trên toàn bộ khu vực video & thanh topbar để thu về MiniPlayer
        binding.playerPanel.canDragDown = {
            state == PanelState.EXPANDED && !isInPipMode
        }
        binding.playerPanel.videoZoneBottomProvider = {
            val w = binding.playerPanel.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val defaultZoneBottom = dpToPx(52f) + w * 9f / 16f
            maxOf(defaultZoneBottom, measuredVideoBottomPx)
        }
        binding.playerPanel.onDragOffset = { deltaY ->
            if (state == PanelState.EXPANDED && !isInPipMode) {
                binding.playerPanel.translationY = deltaY
            }
        }
        binding.playerPanel.onDragFinished = { shouldCollapse ->
            if (state == PanelState.EXPANDED && !isInPipMode) {
                if (shouldCollapse) {
                    collapse()
                } else {
                    expand(fromDragSnapBack = true)
                }
            }
        }

        // Chạm hoặc vuốt ngược lên trên thanh MiniPlayer để bung to lại
        var miniStartX = 0f
        var miniStartY = 0f
        var miniSwipedUp = false
        binding.miniPlayerBar.setOnTouchListener { _, event ->
            if (state != PanelState.COLLAPSED || isInPipMode) return@setOnTouchListener false
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
        clearPipViewportListener()
        isInPipMode = false
        state = PanelState.EXPANDED
        wasCollapsedBeforePip = false
        measuredVideoBottomPx = 0f

        binding.swipeRefreshLayout.visibility = View.VISIBLE
        val params = binding.playerPanel.layoutParams as FrameLayout.LayoutParams
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        params.gravity = Gravity.TOP
        params.topMargin = 0
        params.bottomMargin = 0
        binding.playerPanel.layoutParams = params
        binding.playerPanel.setBackgroundColor(Color.BLACK)

        binding.playerPanel.animate().withEndAction(null).cancel()
        binding.playerPanel.visibility = View.VISIBLE
        binding.playerPanel.translationY = 0f

        resetPlayerWebViewTransform()
        unlockPlayerWebViewSize()

        binding.tvMiniPlayerTitle.text = "LiteTube"
        binding.tvMiniPlayerChannel.text = ""
        binding.miniPlayerBar.visibility = View.GONE

        binding.playerWebView.loadUrl(watchUrl)
    }

    fun collapse() {
        if (isInPipMode || state == PanelState.COLLAPSED || state == PanelState.HIDDEN) return
        clearPendingLayoutListener()
        state = PanelState.COLLAPSED

        val fullW = binding.mainContainer.width
        val fullH = binding.mainContainer.height
        lockPlayerWebViewSize(fullW, fullH)

        binding.miniPlayerBar.visibility = View.VISIBLE
        binding.miniPlayerBar.alpha = 1f

        binding.webView.evaluateJavascript(
            "(function(){return window.__liteTube?String(window.__liteTube.getBottomBarHeight()):'48';})()"
        ) { navHeightRes ->
            if (isInPipMode || state != PanelState.COLLAPSED) return@evaluateJavascript
            val navCssPx = navHeightRes?.trim()?.trim('"')?.toFloatOrNull() ?: 48f
            lastBottomNavPx = (navCssPx * resources.displayMetrics.density)
                .coerceIn(0f, dpToPx(64f))
                .toInt()

            binding.playerWebView.evaluateJavascript(
                "(function(){return window.__liteTube?window.__liteTube.measureVideoRect():'';})()"
            ) { rectRes ->
                if (isInPipMode || state != PanelState.COLLAPSED) return@evaluateJavascript
                val parsedRect = parseVideoRect(rectRes)
                updateMeasuredVideoBottom(fullW, parsedRect)
                animateToMini(fullW, fullH, lastBottomNavPx.toFloat(), parsedRect, animate = true)
            }
        }
    }

    fun expand(fromDragSnapBack: Boolean = false) {
        if (state == PanelState.HIDDEN || isInPipMode) return
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
            binding.playerPanel.animate().withEndAction(null).cancel()
            val params = binding.playerPanel.layoutParams as FrameLayout.LayoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.TOP
            params.topMargin = 0
            params.bottomMargin = 0
            binding.playerPanel.layoutParams = params
            binding.playerPanel.translationY = startTransY
            binding.playerPanel.setBackgroundColor(Color.BLACK)
        }

        binding.miniPlayerBar.visibility = View.GONE

        binding.playerPanel.animate()
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (state != PanelState.EXPANDED || isInPipMode) return@withEndAction
                unlockPlayerWebViewSize()
                onRequestResync()
                refreshMeasuredVideoBounds()
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
        clearPipViewportListener()
        isInPipMode = false
        state = PanelState.HIDDEN
        wasCollapsedBeforePip = false
        lastMiniIconPlaying = null
        measuredVideoBottomPx = 0f

        val targetY = binding.mainContainer.height.toFloat()
        binding.playerPanel.animate().withEndAction(null).cancel()
        binding.playerPanel.animate()
            .translationY(targetY)
            .setDuration(200)
            .withEndAction {
                binding.playerPanel.visibility = View.GONE
                val params = binding.playerPanel.layoutParams as FrameLayout.LayoutParams
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                params.gravity = Gravity.TOP
                params.topMargin = 0
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
            if (playback.isPlaying && state == PanelState.EXPANDED && !isInPipMode) {
                refreshMeasuredVideoBounds()
            }
        }
    }

    private fun refreshMeasuredVideoBounds() {
        val w = binding.mainContainer.width
        if (w <= 0) return
        binding.playerWebView.evaluateJavascript(
            "(function(){return window.__liteTube?window.__liteTube.measureVideoRect():'';})()"
        ) { rectRes ->
            updateMeasuredVideoBottom(w, parseVideoRect(rectRes))
        }
    }

    private fun updateMeasuredVideoBottom(fullW: Int, rect: FloatArray?) {
        if (rect == null || fullW <= 0) return
        val ratio = if (rect[4] > 0f) fullW / rect[4] else resources.displayMetrics.density
        val bottomPx = (rect[1] + rect[3]) * ratio
        if (bottomPx > dpToPx(80f)) {
            measuredVideoBottomPx = bottomPx
        }
    }

    fun prepareForPip(onPipViewportChanged: (() -> Unit)? = null) {
        clearPendingLayoutListener()
        isInPipMode = true
        binding.progressBar.visibility = View.GONE
        binding.swipeRefreshLayout.isEnabled = false

        if (state == PanelState.HIDDEN) {
            return
        }

        if (state == PanelState.COLLAPSED) {
            wasCollapsedBeforePip = true
            state = PanelState.EXPANDED
        }

        binding.swipeRefreshLayout.visibility = View.GONE

        binding.playerPanel.animate().withEndAction(null).cancel()
        binding.playerPanel.clearAnimation()
        val params = binding.playerPanel.layoutParams as FrameLayout.LayoutParams
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        params.gravity = Gravity.TOP
        params.topMargin = 0
        params.bottomMargin = 0
        binding.playerPanel.layoutParams = params
        binding.playerPanel.translationX = 0f
        binding.playerPanel.translationY = 0f
        binding.playerPanel.setBackgroundColor(Color.BLACK)
        binding.playerPanel.visibility = View.VISIBLE

        binding.miniPlayerBar.visibility = View.GONE

        resetPlayerWebViewTransform()
        binding.playerWebView.scrollTo(0, 0)
        unlockPlayerWebViewSize()
        binding.playerPanel.requestLayout()
        binding.playerWebView.requestLayout()

        if (onPipViewportChanged != null && pipViewportListener == null) {
            val listener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (!isInPipMode) return@OnLayoutChangeListener
                val newW = right - left
                val newH = bottom - top
                if (newW > 0 && newH > 0 && (newW != (oldRight - oldLeft) || newH != (oldBottom - oldTop))) {
                    onPipViewportChanged()
                }
            }
            pipViewportListener = listener
            binding.playerWebView.addOnLayoutChangeListener(listener)
        }
    }

    fun restoreFromPip(restoreMiniIfWasCollapsed: Boolean, isFullscreen: Boolean) {
        clearPendingLayoutListener()
        clearPipViewportListener()
        isInPipMode = false
        binding.swipeRefreshLayout.visibility = View.VISIBLE
        if (!isFullscreen) {
            binding.swipeRefreshLayout.isEnabled = true
        }

        if (wasCollapsedBeforePip) {
            wasCollapsedBeforePip = false
            state = PanelState.EXPANDED

            if (restoreMiniIfWasCollapsed && !isFullscreen) {
                waitForFullWindowThen {
                    if (!isInPipMode && state == PanelState.EXPANDED) {
                        collapse()
                    }
                }
            }
        }
    }

    fun onConfigurationChanged() {
        if (isInPipMode || state != PanelState.COLLAPSED) return
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
                if (isInPipMode || state != PanelState.COLLAPSED) {
                    binding.mainContainer.removeOnLayoutChangeListener(this)
                    if (pendingLayoutListener === this) pendingLayoutListener = null
                    return
                }
                val newW = right - left
                val newH = bottom - top
                if (newW <= 0 || newH <= 0) return
                if (newW == (oldRight - oldLeft) && newH == (oldBottom - oldTop)) return
                binding.mainContainer.removeOnLayoutChangeListener(this)
                if (pendingLayoutListener === this) pendingLayoutListener = null
                lockPlayerWebViewSize(newW, newH)
                binding.playerWebView.evaluateJavascript(
                    "(function(){return window.__liteTube?window.__liteTube.measureVideoRect():'';})()"
                ) { rectRes ->
                    if (!isInPipMode && state == PanelState.COLLAPSED) {
                        animateToMini(newW, newH, lastBottomNavPx.toFloat(), parseVideoRect(rectRes), animate = false)
                    }
                }
            }
        }
        pendingLayoutListener = listener
        binding.mainContainer.addOnLayoutChangeListener(listener)
    }

    private fun waitForFullWindowThen(action: () -> Unit) {
        val sysMetrics = Resources.getSystem().displayMetrics
        val minFullWindowSide = (minOf(sysMetrics.widthPixels, sysMetrics.heightPixels) * 0.8f).toInt()
        val minExpandedHeightPx = maxOf(dpToPx(420f).toInt(), minFullWindowSide)

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
                    if (pendingLayoutListener === this) pendingLayoutListener = null
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

    private fun clearPipViewportListener() {
        pipViewportListener?.let { binding.playerWebView.removeOnLayoutChangeListener(it) }
        pipViewportListener = null
    }

    private fun animateToMini(
        fullW: Int,
        fullH: Int,
        bottomNavH: Float,
        rect: FloatArray?,
        animate: Boolean
    ) {
        if (isInPipMode || state != PanelState.COLLAPSED) return

        val miniH = dpToPx(64f)
        val boxW = dpToPx(104f)
        val boxH = dpToPx(58f)
        val boxLeft = dpToPx(3f) // Khớp khít viền 3dp của bg_miniplayer_video_mask (3 + 104 + 3 = 110dp)
        val targetY = (fullH - bottomNavH - miniH).coerceAtLeast(0f)

        // Mặc định: player nằm ngay dưới thanh topbar 48dp, tỉ lệ 16:9
        var vLeft = 0f
        var vTop = dpToPx(48f)
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
            if (!isInPipMode && state == PanelState.COLLAPSED) {
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
        binding.playerWebView.animate().withEndAction(null).cancel()
        binding.playerWebView.clearAnimation()
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
        clearPipViewportListener()
        binding.playerPanel.canDragDown = null
        binding.playerPanel.videoZoneBottomProvider = null
        binding.playerPanel.onDragOffset = null
        binding.playerPanel.onDragFinished = null
        binding.playerPanel.animate().withEndAction(null).cancel()
        binding.playerWebView.animate().withEndAction(null).cancel()
    }
}
