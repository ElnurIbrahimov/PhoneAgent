# PhoneAgent World Model — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the layered personal world model — personal profile, beliefs, preferences, routines, relationships, goals — with memory retrieval scoring, step observations, session reflection, and world-model-driven safety/routing.

**Architecture:** Three-layer memory (working context + episodic + conceptual/personal) backed by Room entities. LLM generates StepObservation after each tool call, updating beliefs and preferences. SafetyGate and IrisRouter query the world model for context-aware decisions.

**Tech Stack:** Kotlin, Room (AppDatabase v5 migration), Jetpack Compose, AgentLoopExecutor, SafetyGate, IrisRouter

---

## Phase 1 — World Model Data Structures

### Task 1: Add World Model Room Entities

**Files:**
- Modify: `app/src/main/java/com/phoneagent/soma/entities/BeliefEntity.kt` — add Bayesian fields
- Modify: `app/src/main/java/com/phoneagent/soma/entities/ObservationEntity.kt` — extend for step observations
- Modify: `app/src/main/java/com/phoneagent/soma/daos/BeliefDao.kt` — add query methods
- Modify: `app/src/main/java/com/phoneagent/soma/daos/ObservationDao.kt` — add step observation queries
- Create: `app/src/main/java/com/phoneagent/worldmodel/PersonalProfileEntity.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/PreferenceEntity.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/RoutineEntity.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/RelationshipEntity.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/GoalEntity.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/PhoneStateCacheEntity.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/PersonalWorldModel.kt`

**Step 1: Create PersonalProfileEntity**

```kotlin
@Entity(tableName = "personal_profile")
data class PersonalProfileEntity(
    @PrimaryKey val id: String = "self",
    val name: String? = null,
    val communicationStyle: String = "CASUAL",  // CASUAL/FORMAL/TERSE/ELABORATE
    val riskTolerance: String = "MEDIUM",        // LOW/MEDIUM/HIGH
    val specialRequirements: String = "[]",       // JSON array
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

**Step 2: Create PreferenceEntity**

```kotlin
@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val category: String,   // routing/tool/safety/display
    val key: String,
    val value: String,     // JSON
    val confidence: Float = 0.5f,
    val lastUpdated: Long = System.currentTimeMillis()
)
```

**Step 3: Create RoutineEntity**

```kotlin
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timeSlot: String,          // MORNING/AFTERNOON/EVENING/NIGHT
    val dayOfWeek: Int? = null,     // null = everyday
    val typicalActivities: String = "[]",  // JSON array
    val energyLevel: String = "MEDIUM",
    val interruptTolerance: String = "MEDIUM",
    val location: String? = null
)
```

**Step 4: Create RelationshipEntity**

```kotlin
@Entity(tableName = "relationships")
data class RelationshipEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relationshipType: String = "UNKNOWN",  // FAMILY/FRIEND/COLLEAGUE/SERVICE/AI/UNKNOWN
    val importance: Float = 0.5f,
    val contactFrequency: String = "WEEKLY",    // DAILY/WEEKLY/MONTHLY/RARELY
    val context: String = "",
    val lastInteraction: Long = 0
)
```

**Step 5: Create GoalEntity**

```kotlin
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
    val deadline: Long? = null,
    val status: String = "ACTIVE",   // ACTIVE/COMPLETED/ABANDONED/PARKED
    val progress: Float = 0f,
    val relatedMemoryIds: String = "[]"  // JSON array
)
```

**Step 6: Create PhoneStateCacheEntity**

```kotlin
@Entity(tableName = "phone_state_cache")
data class PhoneStateCacheEntity(
    @PrimaryKey val id: String = "current",
    val foregroundApp: String = "",
    val appHierarchy: String = "[]",  // JSON array of package names
    val screenType: String = "UNKNOWN",
    val screenPurpose: String = "",
    val confidence: Float = 0f,
    val lastUpdated: Long = System.currentTimeMillis()
)
```

**Step 7: Extend BeliefEntity — add dimension field**

```kotlin
@Entity(tableName = "beliefs")
data class BeliefEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val dimension: String,          // privacy/speed/quality/tool_preference/routing
    val statement: String,          // "prefers.voice.over.text"
    val confidence: Float = 0.5f,
    val evidenceCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
```

**Step 8: Extend ObservationEntity — add step metadata**

```kotlin
@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val step: Int,
    val action: String,
    val result: String,
    val whatILearned: String = "[]",        // JSON array of strings
    val preferenceHint: String? = null,     // JSON {category, key, value}
    val beliefHint: String? = null,         // JSON {dimension, statement}
    val memoryToStore: String? = null,      // JSON MemoryEntry
    val emotionalWeight: Float = 0.5f,
    val importance: Float = 0.5f,
    val timestamp: Long = System.currentTimeMillis()
)
```

**Step 9: Create DAOs for new entities**

```kotlin
@Dao interface PersonalProfileDao {
    @Query("SELECT * FROM personal_profile WHERE id = 'self'") suspend fun get(): PersonalProfileEntity?
    @Insert(onConflict = REPLACE) suspend fun upsert(entity: PersonalProfileEntity)
}

@Dao interface PreferenceDao {
    @Query("SELECT * FROM preferences WHERE category = :cat") suspend fun getByCategory(cat: String): List<PreferenceEntity>
    @Query("SELECT * FROM preferences WHERE category = :cat AND `key` = :key") suspend fun get(cat: String, key: String): PreferenceEntity?
    @Insert(onConflict = REPLACE) suspend fun upsert(entity: PreferenceEntity)
    @Query("DELETE FROM preferences WHERE category = :cat AND `key` = :key") suspend fun delete(cat: String, key: String)
}

@Dao interface RoutineDao {
    @Query("SELECT * FROM routines WHERE timeSlot = :slot OR dayOfWeek = :day") suspend fun getByTimeSlot(slot: String, day: Int): List<RoutineEntity>
    @Query("SELECT * FROM routines") suspend fun getAll(): List<RoutineEntity>
    @Insert(onConflict = REPLACE) suspend fun upsert(entity: RoutineEntity)
}

@Dao interface RelationshipDao {
    @Query("SELECT * FROM relationships ORDER BY importance DESC") suspend fun getAll(): List<RelationshipEntity>
    @Query("SELECT * FROM relationships WHERE id = :id") suspend fun getById(id: String): RelationshipEntity?
    @Insert(onConflict = REPLACE) suspend fun upsert(entity: RelationshipEntity)
}

