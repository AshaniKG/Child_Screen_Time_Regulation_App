package com.example.turnaway.engine

import android.view.View
import com.example.turnaway.util.AppLogger

class FrameThrottlingController(private val overlayView: View? = null) {
    private val TAG = "FrameThrottling"
    private var targetFps = 60
    private var isThrottlingActive = false

    fun setTargetFps(fps: Int) {
        val newFps = fps.coerceIn(5, 60)
        if (this.targetFps != newFps) {
            this.targetFps = newFps
            AppLogger.d(TAG, "Target FPS updated to: $targetFps FPS")
        }

        if (targetFps < 60 && !isThrottlingActive) {
            isThrottlingActive = true
            AppLogger.i(TAG, "Visual frame-rate throttling requested, but native Android restricts global frame manipulation without Root. Relying on Touch Input Latency to simulate system unresponsiveness.")
        } else if (targetFps >= 60) {
            isThrottlingActive = false
        }
    }

    fun stopThrottling() {
        isThrottlingActive = false
    }
}
