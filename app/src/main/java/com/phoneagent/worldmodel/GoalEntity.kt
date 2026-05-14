package com.phoneagent.worldmodel

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
    val deadline: Long? = null,
    val status: String = "ACTIVE",
    val progress: Float = 0f,
    val relatedMemoryIds: String = "[]"
)