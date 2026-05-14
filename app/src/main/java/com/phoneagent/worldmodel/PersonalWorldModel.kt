package com.phoneagent.worldmodel

import com.phoneagent.memory.MemoryDao
import com.phoneagent.memory.MemoryEntity
import com.phoneagent.soma.BeliefEngine
import kotlinx.coroutines.flow.first
import kotlin.math.exp

class PersonalWorldModel(
    private val profileDao: PersonalProfileDao,
    private val preferenceDao: PreferenceDao,
    private val beliefDao: com.phoneagent.soma.daos.BeliefDao,
    private val memoryDao: MemoryDao,
    private val routineDao: RoutineDao,
    private val relationshipDao: RelationshipDao,
    private val goalDao: GoalDao,
    private val phoneStateDao: PhoneStateCacheDao
) {
    private val beliefEngine = BeliefEngine(beliefDao)

    suspend fun getProfile(): PersonalProfileEntity? {
        return profileDao.getById()
    }

    suspend fun updateProfile(profile: PersonalProfileEntity) {
        profileDao.upsert(profile)
    }

    suspend fun observeBelief(dimension: String, statement: String, source: String = "observed") {
        beliefEngine.observe(dimension, statement, source)
    }

    suspend fun contradictBelief(dimension: String, statement: String) {
        beliefEngine.contradict(dimension, statement)
    }

    suspend fun getBelief(dimension: String, statement: String): Double {
        return beliefEngine.getConfidence(dimension, statement)
    }

    suspend fun getPreference(category: String, key: String): PreferenceEntity? {
        return preferenceDao.get(category, key)
    }

    suspend fun setPreference(category: String, key: String, value: String, confidence: Float = 0.5f) {
        val entity = PreferenceEntity(
            category = category,
            key = key,
            value = value,
            confidence = confidence
        )
        preferenceDao.upsert(entity)
    }

    suspend fun getPreferencesForCategory(category: String): List<PreferenceEntity> {
        return preferenceDao.getByCategory(category).first()
    }

    suspend fun getRoutinesForTimeSlot(timeSlot: String): List<RoutineEntity> {
        return routineDao.getByTimeSlot(timeSlot).first()
    }

    suspend fun upsertRoutine(routine: RoutineEntity) {
        routineDao.upsert(routine)
    }

    suspend fun getRelationships(): List<RelationshipEntity> {
        return relationshipDao.getAll().first()
    }

    suspend fun upsertRelationship(relationship: RelationshipEntity) {
        relationshipDao.upsert(relationship)
    }

    suspend fun getActiveGoals(): List<GoalEntity> {
        return goalDao.getByStatus("ACTIVE").first()
    }

    suspend fun upsertGoal(goal: GoalEntity) {
        goalDao.upsert(goal)
    }

    suspend fun completeGoal(goalId: String) {
        goalDao.updateStatus(goalId, "COMPLETED")
    }

    suspend fun getPhoneState(): PhoneStateCacheEntity? {
        return phoneStateDao.getById()
    }

    suspend fun updatePhoneState(state: PhoneStateCacheEntity) {
        phoneStateDao.upsert(state)
    }

    suspend fun retrieveMemories(context: RetrievalContext, query: String, limit: Int = 10): List<MemoryEntity> {
        val allMemories = memoryDao.getAll().first()
        if (allMemories.isEmpty()) return emptyList()

        val queryTerms = query.lowercase().split(" ").filter { it.isNotBlank() }

        val scored = allMemories.map { memory ->
            val relevanceScore = calculateRelevance(memory, queryTerms)
            val contextScore = calculateContextScore(memory, context)
            val recencyScore = calculateRecencyScore(memory.id)

            val finalScore = relevanceScore * 0.4f + contextScore * 0.35f + recencyScore * 0.25f

            memory to finalScore
        }

        return scored
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun calculateRelevance(memory: MemoryEntity, queryTerms: List<String>): Float {
        if (queryTerms.isEmpty()) return 0.5f

        val content = memory.value.lowercase()
        val key = memory.key.lowercase()
        val category = memory.category.lowercase()

        var matches = 0
        for (term in queryTerms) {
            if (content.contains(term) || key.contains(term) || category.contains(term)) {
                matches++
            }
        }

        return (matches.toFloat() / queryTerms.size).coerceIn(0f, 1f)
    }

    private fun calculateRecencyScore(id: Long): Float {
        val decayFactor = exp(-id / 1000.0).toFloat()
        return decayFactor.coerceIn(0.1f, 1f)
    }

    private fun calculateContextScore(memory: MemoryEntity, context: RetrievalContext): Float {
        var score = 0.5f

        context.currentApp?.let { app ->
            if (memory.key.contains(app, ignoreCase = true) || memory.value.contains(app, ignoreCase = true)) {
                score += 0.2f
            }
        }

        context.activeGoal?.let { goal ->
            if (memory.value.contains(goal, ignoreCase = true)) {
                score += 0.15f
            }
        }

        context.recentMemoryIds.let { recentIds ->
            if (memory.key in recentIds || memory.id.toString() in recentIds) {
                score += 0.1f
            }
        }

        return score.coerceIn(0f, 1f)
    }

    suspend fun updateFromStepObservation(observation: StepObservation) {
        observation.memoryToStore?.let { memory ->
            val entity = memory.toEntity()
            memoryDao.insert(entity)
        }

        observation.preferenceHint?.let { hint ->
            setPreference(hint.category, hint.key, hint.value, 0.7f)
        }

        observation.beliefHint?.let { hint ->
            observeBelief(hint.dimension, hint.statement, "step_observation")
        }

        observation.whatILearned.forEach { fact ->
            if (fact.isNotBlank()) {
                val memoryEntry = MemoryEntry(
                    content = fact,
                    importance = 0.6f,
                    emotionalWeight = 0.5f,
                    tags = listOf("learned", "conceptual")
                )
                memoryDao.insert(memoryEntry.toEntity())
            }
        }
    }

    suspend fun reflectOnSession(reflection: SessionReflection) {
        reflection.newPreferences.forEach { hint ->
            setPreference(hint.category, hint.key, hint.value, 0.8f)
        }

        reflection.newBeliefs.forEach { hint ->
            observeBelief(hint.dimension, hint.statement, "session_reflection")
        }

        reflection.memoriesToConsolidate.forEach { memory ->
            memoryDao.insert(memory.toEntity())
        }

        reflection.goalsAchieved.forEach { goalDesc ->
            val goals = goalDao.getByStatus("ACTIVE").first()
            val matchingGoal = goals.find { it.description == goalDesc }
            matchingGoal?.let { completeGoal(it.id) }
        }

        reflection.goalsSuggested.forEach { goal ->
            upsertGoal(goal)
        }

        reflection.userFrustrations.forEach { frustration ->
            if (frustration.isNotBlank()) {
                val memoryEntry = MemoryEntry(
                    content = frustration,
                    importance = 0.7f,
                    emotionalWeight = 0.6f,
                    tags = listOf("frustration", "improvement")
                )
                memoryDao.insert(memoryEntry.toEntity())
            }
        }
    }
}