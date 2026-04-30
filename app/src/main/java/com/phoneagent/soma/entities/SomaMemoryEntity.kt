package com.phoneagent.soma.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "soma_memories")
data class SomaMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val theme: String? = null,
    val emotional_weight: Float = 0.5f,
    val importance: Float = 0.5f,
    val activation_count: Int = 0,
    val tension_score: Float = 0.5f,
    val connection_depth: Float = 0.5f,
    val created_at: Long = System.currentTimeMillis(),
    val last_activated_at: Long = System.currentTimeMillis(),
    val decay_rate: Float = 0.05f,
    val source_type: String = "conversation"
)
