package com.example.turnaway.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dedicated orchestrator managing system-wide mid-transition network throttling via local VpnService.
 *
 * Timing & Lifecycle Rules:
 * 1. First half of transition (0 <= t < 0.5 * T_tr): Throttling strictly OFF (VPN idle / pass-through).
 * 2. Second half of transition (0.5 * T_tr <= t < T_tr): Enforces a repeating 60-second cycle:
 *    - 0s to 50s: Normal throughput / transparent pass-through (isDropActive = false)
 *    - 50s to 60s (10 full seconds): Complete drop mode (isDropActive = true)
 * 3. Transition End (t >= T_tr): Auto-stops completely, tears down VPN service, and restores standard connectivity.
 * 4. Early Cancellation: Terminates VPN service immediately and restores connectivity.
 */
class ThrottleCycleManager private constructor(private val context: Context) {

    private val TAG = "ThrottleCycleManager"
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var throttleJob: Job? = null

    @Volatile
    var isThrottlingRunning: Boolean = false
        private set

    private val _isDropActiveFlow = MutableStateFlow(false)
    val isDropActiveFlow: StateFlow<Boolean> = _isDropActiveFlow.asStateFlow()

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
     * Evaluates transition progress and updates throttling state automatically based on timing:
     * - 0 <= elapsedMs < midpointMs: Ensure throttling cycle is OFF
     * - midpointMs <= elapsedMs < totalTransitionMs: Enforce 50s open / 10s drop cycle
     * - elapsedMs >= totalTransitionMs: Auto-stop completely and teardown VPN service
     */
    fun onTransitionProgress(elapsedMs: Long, totalTransitionMs: Long) {
        if (totalTransitionMs <= 0L) {
            if (isThrottlingRunning || ThrottlerVpnService.isRunning()) {
                stopAndTeardown()
            }
            return
        }

        val midpointMs = totalTransitionMs / 2L

        when {
            // 1. Before midpoint (0 <= t < 0.5 * T_tr): do nothing / keep throttling OFF
            elapsedMs < midpointMs -> {
                if (isThrottlingRunning) {
                    AppLogger.i(TAG, "First half of transition ($elapsedMs ms < midpoint $midpointMs ms): keeping throttling OFF")
                    stopThrottlingCycle()
                }
            }
            // 2. Between midpoint and end (0.5 * T_tr <= t < T_tr): enforce 50s open / 10s drop cycle
            elapsedMs in midpointMs until totalTransitionMs -> {
                if (!isThrottlingRunning) {
                    AppLogger.i(TAG, "Reached transition midpoint ($elapsedMs ms >= midpoint $midpointMs ms): starting 50s open / 10s drop throttling cycle")
                    startThrottlingCycle()
                }
            }
            // 3. At or after transition end (t >= T_tr): auto-stop completely and teardown VPN
            elapsedMs >= totalTransitionMs -> {
                if (isThrottlingRunning || ThrottlerVpnService.isRunning()) {
                    AppLogger.i(TAG, "Transition duration elapsed ($elapsedMs ms >= total $totalTransitionMs ms): auto-stopping throttling and tearing down VPN service")
                    stopAndTeardown()
                }
            }
        }
    }

    fun startThrottlingCycle() {
        if (isThrottlingRunning) return
        isThrottlingRunning = true
        ensureVpnServiceRunning()

        throttleJob?.cancel()
        throttleJob = serviceScope.launch {
            try {
                while (isActive && isThrottlingRunning) {
                    // 50 seconds normal traffic
                    setDropState(false)
                    AppLogger.i(TAG, "Throttling cycle [0s - 50s]: Normal transparent throughput")
                    delay(50_000L)

                    if (!isActive || !isThrottlingRunning) break

                    // 10 seconds total drop
                    setDropState(true)
                    AppLogger.w(TAG, "Throttling cycle [50s - 60s]: Complete network drop engaged for 10 full seconds")
                    delay(10_000L)
                }
            } catch (e: CancellationException) {
                AppLogger.d(TAG, "Throttling cycle coroutine cancelled")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error during throttling cycle execution", e)
            } finally {
                setDropState(false)
            }
        }
    }

    fun stopThrottlingCycle() {
        isThrottlingRunning = false
        throttleJob?.cancel()
        throttleJob = null
        setDropState(false)
    }

    fun stopAndTeardown() {
        stopThrottlingCycle()
        teardownVpnService()
    }

    private fun setDropState(active: Boolean) {
        _isDropActiveFlow.value = active
        ThrottlerVpnService.setDropState(active)
    }

    private fun ensureVpnServiceRunning() {
        if (!ThrottlerVpnService.isRunning()) {
            try {
                val intent = Intent(context, ThrottlerVpnService::class.java).apply {
                    action = ThrottlerVpnService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
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
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to send ACTION_STOP to ThrottlerVpnService", e)
        }
    }
}
