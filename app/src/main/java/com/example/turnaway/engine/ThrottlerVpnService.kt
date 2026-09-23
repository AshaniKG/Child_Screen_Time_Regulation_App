package com.example.turnaway.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.turnaway.MainActivity
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * On-device local VPN Service that intercepts and controls network traffic
 * for parent-selected target applications.
 */
class ThrottlerVpnService : VpnService() {

    private val TAG = "ThrottlerVpnService"
    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var forwardingJob: Job? = null

    companion object {
        const val ACTION_START = "com.example.turnaway.VPN_START"
        const val ACTION_STOP = "com.example.turnaway.VPN_STOP"

        private val isRunningState = AtomicBoolean(false)
        private val isDropActiveState = AtomicBoolean(false)
        private val bandwidthKbpsState = AtomicInteger(-1) // -1 for unlimited

        @Volatile
        var targetedPackageNames: Set<String> = emptySet()

        fun isRunning(): Boolean = isRunningState.get()
        fun isDropActive(): Boolean = isDropActiveState.get()

        fun setDropState(active: Boolean) {
            isDropActiveState.set(active)
        }

        fun setBandwidthLimit(kbps: Int?) {
            bandwidthKbpsState.set(kbps ?: -1)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpnSession()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundVpnNotification()
                setupAndStartVpnTunnel()
                return START_STICKY
            }
        }
    }

    private fun startForegroundVpnNotification() {
        val channelId = "turnaway_vpn_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Network Throttling VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            2001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Network Throttling Active")
            .setContentText("Regulating network connectivity for targeted applications.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(2001, notification)
    }

    private fun setupAndStartVpnTunnel() {
        if (isRunningState.get()) {
            AppLogger.d(TAG, "VPN tunnel is already established and running")
            return
        }

        try {
            val builder = Builder()
                .setSession("TurnAway Local Throttler")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(1500)

            // 1. Exclude TurnAway itself to avoid VPN loops
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Could not add self to disallowed applications", e.message)
            }

            // 2. Add targeted packages selected by parent
            val targets = targetedPackageNames
            if (targets.isNotEmpty()) {
                var addedCount = 0
                for (pkg in targets) {
                    if (pkg != packageName) {
                        try {
                            builder.addAllowedApplication(pkg)
                            addedCount++
                        } catch (e: Exception) {
                            AppLogger.d(TAG, "Failed to route app $pkg through VPN: ${e.message}")
                        }
                    }
                }
                AppLogger.i(TAG, "Configured VPN tunnel for $addedCount target applications")
            } else {
                AppLogger.i(TAG, "No specific target apps configured; running in global mode excluding self")
            }

            val pfd = builder.establish()
            if (pfd == null) {
                AppLogger.e(TAG, "Failed to establish VPN TUN ParcelFileDescriptor")
                stopSelf()
                return
            }

            vpnInterface = pfd
            isRunningState.set(true)
            AppLogger.i(TAG, "VPN TUN interface established successfully")

            startPacketForwardingLoop(pfd)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error establishing VPN interface", e)
            stopSelf()
        }
    }

    private fun startPacketForwardingLoop(pfd: ParcelFileDescriptor) {
        forwardingJob?.cancel()
        forwardingJob = serviceScope.launch {
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            val buffer = ByteArray(32768)

            var lastRateLimitCheck = System.currentTimeMillis()
            var bytesTransferredInWindow = 0L

            try {
                while (isActive && isRunningState.get()) {
                    // Check drop state
                    if (isDropActiveState.get()) {
                        // Drop packets completely during 5-second drop window
                        delay(200)
                        continue
                    }

                    // Bandwidth clamping / rate limiting calculation
                    val limitKbps = bandwidthKbpsState.get()
                    if (limitKbps in 1..1000) {
                        val maxBytesPerSec = limitKbps * 1024 / 8
                        val now = System.currentTimeMillis()
                        if (now - lastRateLimitCheck >= 1000) {
                            lastRateLimitCheck = now
                            bytesTransferredInWindow = 0L
                        }

                        if (bytesTransferredInWindow >= maxBytesPerSec) {
                            delay(50) // Inject latency delay to enforce bandwidth ceiling
                            continue
                        }
                    }

                    val available = withContext(Dispatchers.IO) {
                        try {
                            if (inputStream.available() > 0) inputStream.read(buffer) else 0
                        } catch (e: Exception) {
                            -1
                        }
                    }

                    if (available > 0) {
                        bytesTransferredInWindow += available
                        // Packet forwarding handling (loopback or system socket pass-through)
                        withContext(Dispatchers.IO) {
                            try {
                                outputStream.write(buffer, 0, available)
                            } catch (e: Exception) {
                                // Socket write handling
                            }
                        }
                    } else if (available < 0) {
                        break
                    } else {
                        delay(20)
                    }
                }
            } catch (e: CancellationException) {
                AppLogger.d(TAG, "Packet forwarding loop cancelled")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in packet forwarding loop", e)
            } finally {
                withContext(NonCancellable) {
                    try {
                        inputStream.close()
                        outputStream.close()
                    } catch (e: Exception) {}
                }
            }
        }
    }

    private fun stopVpnSession() {
        isRunningState.set(false)
        isDropActiveState.set(false)
        bandwidthKbpsState.set(-1)

        forwardingJob?.cancel()
        forwardingJob = null

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error closing VPN interface", e)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        AppLogger.i(TAG, "VPN Session stopped successfully")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpnSession()
        serviceScope.cancel()
    }
}
