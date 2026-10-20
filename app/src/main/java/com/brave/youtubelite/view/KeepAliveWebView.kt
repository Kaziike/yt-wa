package com.brave.youtubelite.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * Custom WebView ngăn chặn việc nhân Chromium tự động đình chỉ (suspend)
 * luồng âm thanh/video khi ứng dụng bị thu nhỏ hoặc màn hình tắt.
 */
class KeepAliveWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        // Luôn báo cáo cửa sổ đang VISIBLE cho Chromium engine
        super.dispatchWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Duy trì trạng thái hiển thị đối với trình kết xuất web bên trong
        super.onWindowVisibilityChanged(View.VISIBLE)
    }
}
