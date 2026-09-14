package com.example.turnaway.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    INFO, WARN, ERROR, DEBUG
}

data class LogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val details: String? = null
)

object AppLogger {
    private const val MAX_LOGS = 200
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun i(tag: String, message: String, details: String? = null) {
        addLog(LogLevel.INFO, tag, message, details)
    }

    fun w(tag: String, message: String, details: String? = null) {
        addLog(LogLevel.WARN, tag, message, details)
    }

    fun e(tag: String, message: String, details: String? = null) {
        addLog(LogLevel.ERROR, tag, message, details)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        addLog(LogLevel.ERROR, tag, message, throwable.stackTraceToString())
    }

    fun e(tag: String, message: String, details: String?, throwable: Throwable?) {
        val extraDetails = details ?: throwable?.stackTraceToString()
        addLog(LogLevel.ERROR, tag, message, extraDetails)
    }

    fun d(tag: String, message: String, details: String? = null) {
        addLog(LogLevel.DEBUG, tag, message, details)
    }

    private fun addLog(level: LogLevel, tag: String, message: String, details: String?) {
        synchronized(this) {
            val newEntry = LogEntry(level = level, tag = tag, message = message, details = details)
            val currentList = _logs.value.toMutableList()
            currentList.add(0, newEntry) // Newest first
            if (currentList.size > MAX_LOGS) {
                currentList.removeAt(currentList.lastIndex)
            }
            _logs.value = currentList
        }
    }

    fun clear() {
        synchronized(this) {
            _logs.value = emptyList()
        }
    }
}
