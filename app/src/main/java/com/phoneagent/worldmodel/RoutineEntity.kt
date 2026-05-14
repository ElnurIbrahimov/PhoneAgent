package com.phoneagent.worldmodel

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timeSlot: String,
    val dayOfWeek: Int? = null,
    val typicalActivities: String = "[]",
    val energyLevel: String = "MEDIUM",
    val interruptTolerance: String = "MEDIUM",
    val location: String? = null
)