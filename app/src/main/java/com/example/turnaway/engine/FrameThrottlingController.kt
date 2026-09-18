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
            AppLogger.d(TAG, "Frame throttling disengaged at 60 FPS normal rendering")
        } else {
            isThrottlingEnabled.set(true)
            startThrottlingLoop()
            AppLogger.d(TAG, "Target FPS updated to: $clampedFps FPS (Interval: ${1000L / clampedFps}ms)")
        }
    }

    fun setTargetAppForeground(isForeground: Boolean) {
        if (isTargetAppForeground.getAndSet(isForeground) != isForeground) {
            AppLogger.i(TAG, "Target application in foreground: $isForeground (Lag active: ${isThrottlingEnabled.get() && isForeground})")
            if (isThrottlingEnabled.get()) {
                if (isForeground) {
                    startThrottlingLoop()
                } else {
                    removeOverlay()
                    throttleJob?.cancel()
                }
            }
        }
    }

    private fun startThrottlingLoop() {
        if (!isTargetAppForeground.get() || !isThrottlingEnabled.get()) return

        throttleJob?.cancel()
        throttleJob = controllerScope.launch {
            ensureOverlayAdded()

            while (isActive && isTargetAppForeground.get() && isThrottlingEnabled.get()) {
                val isMpActive = ScreenCaptureForegroundService.isProjectionActive.value
                val currentFps = if (isMpActive) targetFps else 1 // Fallback to 1 FPS freeze if MP is revoked
                val framePeriodMs = (1000L / currentFps.coerceAtLeast(1)).toLong()
                val startTime = System.currentTimeMillis()

                if (isMpActive) {
                    // 1. High-Performance MediaProjection Frame Pipeline
                    val captureService = ScreenCaptureForegroundService.instance
                    val frameBitmap = captureService?.acquireLatestFrame()

                    if (frameBitmap != null && !frameBitmap.isRecycled) {
                        jankOverlay?.updateFrame(frameBitmap)
                        jankOverlay?.visibility = View.VISIBLE
                    }
                } else {
                    // 2. Resilient Fallback: takeScreenshot() at 1 FPS Freeze Mode
                    AppLogger.w(TAG, "MediaProjection inactive or revoked; using takeScreenshot 1 FPS fallback")
                    takeScreenshotFallback()
                }

                val elapsedMs = System.currentTimeMillis() - startTime
                val sleepTime = (framePeriodMs - elapsedMs).coerceAtLeast(10L)
                delay(sleepTime)
            }

            removeOverlay()
        }
    }

    private suspend fun takeScreenshotFallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        suspendCancellableCoroutine<Unit> { cont ->
            try {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        try {
                            val hwBuffer = screenshot.hardwareBuffer
                            val colorSpace = screenshot.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                            bitmap?.let {
                                jankOverlay?.updateFrame(it)
                                jankOverlay?.visibility = View.VISIBLE
                            }
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Fallback screenshot conversion error", e)
                        } finally {
                            if (cont.isActive) cont.resume(Unit, null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        AppLogger.w(TAG, "Fallback screenshot failed (code=$errorCode)")
                        if (cont.isActive) cont.resume(Unit, null)
                    }
                })
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error invoking takeScreenshot fallback", e)
                if (cont.isActive) cont.resume(Unit, null)
            }
        }
    }

    private fun ensureOverlayAdded() {
        if (jankOverlay == null) {
            try {
                jankOverlay = JankGhostOverlayView(service).apply {
                    visibility = View.INVISIBLE
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
                AppLogger.d(TAG, "Added JankGhostOverlayView to WindowManager with FLAG_NOT_TOUCHABLE")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error adding jank overlay view", e)
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
