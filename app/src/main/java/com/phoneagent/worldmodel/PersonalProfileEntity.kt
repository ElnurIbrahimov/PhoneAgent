package com.phoneagent.worldmodel

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personal_profile")
data class PersonalProfileEntity(
    @PrimaryKey val id: String = "self",
    val name: String? = null,
    val communicationStyle: String = "CASUAL",
    val riskTolerance: String = "MEDIUM",
    val specialRequirements: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)