# SOMA Memory System — Ultra Implementation Plan

> **Goal:** Merge Kira's SOMA memory architecture into PhoneAgent as native Kotlin/Compose. 6 new Room tables, IRIS routing, DAEMON background mind, GROUND observer, Telegram push, MemoryBrowser UI.

**Architecture:** Additive — zero existing code removed. New package `com.phoneagent.soma/` for all memory systems. New package `com.phoneagent.iris/` for routing. WorkManager for background tasks. DB migration v2→v3.

**Tech Stack:** Kotlin, Room, Jetpack Compose, WorkManager, OkHttp (Telegram API), Coroutines + Flow

---

## Phase 1: Database Foundation

### Task 1.1: Room entities
- Create: `app/src/main/java/com/phoneagent/soma/entities/BeliefEntity.kt`
- Create: `app/src/main/java/com/phoneagent/soma/entities/MemoryEntity.kt` (renamed, enhanced version)
- Create: `app/src/main/java/com/phoneagent/soma/entities/MemSceneEntity.kt`
- Create: `app/src/main/java/com/phoneagent/soma/entities/ObservationEntity.kt`
- Create: `app/src/main/java/com/phoneagent/soma/entities/DaemonLogEntity.kt`
- Create: `app/src/main/java/com/phoneagent/soma/entities/LpmEntity.kt`

### Task 1.2: DAOs
- Create: `app/src/main/java/com/phoneagent/soma/daos/BeliefDao.kt`
- Create: `app/src/main/java/com/phoneagent/soma/daos/MemoryDao.kt`
- Create: `app/src/main/java/com/phoneagent/soma/daos/MemSceneDao.kt`
- Create: `app/src/main/java/com/phoneagent/soma/daos/ObservationDao.kt`
- Create: `app/src/main/java/com/phoneagent/soma/daos/DaemonLogDao.kt`
- Create: `app/src/main/java/com/phoneagent/soma/daos/LpmDao.kt`

### Task 1.3: Database migration
- Modify: `app/src/main/java/com/phoneagent/memory/AppDatabase.kt` — add all 6 tables, version 2→3

---

## Phase 2: SOMA Core Systems

### Task 2.1: Belief Engine
- Create: `app/src/main/java/com/phoneagent/soma/BeliefEngine.kt` — Bayesian confidence updates, observe/update/retrieve

### Task 2.2: Memory Engine
- Create: `app/src/main/java/com/phoneagent/soma/MemoryEngine.kt` — store, retrieve (decay-adjusted scoring), activate (bump activation_count), forget (below threshold)

### Task 2.3: LPM Manager
- Create: `app/src/main/java/com/phoneagent/soma/LpmManager.kt` — update after session (LLM call), retrieve profile for context

### Task 2.4: MemScenes Engine
- Create: `app/src/main/java/com/phoneagent/soma/MemScenesEngine.kt` — LLM clusters recent memories into themes after session

### Task 2.5: SomaContextBuilder
- Create: `app/src/main/java/com/phoneagent/soma/SomaContextBuilder.kt` — builds the system prompt augmentation from LPM + active beliefs + recent scenes + relevant memories

---

## Phase 3: IRIS Response Router

### Task 3.1: State classifier
- Create: `app/src/main/java/com/phoneagent/iris/IrisState.kt` — tension, energy, hour, message_type classification

### Task 3.2: IRI Router
- Create: `app/src/main/java/com/phoneagent/iris/IrisRouter.kt` — 6 profiles (REFLEX/FAST/SHARP/GENTLE/BALANCED/DEEP), routing logic, history learning

### Task 3.3: Integration
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt` — IRIS pre-processing before sendMessage

---

## Phase 4: DAEMON Background Mind

### Task 4.1: DaemonWorker
- Create: `app/src/main/java/com/phoneagent/soma/daemon/DaemonWorker.kt` — WorkManager periodic task, 8-min interval

### Task 4.2: Inner monologue
- Create: `app/src/main/java/com/phoneagent/soma/daemon/DaemonMind.kt` — builds context from SOMA, calls LLM, writes thought to daemon_log

### Task 4.3: Telegram push
- Create: `app/src/main/java/com/phoneagent/soma/daemon/TelegramPush.kt` — HTTP POST to Telegram Bot API for significant thoughts

### Task 4.4: Integration
- Modify: `app/src/main/java/com/phoneagent/PhoneAgentApplication.kt` — schedule DaemonWorker on app start

---

## Phase 5: GROUND Observer

### Task 5.1: GroundWorker
- Create: `app/src/main/java/com/phoneagent/soma/ground/GroundWorker.kt` — WorkManager periodic task, 60s interval

### Task 5.2: Observation pipeline
- Create: `app/src/main/java/com/phoneagent/soma/ground/GroundObserver.kt` — screenshot → OCR → LLM classification → write to observations table

---

## Phase 6: UI

### Task 6.1: MemoryBrowser screen
- Create: `app/src/main/java/com/phoneagent/ui/MemoryBrowserScreen.kt` — view beliefs, memories, scenes, LPM profile

### Task 6.2: Navigation
- Modify: `app/src/main/java/com/phoneagent/ui/AppRoot.kt` — add "memory" route
- Modify: `app/src/main/java/com/phoneagent/ui/MainScreen.kt` — add Memory quick action

---

## Phase 7: Wiring

### Task 7.1: AgentController wiring
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt` — SOMA repo, IRIS router, session lifecycle hooks, LPM update on task completion

### Task 7.2: Session hooks
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt` — after each task completion, trigger MemScenes + LPM update + belief updates

---

## Build Order

```
T1 (DB entities) → T2 (DAOs) → T3 (migration & AppDatabase)
  → T4 (BeliefEngine) → T5 (MemoryEngine) → T6 (LpmManager + MemScenesEngine + SomaContextBuilder)
    → T7 (IrisState + IrisRouter)
      → T8 (DaemonWorker + DaemonMind + TelegramPush)
        → T9 (GroundWorker + GroundObserver)
          → T10 (MemoryBrowserScreen + navigation)
            → T11 (AgentController wiring + session hooks)

Each task builds on the previous. Sequential.
```