@Dao interface GoalDao {
    @Query("SELECT * FROM goals WHERE status = 'ACTIVE' ORDER BY createdAt DESC") suspend fun getActive(): List<GoalEntity>
    @Query("SELECT * FROM goals WHERE status = :status") suspend fun getByStatus(status: String): List<GoalEntity>
    @Insert(onConflict = REPLACE) suspend fun upsert(entity: GoalEntity)
    @Query("UPDATE goals SET status = :status WHERE id = :id") suspend fun updateStatus(id: String, status: String)
}

@Dao interface PhoneStateCacheDao {
    @Query("SELECT * FROM phone_state_cache WHERE id = 'current'") suspend fun get(): PhoneStateCacheEntity?
    @Insert(onConflict = REPLACE) suspend fun upsert(entity: PhoneStateCacheEntity)
}
```

**Step 10: Add DAOs to AppDatabase v5 migration**

In `AppDatabase.kt`, add to `appDatabase` builder (version 5):
```kotlin
.addMigrations(object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS personal_profile (id TEXT PRIMARY KEY, name TEXT, communicationStyle TEXT, riskTolerance TEXT, specialRequirements TEXT, createdAt INTEGER, updatedAt INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS preferences (id TEXT PRIMARY KEY, category TEXT, `key` TEXT, value TEXT, confidence REAL, lastUpdated INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS routines (id TEXT PRIMARY KEY, timeSlot TEXT, dayOfWeek INTEGER, typicalActivities TEXT, energyLevel TEXT, interruptTolerance TEXT, location TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS relationships (id TEXT PRIMARY KEY, name TEXT, relationshipType TEXT, importance REAL, contactFrequency TEXT, context TEXT, lastInteraction INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS goals (id TEXT PRIMARY KEY, description TEXT, createdAt INTEGER, deadline INTEGER, status TEXT, progress REAL, relatedMemoryIds TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS phone_state_cache (id TEXT PRIMARY KEY, foregroundApp TEXT, appHierarchy TEXT, screenType TEXT, screenPurpose TEXT, confidence REAL, lastUpdated INTEGER)")
        db.execSQL("ALTER TABLE beliefs ADD COLUMN dimension TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE observations ADD COLUMN step INTEGER DEFAULT 0")
    }
})
```

**Step 11: Update BeliefDao for dimension queries**

```kotlin
@Dao interface BeliefDao {
    @Query("SELECT * FROM beliefs WHERE dimension = :dim") suspend fun getByDimension(dim: String): List<BeliefEntity>
    @Query("SELECT * FROM beliefs WHERE `statement` LIKE :pattern") suspend fun getByStatement(pattern: String): List<BeliefEntity>
    @Query("UPDATE beliefs SET confidence = :conf, evidenceCount = evidenceCount + 1, lastUpdated = :ts WHERE id = :id") suspend fun updateConfidence(id: String, conf: Float, ts: Long)
}
```

**Step 12: Add BeliefEngine.observe() and .contradict() methods**

```kotlin
class BeliefEngine(private val beliefDao: BeliefDao) {
    suspend fun observe(statement: String, dimension: String = "general"): Float {
        val existing = beliefDao.getByStatement("%$statement%").firstOrNull()
        val newConfidence = if (existing != null) {
            // Bayesian update: posterior = prior * likelihood / normalization
            val prior = existing.confidence
            val likelihood = 0.7f  // evidence supports the belief
            val posterior = (prior * likelihood) / (prior * likelihood + (1 - prior) * (1 - likelihood))
            beliefDao.updateConfidence(existing.id, posterior, System.currentTimeMillis())
            posterior
        } else {
            beliefDao.insert(BeliefEntity(dimension = dimension, statement = statement, confidence = 0.6f, evidenceCount = 1))
            0.6f
        }
        return newConfidence
    }

    suspend fun contradict(statement: String): Float {
        val existing = beliefDao.getByStatement("%$statement%").firstOrNull() ?: return 0.5f
        // Reduce confidence — user disagreed
        val prior = existing.confidence
        val likelihood = 0.3f  // evidence contradicts
        val posterior = (prior * likelihood) / (prior * likelihood + (1 - prior) * (1 - likelihood))
        beliefDao.updateConfidence(existing.id, posterior, System.currentTimeMillis())
        return posterior
    }
}
```

**Step 13: Commit**

---

### Task 2: Create PersonalWorldModel Facade

**Files:**
- Create: `app/src/main/java/com/phoneagent/worldmodel/PersonalWorldModel.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/WorldModelRetrievalContext.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/RetrievalContext.kt`

**Step 1: Create RetrievalContext**

```kotlin
data class RetrievalContext(
    val currentApp: String? = null,
    val timeSlot: String? = null,
    val location: String? = null,
    val activeGoal: String? = null,
    val recentMemoryIds: List<String> = emptyList(),
    val tensionLevel: Float = 0.5f
)
```

**Step 2: Create PersonalWorldModel — top-level facade**

```kotlin
class PersonalWorldModel(
    private val profileDao: PersonalProfileDao,
    private val beliefDao: BeliefDao,
    private val preferenceDao: PreferenceDao,
    private val routineDao: RoutineDao,
    private val relationshipDao: RelationshipDao,
    private val goalDao: GoalDao,
    private val phoneStateCacheDao: PhoneStateCacheDao,
    private val memoryDao: MemoryDao,
    private val observationDao: ObservationDao
) {
    // --- Profile ---
    suspend fun getProfile(): PersonalProfileEntity =
        profileDao.get() ?: PersonalProfileEntity()

    suspend fun updateProfile(profile: PersonalProfileEntity) {
        profileDao.upsert(profile.copy(updatedAt = System.currentTimeMillis()))
    }

    // --- Beliefs ---
    suspend fun observeBelief(statement: String, dimension: String = "general"): Float =
        beliefEngine.observe(statement, dimension)

    suspend fun contradictBelief(statement: String): Float =
        beliefEngine.contradict(statement)

    suspend fun getBelief(dimension: String, statement: String): Float? =
        beliefDao.getByStatement("%$statement%").firstOrNull()?.confidence

    // --- Preferences ---
    suspend fun getPreference(category: String, key: String): String? =
        preferenceDao.get(category, key)?.value

    suspend fun setPreference(category: String, key: String, value: String, confidence: Float = 0.5f) {
        preferenceDao.upsert(PreferenceEntity(category = category, key = key, value = value, confidence = confidence))
    }

    suspend fun getPreferencesForCategory(category: String): Map<String, String> =
        preferenceDao.getByCategory(category).associate { it.key to it.value }

    // --- Routines ---
    suspend fun getRoutinesForTimeSlot(slot: String, dayOfWeek: Int): List<RoutineEntity> =
        routineDao.getByTimeSlot(slot, dayOfWeek)

    suspend fun upsertRoutine(routine: RoutineEntity) = routineDao.upsert(routine)

    // --- Relationships ---
    suspend fun getRelationships(): List<RelationshipEntity> = relationshipDao.getAll()

    suspend fun upsertRelationship(rel: RelationshipEntity) = relationshipDao.upsert(rel)

    // --- Goals ---
    suspend fun getActiveGoals(): List<GoalEntity> = goalDao.getActive()

    suspend fun upsertGoal(goal: GoalEntity) = goalDao.upsert(goal)

    suspend fun completeGoal(goalId: String) = goalDao.updateStatus(goalId, "COMPLETED")

    // --- Phone State ---
    suspend fun getPhoneState(): PhoneStateCacheEntity? = phoneStateCacheDao.get()

    suspend fun updatePhoneState(state: PhoneStateCacheEntity) =
        phoneStateCacheDao.upsert(state.copy(lastUpdated = System.currentTimeMillis()))

    // --- Memory Retrieval ---
    suspend fun retrieveMemories(query: String, context: RetrievalContext, limit: Int = 10): List<MemoryEntry> {
        val candidates = memoryDao.getAll()
        return candidates
            .map { candidate -> Score(it, query, context, getImportance(it), getRecency(it)) }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.entry }
    }

    private data class ScoredEntry(val entry: MemoryEntry, val score: Float)

    private fun Score(entry: MemoryEntry, query: String, ctx: RetrievalContext, importance: Float, recency: Float): ScoredEntry {
        // Relevance: keyword overlap + context match
        val queryTerms = query.lowercase().split(" ")
        val content = entry.content.lowercase()
        val relevance = queryTerms.count { it in content } / queryTerms.size.toFloat()

        // Context match: app, time, location
        val contextMatch = if (ctx.currentApp != null && ctx.currentApp in content) 0.3f else 0f

        // Combined score
        val score = (relevance * 0.4f) + (importance * 0.3f) + (recency * 0.2f) + (contextMatch * 0.1f)
        return ScoredEntry(entry, score.coerceIn(0f, 1f))
    }

    private fun getImportance(entry: MemoryEntry): Float {
        // From MemoryEntry importance field, normalized
        return entry.importance.coerceIn(0f, 1f)
    }

    private fun getRecency(entry: MemoryEntry): Float {
        val ageDays = (System.currentTimeMillis() - entry.timestamp) / (24 * 60 * 60 * 1000f)
        return (1f - (ageDays / 30f).coerceIn(0f, 1f))  // 30-day decay
    }

    // --- Step Observation ---
    suspend fun updateFromStepObservation(obs: StepObservation) {
        // Store episodic memory
        obs.memoryToStore?.let { memoryDao.insert(it.toEntity()) }

        // Update preference if hint
        obs.preferenceHint?.let { hint ->
            setPreference(hint.category, hint.key, hint.value, confidence = 0.7f)
        }

        // Update belief if hint
        obs.beliefHint?.let { hint ->
            observeBelief(hint.statement, hint.dimension)
        }

        // Add learned facts to conceptual mind
        obs.whatILearned.forEach { fact ->
            memoryDao.insert(MemoryEntry(
                content = fact,
                importance = 0.6f,
                emotionalWeight = 0.3f,
                timestamp = System.currentTimeMillis()
            ).toEntity())
        }
    }

    // --- Session Reflection ---
    suspend fun reflectOnSession(reflection: SessionReflection) {
        reflection.newPreferences.forEach { hint ->
            setPreference(hint.category, hint.key, hint.value, confidence = 0.6f)
        }
        reflection.newBeliefs.forEach { hint ->
            observeBelief(hint.statement, hint.dimension)
        }
        reflection.memoriesToConsolidate.forEach { memory ->
            memoryDao.insert(memory.toEntity())
        }
        reflection.goalsAchieved.forEach { goalId ->
            completeGoal(goalId)
        }
        reflection.goalsSuggested.forEach { goal ->
            upsertGoal(goal.toEntity())
        }
    }
}
```

**Step 3: Add StepObservation data class**

```kotlin
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

data class SessionReflection(
    val sessionId: String,
    val whatWentWell: List<String> = emptyList(),
    val whatCouldImprove: List<String> = emptyList(),
    val userFrustrations: List<String> = emptyList(),
    val newPreferences: List<PreferenceHint> = emptyList(),
    val newBeliefs: List<BeliefHint> = emptyList(),
    val memoriesToConsolidate: List<MemoryEntry> = emptyList(),
    val goalsAchieved: List<String> = emptyList(),
    val goalsSuggested: List<Goal> = emptyList()
)
```

**Step 4: Add to AppDatabase builder**

```kotlin
// In AppDatabase.kt, add to database builder:
.databaseBuilder(...)
    .addCallback(object : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            // Initialize default profile
            db.execSQL("INSERT INTO personal_profile (id, communicationStyle, riskTolerance, createdAt, updatedAt) VALUES ('self', 'CASUAL', 'MEDIUM', ${System.currentTimeMillis()}, ${System.currentTimeMillis()})")
        }
    })
