# PhoneAgent SOTA Architecture: Personal World Model

## Status
**Draft** — For Elnur's review before implementation

---

## 1. Vision

PhoneAgent becomes a persistent personal AI agent that knows YOU — your habits, preferences, routines, relationships, and goals. It runs on your phone as the primary hub, with companion devices (tablet, car, web) connecting to the same personal model. Every interaction deepens its understanding of you. Cloud-first intelligence combined with a rich personal world model that powers all decisions — tool selection, safety assessment, routing, and planning.

**Core insight**: The agent is only as good as its world model. A generic LLM can do tasks. A world-model-powered agent understands YOU and can anticipate your needs, remember your context, and adapt its behavior over time.

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    USER LAYER                            │
│  Voice input / Chat / Companion devices                  │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│               AGENT LOOP (AgentLoopExecutor)             │
│  User message → Context retrieval → LLM reasoning        │
│  → Tool execution → LLM-generated observation → loop     │
└─────────────────────┬───────────────────────────────────┘
                      │
         ┌────────────┴────────────┐
         │                         │
┌────────▼────────┐      ┌─────────▼─────────┐
│ PERSONAL WORLD  │      │    PHONE STATE     │
│     MODEL       │      │     LAYER          │
│                 │      │                    │
│ • Personal profile │   │ • App hierarchy    │
│ • Beliefs          │   │ • UI contexts      │
│ • Preferences      │   │ • Current state    │
│ • Routines         │   │ • Permissions      │
│ • Relationships    │   │ • Capabilities     │
│ • Goals             │   │                    │
└────────┬────────┘      └─────────┬──────────┘
         │                          │
         │    ┌────────────────────┘
         │    │
┌────────▼────▼─────────┐
│    MEMORY LAYERS      │
│                      │
│ ┌──────────────────┐ │
│ │  Working Context │ │ ← current task context (ephemeral)
│ ├──────────────────┤ │
│ │  Episodic Memory  │ │ ← session history, events, observations
│ ├──────────────────┤ │
│ │  Conceptual Mind │ │ ← semantic knowledge, learned patterns
│ ├──────────────────┤ │
│ │  Personal Model  │ │ ← long-term profile, beliefs, preferences
│ └──────────────────┘ │
└──────────────────────┘

         │
┌────────▼──────────┐
│   SAFETY LAYER    │ ← continuously monitors, learns user preferences
└───────────────────┘
```

### Layered Memory Architecture

| Layer | Purpose | Updates | Persistence |
|-------|---------|---------|-------------|
| **Working Context** | Current task — retrieved memories, active goal, recent steps | Every tool call (LLM-generated summary) | Ephemeral (in-memory) |
| **Episodic Memory** | What happened — sessions, events, observations with emotional weight | After each session (LLM reflection) | Room DB (30-day decay) |
| **Conceptual Mind** | What it means — semantic patterns, learned concepts, beliefs | On belief contradictions, significant events | Room DB (conceptual) |
| **Personal Model** | Who you are — preferences, habits, routines, relationships, goals | Continuous learning from feedback | Room DB (long-term) |

---

## 3. Personal World Model — Core Data Structures

### 3.1 Personal Profile (top-level identity)

```kotlin
data class PersonalWorldModel(
    val id: String = UUID.randomUUID().toString(),
    val personalProfile: PersonalProfile,        // who you are
    val beliefEngine: BeliefEngine,              // what you believe
    val preferenceStore: PreferenceStore,         // how you prefer things
    val routineModel: RoutineModel,              // your daily patterns
    val relationshipMap: Map<String, Relationship>, // people in your life
    val goalTracker: GoalTracker                 // current goals and projects
)

data class PersonalProfile(
    val name: String?,
    val communicationStyle: CommunicationStyle,  // formal/casual/short/long
    val decisionPatterns: List<DecisionPattern>,   // how you make decisions
    val riskTolerance: RiskTolerance,            // low/medium/high
    val specialRequirements: List<String>,        // accessibility needs, etc.
)

