package com.example.turnaway.engine

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import com.example.turnaway.util.AppLogger
import java.lang.reflect.Method

class DesaturationOverlayView(context: Context) : View(context) {
    private var saturationFactor = 1.0f
    private var blurFactor = 0.0f
    private var blurRadiusPx = 0

    private val frostedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scatterPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var radialGradient: RadialGradient? = null
    private var scatterBitmap: Bitmap? = null
    private var scatterShader: BitmapShader? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        initScatterTexture()
    }

    private fun initScatterTexture() {
        try {
            // 64x64 subpixel frosted glass scattering texture
            val size = 64
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val random = java.util.Random(1337)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val alpha = 10 + random.nextInt(30)
                    val brightness = 230 + random.nextInt(25)
                    bitmap.setPixel(x, y, Color.argb(alpha, brightness, brightness, brightness))
                }
            }
            scatterBitmap = bitmap
            scatterShader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            scatterPaint.shader = scatterShader
        } catch (e: Exception) {
            AppLogger.d("DesaturationOverlay", "Scatter texture init error: ${e.message}")
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val cx = w / 2f
            val cy = h / 2f
            val radius = Math.hypot(cx.toDouble(), cy.toDouble()).toFloat()
            radialGradient = RadialGradient(
                cx, cy, radius,
                intArrayOf(Color.argb(0, 240, 242, 248), Color.argb(190, 235, 238, 245)),
                floatArrayOf(0.25f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
    }

    fun setSaturationAndBlur(saturationFactor: Float, blurFactor: Float, blurRadiusPx: Int = 0) {
        this.saturationFactor = saturationFactor.coerceIn(0.0f, 1.0f)
        this.blurFactor = blurFactor.coerceIn(0.0f, 1.0f)
        this.blurRadiusPx = blurRadiusPx

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (this.blurFactor > 0.05f) {
                val renderBlur = (this.blurFactor * 18f).coerceIn(1f, 25f)
                setRenderEffect(RenderEffect.createBlurEffect(renderBlur, renderBlur, Shader.TileMode.CLAMP))
            } else {
                setRenderEffect(null)
            }
        }
        invalidate()
    }

    fun setSaturation(factor: Float) {
        setSaturationAndBlur(factor, (1.0f - factor).coerceIn(0.0f, 1.0f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (blurFactor <= 0.001f) return

        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Primary Frosted Diffusion Veil:
        // Scaled to reach ~80% opacity at max (10 px), making fine details and text unreadable
        val baseAlpha = (blurFactor * 200).toInt().coerceIn(0, 200)
        frostedPaint.color = Color.argb(baseAlpha, 238, 240, 246)
        canvas.drawRect(0f, 0f, w, h, frostedPaint)

        // 2. Optical Micro-Scatter Texture (destroys sharp pixel edges)
        val scatterAlpha = (blurFactor * 170).toInt().coerceIn(0, 170)
        scatterPaint.alpha = scatterAlpha
        canvas.drawRect(0f, 0f, w, h, scatterPaint)

        // 3. Radial Peripheral Softening Vignette
        radialGradient?.let { grad ->
            vignettePaint.shader = grad
            vignettePaint.alpha = (blurFactor * 180).toInt().coerceIn(0, 180)
            canvas.drawRect(0f, 0f, w, h, vignettePaint)
        }
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

    fun updateSaturationAndBlur(saturationFactor: Float, blurRadiusPx: Float) {
        val s = saturationFactor.coerceIn(0.0f, 1.0f)
        val clampedRadius = blurRadiusPx.coerceIn(0f, 10f)
        val blurFactor = (clampedRadius / 10f).coerceIn(0.0f, 1.0f)

        // 1. Apply system-level native Gaussian window blur behind the overlay (Android 12+ / API 31)
        applyWindowBlurBehind(clampedRadius.toInt())

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

        // 3. Fallback to Daltonizer
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
        }

        // 4. Always render overlay frosted blur so it works even when system-level blur is disabled by OEM
        applyOverlayFallback(s, blurFactor, clampedRadius.toInt())
    }

    fun updateSaturationAndBlur(saturationFactor: Float, blurRadiusPx: Int) {
        updateSaturationAndBlur(saturationFactor, blurRadiusPx.toFloat())
    }

    private fun applyWindowBlurBehind(blurRadiusPx: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val targetRadius = blurRadiusPx.coerceIn(0, 10)

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
                    AppLogger.d(TAG, "Applied blur behind radius: ${targetRadius}px")
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "Gaussian blur update error: ${e.message}")
            }
        }
    }

    private fun applyOverlayFallback(saturationFactor: Float, blurFactor: Float, blurRadiusPx: Int = 0) {
        if (overlayView is DesaturationOverlayView) {
            overlayView.setSaturationAndBlur(saturationFactor, blurFactor, blurRadiusPx)
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