```

**Step 5: Commit**

---

## Phase 2 — Agent Loop Integration

### Task 3: LLM-Generated StepObservation after each tool call

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/StepObservationGenerator.kt`

**Step 1: Create observation prompt in AgentPromptBuilder**

Add to `AgentPromptBuilder.kt` a new method to build observation prompt:

```kotlin
fun buildStepObservationPrompt(
    step: Int,
    action: String,
    result: String,
    personalContext: String
): String = """
You just executed tool: $action
Result: $result

Based on the personal context: $personalContext

Generate a StepObservation with:
- whatILearned: new facts learned from this action (max 3)
- preferenceHint: if user showed a preference (e.g., seemed annoyed, liked something), null otherwise
- beliefHint: if this action revealed something about user beliefs/values, null otherwise
- memoryToStore: brief episodic memory of what happened, or null if nothing worth remembering

Return as JSON:
{
  "whatILearned": ["fact1", "fact2"],
  "preferenceHint": null | {"category": "routing", "key": "preferredStyle", "value": "brief"},
  "beliefHint": null | {"dimension": "privacy", "statement": "user.is.private.person"},
  "memoryToStore": null | {"content": "...", "importance": 0.6, "emotionalWeight": 0.4}
}
""".trimIndent()
```

**Step 2: Modify AgentLoopExecutor — after tool execution, generate observation**

