package com.phoneagent.worldmodel

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val category: String,
    val key: String,
    val value: String,
    val confidence: Float = 0.5f,
    val lastUpdated: Long = System.currentTimeMillis()
)