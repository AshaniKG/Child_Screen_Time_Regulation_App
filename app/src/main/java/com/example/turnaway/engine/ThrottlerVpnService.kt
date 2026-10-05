package com.example.turnaway.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * On-device system-wide local VPN Service enforcing 10-second network drops every 60 seconds
 * during the wind-down transition phase.
 *
 * When [isDropActive] is true, system IP traffic is routed into a dummy TUN interface (packet blackholing).
 * When [isDropActive] is false, the TUN interface is closed/idle so normal system network connectivity flows smoothly.
 */
class ThrottlerVpnService : VpnService() {

    private val TAG = "ThrottlerVpnService"
    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        const val ACTION_START = "com.example.turnaway.VPN_START"
        const val ACTION_STOP = "com.example.turnaway.VPN_STOP"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "throttler_vpn_channel"

        @Volatile
        var instance: ThrottlerVpnService? = null
            private set

        private val _isDropActiveFlow = MutableStateFlow(false)
        val isDropActiveFlow: StateFlow<Boolean> = _isDropActiveFlow.asStateFlow()

        fun setDropState(active: Boolean) {
            val wasActive = _isDropActiveFlow.value
            _isDropActiveFlow.value = active
            AppLogger.i("ThrottlerVpnService", "setDropState: active=$active (was=$wasActive)")
            instance?.updateDropExecutionState(active)
        }

        fun isRunning(): Boolean = instance != null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        AppLogger.i(TAG, "ThrottlerVpnService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        AppLogger.i(TAG, "onStartCommand: action=$action")

        when (action) {
            ACTION_STOP -> {
                stopVpnSession()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForegroundVpnNotification()
                if (_isDropActiveFlow.value) {
                    setupAndStartVpnTunnel()
                }
            }
        }
        return START_STICKY
    }

    fun updateDropExecutionState(active: Boolean) {
        if (active) {
            AppLogger.w(TAG, "Engaging system-wide network drop window (blackholing packets)")
            setupAndStartVpnTunnel()
        } else {
            AppLogger.i(TAG, "Releasing network drop window; restoring normal system connectivity")
            closeVpnTunnel()
        }
    }

    private fun setupAndStartVpnTunnel() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .setSession("TurnAway System-Wide Throttler")

            vpnInterface = builder.establish()
            AppLogger.i(TAG, "VPN TUN interface established successfully for system-wide network drop")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error establishing VPN TUN interface", e)
            closeVpnTunnel()
        }
    }

    private fun closeVpnTunnel() {
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            AppLogger.e(TAG, "Error closing VPN interface", e)
        } finally {
            vpnInterface = null
        }
    }

    private fun stopVpnSession() {
        closeVpnTunnel()
        _isDropActiveFlow.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        AppLogger.i(TAG, "ThrottlerVpnService stopped successfully")
    }

    private fun startForegroundVpnNotification() {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TurnAway Network Regulation")
                .setContentText("System-wide network regulation service running.")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error starting foreground notification for VPN service", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Network Regulation Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeVpnTunnel()
        instance = null
        AppLogger.i(TAG, "ThrottlerVpnService destroyed")
    }
}