enum class CommunicationStyle { FORMAL, CASUAL, Terse, ELABORATE }
enum class RiskTolerance { LOW, MEDIUM, HIGH }
```

### 3.2 Belief Engine (Bayesian confidence tracking)

```kotlin
data class Belief(
    val dimension: String,          // e.g., "privacy", "speed", "quality"
    val statement: String,          // e.g., "user.prefers.voice.over.text"
    val confidence: Float,           // 0.0–1.0 Bayesian posterior
    val evidenceCount: Int,
    val lastUpdated: Long
)
// BeliefEngine.observe(statement, dimension) → confidence update
// BeliefEngine.contradict(statement) → confidence decrease
// Used by SafetyGate to learn user privacy preferences, by routing to adapt style
```

### 3.3 Preference Store

```kotlin
data class PreferenceStore(
    val routingPreferences: Map<ContextType, RoutingPreference>,
    val toolPreferences: Map<String, ToolPreference>,   // preferred tools for tasks
    val safetyPreferences: SafetyPreferences,          // user's safety boundaries
    val displayPreferences: DisplayPreferences
)

data class RoutingPreference(
    val preferredProfile: IrisProfile,      // how user likes to be addressed
    val preferredTemperature: Float,
    val preferredDepth: Int,
    val responseStyle: ResponseStyle       // brief/detailed/technical
)
```

### 3.4 Routine Model

```kotlin
data class RoutineModel(
    val dailyRoutines: Map<TimeSlot, Routine>,     // morning/afternoon/evening/night
    val locationContexts: Map<Location, Context>,   // home/work/gym/etc.
    val activityPatterns: List<ActivityPattern>    // recurring activities
)

data class Routine(
    val timeSlot: TimeSlot,
    val typicalActivities: List<String>,
    val typicalApps: List<String>,
    val energyLevel: EnergyLevel,         // HIGH/MEDIUM/LOW
    val interruptTolerance: InterruptTolerance  // how tolerant of interruptions
)
```

### 3.5 Relationship Map

```kotlin
data class Relationship(
    val entityId: String,
    val name: String,
    val relationshipType: RelationshipType,  // FAMILY/FRIEND/COLLEAGUE/AI
    val importance: Float,                   // 0.0–1.0
    val contactFrequency: ContactFrequency,  // DAILY/WEEKLY/MONTHLY
    val context: String                       // "works at X", "family member Y"
)

