package com.example.turnaway.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Helper class for managing native system-wide grayscale (daltonizer & saturation) settings.
 *
 * Toggles Android's native monochromacy mode via [Settings.Secure] requiring
 * [Manifest.permission.WRITE_SECURE_SETTINGS] granted via ADB or system authority.
 */
object GrayscaleManager {

    private const val TAG = "GrayscaleManager"

    private const val DISPLAY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val DISPLAY_DALTONIZER = "accessibility_display_daltonizer"

    // Daltonizer monochromacy mode constant
    private const val MONOCHROMACY_MODE = 0
    private const val DALTONIZER_DISABLED = -1

    private var colorDisplayManager: Any? = null
    private var setSaturationMethod: java.lang.reflect.Method? = null
    private var reflectionInitialized = false

    private fun initReflection(context: Context) {
        if (reflectionInitialized) return
        try {
            val cdmClass = Class.forName("android.hardware.display.ColorDisplayManager")
            colorDisplayManager = context.getSystemService(cdmClass)
            if (colorDisplayManager != null) {
                setSaturationMethod = cdmClass.getMethod("setSaturationLevel", Int::class.javaPrimitiveType)
                AppLogger.d(TAG, "ColorDisplayManager reflection initialized successfully")
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "ColorDisplayManager reflection not available: ${e.message}")
        }
        reflectionInitialized = true
    }

    /**
     * Checks if [Manifest.permission.WRITE_SECURE_SETTINGS] is granted to the application.
     */
    fun isPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Queries current system grayscale status from [Settings.Secure].
     * Returns true if both [DISPLAY_DALTONIZER_ENABLED] is 1 and [DISPLAY_DALTONIZER] is 0 (monochromacy).
     */
    fun isGrayscaleActive(context: Context): Boolean {
        return try {
            val enabled = Settings.Secure.getInt(
                context.contentResolver,
                DISPLAY_DALTONIZER_ENABLED,
                0
            ) == 1
            val mode = Settings.Secure.getInt(
                context.contentResolver,
                DISPLAY_DALTONIZER,
                DALTONIZER_DISABLED
            ) == MONOCHROMACY_MODE
            enabled && mode
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking grayscale status", e)
            false
        }
    }

    /**
     * Sets display saturation level smoothly from 100 (full color) down to 0 (monochromacy/grayscale).
     *
     * 1. Attempts hidden API [ColorDisplayManager.setSaturationLevel] for hardware matrix desaturation.
     * 2. Engages native daltonizer ONLY when saturation level reaches 0 (100% complete grayscale).
     */
    fun setSaturationLevel(context: Context, saturationPercent: Int): Boolean {
        val level = saturationPercent.coerceIn(0, 100)
        initReflection(context)

        if (setSaturationMethod != null && colorDisplayManager != null) {
            try {
                setSaturationMethod!!.invoke(colorDisplayManager, level)
                AppLogger.d(TAG, "Set ColorDisplayManager saturation level to $level%")
            } catch (e: Exception) {
                AppLogger.d(TAG, "ColorDisplayManager.setSaturationLevel failed: ${e.message}")
            }
        }

        // Native daltonizer is binary ON/OFF. Keep it OFF during intermediate fading levels,
        // and turn it ON only when saturation reaches 0 (100% grayscale at end of duration).
        return if (level == 0) {
            setGrayscaleEnabled(context, true)
        } else {
            setGrayscaleEnabled(context, false)
        }
    }

    /**
     * Enables or disables system-wide native grayscale via [Settings.Secure].
     *
     * - Enable: Sets [DISPLAY_DALTONIZER] to 0 (monochromacy) and [DISPLAY_DALTONIZER_ENABLED] to 1.
     * - Disable: Sets [DISPLAY_DALTONIZER_ENABLED] to 0 and [DISPLAY_DALTONIZER] to -1.
     *
     * Wraps modifications in try-catch handling [SecurityException].
     * @return true if successful, false otherwise.
     */
    fun setGrayscaleEnabled(context: Context, enabled: Boolean): Boolean {
        if (!isPermissionGranted(context)) {
            AppLogger.w(TAG, "WRITE_SECURE_SETTINGS permission not granted. Cannot toggle grayscale.")
            return false
        }

        return try {
            if (enabled) {
                // Enable native monochromacy
                Settings.Secure.putInt(
                    context.contentResolver,
                    DISPLAY_DALTONIZER,
                    MONOCHROMACY_MODE
                )
                Settings.Secure.putInt(
                    context.contentResolver,
                    DISPLAY_DALTONIZER_ENABLED,
                    1
                )
                AppLogger.i(TAG, "Successfully enabled native system-wide grayscale (monochromacy)")
            } else {
                // Disable daltonizer
                Settings.Secure.putInt(
                    context.contentResolver,
                    DISPLAY_DALTONIZER_ENABLED,
                    0
                )
                Settings.Secure.putInt(
                    context.contentResolver,
                    DISPLAY_DALTONIZER,
                    DALTONIZER_DISABLED
                )
                AppLogger.i(TAG, "Successfully disabled native system-wide grayscale")
            }
            true
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "SecurityException while toggling grayscale. WRITE_SECURE_SETTINGS may be missing.", e)
            false
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to toggle grayscale settings", e)
            false
        }
    }

    /**
     * Returns the exact ADB command required to grant WRITE_SECURE_SETTINGS to the app.
     */
    fun getAdbCommand(context: Context): String {
        return "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }
}
