package com.phoneagent.soma.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "soma_beliefs")
data class BeliefEntity(
    @PrimaryKey val id: String,
    val dimension: String,
    val statement: String,
    val confidence: Double,
    val evidence_count: Int = 0,
    val last_updated: Long = System.currentTimeMillis(),
    val source: String = "observed"
)
