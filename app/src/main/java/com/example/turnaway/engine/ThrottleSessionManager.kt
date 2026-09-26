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

    private fun ensureServiceRunning() {
        if (!ThrottlerVpnService.isRunning()) {
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
                AppLogger.e(TAG, "Failed to ensure ThrottlerVpnService is running", e)
            }
        }
    }

    override fun startSession() {
        if (isSessionActiveState.getAndSet(true)) {
            AppLogger.d(TAG, "Network throttling session is already active")
            return
        }

        AppLogger.i(TAG, "Starting network throttling VPN session")
        ensureServiceRunning()
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
     * Runs periodic 5-second drop windows during an active wind-down transition.
     * Starts with an early 15s delay (so 1-30m sessions experience early friction),
     * then repeats every 45-60 seconds until session ends or lockout occurs.
     */
    private fun startPeriodicDropTimer() {
        dropTimerJob?.cancel()
        dropTimerJob = managerScope.launch {
            try {
                // Initial delay of 15 seconds into wind-down
                delay(15_000L)

                while (isActive && isSessionActiveState.get()) {
                    // Trigger 5-second drop window
                    triggerTemporaryDrop(5000L)

                    // Wait 45 seconds between throttle drops
                    delay(45_000L)

                    if (!isActive || !isSessionActiveState.get()) break
                }
            } catch (e: CancellationException) {
                AppLogger.d(TAG, "Periodic drop timer cancelled")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in periodic drop timer loop", e)
            }
        }
    }

    override fun triggerTemporaryDrop(durationMs: Long) {
        ensureServiceRunning()
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

    /**
     * Enforces continuous network drop on regulated apps during Phase 3 Lockout.
     */
    fun setLockoutThrottle(active: Boolean) {
        if (active) {
            ensureServiceRunning()
            dropTimerJob?.cancel()
            _isDropActiveFlow.value = true
            ThrottlerVpnService.setDropState(true)
            AppLogger.w(TAG, "Lockout throttle engaged (continuous network block on regulated apps)")
        } else {
            _isDropActiveFlow.value = false
            ThrottlerVpnService.setDropState(false)
            AppLogger.i(TAG, "Lockout throttle released")
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