In `AgentLoopExecutor.kt`, after `executeTool()` and parsing the result:

```kotlin
// After tool result is parsed and before next iteration
if (toolResult.isSuccess && enableWorldModelUpdates) {
    val observationPrompt = promptBuilder.buildStepObservationPrompt(
        step = currentStep,
        action = toolCall.name,
        result = toolResult.summary,
        personalContext = personalWorldModel.getProfile().let {
            "name=${it.name}, style=${it.communicationStyle}, risk=${it.riskTolerance}"
        }
    )

    // Ask LLM to generate observation (use same provider, fast model if available)
    val observationResponse = try {
        modelProvider.chatCompletion(
            AgentRequest(messages = listOf(Message(role = "user", content = observationPrompt)))
        )
    } catch (e: Exception) {
        Log.w(TAG, "Failed to generate step observation", e)
        null
    }

    observationResponse?.let { resp ->
        parseStepObservation(resp)?.let { obs ->
            personalWorldModel.updateFromStepObservation(obs)
        }
    }
}
```

**Step 3: Add parseStepObservation helper**

```kotlin
private fun parseStepObservation(response: AgentResponse): StepObservation? {
    return try {
        val json = response.content
            .substringAfter("{")
            .substringBefore("}")
            .let { "{$it}" }

        val obj = JSONObject(json)
        StepObservation(
            step = currentStep,
            action = "",  // filled from context
            result = "",  // filled from context
            whatILearned = obj.getJSONArray("whatILearned").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            },
            preferenceHint = obj.optJSONObject("preferenceHint")?.let {
                PreferenceHint(it.getString("category"), it.getString("key"), it.getString("value"))
            },
            beliefHint = obj.optJSONObject("beliefHint")?.let {
                BeliefHint(it.getString("dimension"), it.getString("statement"))
            },
            memoryToStore = obj.optJSONObject("memoryToStore")?.let {
                MemoryEntry(
                    content = it.getString("content"),
                    importance = it.getDouble("importance").toFloat(),
                    emotionalWeight = it.getDouble("emotionalWeight").toFloat(),
                    timestamp = System.currentTimeMillis()
                )
            }
        )
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse step observation JSON", e)
        null
    }
}
```

**Step 4: Add WorldModelManager to AgentController DI**

In `AgentController.kt`:
```kotlin
// Add as constructor parameter or lazy init
val worldModel: PersonalWorldModel = PersonalWorldModel(
    personalProfileDao = database.personalProfileDao(),
    beliefDao = database.beliefDao(),
    preferenceDao = database.preferenceDao(),
    routineDao = database.routineDao(),
    relationshipDao = database.relationshipDao(),
    goalDao = database.goalDao(),
    phoneStateCacheDao = database.phoneStateCacheDao(),
    memoryDao = database.memoryDao(),
    observationDao = database.observationDao()
)
```

**Step 5: Pass worldModel to AgentLoopExecutor**

```kotlin
suspend fun sendMessage(...) {
    agentLoopExecutor.startLoop(
        message = processedMessage,
        worldModel = worldModel,  // NEW
        ...
    )
}
```

**Step 6: Update AgentLoopExecutor signature**

```kotlin
suspend fun startLoop(
    message: String,
    worldModel: PersonalWorldModel,
    ...
)
```

**Step 7: Commit**

---

### Task 4: Session Reflection at onSessionDone

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt`

**Step 1: Add session reflection prompt**

In `AgentController.kt`, after the loop completes successfully in `onSessionDone()`:

```kotlin
private suspend fun reflectOnSession(sessionId: String, steps: List<AgentStep>, worldModel: PersonalWorldModel) {
    val stepsJson = steps.map {
        "{action: ${it.action.name}, result: ${it.result.substring(0, minOf(200, it.result.length))}}"
    }.joinToString(", ")

    val reflectionPrompt = """
Session ID: $sessionId
Steps taken: [$stepsJson]

Personal context:
${worldModel.getProfile().let { "style=${it.communicationStyle}, risk=${it.riskTolerance}" }}

Generate a SessionReflection as JSON:
{
  "whatWentWell": ["observation1", "observation2"],
  "whatCouldImprove": ["observation1"],
  "userFrustrations": [],
  "newPreferences": [],
  "newBeliefs": [],
  "memoriesToConsolidate": [],
  "goalsAchieved": [],
  "goalsSuggested": []
}
""".trimIndent()

    try {
        val response = currentProvider.chatCompletion(
            AgentRequest(messages = listOf(Message(role = "user", content = reflectionPrompt)))
        )
        val reflection = parseSessionReflection(response.content)
        worldModel.reflectOnSession(reflection)
    } catch (e: Exception) {
        Log.e(TAG, "Session reflection failed", e)
    }
}
```

**Step 2: Wire session reflection into loop completion**

In `AgentLoopExecutor.startLoop()`, when loop completes (task done or step limit):

```kotlin
// In onLoopComplete callback or at end of startLoop
onComplete: (sessionId: String, steps: List<AgentStep>) -> Unit {
    // existing code...
    // AFTER loop completes, trigger reflection
    if (enableWorldModelUpdates) {
        agentController.reflectOnSession(sessionId, steps, worldModel)
    }
}
```

**Step 3: Add parseSessionReflection helper**

```kotlin
private fun parseSessionReflection(content: String): SessionReflection {
    val json = content
        .substringAfter("{")
        .substringBefore("}")
        .let { "{$it}" }

    val obj = JSONObject(json)
    return SessionReflection(
        sessionId = UUID.randomUUID().toString(),
        whatWentWell = obj.getJSONArray("whatWentWell").let { arr -> (0 until arr.length()).map { arr.getString(it) } },
        whatCouldImprove = obj.getJSONArray("whatCouldImprove").let { arr -> (0 until arr.length()).map { arr.getString(it) } },
        userFrustrations = obj.getJSONArray("userFrustrations").let { arr -> (0 until arr.length()).map { arr.getString(it) } },
        newPreferences = obj.getJSONArray("newPreferences").let { arr ->
            (0 until arr.length()).map { jObj ->
                val o = arr.getJSONObject(it)
                PreferenceHint(o.getString("category"), o.getString("key"), o.getString("value"))
            }
        },
        newBeliefs = obj.getJSONArray("newBeliefs").let { arr ->
            (0 until arr.length()).map { jObj ->
                val o = arr.getJSONObject(it)
                BeliefHint(o.getString("dimension"), o.getString("statement"))
            }
        },
        memoriesToConsolidate = emptyList(),
        goalsAchieved = obj.getJSONArray("goalsAchieved").let { arr -> (0 until arr.length()).map { arr.getString(it) } },
        goalsSuggested = emptyList()
    )
}
```

**Step 4: Commit**

---

## Phase 3 — Safety + Routing Integration

### Task 5: SafetyGate — World-Model-Driven Risk Escalation

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/SafetyGate.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/PendingConfirmation.kt` — add riskLevel field already done, verify

