package com.example.turnaway.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import com.example.turnaway.MainActivity
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device system-wide local VPN Service enforcing mid-transition network throttling.
 *
 * All device traffic (except TurnAway and system dialer) is routed through the TUN interface.
 * When [isDropActive] is true, payload packets are dropped (blackholed) for 10 seconds.
 * When [isDropActive] is false, the TUN interface is closed/idle so normal system network
 * traffic flows over native hardware interfaces with zero latency or performance penalty.
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

        @Volatile
        var instance: ThrottlerVpnService? = null

        fun isRunning(): Boolean = isRunningState.get()
        fun isDropActive(): Boolean = isDropActiveState.get()

        fun setDropState(active: Boolean) {
            val wasActive = isDropActiveState.getAndSet(active)
            AppLogger.i("ThrottlerVpnService", "setDropState: active=$active (was=$wasActive)")
            instance?.applyDropState(active)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP -> {
                stopVpnSession()
                return START_NOT_STICKY
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
                AppLogger.w(TAG, "Engaging system-wide network drop window (blackholing packets)")
                setupAndStartVpnTunnel()
            } else {
                AppLogger.i(TAG, "Releasing network drop window; restoring normal system connectivity")
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
                "System-Wide Network Throttling VPN",
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
            .setContentTitle("System-Wide Network Throttling Active")
            .setContentText("Automated mid-transition network regulation active across device.")
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
        try {
            val builder = Builder()
                .setSession("TurnAway System-Wide Throttler")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .setMtu(1500)

            // IPv6 route configuration
            try {
                builder.addAddress("fd00:1:fd00:1::2", 64)
                builder.addRoute("::", 0)
                builder.addDnsServer("2001:4860:4860::8888")
            } catch (e: Exception) {
                AppLogger.d(TAG, "IPv6 route configuration note: ${e.message}")
            }

            // Exclude TurnAway itself to prevent self-interception loop
            try {
                builder.addDisallowedApplication(packageName)
                AppLogger.d(TAG, "Excluded TurnAway package from VPN: $packageName")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to exclude own package $packageName", e)
            }

            // Whitelist system dialer package to protect emergency communications
            try {
                val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                val defaultDialer = telecomManager?.defaultDialerPackage
                if (!defaultDialer.isNullOrBlank()) {
                    builder.addDisallowedApplication(defaultDialer)
                    AppLogger.i(TAG, "Excluded default system dialer from VPN: $defaultDialer")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error excluding default dialer package", e)
            }

            val newPfd = builder.establish()
            if (newPfd == null) {
                AppLogger.e(TAG, "Failed to establish system-wide VPN TUN ParcelFileDescriptor")
                closeVpnInterfaceOnly()
                return
            }

            closeVpnInterfaceOnly()
            vpnInterface = newPfd
            AppLogger.i(TAG, "System-wide VPN TUN interface established successfully!")

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
                    // All read packets from system applications are drained and blackholed (dropped)
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

        forwardingJob?.cancel()
        forwardingJob = null

        closeVpnInterfaceOnly()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        AppLogger.i(TAG, "VPN Session stopped successfully and network restored")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopVpnSession()
        serviceScope.cancel()
    }
}
