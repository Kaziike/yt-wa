package com.kaz.litetube.ui

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.kaz.litetube.R
import com.kaz.litetube.playback.PlaybackCoordinator
import com.kaz.litetube.settings.AppSettings
import java.util.UUID

/**
 * Quản lý chuyên biệt Picture-in-Picture (PiP):
 * - Gắn mã xác thực phiên ngẫu nhiên (EXTRA_PIP_TOKEN) vào mọi PendingIntent của RemoteAction,
 *   ngăn chặn tuyệt đối ứng dụng bên thứ 3 giả mạo Broadcast điều khiển mặc dù dùng RECEIVER_EXPORTED cho SystemUI.
 * - Điều phối duy nhất 1 luồng vào/ra PiP sạch sẽ, không gọi lặp enterPipLayout 4 lần như trước.
 */
class PipController(
    private val activity: AppCompatActivity,
    private val playerSheet: PlayerSheetController,
    private val fullscreenController: FullscreenController,
    private val playback: PlaybackCoordinator
) {

    companion object {
        private const val TAG = "PipController"
        private const val ACTION_PIP_HEADPHONES = "com.kaz.litetube.PIP_HEADPHONES"
        private const val ACTION_PIP_PLAY = "com.kaz.litetube.PIP_PLAY"
        private const val ACTION_PIP_PAUSE = "com.kaz.litetube.PIP_PAUSE"
        private const val ACTION_PIP_NEXT = "com.kaz.litetube.PIP_NEXT"
        private const val EXTRA_PIP_TOKEN = "EXTRA_PIP_TOKEN"

        private const val REQ_PLAY = 1001
        private const val REQ_PAUSE = 1002
        private const val REQ_HEADPHONES = 1003
        private const val REQ_NEXT = 1004
    }

    private val pipAuthToken: String = UUID.randomUUID().toString()
    private var receiverRegistered = false
    private var wasInPipMode = false
    private var isInPipLayout = false
    private var hidingPipToAudioAtMs = 0L

    val isSwitchingToAudioOnly: Boolean
        get() = hidingPipToAudioAtMs > 0L && (SystemClock.elapsedRealtime() - hidingPipToAudioAtMs) < 6000L

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getStringExtra(EXTRA_PIP_TOKEN) != pipAuthToken) {
                Log.w(TAG, "Từ chối broadcast PiP không hợp lệ (sai hoặc thiếu token).")
                return
            }
            when (intent.action) {
                ACTION_PIP_HEADPHONES -> handleHeadphonesAction()
                ACTION_PIP_PLAY -> {
                    playback.play()
                    updateParams()
                }
                ACTION_PIP_PAUSE -> {
                    playback.pause()
                    updateParams()
                }
                ACTION_PIP_NEXT -> playback.runPipCommand("next()")
            }
        }
    }

    fun register() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_HEADPHONES)
            addAction(ACTION_PIP_PLAY)
            addAction(ACTION_PIP_PAUSE)
            addAction(ACTION_PIP_NEXT)
        }
        ContextCompat.registerReceiver(
            activity,
            pipActionReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    fun unregister() {
        if (!receiverRegistered) return
        try {
            activity.unregisterReceiver(pipActionReceiver)
        } catch (_: Exception) {}
        receiverRegistered = false
    }

    fun isInPipMode(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode

    private fun clampedAspectRatio(): Rational {
        val aspect = playback.state.aspectRatio
        val ratio = aspect.toFloat()
        if (ratio.isNaN() || ratio <= 0f) return Rational(16, 9)
        val maxRatio = 2.39f
        val minRatio = 1f / 2.39f
        return when {
            ratio > maxRatio -> Rational(239, 100)
            ratio < minRatio -> Rational(100, 239)
            else -> aspect
        }
    }

    private fun buildProtectedAction(
        actionName: String,
        requestCode: Int,
        iconRes: Int,
        title: String,
        description: String
    ): RemoteAction {
        val intent = Intent(actionName).apply {
            setPackage(activity.packageName)
            putExtra(EXTRA_PIP_TOKEN, pipAuthToken)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            activity,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(
            Icon.createWithResource(activity, iconRes),
            title,
            description,
            pendingIntent
        )
    }

    fun buildParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val builder = PictureInPictureParams.Builder()

        try {
            builder.setAspectRatio(clampedAspectRatio())
        } catch (_: Exception) {}

        val isPlaying = playback.state.isPlaying
        val headphonesAction = buildProtectedAction(
            actionName = ACTION_PIP_HEADPHONES,
            requestCode = REQ_HEADPHONES,
            iconRes = R.drawable.ic_pip_headphones,
            title = "Nghe nền",
            description = "Ẩn cửa sổ nổi, giữ âm thanh nền"
        )
        val playPauseAction = buildProtectedAction(
            actionName = if (isPlaying) ACTION_PIP_PAUSE else ACTION_PIP_PLAY,
            requestCode = if (isPlaying) REQ_PAUSE else REQ_PLAY,
            iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            title = if (isPlaying) "Tạm dừng" else "Phát",
            description = if (isPlaying) "Tạm dừng" else "Phát"
        )
        val nextAction = buildProtectedAction(
            actionName = ACTION_PIP_NEXT,
            requestCode = REQ_NEXT,
            iconRes = android.R.drawable.ic_media_next,
            title = "Tiếp theo",
            description = "Phát video tiếp theo"
        )

        val maxActions = activity.maxNumPictureInPictureActions
        val actions = when {
            maxActions >= 3 -> listOf(headphonesAction, playPauseAction, nextAction)
            maxActions == 2 -> listOf(headphonesAction, playPauseAction)
            else -> listOf(playPauseAction)
        }
        builder.setActions(actions)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoPip = AppSettings.isAutoPipEnabled(activity)
            builder.setAutoEnterEnabled((isPlaying || fullscreenController.isFullscreen) && autoPip)
            builder.setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    fun updateParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                buildParams()?.let { activity.setPictureInPictureParams(it) }
            } catch (_: Exception) {}
        }
    }

    private fun ensurePreparedForPip() {
        isInPipLayout = true
        playerSheet.prepareForPip(
            onPipViewportChanged = {
                if (isInPipMode()) {
                    playback.runPipCommand("syncViewport()")
                }
            }
        )
    }

    fun onPipConfigurationChanged() {
        if (isInPipMode()) {
            ensurePreparedForPip()
            playback.runPipCommand("syncViewport()")
        }
    }

    fun onUserLeaveHint() {
        val autoPip = AppSettings.isAutoPipEnabled(activity)
        if ((playback.state.isPlaying || fullscreenController.isFullscreen) && autoPip) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.isInPictureInPictureMode) {
                ensurePreparedForPip()
                try {
                    buildParams()?.let { activity.enterPictureInPictureMode(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "enterPictureInPictureMode lỗi: ${e.message}")
                    isInPipLayout = false
                    playerSheet.restoreFromPip(restoreMiniIfWasCollapsed = true, isFullscreen = fullscreenController.isFullscreen)
                }
            }
        }
    }

    fun onPictureInPictureModeChanged(inPip: Boolean) {
        if (inPip) {
            wasInPipMode = true
            hidingPipToAudioAtMs = 0L
            ensurePreparedForPip()
            playback.onPipEntered()
            updateParams()
        } else {
            isInPipLayout = false
            when {
                isSwitchingToAudioOnly -> {
                    playerSheet.restoreFromPip(
                        restoreMiniIfWasCollapsed = false,
                        isFullscreen = fullscreenController.isFullscreen
                    )
                }
                activity.lifecycle.currentState == Lifecycle.State.CREATED -> {
                    // Đóng cửa sổ PiP bằng nút [X] -> dừng phát và tắt thông báo nền
                    wasInPipMode = false
                    dismissPipAndStopPlayback()
                }
                else -> {
                    playback.onPipExitedToApp()
                    playerSheet.restoreFromPip(
                        restoreMiniIfWasCollapsed = true,
                        isFullscreen = fullscreenController.isFullscreen
                    )
                }
            }
        }
    }

    fun onActivityResumed() {
        if (!isInPipMode()) {
            wasInPipMode = false
            hidingPipToAudioAtMs = 0L
            if (isInPipLayout) {
                isInPipLayout = false
                playerSheet.restoreFromPip(
                    restoreMiniIfWasCollapsed = true,
                    isFullscreen = fullscreenController.isFullscreen
                )
            }
            playback.onForegroundEntered()
        }
    }

    fun onActivityStopped() {
        if (isInPipMode()) {
            // Đang xem PiP mà khóa màn hình -> giữ âm thanh nền nếu bật Background Play
            playback.onBackgroundEntered(keepAudioAlive = AppSettings.isBackgroundPlayEnabled(activity))
            return
        }

        if (wasInPipMode) {
            wasInPipMode = false
            if (isSwitchingToAudioOnly) {
                hidingPipToAudioAtMs = 0L
                playback.onBackgroundEntered(keepAudioAlive = true)
            } else {
                hidingPipToAudioAtMs = 0L
                dismissPipAndStopPlayback()
            }
            return
        }

        playback.onBackgroundEntered(keepAudioAlive = AppSettings.isBackgroundPlayEnabled(activity))
    }

    private fun handleHeadphonesAction() {
        hidingPipToAudioAtMs = SystemClock.elapsedRealtime()
        isInPipLayout = false
        playback.onPipSwitchedToAudioOnly()
        try {
            activity.moveTaskToBack(true)
        } catch (_: Exception) {}
    }

    private fun dismissPipAndStopPlayback() {
        if (fullscreenController.isFullscreen) {
            fullscreenController.exit()
        }
        playerSheet.restoreFromPip(restoreMiniIfWasCollapsed = false, isFullscreen = false)
        playback.onPipDismissedAndStop()
        updateParams()
    }
}
