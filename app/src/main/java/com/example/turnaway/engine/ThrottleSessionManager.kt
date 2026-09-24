package com.example.turnaway.engine

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton / Manager orchestrating local network throttling VPN sessions,
 * bandwidth clamping limits, and periodic 5-second drop windows once every 60 seconds.
 */
class ThrottleSessionManager private constructor(private val context: Context) : NetworkThrottleController {

    private val TAG = "ThrottleSessionManager"
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var dropTimerJob: Job? = null

    private val isSessionActiveState = AtomicBoolean(false)
    private val _isDropActiveFlow = MutableStateFlow(false)
    val isDropActiveFlow: StateFlow<Boolean> = _isDropActiveFlow.asStateFlow()

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

    override fun startSession() {
        if (isSessionActiveState.getAndSet(true)) {
            AppLogger.d(TAG, "Network throttling session is already active")
            return
        }

        AppLogger.i(TAG, "Starting network throttling VPN session")

        try {
            val intent = Intent(context, ThrottlerVpnService::class.java).apply {
                action = ThrottlerVpnService.ACTION_START
                putStringArrayListExtra(ThrottlerVpnService.EXTRA_TARGET_PACKAGES, ArrayList(ThrottlerVpnService.targetedPackageNames))
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start ThrottlerVpnService", e)
        }

        startPeriodicDropTimer()
    }

    override fun stopSession() {
        if (!isSessionActiveState.getAndSet(false)) {
            AppLogger.d(TAG, "Network throttling session is not active")
            return
        }

        AppLogger.i(TAG, "Stopping network throttling VPN session")

        dropTimerJob?.cancel()
        dropTimerJob = null
        _isDropActiveFlow.value = false
        ThrottlerVpnService.setDropState(false)

        try {
            val intent = Intent(context, ThrottlerVpnService::class.java).apply {
                action = ThrottlerVpnService.ACTION_STOP
            }
            context.startService(intent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to stop ThrottlerVpnService", e)
        }
    }

    override fun setThrottlingActive(enabled: Boolean) {
        if (enabled) {
            startSession()
        } else {
            stopSession()
        }
    }

    /**
     * Runs periodic 5-second drop window once every 60 seconds during an active session.
     */
    private fun startPeriodicDropTimer() {
        dropTimerJob?.cancel()
        dropTimerJob = managerScope.launch {
            try {
                while (isActive && isSessionActiveState.get()) {
                    // Wait for 60 seconds interval
                    AppLogger.d(TAG, "Waiting 60s until next network throttle drop window")
                    delay(60_000L)

                    if (!isActive || !isSessionActiveState.get()) break

                    // Trigger 5-second drop window
                    triggerTemporaryDrop(5000L)
                }
            } catch (e: CancellationException) {
                AppLogger.d(TAG, "Periodic drop timer cancelled")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in periodic drop timer loop", e)
            }
        }
    }

    override fun triggerTemporaryDrop(durationMs: Long) {
        managerScope.launch {
            AppLogger.w(TAG, "Triggering temporary ${durationMs}ms network drop window...")
            _isDropActiveFlow.value = true
            ThrottlerVpnService.setDropState(true)

            delay(durationMs)

            ThrottlerVpnService.setDropState(false)
            _isDropActiveFlow.value = false
            AppLogger.i(TAG, "Temporary network drop window ended")
        }
    }

    override fun setBandwidthLimitKbps(kbps: Int?) {
        AppLogger.i(TAG, "Setting bandwidth throughput limit: ${kbps ?: "Unlimited"} kbps")
        ThrottlerVpnService.setBandwidthLimit(kbps)
    }

    fun setTargetPackageNames(packages: Set<String>) {
        AppLogger.i(TAG, "Setting ${packages.size} target app packages for network throttling: $packages")
        ThrottlerVpnService.targetedPackageNames = packages

        if (isSessionActive()) {
            try {
                val intent = Intent(context, ThrottlerVpnService::class.java).apply {
                    action = ThrottlerVpnService.ACTION_UPDATE_TARGETS
                    putStringArrayListExtra(ThrottlerVpnService.EXTRA_TARGET_PACKAGES, ArrayList(packages))
                }
                context.startService(intent)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to send ACTION_UPDATE_TARGETS intent to ThrottlerVpnService", e)
            }
        }
    }

    fun updateTargetPackages(packages: Set<String>) {
        setTargetPackageNames(packages)
    }

    fun isSessionActive(): Boolean {
        return isSessionActiveState.get() || ThrottlerVpnService.isRunning()
    }
}
