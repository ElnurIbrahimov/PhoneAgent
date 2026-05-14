package com.phoneagent.worldmodel

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "phone_state_cache")
data class PhoneStateCacheEntity(
    @PrimaryKey val id: String = "current",
    val foregroundApp: String = "",
    val appHierarchy: String = "[]",
    val screenType: String = "UNKNOWN",
    val screenPurpose: String = "",
    val confidence: Float = 0f,
    val lastUpdated: Long = System.currentTimeMillis()
)