package com.phoneagent.worldmodel

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "iris_routing_history")
data class RoutingDecisionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val messagePreview: String,
    val profile: String,
    val temperature: Float,
    val style: String,
    val depth: Int,
    val outcome: String? = null,
    val sessionId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)