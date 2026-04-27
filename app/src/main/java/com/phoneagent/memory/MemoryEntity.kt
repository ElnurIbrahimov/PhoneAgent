package com.phoneagent.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "general"
)
