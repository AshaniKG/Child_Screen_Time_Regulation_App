package com.example.turnaway.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.turnaway.util.AppLogger
import kotlin.math.roundToInt

/**
 * Rebuilt System-Wide Media Volume Fade and Restoration Engine with Hardware Key & Slider Lockout.
 *
 * Strictly targets [AudioManager.STREAM_MUSIC] only.
 * Captures initial baseline volume at t=0, linearly decays system media volume to 0 (silence)
 * at the end of the transition duration, locks volume at 0 until stopped, and restores
 * baseline volume upon stopping.
 */
class MediaVolumeManager(
    private val context: Context,
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
) {
    private val TAG = "MediaVolumeManager"

    var capturedVolume: Int = -1
        private set

    var isFadingActive: Boolean = false
        private set

    var currentTargetVolume: Int = 0
        private set

    var transitionDurationMs: Long = 0L
        private set

    var transitionStartTime: Long = 0L
        private set

    var enableAudioFade: Boolean = true
        private set

    private var isTrackingObserver = false
    private var volumeObserver: ContentObserver? = null
    private var volumeBroadcastReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Snapshots current media volume as 100% baseline and starts the fade sequence.
     */
    fun startFade(durationMs: Long, enableFade: Boolean = true) {
        enableAudioFade = enableFade
        transitionDurationMs = durationMs
        transitionStartTime = System.currentTimeMillis()

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val rawVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        capturedVolume = if (rawVol > 0) {
            rawVol
        } else {
            (maxVol * 0.7f).roundToInt().coerceIn(1, maxVol)
        }
        currentTargetVolume = capturedVolume
        isFadingActive = true

        AppLogger.i(TAG, "Started MediaVolumeManager fade: duration=${durationMs}ms, enableFade=$enableFade, capturedVol=$capturedVolume/$maxVol")

        if (enableAudioFade) {
            registerVolumeObserver()
            applyVolumeForElapsed(0L)
        }
    }

    /**
     * Legacy compatibility method wrapper for startFade.
     */
    fun startFadeSession(durationMs: Long, enableFade: Boolean = true) {
        startFade(durationMs, enableFade)
    }

    /**
     * Calculates the target volume for the elapsed transition time t in [0, transitionDurationMs].
     * Target Volume = round(capturedVolume * (1.0 - (t / T_tr))).
     */
    fun calculateCurrentTargetVolume(elapsedMs: Long): Int {
        if (!enableAudioFade || capturedVolume < 0) return capturedVolume.coerceAtLeast(0)
        return DecayCurveCalculator.calculateCurrentVolume(
            elapsedTransitionMs = elapsedMs,
            totalTransitionMs = transitionDurationMs,
            initialVol = capturedVolume
        )
    }

    /**
     * Legacy compatibility method wrapper for calculateCurrentTargetVolume.
     */
    fun getCurrentAllowedVolume(elapsedMs: Long): Int {
        return calculateCurrentTargetVolume(elapsedMs)
    }

    /**
     * Computes the current target volume for elapsedMs and applies it via AudioManager.setStreamVolume.
     * Uses flag 0 to prevent volume popups from flashing on screen.
     */
    fun applyVolumeForElapsed(elapsedMs: Long) {
        if (!enableAudioFade || !isFadingActive) return
        val target = calculateCurrentTargetVolume(elapsedMs)
        currentTargetVolume = target
        try {
            val actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (actual != target) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                AppLogger.d(TAG, "Applied media volume $target/$capturedVolume for elapsed ${elapsedMs}ms")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error applying media volume", e)
        }
    }

    /**
     * Instantly neutralizes on-screen slider adjustments (ContentObserver or Intent broadcast)
     * if the actual system volume exceeds the active calculated ceiling currentTargetVolume.
     */
    fun enforceVolume() {
        if (!enableAudioFade || !isFadingActive) return
        val elapsedMs = System.currentTimeMillis() - transitionStartTime
        val target = calculateCurrentTargetVolume(elapsedMs)
        currentTargetVolume = target
        try {
            val actualVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (actualVol > currentTargetVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentTargetVolume, 0)
                AppLogger.w(TAG, "Clamped media volume override attempt back to $currentTargetVolume (was $actualVol)")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error enforcing media volume clamp", e)
        }
    }

    /**
     * Legacy compatibility method wrapper for enforceVolume.
     */
    fun enforceVolume(allowedVol: Int) {
        currentTargetVolume = allowedVol
        enforceVolume()
    }

    /**
     * Registers ContentObserver on Settings.System.CONTENT_URI and BroadcastReceiver for VOLUME_CHANGED_ACTION
     * to immediately catch touchscreen slider dragging.
     */
    private fun registerVolumeObserver() {
        if (isTrackingObserver) return
        try {
            volumeObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    enforceVolume()
                }
            }

            context.contentResolver.registerContentObserver(
                android.provider.Settings.System.CONTENT_URI,
                true,
                volumeObserver!!
            )

            volumeBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    enforceVolume()
                }
            }

            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(volumeBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(volumeBroadcastReceiver, filter)
            }

            isTrackingObserver = true
            AppLogger.i(TAG, "Registered system volume ContentObserver and VOLUME_CHANGED_ACTION BroadcastReceiver")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error registering volume observers", e)
        }
    }

    /**
     * Called when session is stopped or aborted.
     * Unregisters observers, restores media volume to captured baseline snapshot, and releases state.
     */
    fun onStopClicked() {
        if (isTrackingObserver) {
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
            isTrackingObserver = false
        }

        isFadingActive = false

        if (enableAudioFade && capturedVolume >= 0) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, capturedVolume, 0)
                AppLogger.i(TAG, "Restored media stream volume to captured baseline $capturedVolume")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error restoring volume to baseline", e)
            }
        }

        capturedVolume = -1
        currentTargetVolume = 0
    }

    /**
     * Legacy compatibility method wrapper for onStopClicked.
     */
    fun stopAndRestoreVolume() {
        onStopClicked()
    }
}
