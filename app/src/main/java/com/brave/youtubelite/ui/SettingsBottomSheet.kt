package com.brave.youtubelite.ui

import androidx.appcompat.app.AppCompatActivity
import com.brave.youtubelite.R
import com.brave.youtubelite.databinding.DialogSettingsBinding
import com.brave.youtubelite.network.AdBlockEngine
import com.brave.youtubelite.network.WebResourceCacheManager
import com.brave.youtubelite.settings.AppSettings
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Hộp thoại Cài đặt BottomSheet sử dụng ViewBinding:
 * - Tính toán và xóa bộ nhớ đệm hoàn toàn bất đồng bộ trên luồng nền (không gây giật UI).
 * - Đồng bộ tức thì cấu hình xuống AdBlockEngine, WebResourceCacheManager, PiP và cả 2 WebView.
 */
object SettingsBottomSheet {

    fun show(
        activity: AppCompatActivity,
        onSettingsChanged: () -> Unit,
        onClearWebViewsCache: () -> Unit,
        onReloadActiveWebView: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = BottomSheetDialog(activity)
        val binding = DialogSettingsBinding.inflate(activity.layoutInflater)
        dialog.setContentView(binding.root)

        binding.switchAdBlock.isChecked = AppSettings.isAdBlockEnabled(activity)
        binding.switchBackgroundPlay.isChecked = AppSettings.isBackgroundPlayEnabled(activity)
        binding.switchAutoPip.isChecked = AppSettings.isAutoPipEnabled(activity)
        binding.switchPreloadCache.isChecked = AppSettings.isPreloadCacheEnabled(activity)

        fun refreshCacheLabel() {
            AppSettings.computeFormattedCacheSizeAsync(activity) { formattedSize ->
                if (dialog.isShowing) {
                    binding.tvCacheSize.text = activity.getString(R.string.setting_cache_size_format, formattedSize)
                }
            }
        }

        refreshCacheLabel()

        binding.switchAdBlock.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setAdBlockEnabled(activity, isChecked)
            AdBlockEngine.isEnabled = isChecked
            onSettingsChanged()
        }

        binding.switchBackgroundPlay.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setBackgroundPlayEnabled(activity, isChecked)
            onSettingsChanged()
        }

        binding.switchAutoPip.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setAutoPipEnabled(activity, isChecked)
            onSettingsChanged()
        }

        binding.switchPreloadCache.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setPreloadCacheEnabled(activity, isChecked)
            WebResourceCacheManager.isEnabled = isChecked
            onSettingsChanged()
        }

        binding.btnClearCache.setOnClickListener {
            binding.btnClearCache.isEnabled = false
            onClearWebViewsCache()
            AppSettings.clearAppCacheAsync(activity) { formattedSize ->
                if (dialog.isShowing) {
                    binding.tvCacheSize.text = activity.getString(R.string.setting_cache_size_format, formattedSize)
                    binding.btnClearCache.isEnabled = true
                }
            }
        }

        binding.btnReloadInSettings.setOnClickListener {
            dialog.dismiss()
            onReloadActiveWebView()
        }

        binding.btnCloseSettings.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
