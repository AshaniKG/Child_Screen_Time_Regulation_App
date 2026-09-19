package com.example.turnaway.engine

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.turnaway.service.ScreenCaptureForegroundService
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean

class FrameThrottlingController(
    private val service: AccessibilityService,
    private val overlayManager: WindowManager
) {
    private val TAG = "FrameThrottling"

    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var throttleJob: Job? = null

    private var targetFps = 60
    private val isTargetAppForeground = AtomicBoolean(false)
    private val isThrottlingEnabled = AtomicBoolean(false)

    private var jankOverlay: JankGhostOverlayView? = null

    fun setTargetFps(fps: Int) {
        val clampedFps = fps.coerceIn(1, 60)
        this.targetFps = clampedFps

        if (clampedFps >= 55) {
            isThrottlingEnabled.set(false)
            removeOverlay()
            throttleJob?.cancel()
            throttleJob = null
            AppLogger.d(TAG, "Frame throttling disengaged at 60 FPS normal rendering")
        } else {
            isThrottlingEnabled.set(true)
            if (isTargetAppForeground.get() && (throttleJob == null || throttleJob?.isActive == false)) {
                startThrottlingLoop()
            } else {
                // Loop already running — immediately update pulse to new FPS
                jankOverlay?.startPulse(clampedFps)
            }
            AppLogger.d(TAG, "Target FPS updated to: $clampedFps FPS (Interval: ${1000L / clampedFps}ms)")
        }
    }

    fun setTargetAppForeground(isForeground: Boolean) {
        if (isTargetAppForeground.getAndSet(isForeground) != isForeground) {
            AppLogger.i(TAG, "Target application in foreground: $isForeground (Lag active: ${isThrottlingEnabled.get() && isForeground})")
            if (isForeground) {
                if (isThrottlingEnabled.get() && (throttleJob == null || throttleJob?.isActive == false)) {
                    startThrottlingLoop()
                }
            } else {
                removeOverlay()
                throttleJob?.cancel()
                throttleJob = null
            }
        }
    }

    private var lastScreenshotRequestTime = 0L

    private fun startThrottlingLoop() {
        if (!isTargetAppForeground.get() || !isThrottlingEnabled.get()) return

        throttleJob?.cancel()
        throttleJob = controllerScope.launch {
            ensureOverlayAdded()

            // Kick off the lag-pulse animation immediately on the overlay.
            // The pulse drives all the visual "lag" effect — no screenshots required.
            jankOverlay?.startPulse(targetFps)
            AppLogger.i(TAG, "Lag pulse started at ${targetFps} FPS")

            var iteration = 0L
            var lastCaptureAttemptTime = 0L

            while (isActive && isTargetAppForeground.get() && isThrottlingEnabled.get()) {
                iteration++
                val isMpActive = ScreenCaptureForegroundService.isProjectionActive.value
                val displayFps = targetFps.coerceAtLeast(1)
                val framePeriodMs = (1000L / displayFps).toLong()
                val startTime = System.currentTimeMillis()

                // Update the pulse FPS if it has changed (e.g. wind-down ramp)
                jankOverlay?.setDebugInfo(
                    if (isMpActive) "MediaProjection" else "Lag Pulse",
                    displayFps
                )

                if (isMpActive) {
                    // Optional: pull MediaProjection frame to show real content under the pulse
                    val frameBitmap = ScreenCaptureForegroundService.instance?.acquireLatestFrame()
                    if (frameBitmap != null && !frameBitmap.isRecycled) {
                        AppLogger.d(TAG, "MediaProjection frame acquired: ${frameBitmap.width}x${frameBitmap.height}")
                        // updateFrame in new overlay just notes the source, keeps pulse running
                        jankOverlay?.updateFrame(frameBitmap, "MediaProjection", displayFps)
                    }
                }
                // No else-branch screenshot needed — the pulse IS the lag effect

                // Re-start pulse if FPS changed significantly (wind-down ramp)
                if (iteration % 30L == 0L) {
                    AppLogger.d(TAG, "Loop iteration #$iteration | isMpActive=$isMpActive | displayFps=$displayFps")
                    jankOverlay?.startPulse(displayFps)  // Restarts with updated period/alpha
                }

                val elapsedMs = System.currentTimeMillis() - startTime
                val sleepTime = (framePeriodMs - elapsedMs).coerceAtLeast(16L)
                delay(sleepTime)
            }

            jankOverlay?.stopPulse()
            removeOverlay()
        }
    }


    private suspend fun takeScreenshotFallback(fps: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            AppLogger.e(TAG, "takeScreenshot requires Android 11 (API 30)+. Current: ${Build.VERSION.SDK_INT}")
            return
        }

        val now = System.currentTimeMillis()
        val elapsedSinceLast = now - lastScreenshotRequestTime
        lastScreenshotRequestTime = now
        AppLogger.d(TAG, "Requesting takeScreenshot(). Elapsed since last call: ${elapsedSinceLast}ms")

        suspendCancellableCoroutine<Unit> { cont ->
            try {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        try {
                            val hwBuffer = screenshot.hardwareBuffer
                            val colorSpace = screenshot.colorSpace
                            val duration = System.currentTimeMillis() - now
                            AppLogger.i(TAG, "takeScreenshot SUCCESS in ${duration}ms! Buffer: ${hwBuffer.width}x${hwBuffer.height}, format=${hwBuffer.format}")

                            val bitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                            if (bitmap != null) {
                                jankOverlay?.updateFrame(bitmap, "takeScreenshot (API 30)", fps)
                            } else {
                                AppLogger.e(TAG, "Bitmap.wrapHardwareBuffer returned NULL from hardware buffer")
                            }
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Fallback screenshot conversion error: ${e.message}", e)
                        } finally {
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        val errorName = when (errorCode) {
                            1 -> "ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR (1)"
                            2 -> "ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS (2)"
                            3 -> "ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT (3)"
                            4 -> "ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY (4)"
                            5 -> "ERROR_TAKE_SCREENSHOT_INVALID_WINDOW (5)"
                            else -> "UNKNOWN_ERROR ($errorCode)"
                        }
                        AppLogger.e(TAG, "takeScreenshot FAILED! Error code: $errorName")
                        if (cont.isActive) cont.resume(Unit)
                    }
                })
            } catch (e: Exception) {
                AppLogger.e(TAG, "Exception during takeScreenshot() invocation", e)
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private fun ensureOverlayAdded() {
        if (jankOverlay == null) {
            try {
                // Confirm context is AccessibilityService
                AppLogger.i(TAG, "Creating JankGhostOverlayView with context: ${service::class.java.name}, overlayManager: ${overlayManager::class.java.name}")

                jankOverlay = JankGhostOverlayView(service).apply {
                    visibility = View.VISIBLE // Make visible immediately so debug marker renders!
                }

                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }

                overlayManager.addView(jankOverlay, layoutParams)
                AppLogger.i(TAG, "Successfully added JankGhostOverlayView to WindowManager with TYPE_ACCESSIBILITY_OVERLAY & FLAG_NOT_TOUCHABLE")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Fatal error adding JankGhostOverlayView to WindowManager", e)
                jankOverlay = null
            }
        }
    }

    private fun removeOverlay() {
        jankOverlay?.let { view ->
            try {
                view.clearFrames()
                overlayManager.removeView(view)
                AppLogger.d(TAG, "Removed JankGhostOverlayView from WindowManager")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error removing jank overlay view", e)
            }
            jankOverlay = null
        }
    }

    fun stopThrottling() {
        isThrottlingEnabled.set(false)
        throttleJob?.cancel()
        removeOverlay()

        // Cleanly stop ScreenCaptureForegroundService when throttling stops
        ScreenCaptureForegroundService.stop(service)
        AppLogger.i(TAG, "Frame throttling stopped; MediaProjection service notified to stop")
    }

    fun release() {
        stopThrottling()
        controllerScope.cancel()
    }
}
