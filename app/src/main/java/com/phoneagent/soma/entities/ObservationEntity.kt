package com.phoneagent.soma.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "soma_observations")
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val app_package: String = "",
    val app_name: String = "",
    val ocr_text_snippet: String = "",
    val activity_classification: String? = null,
    val emotional_tone: String? = null,
    val observed_at: Long = System.currentTimeMillis()
)
