package com.example.turnaway.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.WindowManager
import com.example.turnaway.util.AppLogger
import com.example.turnaway.util.GrayscaleManager
import kotlin.math.abs

/**
 * Overlay view that renders a visual color veil with customizable tint color and max opacity.
 */
class DesaturationOverlayView(context: Context) : View(context) {

    private var overlayEnabled: Boolean = false
    private var overlayProgress: Float = 0f
    private var overlayColor: Int = Color.GRAY
    private var overlayMaxAlpha: Float = 0.70f

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        // Use standard layer rendering to prevent offscreen GPU buffer allocation flicker on initial draw
        setLayerType(LAYER_TYPE_NONE, null)
    }

    fun setOverlayConfig(
        enabled: Boolean,
        progress: Float,
        color: Int,
        maxAlpha: Float
    ) {
        val newProgress = progress.coerceIn(0.0f, 1.0f)
        val newMaxAlpha = maxAlpha.coerceIn(0.05f, 1.0f)

        if (this.overlayEnabled != enabled ||
            abs(this.overlayProgress - newProgress) > 0.005f ||
            this.overlayColor != color ||
            abs(this.overlayMaxAlpha - newMaxAlpha) > 0.01f) {

            this.overlayEnabled = enabled
            this.overlayProgress = newProgress
            this.overlayColor = color
            this.overlayMaxAlpha = newMaxAlpha
            invalidate()
        }
    }

    fun clearAll() {
        if (this.overlayEnabled || this.overlayProgress > 0f) {
            this.overlayEnabled = false
            this.overlayProgress = 0f
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw custom color veil if overlay is enabled
        if (overlayEnabled && overlayProgress > 0.001f) {
            val alpha = (overlayProgress * overlayMaxAlpha * 255).toInt().coerceIn(0, 255)
            if (alpha <= 0) return

            val r = Color.red(overlayColor)
            val g = Color.green(overlayColor)
            val b = Color.blue(overlayColor)
            overlayPaint.color = Color.argb(alpha, r, g, b)

            val displayMetrics = resources.displayMetrics
            val right = canvas.width.toFloat().coerceAtLeast(width.toFloat()).coerceAtLeast(displayMetrics.widthPixels.toFloat() * 2f)
            val bottom = canvas.height.toFloat().coerceAtLeast(height.toFloat()).coerceAtLeast(displayMetrics.heightPixels.toFloat() * 2f)
            canvas.drawRect(0f, 0f, right, bottom, overlayPaint)
        }
    }
}

/**
 * Controls display visual adjustments:
 * - Native hardware system grayscale (via [GrayscaleManager])
 * - Visual screen overlay veil with customizable tint color and max opacity
 */
class ColorDesaturationController(
    private val context: Context,
    private val overlayView: View? = null,
    private val overlayManager: WindowManager? = null
) {
    private val TAG = "ColorDesaturation"

    fun hasWriteSecureSettingsPermission(): Boolean {
        return GrayscaleManager.isPermissionGranted(context)
    }

    private var lastGrayscaleState: Boolean? = null

    /**
     * Updates visual effects (system grayscale & overlay veil).
     */
    fun updateVisualEffects(
        enableSystemGrayscale: Boolean,
        saturationFactor: Float = 1.0f,
        enableOverlay: Boolean = false,
        overlayProgress: Float = 0f,
        overlayColorHex: String = "#808080",
        overlayMaxAlpha: Float = 0.70f
    ) {
        // 1. System Grayscale (Native system display matrix via WRITE_SECURE_SETTINGS)
        val currentSysGrayscale = GrayscaleManager.isGrayscaleActive(context)
        if (enableSystemGrayscale) {
            if (!currentSysGrayscale || lastGrayscaleState != true) {
                lastGrayscaleState = true
                val success = GrayscaleManager.setGrayscaleEnabled(context, true)
                AppLogger.i(TAG, "Enabling system grayscale (target state: true, current: $currentSysGrayscale, success: $success)")
            }
        } else {
            if (currentSysGrayscale || lastGrayscaleState != false) {
                lastGrayscaleState = false
                val success = GrayscaleManager.setGrayscaleEnabled(context, false)
                AppLogger.i(TAG, "Disabling system grayscale (target state: false, current: $currentSysGrayscale, success: $success)")
            }
        }

        // 2. Visual Overlay Veil (Independent of grayscale, customizable color & opacity)
        val parsedColor = try {
            Color.parseColor(overlayColorHex)
        } catch (e: Exception) {
            Color.GRAY
        }

        if (overlayView is DesaturationOverlayView) {
            overlayView.setOverlayConfig(
                enabled = enableOverlay,
                progress = overlayProgress,
                color = parsedColor,
                maxAlpha = overlayMaxAlpha
            )
        }
    }

    fun updateSaturation(saturationFactor: Float) {
        updateVisualEffects(
            enableSystemGrayscale = true,
            saturationFactor = saturationFactor,
            enableOverlay = false,
            overlayProgress = 0f
        )
    }

    /**
     * Resets all visual effects back to normal defaults.
     */
    fun resetAll() {
        lastGrayscaleState = false
        GrayscaleManager.setGrayscaleEnabled(context, false)
        GrayscaleManager.setSaturationLevel(context, 100)

        if (overlayView is DesaturationOverlayView) {
            overlayView.clearAll()
        }
    }
}