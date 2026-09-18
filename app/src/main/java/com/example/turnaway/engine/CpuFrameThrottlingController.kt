package com.example.turnaway.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin

class CpuFrameThrottlingController(private val context: Context) {
    private val TAG = "CpuFrameThrottling"

    private val controllerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var workerJobs = mutableListOf<Job>()

    private val isThrottlingEnabled = AtomicBoolean(false)
    private val isTargetAppForeground = AtomicBoolean(false)
    private val isScreenOn = AtomicBoolean(true)

    @Volatile
    private var targetFps = 60

    @Volatile
    private var activeDutyMs = 0L

    @Volatile
    private var sleepDutyMs = 100L

    // Screen State Receiver to automatically pause CPU load when screen is off
    private var isReceiverRegistered = false
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn.set(false)
                    AppLogger.d(TAG, "Screen off: pausing frame throttling workers")
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn.set(true)
                    AppLogger.d(TAG, "Screen on: resuming frame throttling workers")
                }
            }
        }
    }

    init {
        registerScreenReceiver()
        startWorkerThreads()
    }

    private fun registerScreenReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                }
                ContextCompat.registerReceiver(
                    context,
                    screenStateReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                isReceiverRegistered = true
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error registering screen state receiver", e)
            }
        }
    }

    private fun unregisterScreenReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(screenStateReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error unregistering screen state receiver", e)
            }
        }
    }

    fun setTargetFps(fps: Int) {
        val clampedFps = fps.coerceIn(5, 60)
        this.targetFps = clampedFps

        // Recalculate duty cycle (100ms cadence)
        // 60 FPS -> 0ms active (0% load)
        // 30 FPS -> 35ms active, 65ms sleep
        // 15 FPS -> 70ms active, 30ms sleep
        // 5 FPS  -> 88ms active, 12ms sleep
        if (clampedFps >= 55) {
            activeDutyMs = 0L
            sleepDutyMs = 100L
            isThrottlingEnabled.set(false)
        } else {
            val progress = (60f - clampedFps) / 55f // 0.0 (fast) to 1.0 (maximum lag)
            activeDutyMs = (progress * 88f).toLong().coerceIn(10L, 88L)
            sleepDutyMs = (100L - activeDutyMs).coerceIn(12L, 90L)
            isThrottlingEnabled.set(true)
        }

        AppLogger.d(TAG, "Target FPS: $clampedFps | Duty: ${activeDutyMs}ms active / ${sleepDutyMs}ms sleep")
    }

    fun setTargetAppForeground(isForeground: Boolean) {
        if (isTargetAppForeground.getAndSet(isForeground) != isForeground) {
            AppLogger.i(TAG, "Target application in foreground: $isForeground (Lag active: ${isThrottlingEnabled.get() && isForeground})")
        }
    }

    fun stopThrottling() {
        isThrottlingEnabled.set(false)
        activeDutyMs = 0L
        sleepDutyMs = 100L
        AppLogger.i(TAG, "CPU frame throttling stopped")
    }

    private fun startWorkerThreads() {
        val totalCores = Runtime.getRuntime().availableProcessors()
        // Calibrate worker count to saturate VSYNC deadlines without locking system core
        val workerCount = (totalCores - 1).coerceIn(2, 4)

        AppLogger.i(TAG, "Initializing $workerCount VSYNC contention worker threads (System cores: $totalCores)")

        for (i in 0 until workerCount) {
            val job = controllerScope.launch {
                while (isActive) {
                    val shouldThrottle = isThrottlingEnabled.get() &&
                            isTargetAppForeground.get() &&
                            isScreenOn.get() &&
                            activeDutyMs > 0L

                    if (shouldThrottle) {
                        val activeMs = activeDutyMs
                        val sleepMs = sleepDutyMs

                        val startTime = System.currentTimeMillis()
                        // Controlled computational burst competing for Choreographer / SurfaceFlinger scheduling
                        var dummy = 0.0
                        while (System.currentTimeMillis() - startTime < activeMs && isActive) {
                            for (j in 0..500) {
                                dummy += sin(j.toDouble())
                            }
                        }

                        // Yield CPU to maintain system responsiveness and allow OS looper dispatch
                        if (sleepMs > 0) {
                            delay(sleepMs)
                        }
                    } else {
                        // Completely idle when target app is not in foreground or wind-down is not active
                        delay(150)
                    }
                }
            }
            workerJobs.add(job)
        }
    }

    fun release() {
        stopThrottling()
        unregisterScreenReceiver()
        controllerScope.cancel()
    }
}
