package com.phoneagent.soma.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "safety_audit")
data class SafetyAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolName: String,
    val riskLevel: String,
    val riskReason: String,
    val argsSummary: String,
    val decision: String,
    val taskId: String?,
    val timestamp: Long = System.currentTimeMillis()
)