**Step 1: Extend SafetyGate.assess() signature**

```kotlin
data class RiskAssessment(
    val level: RiskLevel,           // LOW/MEDIUM/HIGH
    val reasons: List<String>,      // why this risk level
    val escalationFactors: List<String> = emptyList(),  // what made us escalate
    val userLearnedPreference: String? = null  // "deniedTool:phone.send_sms"
)

fun assess(
    tool: ToolCall,
    args: Map<String, String>,
    screenContext: ScreenUnderstanding?,
    worldModel: PersonalWorldModel? = null  // NEW
): RiskAssessment
```

**Step 2: Implement world-model-aware escalation**

In `SafetyGate.kt`:

```kotlin
fun assess(
    tool: ToolCall,
    args: Map<String, String>,
    screenContext: ScreenUnderstanding?,
    worldModel: PersonalWorldModel? = null
): RiskAssessment {
    val baseRisk = getBaseRisk(tool)
    val reasons = mutableListOf("base risk: ${baseRisk.name}")
    val escalationFactors = mutableListOf<String>()

    worldModel ?: return RiskAssessment(baseRisk, reasons)

    val profile = runCatching { worldModel.getProfile() }.getOrNull()
    val riskTolerance = profile?.riskTolerance ?: "MEDIUM"

    // User's learned safety preferences
    val deniedTools = worldModel.getPreferencesForCategory("safety_denied")
    if (tool.name in deniedTools.values) {
        escalationFactors.add("tool previously denied by user")
        return RiskAssessment(RiskLevel.HIGH, reasons + "user previously denied this tool", escalationFactors)
    }

    // Risk tolerance escalation
    if (riskTolerance == "LOW" && baseRisk != RiskLevel.LOW) {
        escalationFactors.add("user risk tolerance: LOW")
        return RiskAssessment(RiskLevel.HIGH, reasons + "escalated: user prefers maximum caution", escalationFactors)
    }

    // Privacy belief — if user shown high concern for privacy
    if (worldModel != null) {
        runCatching {
            val privacyConcern = worldModel.getBelief("privacy", "%concerned.about.privacy%") ?: 0.5f
            if (privacyConcern > 0.7f && tool.name in PRIVACY_SENSITIVE_TOOLS) {
                escalationFactors.add("user privacy concern: $privacyConcern")
                return RiskAssessment(
                    RiskLevel.HIGH,
                    reasons + "escalated: user is privacy-conscious and this tool accesses sensitive data",
                    escalationFactors
                )
            }
        }
    }

    // Financial context escalation
    if (screenContext?.screenType == "FINANCIAL" && tool.name in FINANCIAL_TOOLS) {
        escalationFactors.add("financial context detected")
        return RiskAssessment(RiskLevel.HIGH, reasons + "escalated: financial app context", escalationFactors)
    }

    // Screen understanding — check if sensitive content visible
    screenContext?.let { ctx ->
        val sensitiveElements = ctx.interactiveElements.count { it.isSensitive }
        if (sensitiveElements > 3 && baseRisk != RiskLevel.LOW) {
            escalationFactors.add("multiple sensitive elements on screen")
        }
    }

    return if (escalationFactors.isNotEmpty()) {
        val newLevel = if (riskTolerance == "LOW") RiskLevel.HIGH else RiskLevel.MEDIUM
        RiskAssessment(newLevel, reasons, escalationFactors)
    } else {
        RiskAssessment(baseRisk, reasons)
    }
}
```

**Step 3: Record safety decision for learning**

When `PendingConfirmation` is approved/denied, record in safety_audit:

```kotlin
// Already has audit logging — ensure world model snapshot is included
val audit = SafetyAuditEntity(
    toolName = tool.name,
    args = args.toString(),
    riskLevel = assessment.level.name,
    decision = decision.name,  // APPROVED/DENIED
    timestamp = System.currentTimeMillis(),
    riskFactors = assessment.escalationFactors.joinToString(";"),
    worldModelSnapshot = worldModel?.getProfile()?.let {
        "${it.communicationStyle},${it.riskTolerance}"
    } ?: ""
)
safetyAuditDao.insert(audit)

// Learning: if DENIED → update beliefs/preferences
if (decision == Decision.DENIED) {
    worldModel?.setPreference("safety_denied", tool.name, "true", confidence = 0.7f)
    worldModel?.observeBelief("dislikes.${tool.name}", "safety")
}
```

**Step 4: Add PRIVACY_SENSITIVE_TOOLS and FINANCIAL_TOOLS sets**

```kotlin
private val PRIVACY_SENSITIVE_TOOLS = setOf(
    "phone.clipboard", "phone.notifications", "accessibility.read_tree",
    "browser.type_into_selector", "browser.type_into_focused"
)

private val FINANCIAL_TOOLS = setOf(
    "phone.send_sms", "phone.call", "browser.click_text", "browser.type_into_selector"
)
```

**Step 5: Commit**

---

### Task 6: IrisRouter — World-Model-Driven Routing

**Files:**
- Modify: `app/src/main/java/com/phoneagent/iris/IrisRouter.kt`
- Modify: `app/src/main/java/com/phoneagent/iris/IrisProfile.kt` — add style/depth fields

**Step 1: Extend IrisProfile with style and depth**

```kotlin
data class IrisProfile(
    val name: String,           // REFLEX/FAST/SHARP/GENTLE/BALANCED/DEEP
    val temperature: Float,
    val maxSteps: Int,
    val style: String = "conversational",  // brief/detailed/technical/conversational
    val depth: Int = 2          // 1-5, how deeply to reason
)
```

**Step 2: Extend IrisState to record routing decisions**

```kotlin
@Entity(tableName = "iris_routing_history")
data class RoutingDecisionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val messagePreview: String,     // first 100 chars
    val profile: String,
    val temperature: Float,
    val style: String,
    val depth: Int,
    val outcome: String? = null,    // positive/negative/neutral — filled later
    val sessionId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao interface RoutingDecisionDao {
    @Query("SELECT * FROM iris_routing_history ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecent(): List<RoutingDecisionEntity>
    @Insert suspend fun insert(entity: RoutingDecisionEntity)
    @Query("UPDATE iris_routing_history SET outcome = :outcome WHERE id = :id")
    suspend fun updateOutcome(id: String, outcome: String)
}
```

