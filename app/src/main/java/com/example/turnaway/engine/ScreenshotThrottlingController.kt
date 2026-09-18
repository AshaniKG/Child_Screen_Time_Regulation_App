package com.example.turnaway.engine

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class ScreenshotThrottlingController(
    private val service: AccessibilityService,
    private val overlayManager: WindowManager
) {
    private val TAG = "ScreenshotThrottling"

    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var throttleJob: Job? = null

    private var targetFps = 60
    private val isTargetAppForeground = AtomicBoolean(false)
    private val isThrottlingEnabled = AtomicBoolean(false)

    private var screenshotOverlay: ImageView? = null

    fun setTargetFps(fps: Int) {
        val clampedFps = fps.coerceIn(1, 60)
        this.targetFps = clampedFps

        if (clampedFps >= 55) {
            isThrottlingEnabled.set(false)
            removeOverlay()
            throttleJob?.cancel()
        } else {
            isThrottlingEnabled.set(true)
            startThrottlingLoop()
        }
        AppLogger.d(TAG, "Screenshot Target FPS: $clampedFps")
    }

    fun setTargetAppForeground(isForeground: Boolean) {
        if (isTargetAppForeground.getAndSet(isForeground) != isForeground) {
            AppLogger.i(TAG, "Target application in foreground: $isForeground")
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            AppLogger.w(TAG, "Screenshot throttling requires API 30+")
            return
        }

        if (!isTargetAppForeground.get() || !isThrottlingEnabled.get()) return

        throttleJob?.cancel()
        throttleJob = controllerScope.launch {
            ensureOverlayAdded()
            
            while (isActive && isTargetAppForeground.get() && isThrottlingEnabled.get()) {
                val delayMs = (1000L / targetFps.coerceAtLeast(1)).toLong()
                val start = System.currentTimeMillis()

                takeScreenshotAndDisplay()

                val elapsed = System.currentTimeMillis() - start
                val sleepTime = (delayMs - elapsed).coerceAtLeast(0)
                delay(sleepTime)
            }
            
            removeOverlay()
        }
    }

    private suspend fun takeScreenshotAndDisplay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        suspendCancellableCoroutine<Unit> { continuation ->
            val mainExecutor = service.mainExecutor
            service.takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    try {
                        val hwBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        val bitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                        
                        screenshotOverlay?.let { view ->
                            view.setImageBitmap(bitmap)
                            view.visibility = View.VISIBLE
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error displaying screenshot", e)
                    } finally {
                        if (continuation.isActive) continuation.resume(Unit, null)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    AppLogger.w(TAG, "Screenshot failed with error code: $errorCode")
                    if (continuation.isActive) continuation.resume(Unit, null)
                }
            })
        }
    }

    private fun ensureOverlayAdded() {
        if (screenshotOverlay == null) {
            try {
                screenshotOverlay = ImageView(service).apply {
                    setBackgroundColor(Color.BLACK)
                    scaleType = ImageView.ScaleType.FIT_XY
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
                    android.graphics.PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }

                overlayManager.addView(screenshotOverlay, layoutParams)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error adding screenshot overlay", e)
                screenshotOverlay = null
            }
        }
    }

    private fun removeOverlay() {
        screenshotOverlay?.let { view ->
            try {
                overlayManager.removeView(view)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error removing screenshot overlay", e)
            }
            screenshotOverlay = null
        }
    }

    fun stopThrottling() {
        isThrottlingEnabled.set(false)
        throttleJob?.cancel()
        removeOverlay()
        AppLogger.i(TAG, "Screenshot frame throttling stopped")
    }

    fun release() {
        stopThrottling()
        controllerScope.cancel()
    }
}
