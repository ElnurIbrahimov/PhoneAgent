package com.phoneagent.worldmodel

data class SessionReflection(
    val sessionId: String,
    val whatWentWell: List<String> = emptyList(),
    val whatCouldImprove: List<String> = emptyList(),
    val userFrustrations: List<String> = emptyList(),
    val newPreferences: List<PreferenceHint> = emptyList(),
    val newBeliefs: List<BeliefHint> = emptyList(),
    val memoriesToConsolidate: List<MemoryEntry> = emptyList(),
    val goalsAchieved: List<String> = emptyList(),
    val goalsSuggested: List<GoalEntity> = emptyList()
)