**Step 3: Rewrite selectProfile with world model**

```kotlin
suspend fun selectProfile(
    message: String,
    worldModel: PersonalWorldModel? = null
): RoutingDecision {
    val baseProfile = analyzeMessageType(message)
    val timeSlot = getTimeSlot()
    val energyLevel = estimateEnergyLevel(worldModel)

    // Get user preferences from world model
    val routingPrefs = worldModel?.getPreferencesForCategory("routing")
    val profile = routingPrefs?.get("preferredProfile")?.let {
        IrisProfile.valueOf(it)
    } ?: baseProfile

    // Adjust temperature based on user preferences
    val preferredTemp = routingPrefs?.get("temperature")?.toFloatOrNull()
    val temp = preferredTemp ?: profile.temperature

    // Adjust style based on communication preference
    val commStyle = worldModel?.getProfile()?.communicationStyle ?: "CASUAL"
    val style = when (commStyle) {
        "TERSE" -> "brief"
        "FORMAL" -> "detailed"
        "ELABORATE" -> "detailed"
        else -> "conversational"
    }

    // Adjust depth based on energy + time of day
    val depth = when {
        energyLevel == "LOW" -> 1
        timeSlot == "MORNING" && energyLevel != "LOW" -> 3
        message.length > 500 -> 3  // complex message = deeper reasoning
        else -> 2
    }

    // Record decision for learning
    val decisionId = UUID.randomUUID().toString()
    routingDecisionDao.insert(
        RoutingDecisionEntity(
            messagePreview = message.take(100),
            profile = profile.name,
            temperature = temp,
            style = style,
            depth = depth
        )
    )

    return RoutingDecision(
        profile = profile,
        temperature = temp,
        style = style,
        depth = depth,
        reasoning = "profile=${profile.name}, style=$style, depth=$depth (energy=$energyLevel, time=$timeSlot)"
    )
}
```

**Step 4: Add recordRoutingOutcome for learning**

```kotlin
suspend fun recordRoutingOutcome(outcome: String, sessionId: String? = null) {
    // Update most recent un-Outcome'd routing decision
    routingDecisionDao.getRecent().firstOrNull { it.outcome == null }?.let {
        routingDecisionDao.updateOutcome(it.id, outcome)
    }

    // Learn from outcome: if negative → reduce confidence in this profile for similar messages
    if (outcome == "negative" && sessionId != null) {
        worldModel?.observeBelief("dislikes.${profile.name}.routing", "routing")
    }
}
```

**Step 5: Wire session outcome into onSessionDone**

In `AgentController.onSessionDone()`:
```kotlin
val userSatisfaction = measureUserSatisfaction(steps)  // based on approval rate, step count
val outcome = when {
    userSatisfaction > 0.8f -> "positive"
    userSatisfaction < 0.4f -> "negative"
    else -> "neutral"
}
irisRouter.recordRoutingOutcome(outcome, sessionId)
```

**Step 6: Commit**

---

## Phase 4 — Phone State Layer

### Task 7: PhoneStateModel — Real-Time Screen Understanding

**Files:**
- Create: `app/src/main/java/com/phoneagent/worldmodel/PhoneStateModel.kt`
- Modify: `app/src/main/java/com/phoneagent/perception/ScreenCaptureManager.kt` — update phone state after capture

**Step 1: Create PhoneStateModel**

```kotlin
data class PhoneStateModel(
    val foregroundApp: AppContext?,
    val appHierarchy: List<AppContext>,
    val currentScreen: ScreenUnderstanding,
    val recentActions: List<RecentAction>
)

data class ScreenUnderstanding(
    val screenType: ScreenType,
    val mainPurpose: String,
    val interactiveElements: List<UIElement>,
    val informationalElements: List<UIElement>,
    val confidence: Float
)

enum class ScreenType {
    HOMESCREEN, APP_LAUNCHER, LIST, MESSAGING, CALENDAR,
    EMAIL, BROWSER, SETTINGS, FINANCIAL, SOCIAL_MEDIA,
    UNKNOWN
}

data class UIElement(
    val text: String,
    val bounds: Rect,
    val isInteractive: Boolean,
    val isSensitive: Boolean,  // password, phone number, etc.
    val role: String           // button, text_field, link, etc.
)

data class RecentAction(
    val toolName: String,
    val timestamp: Long,
    val success: Boolean
)
```

**Step 2: Create phone state classifier**

After each screenshot capture, ask LLM to classify the screen:

```kotlin
fun buildScreenUnderstandingPrompt(
    ocrText: String,
    appName: String
): String = """
App: $appName
Screen OCR:
$ocrText

Classify this screen and return as JSON:
{
  "screenType": "LIST|MESSAGING|CALENDAR|EMAIL|BROWSER|SETTINGS|FINANCIAL|SOCIAL_MEDIA|HOMESCREEN|UNKNOWN",
  "mainPurpose": "one sentence what user is doing",
  "interactiveElements": [
    {"text": "Submit", "isSensitive": false, "role": "button"},
    {"text": "Password", "isSensitive": true, "role": "text_field"}
  ],
  "informationalElements": [
    {"text": "Unread messages: 3", "isSensitive": false}
  ],
  "confidence": 0.85
}
""".trimIndent()
```

**Step 3: Update phone state after each capture**

In `AgentLoopExecutor`, after `screenCaptureManager.capture()`:

```kotlin
val screenClassification = try {
    val response = modelProvider.chatCompletion(
        AgentRequest(messages = listOf(
            Message(role = "user", content = screenCaptureManager.buildScreenUnderstandingPrompt(
                ocrText = ocrResult.text,
                appName = screenCaptureManager.currentAppName
            ))
        ))
    )
    parseScreenUnderstanding(response.content)
} catch (e: Exception) {
    null
}

screenClassification?.let { classification ->
    val foregroundApp = AppContext(
        packageName = screenCaptureManager.currentPackage ?: "",
        appName = screenCaptureManager.currentAppName ?: "Unknown",
        category = classifyAppCategory(screenCaptureManager.currentPackage),
        trustLevel = safetyPolicy.getTrustLevel(screenCaptureManager.currentPackage)
    )

    val newState = PhoneStateModel(
        foregroundApp = foregroundApp,
        appHierarchy = listOf(foregroundApp),  // TODO: get full stack
        currentScreen = classification,
        recentActions = recentActions.takeLast(5)
    )

    worldModel.updatePhoneState(
        PhoneStateCacheEntity(
            foregroundApp = foregroundApp.packageName,
            appHierarchy = "[]",  // JSON
            screenType = classification.screenType.name,
            screenPurpose = classification.mainPurpose,
            confidence = classification.confidence
        )
    )
}
```