enum class RelationshipType { FAMILY, FRIEND, COLLEAGUE, SERVICE, AI, UNKNOWN }
```

### 3.6 Goal Tracker

```kotlin
data class GoalTracker(
    val activeGoals: List<Goal>,            // currently pursuing
    val completedGoals: List[Goal>,          // historical
    val deferredGoals: List<Goal>,          // parked for later
    val suggestedGoals: List<Goal>          // AI-suggested based on patterns
)

data class Goal(
    val id: String,
    val description: String,
    val createdAt: Long,
    val deadline: Long?,
    val status: GoalStatus,           // ACTIVE/COMPLETED/ABANDONED/PARKED
    val progress: Float,              // 0.0–1.0
    val relatedMemories: List<String>  // memory IDs relevant to this goal
)
```

---

## 4. Memory Flow — How the Agent Learns

### 4.1 Memory Retrieval Chain (at each step)

```
Query
  │
  ▼
[Working Context] ← current task context (already in memory)
  │
  ▼
[Semantic Search] ← "what do I know about X?"
  │
  ├──→ [Conceptual Mind]    ← semantic patterns, beliefs
  │
  ├──→ [Episodic Memory]    ← past events with similar context
  │
  └──→ [Personal Model]     ← user preferences, routines
        │
        ▼
    [LLM Rerank] ← rerank retrieved memories by relevance to current context
        │
        ▼
    [Context Assembly] ← build context string for LLM
```

### 4.2 Memory Update — LLM-Generated Summaries

After each tool execution, the LLM generates a structured observation:

```kotlin
data class StepObservation(
    val step: Int,
    val action: String,
    val result: String,
    val whatILearned: List<String>,        // new facts for world model
    val preferenceUpdate: PreferenceHint?, // "user seemed annoyed by X"
    val beliefUpdate: BeliefHint?,          // "user corrects me on Y"
    val memoryToStore: MemoryEntry?          // episodic memory to persist
)
```

The `AgentLoopExecutor` sends this to `MemoryEngine.updateFromObservation()`:

```kotlin
fun updateFromObservation(obs: StepObservation) {
    // 1. Store episodic memory from obs.memoryToStore
    // 2. If obs.preferenceUpdate != null → PreferenceStore.update()
    // 3. If obs.beliefUpdate != null → BeliefEngine.update()
    // 4. If obs.whatILearned is non-empty → ConceptualMind.addFacts()
    // 5. Check for belief contradictions → trigger BeliefEngine.contradict()
}
```

### 4.3 Session-Level Reflection

At session end (`AgentController.onSessionDone()`), the agent does a deeper reflection:

```kotlin
data class SessionReflection(
    val sessionId: String,
    val whatWentWell: List<String>,
    val whatCouldImprove: List<String>,
    val userFrustrations: List<String>,
    val newPreferences: List<PreferenceHint>,
    val newBeliefs: List<BeliefHint>,
    val memoriesToConsolidate: List<MemoryEntry>,
    val goalsAchieved: List<String>,
    val goalsSuggested: List<Goal>
)

// → Stored in episodic memory
// → PreferenceStore updated
// → BeliefEngine updated
// → GoalTracker updated if goals were mentioned or achieved
```

### 4.4 Learning from User Feedback

When user corrects the agent (via PendingConfirmation approval/denial or explicit feedback):

```kotlin
fun recordFeedback(
    originalAction: AgentAction,
    userResponse: UserResponse,
    context: SessionContext
) {
    // Update belief: user approved this action → increase confidence
    beliefEngine.observe(
        "prefers.${originalAction.toolName}",
        "tool_preference"
    )

    // Update preference: user's risk tolerance
    safetyPreferences.adaptFromFeedback(originalAction, userResponse)

    // Update routing: learn communication style from response pattern
    routingPreferences.learnFromFeedback(context)
}
```

---

## 5. Phone State Layer

The agent maintains a real-time model of the phone's state — not just OCR snapshots, but a structured understanding:

```kotlin
data class PhoneStateModel(
    val foregroundApp: AppContext,
    val appHierarchy: List<AppContext>,      // stack of visible apps
    val currentScreen: ScreenUnderstanding,
    val notificationState: List<Notification>,
    val accessibilityTree: AccessibilityNode?,
    val recentActions: List<RecentAction>
)

data class ScreenUnderstanding(
    val screenType: ScreenType,             // LIST/CALENDAR/MESSAGING/ETC.
    val interactiveElements: List<UIElement>,
    val informationalElements: List<UIElement>,
    val confidence: Float,
    val mainPurpose: String                  // LLM-generated: "user is reading email"
)

data class AppContext(
    val packageName: String,
    val appName: String,
    val isLauncherApp: Boolean,
    val category: AppCategory,
    val trustLevel: TrustLevel              // SYSTEM/TRUSTED/UNKNOWN/DENYLISTED
)
```

**How it updates**: After each screenshot + OCR, the LLM (or a lightweight classifier) outputs the `ScreenUnderstanding`. The `PhoneStateModel` is updated incrementally — only changes are recorded, not the full state each time.

---

## 6. Agent Loop — Revised

The loop is enhanced with world model integration at every step:

```
User Message
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│ 1. CONTEXT RETRIEVAL                                     │
│    worldModel.retrieve(query, currentContext)            │
│    → Personal preferences + relevant memories + beliefs  │
└──────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│ 2. PLANNING (optional — for complex tasks)               │
│    If task complexity > threshold:                       │
│      LLM.generateSubgoalPlan(task, worldModel)          │
│      → list of subgoals with expected outcomes           │
└──────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│ 3. ROUTING                                               │
│    IrisRouter.selectProfile(message, worldModel)        │
│    → profile + temperature + depth + style              │
└──────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│ 4. SYSTEM PROMPT CONSTRUCTION                             │
│    AgentPromptBuilder.build(                             │
│      personalContext = worldModel.getPersonalContext(),  │
│      recentMemories = worldModel.retrieve(...),         │
│      phoneState = phoneStateModel.getCurrentState(),     │
│      activeGoals = worldModel.goalTracker.activeGoals   │
│    )                                                      │
└──────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│ 5. LOOP (max 12 steps)                                   │
│    ┌──────────────────────────────────────────────┐      │
│    │ a. Capture screenshot + OCR                  │      │
│    │ b. Update PhoneStateModel (LLM classification)│      │
│    │ c. Build vision payload                       │      │
│    │ d. LLM reasoning → tool call                 │      │
│    │ e. SafetyGate.assess() with worldModel       │      │
│    │ f. Execute tool (or request confirmation)    │      │
│    │ g. LLM-generated StepObservation              │      │
│    │ h. worldModel.updateFromObservation(obs)     │      │
│    │ i. DestructiveActionDetector.checkSequence() │      │
│    └──────────────────────────────────────────────┘      │
│    Continue until task complete or step limit            │
└──────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│ 6. SESSION REFLECTION                                     │
│    worldModel.reflectOnSession(session)                  │
│    → update beliefs, preferences, episodic memory        │
│    → check goal completion                               │
│    → sync to companion devices if changed               │
└──────────────────────────────────────────────────────────┘
```

---

## 7. Companion Device Protocol

Your phone is the primary hub. Companion devices (tablet, car, web) connect to it:

```
Companion Device          Primary Hub (Phone)
       │                         │
       │──── authenticate ──────▶│ (shared secret, device fingerprint)
       │                         │
       │◀─── world model sync ───│ (read-only or read-write based on permissions)
       │                         │
       │──── query ─────────────▶│ (ask phone: "what's my mom's number?")
       │                         │
       │◀─── response ───────────│
```

**Sync Protocol:**
- Phone exposes a local REST API (or encrypted broadcast) for companions
- World model is partitioned: sensitive data (passwords, financial info) stays on phone
- Companions get: preferences, routines, non-sensitive memories, active goals
- Commands from companions go through SafetyGate on the phone first

**Device roles:**
- **Hub (Phone)**: Full world model, all tools, safety decisions, memory authority
- **Companion (Tablet/Car/Web)**: Read world model, send commands, receive updates
- **Offline companion**: Cached world model snapshot, limited execution until reconnected

---

## 8. Safety — World-Model-Augmented

SafetyGate gets smarter with the world model:

```kotlin
fun assess(
    tool: ToolCall,
    args: Map<String, String>,
    worldModel: PersonalWorldModel  // NEW: world model context
): RiskAssessment {
    // Base risk from tool type
    val baseRisk = riskDatabase[tool.name] ?: MEDIUM

    // User's risk tolerance from profile
    val userRiskTolerance = worldModel.personalProfile.riskTolerance

    // Learned safety preferences
    val learnedPreferences = worldModel.safetyPreferences

    // Context-aware escalation
    val context = worldModel.getRecentContext()
    val isFinancialContext = context.activeApps.any { it.category == FINANCIAL }
    val isSensitiveTime = context.timeSlot == TimeSlot.NIGHT // user might be tired

    // Belief-based: has user previously expressed privacy concerns?
    val privacyConcernLevel = worldModel.beliefEngine.getConfidence("concerned.about.privacy")

    return when {
        baseRisk == LOW → LOW
        baseRisk == MEDIUM && privacyConcernLevel > 0.7 → escalate(HIGH)
        tool.name in learnedPreferences.deniedTools → escalate(HIGH)
        isFinancialContext && tool.name in DANGEROUS_FINANCIAL_TOOLS → escalate(HIGH)
        userRiskTolerance == LOW → escalate(baseRisk)
        else → baseRisk
    }
}
```

**Safety audit log** is already implemented (v4 `safety_audit` table). What's new:
- `SafetyAuditEntity` gets a `worldModelVersion` field (snapshot of relevant beliefs at decision time)
- Pattern: DENIED actions increase confidence in `prefers.safe.${toolCategory}` belief
- Pattern: APPROVED actions on dangerous tools teach the agent it's OK when user is present

---

## 9. World Model Storage — Room Schema

```kotlin
// New tables added to AppDatabase v5

@Entity(tableName = "personal_profile")
data class PersonalProfileEntity(
    @PrimaryKey val id: String = "self",
    val name: String?,
    val communicationStyle: String,  // serialized enum
    val riskTolerance: String,
    val specialRequirements: String, // JSON array
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "beliefs")
data class BeliefEntity(
    @PrimaryKey val id: String,       // UUID
    val dimension: String,
    val statement: String,
    val confidence: Float,
    val evidenceCount: Int,
    val lastUpdated: Long
)

@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val id: String,
    val category: String,            // routing/tool/safety/display
    val key: String,
    val value: String,               // JSON
    val confidence: Float,           // how sure we are this preference is correct
    val lastUpdated: Long
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val timeSlot: String,            // MORNING/AFTERNOON/EVENING/NIGHT
    val dayOfWeek: Int?,              // null = everyday
    val typicalActivities: String,    // JSON
    val energyLevel: String,
    val interruptTolerance: String,
    val location: String?            // home/work/etc.
)

