package com.brave.youtubelite.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * FrameLayout chuyên dụng cho Tầng Phát Video (playerPanel):
 * - Cho phép người dùng vuốt dọc từ trên xuống ở BẤT KỲ ĐÂU trên toàn bộ khu vực video (và thanh topbar)
 *   để kéo mượt trình phát về MiniPlayer.
 * - Hoàn toàn KHÔNG chặn thao tác chạm đơn (Play/Pause, Phụ đề, Cài đặt, Toàn màn hình, bấm Logo),
 *   KHÔNG chặn thao tác tua ngang thanh thời gian (seekbar), và KHÔNG ảnh hưởng cuộn danh sách video/bình luận bên dưới.
 */
class DraggablePlayerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var canDragDown: (() -> Boolean)? = null
    var videoZoneBottomProvider: (() -> Float)? = null
    var onDragOffset: ((deltaY: Float) -> Unit)? = null
    var onDragFinished: ((shouldCollapse: Boolean) -> Unit)? = null

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val density: Float = context.resources.displayMetrics.density

    private var downRawX = 0f
    private var downRawY = 0f
    private var dragStartRawY = 0f
    private var touchStartedInVideoZone = false
    private var gestureLockedOtherAxis = false
    private var isDraggingDown = false
    private var velocityTracker: VelocityTracker? = null

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Khi ngón tay bắt đầu trong vùng video và chưa khóa theo hướng ngang/vuốt lên,
        // giữ quyền đánh giá hướng kéo cho DraggablePlayerLayout để WebView không chặn cử chỉ kéo xuống.
        if (touchStartedInVideoZone && !gestureLockedOtherAxis) {
            return
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val enabled = canDragDown?.invoke() == true
        if (!enabled) {
            resetTouchState()
            return false
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = ev.rawX
                downRawY = ev.rawY
                dragStartRawY = ev.rawY
                isDraggingDown = false
                gestureLockedOtherAxis = false

                val zoneBottom = videoZoneBottomProvider?.invoke()
                    ?: (52f * density + width * 9f / 16f)
                touchStartedInVideoZone = ev.y >= 0f && ev.y <= zoneBottom

                recycleVelocityTracker()
                if (touchStartedInVideoZone) {
                    velocityTracker = VelocityTracker.obtain().apply { addMovement(ev) }
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!touchStartedInVideoZone || gestureLockedOtherAxis) return false
                velocityTracker?.addMovement(ev)

                val dx = ev.rawX - downRawX
                val dy = ev.rawY - downRawY

                // Nếu người dùng kéo ngang (tua seekbar) hoặc vuốt ngược lên -> nhường 100% cho WebView
                if ((abs(dx) > touchSlop && abs(dx) >= abs(dy)) || dy < -touchSlop) {
                    gestureLockedOtherAxis = true
                    recycleVelocityTracker()
                    return false
                }

                // Nếu kéo rõ ràng theo phương thẳng đứng xuống dưới trong vùng video -> bắt đầu kéo thu nhỏ
                if (dy > touchSlop && dy > abs(dx) * 1.25f) {
                    isDraggingDown = true
                    dragStartRawY = ev.rawY
                    return true
                }
                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resetTouchState()
                return false
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isDraggingDown) {
            return super.onTouchEvent(ev)
        }

        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val deltaY = (ev.rawY - dragStartRawY).coerceAtLeast(0f)
                onDragOffset?.invoke(deltaY)
                return true
            }

            MotionEvent.ACTION_UP -> {
                val totalDragY = (ev.rawY - dragStartRawY).coerceAtLeast(0f)
                val vt = velocityTracker
                vt?.computeCurrentVelocity(1000)
                val yVelocity = vt?.yVelocity ?: 0f

                val collapseDistanceThreshold = 72f * density
                val flingVelocityThreshold = 650f * density
                val minFlingDrag = 20f * density

                val shouldCollapse = totalDragY > collapseDistanceThreshold ||
                    (totalDragY > minFlingDrag && yVelocity > flingVelocityThreshold)

                resetTouchState()
                onDragFinished?.invoke(shouldCollapse)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                resetTouchState()
                onDragFinished?.invoke(false)
                return true
            }
        }
        return true
    }

    private fun resetTouchState() {
        touchStartedInVideoZone = false
        gestureLockedOtherAxis = false
        isDraggingDown = false
        recycleVelocityTracker()
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
