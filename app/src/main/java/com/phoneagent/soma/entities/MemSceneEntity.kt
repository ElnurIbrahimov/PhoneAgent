package com.phoneagent.soma.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "soma_scenes")
data class MemSceneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val theme: String,
    val summary: String,
    val memory_ids: String = "[]",
    val created_at: Long = System.currentTimeMillis(),
    val session_id: String = ""
)
