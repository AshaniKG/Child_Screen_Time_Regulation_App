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
        var instance: ThrottlerVpnService? = null

        @Volatile
        var targetedPackageNames: Set<String> = emptySet()

        fun isRunning(): Boolean = isRunningState.get()
        fun isDropActive(): Boolean = isDropActiveState.get()

        fun setDropState(active: Boolean) {
            val wasActive = isDropActiveState.getAndSet(active)
            AppLogger.i("ThrottlerVpnService", "setDropState: active=$active (was=$wasActive)")
            instance?.applyDropState(active)
        }

        fun setBandwidthLimit(kbps: Int?) {
            bandwidthKbpsState.set(kbps ?: -1)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
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
                if (isDropActiveState.get()) {
                    rebuildVpnTunnel()
                }
                return START_STICKY
            }
            else -> {
                isRunningState.set(true)
                startForegroundVpnNotification()
                if (isDropActiveState.get()) {
                    setupAndStartVpnTunnel()
                }
                return START_STICKY
            }
        }
    }

    fun applyDropState(active: Boolean) {
        serviceScope.launch(Dispatchers.Main) {
            if (active) {
                AppLogger.w(TAG, "Engaging VPN network drop for ${targetedPackageNames.size} target apps")
                setupAndStartVpnTunnel()
            } else {
                AppLogger.i(TAG, "Releasing VPN network drop; restoring normal app connectivity")
                closeVpnInterfaceOnly()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(2001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2001, notification)
        }
    }

    private fun setupAndStartVpnTunnel() {
        var targets = targetedPackageNames
        if (targets.isEmpty()) {
            // Attempt to load from database if not yet passed in memory
            try {
                val db = com.example.turnaway.data.db.SoftLandingDatabase.getDatabase(applicationContext)
                val dbTargets: Set<String> = runBlocking(Dispatchers.IO) {
                    try {
                        db.softLandingDao().getTargetedPackageNamesList().toSet()
                    } catch (e: Exception) {
                        emptySet<String>()
                    }
                }
                if (dbTargets.isNotEmpty()) {
                    targetedPackageNames = dbTargets
                    targets = dbTargets
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "Database target lookup note: ${e.message}")
            }
        }

        if (targets.isEmpty()) {
            AppLogger.w(TAG, "0 target applications selected. VPN tunnel will not capture traffic until apps are selected.")
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

            // IPv6 address and route to prevent bypass on modern cellular & Wi-Fi networks
            try {
                builder.addAddress("fd00:1:fd00:1::2", 64)
                builder.addRoute("::", 0)
                builder.addDnsServer("2001:4860:4860::8888")
            } catch (e: Exception) {
                AppLogger.d(TAG, "IPv6 route configuration note: ${e.message}")
            }

            // Strict Per-App Routing: Route ONLY selected target apps through TUN interface.
            // NOTE: Do not call addDisallowedApplication alongside addAllowedApplication, as
            // VpnService.Builder enforces that allowed and disallowed modes are mutually exclusive.
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
                return
            }

            val newPfd = builder.establish()
            if (newPfd == null) {
                AppLogger.e(TAG, "Failed to establish new VPN TUN ParcelFileDescriptor")
                closeVpnInterfaceOnly()
                return
            }

            // Seamlessly swap old descriptor for new descriptor
            closeVpnInterfaceOnly()
            vpnInterface = newPfd
            AppLogger.i(TAG, "Strict per-app VPN tunnel established successfully for $addedCount apps!")

            startPacketForwardingLoop(newPfd)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error building/establishing VPN interface", e)
            closeVpnInterfaceOnly()
        }
    }

    private fun startPacketForwardingLoop(pfd: ParcelFileDescriptor) {
        forwardingJob?.cancel()
        forwardingJob = serviceScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(32768)
            val inputStream = FileInputStream(pfd.fileDescriptor)
            try {
                while (isActive && isRunningState.get() && vpnInterface != null) {
                    val read = try {
                        inputStream.read(buffer)
                    } catch (e: Exception) {
                        -1
                    }
                    if (read <= 0) {
                        delay(20)
                    }
                    // Packets from regulated apps are drained and dropped (blackholed)
                }
            } catch (e: CancellationException) {
                AppLogger.d(TAG, "Packet forwarding loop cancelled")
            } catch (e: Exception) {
                AppLogger.d(TAG, "Packet forwarding loop ended: ${e.message}")
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
        instance = null
        stopVpnSession()
        serviceScope.cancel()
    }
}
