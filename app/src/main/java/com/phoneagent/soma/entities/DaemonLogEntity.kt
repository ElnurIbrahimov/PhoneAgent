package com.phoneagent.soma.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "soma_daemon_log")
data class DaemonLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val thought: String,
    val significance: Float = 0f,
    val pushed_to_user: Boolean = false,
    val triggered_by: String? = null,
    val created_at: Long = System.currentTimeMillis()
)
