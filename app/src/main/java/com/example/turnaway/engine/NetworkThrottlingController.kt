package com.example.turnaway.engine

import android.content.Context
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * Controller for managing periodic network throttling during wind-down transition.
 * When enabled, network access is throttled for 5 seconds once every 1 to 2 minutes.
 */
class NetworkThrottlingController(private val context: Context) {

    private val TAG = "NetworkThrottling"
    private val controllerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var throttleJob: Job? = null

    private val isThrottlingActive = AtomicBoolean(false)
    private val isCurrentlyThrottled = AtomicBoolean(false)

    var onStateChangedListener: ((Boolean) -> Unit)? = null

    /**
     * Starts the network throttling cycle during wind-down.
     * Throttles network for 5 seconds, once every 1 to 2 minutes (60s - 120s).
     */
    fun startThrottling() {
        if (isThrottlingActive.getAndSet(true)) {
            AppLogger.d(TAG, "Network throttling cycle is already running")
            return
        }

        AppLogger.i(TAG, "Starting periodic network throttling cycle (5s duration every 1-2 mins)")

        throttleJob?.cancel()
        throttleJob = controllerScope.launch {
            try {
                while (isActive && isThrottlingActive.get()) {
                    // Random interval between 60 and 120 seconds (1-2 minutes)
                    val intervalMs = Random.nextLong(60_000L, 120_000L)
                    AppLogger.d(TAG, "Next network throttle window in ${intervalMs / 1000}s")

                    delay(intervalMs)

                    if (!isActive || !isThrottlingActive.get()) break

                    // Engage 5-second network throttle burst
                    engageNetworkThrottleBurst()
                }
            } catch (e: CancellationException) {
                AppLogger.d(TAG, "Network throttling coroutine cancelled")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in network throttling cycle", e)
            } finally {
                restoreNormalNetwork()
            }
        }
    }

    private suspend fun engageNetworkThrottleBurst() {
        AppLogger.w(TAG, "Engaging 5-second network throttle burst...")
        isCurrentlyThrottled.set(true)
        onStateChangedListener?.invoke(true)

        applyNetworkRestrictions(true)

        // Throttle for exactly 5 seconds
        delay(5000L)

        applyNetworkRestrictions(false)

        isCurrentlyThrottled.set(false)
        onStateChangedListener?.invoke(false)
        AppLogger.i(TAG, "5-second network throttle burst complete. Normal network restored.")
    }

    private fun applyNetworkRestrictions(enableRestriction: Boolean) {
        try {
            // Attempt standard system & shell netpolicy/svc commands via runtime process execution
            if (enableRestriction) {
                Runtime.getRuntime().exec(arrayOf("cmd", "netpolicy", "add", "restrict-background", context.packageName))
                Runtime.getRuntime().exec(arrayOf("svc", "wifi", "disable"))
                Runtime.getRuntime().exec(arrayOf("svc", "data", "disable"))
            } else {
                Runtime.getRuntime().exec(arrayOf("cmd", "netpolicy", "remove", "restrict-background", context.packageName))
                Runtime.getRuntime().exec(arrayOf("svc", "wifi", "enable"))
                Runtime.getRuntime().exec(arrayOf("svc", "data", "enable"))
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "System command execution note: ${e.message}")
        }
    }

    fun isCurrentlyThrottled(): Boolean {
        return isCurrentlyThrottled.get()
    }

    /**
     * Immediately stops network throttling and restores network connectivity.
     */
    fun stopThrottling() {
        if (isThrottlingActive.getAndSet(false)) {
            AppLogger.i(TAG, "Stopping network throttling")
        }
        throttleJob?.cancel()
        throttleJob = null
        restoreNormalNetwork()
    }

    private fun restoreNormalNetwork() {
        isCurrentlyThrottled.set(false)
        applyNetworkRestrictions(false)
        onStateChangedListener?.invoke(false)
    }

    fun release() {
        stopThrottling()
        controllerScope.cancel()
    }
}
