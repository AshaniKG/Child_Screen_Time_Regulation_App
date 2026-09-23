package com.example.turnaway.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 * On-device local VPN Service enforcing strict per-app routing via [VpnService.Builder.addAllowedApplication].
 * Only parent-selected target apps are captured by the TUN interface; all other apps and TurnAway
 * bypass the VPN completely and maintain normal internet performance.
 */
class ThrottlerVpnService : VpnService() {

    private val TAG = "ThrottlerVpnService"
    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var forwardingJob: Job? = null

    companion object {
        const val ACTION_START = "com.example.turnaway.VPN_START"
        const val ACTION_STOP = "com.example.turnaway.VPN_STOP"
        const val ACTION_UPDATE_TARGETS = "com.example.turnaway.VPN_UPDATE_TARGETS"
        const val EXTRA_TARGET_PACKAGES = "EXTRA_TARGET_PACKAGES"

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
        val action = intent?.action

        // Check if package list is provided in intent extras
        val packagesList = intent?.getStringArrayListExtra(EXTRA_TARGET_PACKAGES)
        if (packagesList != null) {
            targetedPackageNames = packagesList.toSet()
        }

        when (action) {
            ACTION_STOP -> {
                stopVpnSession()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_TARGETS -> {
                rebuildVpnTunnel()
                return START_STICKY
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
            .setContentTitle("Per-App Network Throttling Active")
            .setContentText("Regulating network connectivity exclusively for selected applications.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(2001, notification)
    }

    private fun setupAndStartVpnTunnel() {
        if (isRunningState.get()) {
            rebuildVpnTunnel()
            return
        }

        val targets = targetedPackageNames
        if (targets.isEmpty()) {
            AppLogger.w(TAG, "0 target applications selected. VPN tunnel will not capture traffic until apps are selected.")
            isRunningState.set(false)
            return
        }

        buildAndEstablishInterface(targets)
    }

    private fun rebuildVpnTunnel() {
        val targets = targetedPackageNames
        AppLogger.i(TAG, "Rebuilding VPN tunnel dynamically for ${targets.size} target apps")

        if (targets.isEmpty()) {
            AppLogger.w(TAG, "0 target apps remaining after update. Closing VPN tunnel.")
            closeVpnInterfaceOnly()
            isRunningState.set(false)
            return
        }

        buildAndEstablishInterface(targets)
    }

    private fun buildAndEstablishInterface(targets: Set<String>) {
        try {
            val builder = Builder()
                .setSession("TurnAway Per-App Throttler")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(1500)

            // 1. Exclude TurnAway itself so our app maintains 100% full speed and avoids loops
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Could not add self to disallowed applications", e.message)
            }

            // 2. Strict Per-App Routing: Route ONLY selected target apps through TUN interface
            var addedCount = 0
            val pm = packageManager
            for (pkg in targets) {
                if (pkg != packageName) {
                    try {
                        pm.getPackageInfo(pkg, 0)
                        builder.addAllowedApplication(pkg)
                        addedCount++
                    } catch (e: PackageManager.NameNotFoundException) {
                        AppLogger.w(TAG, "Target application package not found on device: $pkg", e.message)
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Could not add allowed application $pkg", e.message)
                    }
                }
            }

            if (addedCount == 0) {
                AppLogger.w(TAG, "No valid installed target applications to route. Pausing VPN interface.")
                closeVpnInterfaceOnly()
                isRunningState.set(false)
                return
            }

            val newPfd = builder.establish()
            if (newPfd == null) {
                AppLogger.e(TAG, "Failed to establish new VPN TUN ParcelFileDescriptor")
                closeVpnInterfaceOnly()
                isRunningState.set(false)
                return
            }

            // Seamlessly swap old descriptor for new descriptor without dropping unrelated connections
            closeVpnInterfaceOnly()
            vpnInterface = newPfd
            isRunningState.set(true)
            AppLogger.i(TAG, "Strict per-app VPN tunnel established successfully for $addedCount apps!")

            startPacketForwardingLoop(newPfd)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error building/establishing VPN interface", e)
            closeVpnInterfaceOnly()
            isRunningState.set(false)
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
                        // Packet forwarding loop
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

    private fun closeVpnInterfaceOnly() {
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            AppLogger.d(TAG, "Note closing VPN descriptor: ${e.message}")
        }
    }

    private fun stopVpnSession() {
        isRunningState.set(false)
        isDropActiveState.set(false)
        bandwidthKbpsState.set(-1)

        forwardingJob?.cancel()
        forwardingJob = null

        closeVpnInterfaceOnly()

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