**Step 4: Use phone state in SafetyGate**

In `SafetyGate.assess()`:
```kotlin
val screenContext = worldModel?.getPhoneState()?.let {
    ScreenUnderstanding(
        screenType = ScreenType.valueOf(it.screenType),
        mainPurpose = it.screenPurpose,
        interactiveElements = emptyList(),
        informationalElements = emptyList(),
        confidence = it.confidence
    )
}
```

**Step 5: Commit**

---

## Phase 5 — Prompt Builder Integration

### Task 8: AgentPromptBuilder — Inject Personal Context

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentPromptBuilder.kt`

**Step 1: Add personal context method**

```kotlin
fun buildPersonalContext(worldModel: PersonalWorldModel, context: RetrievalContext): String {
    val profile = worldModel.getProfile()
    val activeGoals = worldModel.getActiveGoals()
    val relevantMemories = runCatching {
        worldModel.retrieveMemories(
            query = context.currentTask ?: "",
            context = context,
            limit = 3
        )
    }.getOrNull() ?: emptyList()

    return buildString {
        appendLine("=== PERSONAL CONTEXT ===")
        profile.name?.let { appendLine("User name: $it") }
        appendLine("Communication style: ${profile.communicationStyle}")
        appendLine("Risk tolerance: ${profile.riskTolerance}")

        if (activeGoals.isNotEmpty()) {
            appendLine("Active goals:")
            activeGoals.forEach { appendLine("  - ${it.description} (${(it.progress * 100).toInt()}%)") }
        }

        if (relevantMemories.isNotEmpty()) {
            appendLine("Relevant memories:")
            relevantMemories.forEach { appendLine("  - ${it.content.take(100)}") }
        }

        appendLine("========================")
    }
}
```

**Step 2: Inject into main system prompt**

Modify `build()` method to accept `worldModel` and `retrievalContext`:

```kotlin
fun build(
    task: String,
    phoneState: PhoneStateModel?,
    recentSteps: List<AgentStep>,
    worldModel: PersonalWorldModel? = null,
    retrievalContext: RetrievalContext? = null
): AgentRequest {
    val personalContext = worldModel?.let {
        buildPersonalContext(it, retrievalContext ?: RetrievalContext())
    } ?: ""

    val fullTask = if (personalContext.isNotEmpty()) {
        "$personalContext\n\nTask: $task"
    } else {
        task
    }

    // ... rest of existing build logic
}
```

**Step 3: Update AgentLoopExecutor to pass worldModel**

In `AgentLoopExecutor.buildRequest()`:
```kotlin
val retrievalContext = RetrievalContext(
    currentApp = screenCaptureManager.currentPackage,
    timeSlot = getTimeSlot(),
    currentTask = task
)

val request = promptBuilder.build(
    task = task,
    phoneState = currentPhoneState,
    recentSteps = agentSteps,
    worldModel = worldModel,
    retrievalContext = retrievalContext
)
```

**Step 4: Commit**

---

## Phase 6 — Phone State Cache DAO + AppDatabase v5

### Task 9: AppDatabase v5 Migration + DAOs

**Files:**
- Modify: `app/src/main/java/com/phoneagent/memory/AppDatabase.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/PersonalProfileDao.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/PreferenceDao.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/RoutineDao.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/RelationshipDao.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/GoalDao.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/PhoneStateCacheDao.kt`
- Create: `app/src/main/java/com/phoneagent/worldmodel/RoutingDecisionDao.kt`

**Step 1: Write all DAOs**

Each follows the same pattern:
```kotlin
@Dao interface PersonalProfileDao {
    @Query("SELECT * FROM personal_profile WHERE id = 'self'") suspend fun get(): PersonalProfileEntity?
    @Insert(onConflict = REPLACE) suspend fun upsert(entity: PersonalProfileEntity)
}
```

**Step 2: Update AppDatabase — version 5**

```kotlin
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS personal_profile (id TEXT PRIMARY KEY, name TEXT, communicationStyle TEXT, riskTolerance TEXT, specialRequirements TEXT, createdAt INTEGER, updatedAt INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS preferences (id TEXT PRIMARY KEY, category TEXT, `key` TEXT, value TEXT, confidence REAL, lastUpdated INTEGER)")
        db.execSQL("CREATE INDEX idx_preferences_category ON preferences(category)")
        db.execSQL("CREATE TABLE IF NOT EXISTS routines (id TEXT PRIMARY KEY, timeSlot TEXT, dayOfWeek INTEGER, typicalActivities TEXT, energyLevel TEXT, interruptTolerance TEXT, location TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS relationships (id TEXT PRIMARY KEY, name TEXT, relationshipType TEXT, importance REAL, contactFrequency TEXT, context TEXT, lastInteraction INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS goals (id TEXT PRIMARY KEY, description TEXT, createdAt INTEGER, deadline INTEGER, status TEXT, progress REAL, relatedMemoryIds TEXT)")
        db.execSQL("CREATE INDEX idx_goals_status ON goals(status)")
        db.execSQL("CREATE TABLE IF NOT EXISTS phone_state_cache (id TEXT PRIMARY KEY, foregroundApp TEXT, appHierarchy TEXT, screenType TEXT, screenPurpose TEXT, confidence REAL, lastUpdated INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS iris_routing_history (id TEXT PRIMARY KEY, messagePreview TEXT, profile TEXT, temperature REAL, style TEXT, depth INTEGER, outcome TEXT, sessionId TEXT, timestamp INTEGER)")
        db.execSQL("CREATE INDEX idx_beliefs_dimension ON beliefs(dimension)")
        db.execSQL("ALTER TABLE beliefs ADD COLUMN dimension TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE observations ADD COLUMN step INTEGER DEFAULT 0")
    }
}
```

**Step 3: Add to database builder**

```kotlin
.addMigrations(MIGRATION_4_5)
```

**Step 4: Register DAOs**

```kotlin
abstract fun personalProfileDao(): PersonalProfileDao
abstract fun preferenceDao(): PreferenceDao
abstract fun routineDao(): RoutineDao
abstract fun relationshipDao(): RelationshipDao
abstract fun goalDao(): GoalDao
abstract fun phoneStateCacheDao(): PhoneStateCacheDao
abstract fun routingDecisionDao(): RoutingDecisionDao
```

**Step 5: Commit**

---

## Phase 7 — Learning from User Feedback

### Task 10: Wire PendingConfirmation decisions into BeliefEngine

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt` — in `onPendingConfirmationResult()`
- Modify: `app/src/main/java/com/phoneagent/agent/PendingConfirmation.kt`

