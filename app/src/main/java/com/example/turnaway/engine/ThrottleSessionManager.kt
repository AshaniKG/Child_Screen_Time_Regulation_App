package com.example.turnaway.engine

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manager adapter for local network throttling VPN sessions, delegating autonomous timing cycles
 * to [ThrottleCycleManager] and providing status flows for the application UI.
 */
class ThrottleSessionManager private constructor(private val context: Context) : NetworkThrottleController {

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
     * Returns intent if permission is needed, or null if already granted.
     */
    fun checkVpnPermissionNeeded(): Intent? {
        return VpnService.prepare(context)
    }

    fun onTransitionProgress(elapsedMs: Long, totalTransitionMs: Long) {
        throttleCycleManager.onTransitionProgress(elapsedMs, totalTransitionMs)
    }

    override fun startSession() {
        if (isSessionActiveState.getAndSet(true)) {
            AppLogger.d(TAG, "Network throttling session is already active")
            return
        }
        AppLogger.i(TAG, "Starting network throttling session")
    }

    override fun stopSession() {
        isSessionActiveState.set(false)
        AppLogger.i(TAG, "Stopping network throttling session")
        throttleCycleManager.stopAndTeardown()
    }

    override fun setThrottlingActive(enabled: Boolean) {
        if (enabled) {
            startSession()
        } else {
            stopSession()
        }
    }

    override fun triggerTemporaryDrop(durationMs: Long) {
        throttleCycleManager.startThrottlingCycle()
    }

    override fun setBandwidthLimitKbps(kbps: Int?) {
        AppLogger.d(TAG, "Bandwidth limit configuration note: $kbps")
    }

    fun isSessionActive(): Boolean {
        return isSessionActiveState.get() || throttleCycleManager.isThrottlingRunning || ThrottlerVpnService.isRunning()
    }

    fun setTargetPackageNames(packages: Set<String>) {
        // System-wide throttling routes all device traffic globally; target app filtering is no longer required.
    }

    fun updateTargetPackages(packages: Set<String>) {
        setTargetPackageNames(packages)
    }
}
