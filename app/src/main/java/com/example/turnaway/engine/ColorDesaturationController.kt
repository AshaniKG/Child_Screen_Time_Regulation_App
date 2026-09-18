package com.example.turnaway.engine

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import com.example.turnaway.util.AppLogger
import java.lang.reflect.Method

class DesaturationOverlayView(context: Context) : View(context) {
    private var saturationFactor = 1.0f
    private var blurFactor = 0.0f

    // 1. Android ColorMatrix initialized for human visual luminance sensitivity (Rec. 709: ~21% R, ~71% G, ~7% B)
    private val colorMatrix = ColorMatrix()
    private val matrixPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val diffusePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        // 2. Enable Hardware Blending: offload rendering layer to GPU to eliminate frame drops over active apps
        setLayerType(LAYER_TYPE_HARDWARE, matrixPaint)
        updateColorMatrix(1.0f)
    }

    fun updateColorMatrix(s: Float) {
        val sat = s.coerceIn(0.0f, 1.0f)
        val lr = 0.2126f
        val lg = 0.7152f
        val lb = 0.0722f

        // Black-scale transformation: scales both color saturation and luminance toward zero (black)
        val matrix = floatArrayOf(
            (lr + (1f - lr) * sat) * sat, (lg * (1f - sat)) * sat,       (lb * (1f - sat)) * sat,       0f, 0f,
            (lr * (1f - sat)) * sat,       (lg + (1f - lg) * sat) * sat, (lb * (1f - sat)) * sat,       0f, 0f,
            (lr * (1f - sat)) * sat,       (lg * (1f - sat)) * sat,       (lb + (1f - lb) * sat) * sat, 0f, 0f,
            0f,                           0f,                           0f,                           1f, 0f
        )
        colorMatrix.set(matrix)
        // 3. Attach ColorMatrixColorFilter to the drawing pipeline
        matrixPaint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        setLayerType(LAYER_TYPE_HARDWARE, matrixPaint)
    }

    fun setSaturationAndBlur(saturationFactor: Float, blurFactor: Float) {
        this.saturationFactor = saturationFactor.coerceIn(0.0f, 1.0f)
        this.blurFactor = blurFactor.coerceIn(0.0f, 1.0f)
        updateColorMatrix(this.saturationFactor)
        invalidate()
    }

    fun setSaturation(factor: Float) {
        setSaturationAndBlur(factor, (1.0f - factor).coerceIn(0.0f, 1.0f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }
}

class ColorDesaturationController(
    private val context: Context,
    private val overlayView: View,
    private val overlayManager: WindowManager? = null
) {
    private val TAG = "ColorDesaturation"
    
    private var colorDisplayManager: Any? = null
    private var setSaturationMethod: Method? = null
    private var reflectionInitialized = false

    init {
        tryInitReflection()
    }

    private fun tryInitReflection() {
        if (reflectionInitialized) return
        try {
            val cdmClass = Class.forName("android.hardware.display.ColorDisplayManager")
            colorDisplayManager = context.getSystemService(cdmClass)
            if (colorDisplayManager != null) {
                setSaturationMethod = cdmClass.getMethod("setSaturationLevel", Int::class.javaPrimitiveType)
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "ColorDisplayManager reflection not available: ${e.message}")
        }
        reflectionInitialized = true
    }

    fun hasWriteSecureSettingsPermission(): Boolean {
        return context.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun updateSaturationAndBlur(saturationFactor: Float, blurRadiusPx: Int) {
        val s = saturationFactor.coerceIn(0.0f, 1.0f)
        val clampedRadius = blurRadiusPx.coerceIn(0, 10)
        val blurFactor = (clampedRadius / 10f).coerceIn(0.0f, 1.0f)

        // 1. Apply system-level native Gaussian window blur behind the overlay (Android 12+ / API 31)
        applyWindowBlurBehind(clampedRadius)

        // 2. Try hidden API for smooth real display desaturation
        var hardwareSuccess = false
        if (setSaturationMethod != null && colorDisplayManager != null) {
            try {
                val level = (s * 100).toInt()
                setSaturationMethod!!.invoke(colorDisplayManager, level)
                hardwareSuccess = true
            } catch (e: Exception) {
                // Fails if without CONTROL_DISPLAY_COLOR_TRANSFORMS permission
            }
        }

        // 3. Fallback to Daltonizer + Overlay Canvas
        if (!hardwareSuccess) {
            val hasAdb = hasWriteSecureSettingsPermission()
            if (hasAdb) {
                try {
                    if (s <= 0.05f) {
                        Settings.Secure.putInt(context.contentResolver, "accessibility_display_daltonizer_enabled", 1)
                        Settings.Secure.putInt(context.contentResolver, "accessibility_display_daltonizer", 0)
                        AppLogger.i(TAG, "Applied native Daltonizer (Grayscale) at 0 saturation")
                    } else {
                        Settings.Secure.putInt(context.contentResolver, "accessibility_display_daltonizer_enabled", 0)
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to apply Daltonizer settings", e)
                }
            }
            
            // Apply overlay fallback during transition (white-washed and Gaussian blurred)
            applyOverlayFallback(s, blurFactor)
        }
    }

    private fun applyWindowBlurBehind(blurRadiusPx: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val targetRadius = blurRadiusPx.coerceIn(0, 10)
                if (targetRadius > 0) {
                    overlayView.setRenderEffect(
                        android.graphics.RenderEffect.createBlurEffect(
                            targetRadius.toFloat(),
                            targetRadius.toFloat(),
                            android.graphics.Shader.TileMode.CLAMP
                        )
                    )
                } else {
                    overlayView.setRenderEffect(null)
                }

                if (overlayManager != null) {
                    val lp = overlayView.layoutParams as? WindowManager.LayoutParams ?: return
                    if (targetRadius > 0) {
                        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                        lp.setBlurBehindRadius(targetRadius)
                    } else {
                        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                        lp.setBlurBehindRadius(0)
                    }
                    overlayManager.updateViewLayout(overlayView, lp)
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "Gaussian blur update error: ${e.message}")
            }
        }
    }

    private fun applyOverlayFallback(saturationFactor: Float, blurFactor: Float) {
        if (overlayView is DesaturationOverlayView) {
            overlayView.setSaturationAndBlur(saturationFactor, blurFactor)
        } else {
            val s = saturationFactor.coerceIn(0.0f, 1.0f)
            val alpha = ((1.0f - s) * 220).toInt()
            overlayView.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
        }
    }

    fun updateSaturation(saturationFactor: Float) {
        val blurRadius = ((1.0f - saturationFactor) * 10).toInt().coerceIn(0, 10)
        updateSaturationAndBlur(saturationFactor, blurRadius)
    }
}






