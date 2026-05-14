package com.phoneagent.worldmodel

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "relationships")
data class RelationshipEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relationshipType: String = "UNKNOWN",
    val importance: Float = 0.5f,
    val contactFrequency: String = "WEEKLY",
    val context: String = "",
    val lastInteraction: Long = 0
)