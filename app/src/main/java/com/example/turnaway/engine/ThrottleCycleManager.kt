package com.example.turnaway.engine

import android.content.Context
import android.content.Intent
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Dedicated orchestrator managing system-wide network throttling via local VpnService.
 *
 * Timing Specification:
 * - Begins at 50% (halfway point) of the wind-down transition duration.
 * - During 2nd half of transition phase: Alternating 1-minute ON / 1-minute OFF cycle (120-second period).
 * - 0s <= (activeElapsedMs % 120,000) < 60,000: System-wide network drop engaged (VPN blackholing).
 * - 60s <= (activeElapsedMs % 120,000) < 120,000: Normal connectivity restored (VPN pass-through).
 * - Transition End / Session Abort: Auto-stops completely and tears down VPN service automatically.
 */
class ThrottleCycleManager private constructor(private val context: Context) {

    private val TAG = "ThrottleCycleManager"
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var throttleJob: Job? = null

    val isDropActiveFlow: StateFlow<Boolean> = ThrottlerVpnService.isDropActiveFlow
    val isThrottlingRunning: Boolean get() = throttleJob?.isActive == true

    companion object {
        @Volatile
        private var INSTANCE: ThrottleCycleManager? = null

        fun getInstance(context: Context): ThrottleCycleManager {
            return INSTANCE ?: synchronized(this) {
                val instance = ThrottleCycleManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Evaluates transition progress and starts throttling at 50% transition progress, stopping when complete.
     */
    fun onTransitionProgress(elapsedMs: Long, totalTransitionMs: Long) {
        if (elapsedMs >= totalTransitionMs) {
            if (isThrottlingRunning || ThrottlerVpnService.isRunning()) {
                AppLogger.i(TAG, "Transition duration elapsed ($elapsedMs ms >= $totalTransitionMs ms): stopping network throttling cycle")
                stopAndTeardown()
            }
            return
        }

        val halfTransitionMs = totalTransitionMs / 2L
        if (elapsedMs < halfTransitionMs) {
            if (isDropActiveFlow.value) {
                setDropState(false)
                AppLogger.i(TAG, "First half of transition ($elapsedMs ms < $halfTransitionMs ms): network throttle inactive")
            }
            return
        }

        ensureVpnServiceRunning()

        val activeElapsedMs = elapsedMs - halfTransitionMs
        val cyclePosMs = activeElapsedMs % 120_000L
        val shouldDrop = cyclePosMs < 60_000L

        if (shouldDrop != isDropActiveFlow.value) {
            setDropState(shouldDrop)
            if (shouldDrop) {
                AppLogger.w(TAG, "1-minute system-wide network drop engaged at t=$elapsedMs ms (second-half activeElapsed=${activeElapsedMs}ms)")
            } else {
                AppLogger.i(TAG, "1-minute network drop ended at t=$elapsedMs ms; 1-minute normal connectivity restored")
            }
        }
    }

    fun startThrottlingCycle() {
        if (isThrottlingRunning) return
        ensureVpnServiceRunning()
        AppLogger.i(TAG, "Started system-wide throttling cycle")
    }

    fun stopAndTeardown() {
        throttleJob?.cancel()
        throttleJob = null
        setDropState(false)
        teardownVpnService()
        AppLogger.i(TAG, "Stopped throttling cycle and tore down VPN service")
    }

    private fun setDropState(active: Boolean) {
        ThrottlerVpnService.setDropState(active)
    }

    private fun ensureVpnServiceRunning() {
        if (!ThrottlerVpnService.isRunning()) {
            try {
                val intent = Intent(context, ThrottlerVpnService::class.java).apply {
                    action = ThrottlerVpnService.ACTION_START
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                AppLogger.i(TAG, "Started ThrottlerVpnService foreground service")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to start ThrottlerVpnService", e)
            }
        }
    }

    private fun teardownVpnService() {
        try {
            val intent = Intent(context, ThrottlerVpnService::class.java).apply {
                action = ThrottlerVpnService.ACTION_STOP
            }
            context.startService(intent)
            AppLogger.i(TAG, "Sent ACTION_STOP to ThrottlerVpnService")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to send ACTION_STOP to ThrottlerVpnService", e)
        }
    }
}