@Entity(tableName = "relationships")
data class RelationshipEntity(
    @PrimaryKey val id: String,
    val name: String,
    val relationshipType: String,
    val importance: Float,
    val contactFrequency: String,
    val context: String,
    val lastInteraction: Long
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val description: String,
    val createdAt: Long,
    val deadline: Long?,
    val status: String,              // ACTIVE/COMPLETED/ABANDONED/PARKED
    val progress: Float,
    val relatedMemoryIds: String     // JSON array
)

@Entity(tableName = "phone_state_cache")
data class PhoneStateCacheEntity(
    @PrimaryKey val id: String = "current",
    val foregroundApp: String,
    val appHierarchy: String,       // JSON array
    val screenType: String,
    val confidence: Float,
    val lastUpdated: Long
)
```

**Memory retrieval** is upgraded: instead of simple `getMemoriesForDateRange()`, we use a retrieval score:

```kotlin
fun retrieve(query: String, context: RetrievalContext): List<MemoryEntry> {
    // 1. Vector similarity on memory content (future: embeddings)
    // 2. Time decay: recent memories weighted higher
    // 3. Importance weight: stored importance field
    // 4. relevance to current context (app, time, location)
    // 5. Recency boost: same day last week preferred over arbitrary day

    val candidates = memoryDao.getAll() // or vector search when embeddings added
    return candidates
        .map { score(it, query, context) }
        .sortedByDescending { it.score }
        .take(10)
}
```

---

## 10. Iris Routing — World-Model-Driven

`IrisRouter` gets a major upgrade — routing is driven by the world model:

```kotlin
data class RoutingDecision(
    val profile: IrisProfile,        // REFLEX/FAST/SHARP/GENTLE/BALANCED/DEEP
    val temperature: Float,
    val style: String,              // brief/detailed/technical/conversational
    val depth: Int,                 // 1-5
    val reasoning: String           // why this routing was chosen
)

