package com.example.turnaway.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.WindowManager
import com.example.turnaway.util.AppLogger
import com.example.turnaway.util.GrayscaleManager

/**
 * Overlay view that renders:
 * 1. An optional visual color veil (with customizable tint color and max opacity)
 * 2. An optical frosted-glass softening & blur dispersion effect (up to 80px),
 *    working reliably across all devices including those where hardware cross-window blur is disabled.
 */
class DesaturationOverlayView(context: Context) : View(context) {

    private var overlayEnabled: Boolean = false
    private var overlayProgress: Float = 0f
    private var overlayColor: Int = Color.GRAY
    private var overlayMaxAlpha: Float = 0.70f
    private var blurRadius: Float = 0f

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val blurBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val blurNoisePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var frostedNoiseBitmap: Bitmap? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        createFrostedNoiseTexture()
    }

    private fun createFrostedNoiseTexture() {
        try {
            val size = 64
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val random = java.util.Random(1337)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val brightness = 210 + random.nextInt(45)
                    val alpha = 50 + random.nextInt(90)
                    bitmap.setPixel(x, y, Color.argb(alpha, brightness, brightness, brightness))
                }
            }
            val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            blurNoisePaint.shader = shader
            frostedNoiseBitmap = bitmap
        } catch (e: Exception) {
            AppLogger.e("DesaturationOverlayView", "Failed to create frosted noise texture", e)
        }
    }

    fun setOverlayConfig(
        enabled: Boolean,
        progress: Float,
        color: Int,
        maxAlpha: Float
    ) {
        this.overlayEnabled = enabled
        this.overlayProgress = progress.coerceIn(0.0f, 1.0f)
        this.overlayColor = color
        this.overlayMaxAlpha = maxAlpha.coerceIn(0.05f, 1.0f)
        invalidate()
    }

    fun setBlur(radius: Float) {
        this.blurRadius = radius.coerceAtLeast(0f)
        invalidate()
    }

    fun clearAll() {
        this.overlayEnabled = false
        this.overlayProgress = 0f
        this.blurRadius = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw custom color veil if overlay is enabled
        if (overlayEnabled && overlayProgress > 0.001f) {
            val alpha = (overlayProgress * overlayMaxAlpha * 255).toInt().coerceIn(0, 255)
            val r = Color.red(overlayColor)
            val g = Color.green(overlayColor)
            val b = Color.blue(overlayColor)
            overlayPaint.color = Color.argb(alpha, r, g, b)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        }

        // 2. Optical Frosted-Glass Softening & Blur Diffusion (scaled up to 80px)
        if (blurRadius > 0.01f) {
            val blurProgress = (blurRadius / 80f).coerceIn(0f, 1f)

            // Pass A: Milky/Frosted diffusion veil that softens high-contrast elements beneath
            // Alpha scales from 35 (mild softening) up to 200 (~80% frosted glass opacity)
            val baseAlpha = (35 + blurProgress * 165).toInt().coerceIn(0, 205)
            blurBasePaint.color = Color.argb(baseAlpha, 225, 230, 240)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blurBasePaint)

            // Pass B: Micro-lattice dispersion texture that scatters light and breaks edge sharpness
            if (blurNoisePaint.shader != null) {
                val noiseAlpha = (blurProgress * 175).toInt().coerceIn(0, 180)
                blurNoisePaint.alpha = noiseAlpha
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blurNoisePaint)
            }
        }
    }
}

/**
 * Controls display visual adjustments:
 * - Native hardware system grayscale (via [GrayscaleManager])
 * - Visual screen overlay veil with customizable tint color and max opacity
 * - Optical frosted softening & hardware window blur behind (up to 80px)
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

    /**
     * Updates all visual effects independently.
     */
    fun updateVisualEffects(
        enableSystemGrayscale: Boolean,
        saturationFactor: Float = 1.0f,
        enableOverlay: Boolean = false,
        overlayProgress: Float = 0f,
        overlayColorHex: String = "#808080",
        overlayMaxAlpha: Float = 0.70f,
        blurRadiusPx: Float = 0f
    ) {
        // 1. System Grayscale (Native system display matrix via WRITE_SECURE_SETTINGS)
        if (enableSystemGrayscale) {
            val s = saturationFactor.coerceIn(0.0f, 1.0f)
            val saturationPercent = (s * 100).toInt().coerceIn(0, 100)
            GrayscaleManager.setSaturationLevel(context, saturationPercent)

            if (s <= 0.001f) {
                GrayscaleManager.setGrayscaleEnabled(context, true)
            } else {
                GrayscaleManager.setGrayscaleEnabled(context, false)
            }
        } else {
            // System grayscale disabled: ensure native color saturation is 100%
            GrayscaleManager.setGrayscaleEnabled(context, false)
            GrayscaleManager.setSaturationLevel(context, 100)
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
            overlayView.setBlur(blurRadiusPx)
        }

        // 3. Android 12+ (API 31+) hardware window blur behind if supported by device
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && overlayView != null && overlayManager != null) {
            try {
                val lp = overlayView.layoutParams as? WindowManager.LayoutParams
                if (lp != null) {
                    val targetBlur = (blurRadiusPx * 2).toInt().coerceIn(0, 160)
                    if (targetBlur > 0) {
                        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                        lp.setBlurBehindRadius(targetBlur)
                    } else {
                        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                        lp.setBlurBehindRadius(0)
                    }
                    overlayManager.updateViewLayout(overlayView, lp)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error updating window blur behind radius", e)
            }
        }
    }

    /**
     * Backward-compatible helper for legacy callers.
     */
    fun updateSaturationAndBlur(saturationFactor: Float, blurRadiusPx: Float = 0f) {
        updateVisualEffects(
            enableSystemGrayscale = true,
            saturationFactor = saturationFactor,
            enableOverlay = false,
            overlayProgress = 0f,
            blurRadiusPx = blurRadiusPx
        )
    }

    fun updateSaturationAndBlur(saturationFactor: Float, blurRadiusPx: Int) {
        updateSaturationAndBlur(saturationFactor, blurRadiusPx.toFloat())
    }

    fun updateSaturation(saturationFactor: Float) {
        updateSaturationAndBlur(saturationFactor, 0f)
    }

    /**
     * Resets all visual effects back to normal defaults.
     */
    fun resetAll() {
        GrayscaleManager.setGrayscaleEnabled(context, false)
        GrayscaleManager.setSaturationLevel(context, 100)

        if (overlayView is DesaturationOverlayView) {
            overlayView.clearAll()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && overlayView != null && overlayManager != null) {
            try {
                val lp = overlayView.layoutParams as? WindowManager.LayoutParams
                if (lp != null) {
                    lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                    lp.setBlurBehindRadius(0)
                    overlayManager.updateViewLayout(overlayView, lp)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error resetting window blur behind radius", e)
            }
        }
    }
}