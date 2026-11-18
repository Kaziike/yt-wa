package com.kaz.litetube.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView

/**
 * Custom WebView quản lý hiển thị thông minh theo ngữ cảnh:
 * - Khi keepAliveInBackground = true (đang phát âm thanh nền hợp lệ hoặc đang ở cửa sổ PiP):
 *   báo cáo View.VISIBLE để nhân Chromium không đình chỉ luồng giải mã âm thanh.
 * - Khi keepAliveInBackground = false (Browse WebView hoặc khi không phát nền):
 *   truyền đúng trạng thái ẩn/hiện thực tế để Chromium giải phóng bộ nhớ đệm GPU & ngủ tiết kiệm pin.
 */
class KeepAliveWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var keepAliveInBackground: Boolean = false
        private set

    fun updateKeepAlive(enabled: Boolean) {
        if (keepAliveInBackground == enabled) return
        keepAliveInBackground = enabled
        val currentWinVis = windowVisibility
        if (!enabled && currentWinVis != View.VISIBLE) {
            super.dispatchWindowVisibilityChanged(currentWinVis)
            super.onWindowVisibilityChanged(currentWinVis)
        } else if (enabled) {
            super.dispatchWindowVisibilityChanged(View.VISIBLE)
            super.onWindowVisibilityChanged(View.VISIBLE)
        }
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        val effective = if (keepAliveInBackground) View.VISIBLE else visibility
        super.dispatchWindowVisibilityChanged(effective)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        val effective = if (keepAliveInBackground) View.VISIBLE else visibility
        super.onWindowVisibilityChanged(effective)
    }

    /**
     * Giải phóng sạch sẽ WebView, gỡ khỏi cây View và hủy cầu nối JNI để chống rò rỉ Activity.
     */
    fun safeDestroy(bridgeName: String) {
        try {
            keepAliveInBackground = false
            (parent as? ViewGroup)?.removeView(this)
            stopLoading()
            removeJavascriptInterface(bridgeName)
            webChromeClient = null
            loadUrl("about:blank")
            clearHistory()
            destroy()
        } catch (_: Exception) {}
    }
}