fun selectProfile(message: String, worldModel: PersonalWorldModel): RoutingDecision {
    val personalProfile = worldModel.personalProfile
    val preferences = worldModel.routingPreferences[getContextType(message)]
    val currentState = worldModel.getCurrentState()
    val activeGoals = worldModel.goalTracker.activeGoals

    // Base profile from preferences or fall back to message analysis
    val baseProfile = preferences?.preferredProfile ?: analyzeMessageType(message)

    // Adjust based on current state
    val adjustedProfile = when {
        currentState.energyLevel == LOW && personalProfile.communicationStyle != Terse ->
            baseProfile.copy(style = "brief") // user is tired, be concise
        activeGoals.isNotEmpty() && currentState.timeSlot == MORNING ->
            baseProfile.copy(depth = 3)       // morning energy, can handle complexity
        personalProfile.communicationStyle == Terse ->
            baseProfile.copy(style = "brief", temperature = 0.3f)
    }

    // Record routing decision for learning
    irisState.recordRoutingDecision(
        messagePreview = message.take(100),
        decision = adjustedProfile,
        outcome = null // filled in after session
    )

    return adjustedProfile
}
```

---

## 11. Streaming UI — Real-Time Agent Reasoning

**Problem**: User sees "Thinking..." then the complete response. They can't see the agent's reasoning.

**Solution**: Stream token-by-token with structured metadata:

```
[Thought] Let me check your calendar first...        ◀── agent thinking
[Action] → phone.read_calendar(date=today)          ◀── tool call
[Observation] You have 2 meetings today at 10am...  ◀── result
[Plan] I'll send them a quick SMS to confirm...      ◀── next step
```

**Implementation**: `BaseOpenAiProvider.chatCompletionStream()` already exists but is unused. We wire it up:

```kotlin
// AgentController.kt
suspend fun sendMessageStream(
    message: String,
    onToken: (String) -> Unit,
    onReasoning: (ReasoningChunk) -> Unit
) {
    // Use streaming parser
    provider.chatCompletionStream(request) { chunk ->
        when (chunk.type) {
            TOKEN -> onToken(chunk.text)
            REASONING -> onReasoning(chunk.reasoning)
            TOOL_CALL -> onReasoning(chunk.toolCall)
            OBSERVATION -> onReasoning(chunk.observation)
        }
    }
}
```

UI shows:
- Expanding "Thinking..." card with streamed tokens
- Color-coded chunks: thoughts (blue), actions (green), observations (gray)
- User can collapse/expand reasoning

---

## 12. Implementation Phases

### Phase 1: World Model Foundations (this session)
- [ ] Design doc (this file) — **done pending approval**
- [ ] Write implementation plan
- [ ] Implement `PersonalWorldModel` data structures + Room entities
- [ ] Upgrade `MemoryEngine` with new retrieval scoring
- [ ] Upgrade `AgentPromptBuilder` to inject personal context
- [ ] Implement `StepObservation` + `updateFromObservation()`
- [ ] Wire `SafetyGate` to world model for risk escalation
- [ ] Upgrade `IrisRouter` with world-model-driven routing
- [ ] Session reflection at `onSessionDone()`
- [ ] Learning from user feedback on `PendingConfirmation` decisions

### Phase 2: Companion Protocol
- [ ] Hub API on phone (local REST endpoint via NanoHTTPD or similar)
- [ ] Companion device SDK (Android + web)
- [ ] World model partitioning (sensitive vs non-sensitive)
- [ ] Sync protocol with conflict resolution
- [ ] Companion auth (shared secret)

### Phase 3: Streaming UI
- [ ] Wire streaming parser to UI
- [ ] Chat screen streaming rendering
- [ ] Reasoning card component
- [ ] Collapse/expand reasoning

### Phase 4: Planning & Self-Reflection
- [ ] Task decomposition (LLM-generated subgoal plans)
- [ ] Self-reflection: agent reviews its own errors
- [ ] Explicit goal tracking with deadline reminders
- [ ] Belief contradiction → proactive learning

---

## 13. Key Files to Modify

| File | Change |
|------|--------|
| `AppDatabase.kt` | Add 7 new tables for world model |
| `AgentController.kt` | Wire world model into all decisions, record feedback |
| `AgentLoopExecutor.kt` | Add `StepObservation` generation, call `worldModel.updateFromObservation()` |
| `AgentPromptBuilder.kt` | Inject personal context, preferences, active goals |
| `SafetyGate.kt` | Accept `PersonalWorldModel`, use beliefs for escalation |
| `MemoryEngine.kt` | New scoring-based retrieval, `updateFromObservation()` |
| `IrisRouter.kt` | World-model-driven routing |
| `PhoneStateModel.kt` | NEW: phone state understanding |
| `PersonalWorldModel.kt` | NEW: top-level model + all sub-components |
| `ChatScreen.kt` | Add streaming + reasoning card UI |

---

## 14. Success Metrics

- Agent correctly recalls user preferences without being told twice
- Safety decisions reflect user's demonstrated risk tolerance (not just rules)
- Routing adapts to user's communication style after 1-2 sessions
- Phone state model enables "I was just in this app" type memory
- Companion devices query the hub and get correct personalized responses