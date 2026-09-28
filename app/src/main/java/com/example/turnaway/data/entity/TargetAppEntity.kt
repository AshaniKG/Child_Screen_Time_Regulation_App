package com.example.turnaway.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "target_apps")
data class TargetAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isTargeted: Boolean = false
)
