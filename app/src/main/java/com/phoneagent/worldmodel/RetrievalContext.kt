package com.phoneagent.worldmodel

data class RetrievalContext(
    val currentApp: String? = null,
    val timeSlot: String? = null,
    val location: String? = null,
    val activeGoal: String? = null,
    val recentMemoryIds: List<String> = emptyList(),
    val tensionLevel: Float = 0.5f
)