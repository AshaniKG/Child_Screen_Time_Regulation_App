package com.example.turnaway.engine

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import com.example.turnaway.util.AppLogger

class FrameThrottlingController(
    private val service: AccessibilityService,
    private val overlayView: android.view.View? = null,
    overlayManager: WindowManager? = null
) {
    private val TAG = "FrameThrottling"
    private val screenshotThrottler = overlayManager?.let { ScreenshotThrottlingController(service, it) }

    private var targetFps = 60
    private var isThrottlingActive = false

    fun setTargetFps(fps: Int) {
        val newFps = fps.coerceIn(1, 60)
        if (this.targetFps != newFps) {
            this.targetFps = newFps
            AppLogger.d(TAG, "Target FPS updated to: $targetFps FPS")
        }

        if (targetFps < 60) {
            isThrottlingActive = true
            screenshotThrottler?.setTargetFps(targetFps)
        } else {
            stopThrottling()
        }
    }

    fun setTargetAppForeground(isForeground: Boolean) {
        screenshotThrottler?.setTargetAppForeground(isForeground)
    }

    fun stopThrottling() {
        isThrottlingActive = false
        screenshotThrottler?.stopThrottling()
        AppLogger.i(TAG, "Frame throttling stopped; normal screen rendering restored")
    }

    fun release() {
        stopThrottling()
        screenshotThrottler?.release()
    }
}
