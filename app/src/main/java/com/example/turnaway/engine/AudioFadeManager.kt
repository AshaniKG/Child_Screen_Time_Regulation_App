package com.example.turnaway.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.example.turnaway.util.AppLogger
import kotlin.math.roundToInt

/**
 * Manages system-wide background audio volume reduction, continuous clamping,
 * and restoration during the Soft-Landing transition.
 */
class AudioFadeManager(
    private val context: Context,
    private val audioManager: AudioManager
) {
    private val TAG = "AudioFadeManager"
    private var isTracking = false
    private var initialVolume: Int = 10
    private var totalTransitionMs: Long = 0L
    private var transitionStartTime: Long = 0L
    private var enableAudioFade: Boolean = true

    private var volumeObserver: ContentObserver? = null
    private var volumeBroadcastReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())

    fun startFadeSession(
        durationMs: Long,
        enableFade: Boolean
    ) {
        enableAudioFade = enableFade
        totalTransitionMs = durationMs
        transitionStartTime = System.currentTimeMillis()

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (currentVol > 0) {
            initialVolume = currentVol
        } else if (initialVolume <= 0) {
            initialVolume = (maxVol * 0.7f).roundToInt().coerceIn(1, maxVol)
        }

        AppLogger.i(TAG, "Started AudioFadeManager session: duration=${durationMs}ms, enableFade=$enableFade, initialVol=$initialVolume/$maxVol")

        if (enableAudioFade) {
            registerVolumeObserver()
            // Immediately enforce initial target volume
            applyVolumeForElapsed(0L)
        }
    }

    fun getCurrentAllowedVolume(elapsedMs: Long): Int {
        if (!enableAudioFade) return initialVolume
        return DecayCurveCalculator.calculateCurrentVolume(
            elapsedTransitionMs = elapsedMs,
            totalTransitionMs = totalTransitionMs,
            initialVol = initialVolume
        )
    }

    fun applyVolumeForElapsed(elapsedMs: Long) {
        if (!enableAudioFade) return
        val allowedVol = getCurrentAllowedVolume(elapsedMs)
        enforceVolume(allowedVol)
    }

    fun enforceVolume(allowedVol: Int) {
        if (!enableAudioFade) return
        try {
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVol > allowedVol) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, allowedVol, 0)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error enforcing media stream volume", e)
        }
    }

    private fun registerVolumeObserver() {
        if (isTracking) return
        try {
            volumeObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    val elapsedMs = System.currentTimeMillis() - transitionStartTime
                    val allowed = getCurrentAllowedVolume(elapsedMs)
                    enforceVolume(allowed)
                }
            }

            context.contentResolver.registerContentObserver(
                android.provider.Settings.System.CONTENT_URI,
                true,
                volumeObserver!!
            )

            volumeBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val elapsedMs = System.currentTimeMillis() - transitionStartTime
                    val allowed = getCurrentAllowedVolume(elapsedMs)
                    enforceVolume(allowed)
                }
            }

            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(volumeBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(volumeBroadcastReceiver, filter)
            }

            isTracking = true
            AppLogger.i(TAG, "Audio volume content observer and broadcast receiver registered successfully")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error registering volume observers", e)
        }
    }

    fun stopAndRestoreVolume() {
        if (isTracking) {
            try {
                volumeObserver?.let {
                    context.contentResolver.unregisterContentObserver(it)
                }
                volumeBroadcastReceiver?.let {
                    context.unregisterReceiver(it)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error unregistering volume observers", e)
            }
            volumeObserver = null
            volumeBroadcastReceiver = null
            isTracking = false
        }

        if (enableAudioFade) {
            try {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val restoreVol = if (initialVolume > 0) initialVolume else (maxVol * 0.7f).roundToInt().coerceIn(1, maxVol)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVol, 0)
                AppLogger.i(TAG, "Restored media stream volume to $restoreVol")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error restoring volume", e)
            }
        }
    }
}
