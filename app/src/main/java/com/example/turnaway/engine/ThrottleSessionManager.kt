package com.example.turnaway.engine

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manager adapter for local network throttling VPN sessions, delegating autonomous timing cycles
 * to [ThrottleCycleManager] and providing status flows for application UI.
 */
class ThrottleSessionManager private constructor(private val context: Context) {

    private val TAG = "ThrottleSessionManager"
    private val isSessionActiveState = AtomicBoolean(false)
    private val throttleCycleManager = ThrottleCycleManager.getInstance(context)

    val isDropActiveFlow: StateFlow<Boolean> = throttleCycleManager.isDropActiveFlow

    companion object {
        @Volatile
        private var INSTANCE: ThrottleSessionManager? = null

        fun getInstance(context: Context): ThrottleSessionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = ThrottleSessionManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Checks if VPN permission consent is required.
     * Returns null if consent is already granted, or Intent to request consent from user.
     */
    fun checkVpnPermissionNeeded(): Intent? {
        return VpnService.prepare(context)
    }

    fun onTransitionProgress(elapsedMs: Long, totalTransitionMs: Long) {
        throttleCycleManager.onTransitionProgress(elapsedMs, totalTransitionMs)
    }

    fun startSession() {
        if (isSessionActiveState.getAndSet(true)) {
            AppLogger.d(TAG, "Network throttling session is already active")
            return
        }
        AppLogger.i(TAG, "Starting network throttling session")
        throttleCycleManager.startThrottlingCycle()
    }

    fun stopSession() {
        isSessionActiveState.set(false)
        AppLogger.i(TAG, "Stopping network throttling session")
        throttleCycleManager.stopAndTeardown()
    }

    fun isThrottlingActive(): Boolean {
        return isSessionActiveState.get() || throttleCycleManager.isThrottlingRunning || ThrottlerVpnService.isRunning()
    }
}
