package com.phoneagent.soma.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "soma_lpm")
data class LpmEntity(
    @PrimaryKey val id: String = "singleton",
    val raw_profile: String = "",
    val behavioral_predictions: String = "[]",
    val trigger_map: String = "{}",
    val foresight_signals: String = "[]",
    val last_session_at: Long = System.currentTimeMillis(),
    val total_sessions: Int = 0,
    val updated_at: Long = System.currentTimeMillis()
)
