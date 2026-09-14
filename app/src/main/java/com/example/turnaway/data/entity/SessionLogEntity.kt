package com.example.turnaway.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "session_logs")
data class SessionLogEntity(
    @PrimaryKey val sessionId: String = UUID.randomUUID().toString(),
    val timestampStart: Long = System.currentTimeMillis(),
    val durationActiveMs: Long,
    val timeToDisengageMs: Long,
    val wasManuallyAborted: Boolean = false
)