**Step 1: Record feedback on approval/denial**

In `AgentController.onPendingConfirmationResult()`:

```kotlin
private suspend fun onPendingConfirmationResult(
    pending: PendingConfirmation,
    approved: Boolean,
    worldModel: PersonalWorldModel
) {
    if (approved) {
        // User approved — slight increase in confidence for similar actions
        worldModel.observeBelief("approves.${pending.toolName}", "safety")
    } else {
        // User denied — strong signal this tool/category is sensitive
        worldModel.setPreference("safety_denied", pending.toolName, "true", confidence = 0.8f)
        worldModel.contradictBelief("approves.${pending.toolName}")
    }

    // Update safety audit with outcome
    safetyAuditDao.updateDecision(pending.id, if (approved) "APPROVED" else "DENIED")
}
```

**Step 2: Add routing outcome on session end**

In `onSessionDone()`, gather metrics:
```kotlin
private suspend fun onSessionDone(sessionId: String, steps: List<AgentStep>, worldModel: PersonalWorldModel) {
    val approvalRate = steps.count { it.status == AgentStepStatus.CONFIRMED_APPROVED } / steps.size.toFloat()
    val stepCount = steps.size

    // If user denied many confirmations → negative routing outcome
    val routingOutcome = when {
        approvalRate < 0.5f -> "negative"
        approvalRate > 0.9f && stepCount < 5 -> "positive"  // quick success
        else -> "neutral"
    }

    irisRouter.recordRoutingOutcome(routingOutcome, sessionId)
    reflectOnSession(sessionId, steps, worldModel)
}
```

**Step 3: Commit**

---

## Phase 8 — Tests + Verification

### Task 11: Unit Tests

**Files:**
- Create: `app/src/test/java/com/phoneagent/worldmodel/PersonalWorldModelTest.kt`
- Create: `app/src/test/java/com/phoneagent/worldmodel/BeliefEngineTest.kt`
- Create: `app/src/test/java/com/phoneagent/agent/SafetyGateWorldModelTest.kt`
- Create: `app/src/test/java/com/phoneagent/iris/IrisRouterWorldModelTest.kt`

**Step 1: PersonalWorldModelTest**

```kotlin
class PersonalWorldModelTest {
    private lateinit var db: AppDatabase
    private lateinit var wm: PersonalWorldModel

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder().build()
        wm = PersonalWorldModel(
            db.personalProfileDao(),
            db.beliefDao(),
            db.preferenceDao(),
            db.routineDao(),
            db.relationshipDao(),
            db.goalDao(),
            db.phoneStateCacheDao(),
            db.memoryDao(),
            db.observationDao()
        )
    }

    @Test fun `profile defaults to CASUAL MEDIUM on first access`() = runTest {
        val profile = wm.getProfile()
        assertEquals("CASUAL", profile.communicationStyle)
        assertEquals("MEDIUM", profile.riskTolerance)
    }

    @Test fun `preference persists and retrieves`() = runTest {
        wm.setPreference("routing", "style", "brief")
        val retrieved = wm.getPreference("routing", "style")
        assertEquals("brief", retrieved)
    }

    @Test fun `retrieveMemories returns scored results`() = runTest {
        wm.updateFromStepObservation(StepObservation(
            step = 1, action = "test", result = "ok",
            memoryToStore = MemoryEntry(content = "visited coffee shop", importance = 0.7f, emotionalWeight = 0.5f)
        ))
        val memories = wm.retrieveMemories("coffee shop", RetrievalContext())
        assertTrue(memories.isNotEmpty())
    }
}
```

**Step 2: BeliefEngineTest**

```kotlin
class BeliefEngineTest {
    private lateinit var beliefDao: BeliefDao
    private lateinit var engine: BeliefEngine

    @Test fun `observe increases confidence`() = runTest {
        val conf = engine.observe("user.likes.short.responses", "routing")
        assertTrue(conf > 0.5f)
    }

    @Test fun `contradict decreases confidence`() = runTest {
        engine.observe("user.likes.short.responses", "routing")
        val conf = engine.contradict("user.likes.short.responses")
        assertTrue(conf < 0.6f)  // decreased from 0.6
    }
}
```

**Step 3: SafetyGateWorldModelTest**

```kotlin
class SafetyGateWorldModelTest {
    private lateinit var safetyGate: SafetyGate
    private lateinit var worldModel: PersonalWorldModel

    @Test fun `escalates to HIGH when user risk tolerance is LOW`() = runTest {
        worldModel.updateProfile(PersonalProfileEntity(riskTolerance = "LOW"))

        val assessment = safetyGate.assess(
            toolCall = ToolCall("browser.click_text", emptyMap()),
            args = emptyMap(),
            screenContext = null,
            worldModel = worldModel
        )

        assertEquals(RiskLevel.HIGH, assessment.level)
        assertTrue(assessment.escalationFactors.contains("user risk tolerance: LOW"))
    }

    @Test fun `escalates when tool previously denied by user`() = runTest {
        worldModel.setPreference("safety_denied", "phone.send_sms", "true", confidence = 0.8f)

        val assessment = safetyGate.assess(
            toolCall = ToolCall("phone.send_sms", emptyMap()),
            args = emptyMap(),
            screenContext = null,
            worldModel = worldModel
        )

        assertEquals(RiskLevel.HIGH, assessment.level)
    }
}
```

**Step 4: Run tests**

```bash
cd C:\Users\asus\Desktop\PhoneAgent
.\gradlew.bat testDebugUnitTest --tests "*PersonalWorldModelTest" --tests "*BeliefEngineTest" --tests "*SafetyGateWorldModelTest" -q
```

**Step 5: Commit**

---

## Phase 9 — Companion Protocol (Future)

### Task 12: Hub API Design (Stub for future implementation)

**This is stub only — write design note, don't implement yet.**

```kotlin
// Stub file: app/src/main/java/com/phoneagent/companion/HubApiServer.kt
// TODO: Implement local REST API (NanoHTTPD or Android's LocalServerSocket)
// for companion devices to query world model
```

---

## Execution Options

**Plan complete and saved to `docs/plans/2026-05-14-phoneagent-sota-architecture.md`.**

Two execution options:

**1. Subagent-Driven (this session)** — I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** — Open new session with executing-plans, batch execution with checkpoints

Which approach?