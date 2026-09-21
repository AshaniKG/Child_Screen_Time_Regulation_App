package com.example.turnaway.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.view.View
import android.view.WindowManager
import com.example.turnaway.util.AppLogger
import com.example.turnaway.util.GrayscaleManager

/**
 * Overlay view that renders a smooth, hardware-accelerated color fading desaturation veil
 * over the screen as transition progress advances from 1.0 (full color) down to 0.0 (grayscale).
 */
class DesaturationOverlayView(context: Context) : View(context) {

    private var saturationFactor = 1.0f
    private val desaturationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setSaturation(factor: Float) {
        this.saturationFactor = factor.coerceIn(0.0f, 1.0f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val fadeFactor = (1.0f - saturationFactor).coerceIn(0.0f, 1.0f)
        if (fadeFactor <= 0.001f) return

        // Smooth gradual color desaturation veil: ramps opacity from 0% at start up to ~80% at complete duration
        val baseAlpha = (fadeFactor * 200).toInt().coerceIn(0, 200)
        desaturationPaint.color = Color.argb(baseAlpha, 185, 188, 195)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), desaturationPaint)
    }
}

/**
 * Controls display visual adjustments (gradual color desaturation via [DesaturationOverlayView]
 * and [GrayscaleManager]).
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

    fun updateSaturationAndBlur(saturationFactor: Float, blurRadiusPx: Float = 0f) {
        val s = saturationFactor.coerceIn(0.0f, 1.0f)
        val saturationPercent = (s * 100).toInt().coerceIn(0, 100)

        // 1. Update hardware matrix saturation via GrayscaleManager
        GrayscaleManager.setSaturationLevel(context, saturationPercent)

        // 2. Smoothly fade color overlay veil if overlayView is active
        if (overlayView is DesaturationOverlayView) {
            overlayView.setSaturation(s)
        }

        // 3. At 100% completion (s == 0.0f), lock in system-wide native daltonizer
        if (s <= 0.001f) {
            GrayscaleManager.setGrayscaleEnabled(context, true)
            AppLogger.i(TAG, "Completed transition: Enabled 100% native system grayscale")
        } else if (s >= 0.999f) {
            GrayscaleManager.setGrayscaleEnabled(context, false)
            AppLogger.d(TAG, "Restored 100% full vibrant display color")
        }
    }

    fun updateSaturationAndBlur(saturationFactor: Float, blurRadiusPx: Int) {
        updateSaturationAndBlur(saturationFactor, blurRadiusPx.toFloat())
    }

    fun updateSaturation(saturationFactor: Float) {
        updateSaturationAndBlur(saturationFactor, 0f)
    }
}