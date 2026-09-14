package com.example.turnaway.engine

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.Settings
import android.view.View
import com.example.turnaway.util.AppLogger
import java.lang.reflect.Method

class DesaturationOverlayView(context: Context) : View(context) {
    private var saturationFactor = 1.0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setSaturation(factor: Float) {
        this.saturationFactor = factor.coerceIn(0.0f, 1.0f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (saturationFactor < 0.98f) {
            // High alpha, light silver color creates a strong "washed out" foggy effect
            val desaturationAlpha = ((1.0f - saturationFactor) * 225).toInt().coerceIn(0, 225)
            paint.color = Color.argb(desaturationAlpha, 210, 210, 210)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }
}

class ColorDesaturationController(
    private val context: Context,
    private val overlayView: View
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

    fun updateSaturation(saturationFactor: Float) {
        val s = saturationFactor.coerceIn(0.0f, 1.0f)
        
        // Try hidden API for smooth real desaturation first
        var hardwareSuccess = false
        if (setSaturationMethod != null && colorDisplayManager != null) {
            try {
                val level = (s * 100).toInt()
                setSaturationMethod!!.invoke(colorDisplayManager, level)
                hardwareSuccess = true
            } catch (e: Exception) {
                // Fails if we don't have CONTROL_DISPLAY_COLOR_TRANSFORMS permission
            }
        }

        // If smooth hardware desaturation failed, fallback to Daltonizer + Overlay
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
            
            // Always apply overlay fallback during transition so it looks white-washed
            applyOverlayFallback(s)
        }
    }

    private fun applyOverlayFallback(saturationFactor: Float) {
        if (overlayView is DesaturationOverlayView) {
            overlayView.setSaturation(saturationFactor)
        } else {
            val s = saturationFactor.coerceIn(0.0f, 1.0f)
            val alpha = ((1.0f - s) * 200).toInt()
            overlayView.setBackgroundColor(Color.argb(alpha, 180, 180, 180))
        }
    }
}






