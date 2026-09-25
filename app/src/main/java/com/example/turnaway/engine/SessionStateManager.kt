package com.example.turnaway.engine

import android.content.Context
import android.content.SharedPreferences
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionState {
    IDLE,
    NORMAL_USAGE,       // Counting down until transition point
    IN_TRANSITION,      // Wind-down running
    COMPLETED_LOCKED    // Timers ended; all restrictions persist
}

data class SessionConfig(
    val totalUsageMinutes: Int = 30,    // 15..120
    val transitionMinutes: Int = 5      // 5..15
) {
    init {
        val total = totalUsageMinutes.coerceIn(15, 120)
        val transition = transitionMinutes.coerceIn(5, 15).coerceAtMost(total)
        require(transition <= total) {
            "Transition duration cannot exceed total usage duration"
        }
    }
}

data class SessionProgress(
    val state: SessionState = SessionState.IDLE,
    val config: SessionConfig = SessionConfig(),
    val startTimestampMs: Long = 0L,
    val totalTimeRemainingMs: Long = 0L,
    val normalTimeRemainingMs: Long = 0L,
    val transitionTimeRemainingMs: Long = 0L,
    val currentSaturation: Float = 1.0f,
    val currentTouchDelayMs: Long = 0L,
    val currentVolumePercent: Float = 1.0f,
    val isNetworkThrottled: Boolean = false
)

object SessionStateManager {
    private const val TAG = "SessionStateManager"
    private const val PREFS_NAME = "turnaway_session_prefs"
    private const val KEY_SESSION_ACTIVE = "session_active"
    private const val KEY_START_TIMESTAMP = "start_timestamp"
    private const val KEY_TOTAL_USAGE_MIN = "total_usage_minutes"
    private const val KEY_TRANSITION_MIN = "transition_minutes"

    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _sessionProgress = MutableStateFlow(SessionProgress())
    val sessionProgress: StateFlow<SessionProgress> = _sessionProgress.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isSessionActive(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SESSION_ACTIVE, false)
    }

    fun getStartTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_START_TIMESTAMP, 0L)
    }

    fun getSavedConfig(context: Context): SessionConfig {
        val prefs = getPrefs(context)
        val total = prefs.getInt(KEY_TOTAL_USAGE_MIN, 30).coerceIn(15, 120)
        val trans = prefs.getInt(KEY_TRANSITION_MIN, 5).coerceIn(5, 15).coerceAtMost(total)
        return SessionConfig(totalUsageMinutes = total, transitionMinutes = trans)
    }

    fun startSession(context: Context, totalUsageMinutes: Int, transitionMinutes: Int): SessionConfig {
        val total = totalUsageMinutes.coerceIn(15, 120)
        val trans = transitionMinutes.coerceIn(5, 15).coerceAtMost(total)
        val config = SessionConfig(totalUsageMinutes = total, transitionMinutes = trans)
        val now = System.currentTimeMillis()

        getPrefs(context).edit()
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .putLong(KEY_START_TIMESTAMP, now)
            .putInt(KEY_TOTAL_USAGE_MIN, total)
            .putInt(KEY_TRANSITION_MIN, trans)
            .apply()

        _sessionState.value = SessionState.NORMAL_USAGE
        _sessionProgress.value = SessionProgress(
            state = SessionState.NORMAL_USAGE,
            config = config,
            startTimestampMs = now,
            totalTimeRemainingMs = total * 60_000L,
            normalTimeRemainingMs = (total - trans) * 60_000L,
            transitionTimeRemainingMs = trans * 60_000L
        )

        AppLogger.i(TAG, "Started session: totalUsage=${total}m, transition=${trans}m at $now")
        return config
    }

    fun stopSession(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_SESSION_ACTIVE, false)
            .putLong(KEY_START_TIMESTAMP, 0L)
            .apply()

        _sessionState.value = SessionState.IDLE
        _sessionProgress.value = SessionProgress(state = SessionState.IDLE)
        AppLogger.i(TAG, "Stopped session and cleared state.")
    }

    fun updateProgress(progress: SessionProgress) {
        _sessionState.value = progress.state
        _sessionProgress.value = progress
    }
}
