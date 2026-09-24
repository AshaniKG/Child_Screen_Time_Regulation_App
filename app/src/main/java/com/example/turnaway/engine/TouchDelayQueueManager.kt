package com.example.turnaway.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import com.example.turnaway.util.AppLogger

class TouchDelayQueueManager(private val service: AccessibilityService) {
    private val TAG = "TouchDelayQueue"
    private val handler = Handler(Looper.getMainLooper())
    private var currentDelayMs = 0L

    fun setTouchDelay(delayMs: Long) {
        val newDelay = delayMs.coerceIn(0L, 2000L)
        if (this.currentDelayMs != newDelay) {
            this.currentDelayMs = newDelay
            AppLogger.i(TAG, "Touch input latency set to: ${currentDelayMs}ms")
        }
    }

    fun getTouchDelay(): Long = currentDelayMs

    fun resetQueue() {
        handler.removeCallbacksAndMessages(null)
        currentDelayMs = 0L
        AppLogger.i(TAG, "Touch delay queue cleared and latency reset to 0ms")
    }

    fun calculateCurrentTouchDelay(
        elapsedTransitionMs: Long,
        totalTransitionMs: Long,
        minLagMs: Long = 0L,
        maxLagMs: Long
    ): Long {
        return DecayCurveCalculator.calculateCurrentTouchDelay(
            elapsedTransitionMs,
            totalTransitionMs,
            minLagMs,
            maxLagMs
        )
    }

    fun processInterceptedMotionEvent(path: Path, durationMs: Long, onComplete: () -> Unit) {
        if (currentDelayMs == 0L) {
            dispatchPathDirectly(path, durationMs)
            onComplete()
            return
        }

        val effectiveDuration = durationMs.coerceIn(50L, 500L)
        val stroke = GestureDescription.StrokeDescription(path, 0, effectiveDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        AppLogger.d(TAG, "Queuing gesture touch for ${currentDelayMs}ms delay...")
        handler.postDelayed({
            try {
                val dispatched = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        AppLogger.d(TAG, "Delayed gesture dispatched successfully")
                        onComplete()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        AppLogger.w(TAG, "Delayed gesture dispatch cancelled by system")
                        onComplete()
                    }
                }, null)
                if (!dispatched) {
                    onComplete()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error dispatching delayed gesture", e)
                onComplete()
            }
        }, currentDelayMs)
    }

    private fun dispatchPathDirectly(path: Path, durationMs: Long) {
        try {
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(50L, 500L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error dispatching direct gesture", e)
        }
    }
}
