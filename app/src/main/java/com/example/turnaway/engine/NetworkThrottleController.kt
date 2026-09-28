package com.example.turnaway.engine

/**
 * Controller interface for managing local VPN-based network throttling sessions.
 */
interface NetworkThrottleController {
    /**
     * Starts an active network throttling VPN session.
     */
    fun startSession()

    /**
     * Stops the network throttling VPN session.
     */
    fun stopSession()

    /**
     * Enables or disables active throttling dynamics.
     */
    fun setThrottlingActive(enabled: Boolean)

    /**
     * Triggers an artificial network dropout window for the specified duration (default: 5 seconds).
     */
    fun triggerTemporaryDrop(durationMs: Long = 5000L)

    /**
     * Sets bandwidth throughput limit in kbps (e.g. 32–64 kbps), or null for unlimited.
     */
    fun setBandwidthLimitKbps(kbps: Int?)
}
