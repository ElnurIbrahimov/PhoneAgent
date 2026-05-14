package com.phoneagent.worldmodel

data class StepObservation(
    val step: Int,
    val action: String,
    val result: String,
    val whatILearned: List<String> = emptyList(),
    val preferenceHint: PreferenceHint? = null,
    val beliefHint: BeliefHint? = null,
    val memoryToStore: MemoryEntry? = null
)

data class PreferenceHint(
    val category: String,
    val key: String,
    val value: String
)

data class BeliefHint(
    val dimension: String,
    val statement: String
)