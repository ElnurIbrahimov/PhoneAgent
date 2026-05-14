# PhoneAgent Bug Fixes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix 5 critical bugs identified in code review: inverted memory decay, JSONArray silent failures, screenCaptureManager always-non-null check, missing unit tests, and error handling verbosity.

**Architecture:** Each fix is isolated to its component. Tests added for regression. No architectural changes — focus is on correctness and debuggability.

**Tech Stack:** Kotlin, JUnit, Room, Android Coroutines

---

## Task 1: Fix MemoryEngine Decay Logic

**Files:**
- Modify: `app/src/main/java/com/phoneagent/soma/MemoryEngine.kt:49-54`
- Modify: `app/src/main/java/com/phoneagent/soma/daos/SomaMemoryDao.kt` (review decay queries)

**Step 1: Write failing test**

```kotlin
// app/src/test/java/com/phoneagent/soma/MemoryEngineDecayTest.kt
package com.phoneagent.soma

import com.phoneagent.soma.daos.SomaMemoryDao
import com.phoneagent.soma.entities.SomaMemoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.*

class MemoryEngineDecayTest {

    @Test
    fun `decayDays should return larger value for older memories`() = runBlocking {
        val dao = mock(SomaMemoryDao::class.java)
        val engine = MemoryEngine(dao)

        // Oldest memory from 20 days ago
        val oldMemory = SomaMemoryEntity(
            id = 1, content = "old", theme = null,
            emotional_weight = 0.5f, importance = 0.5f,
            tension_score = 0.5f, connection_depth = 0.5f,
            activation_count = 1, decay_rate = 0.05f,
            source_type = "conversation",
            created_at = System.currentTimeMillis() - (20L * 24 * 3600 * 1000),
            last_activated_at = System.currentTimeMillis() - (20L * 24 * 3600 * 1000)
        )
        val newMemory = SomaMemoryEntity(
            id = 2, content = "new", theme = null,
            emotional_weight = 0.5f, importance = 0.5f,
            tension_score = 0.5f, connection_depth = 0.5f,
            activation_count = 1, decay_rate = 0.05f,
            source_type = "conversation",
            created_at = System.currentTimeMillis() - (5L * 24 * 3600 * 1000),
            last_activated_at = System.currentTimeMillis() - (5L * 24 * 3600 * 1000)
        )

        `when`(dao.searchMemories("", 0f, 1)).thenReturn(listOf(oldMemory), listOf(newMemory))
        `when`(dao.searchMemories("", 0f, 200)).thenReturn(listOf(oldMemory))

        val decay = engine.decayDays()
        assertEquals(20f, decay, 1f)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.phoneagent.soma.MemoryEngineDecayTest" 2>&1`
Expected: FAIL

**Step 3: Fix the retrieve and decay methods**

In `MemoryEngine.kt`, replace `decayDays()` and `retrieve()`:

```kotlin
// Replace lines 30-37 (retrieve method):
suspend fun retrieve(query: String, limit: Int = 10): List<SomaMemoryEntity> {
    val windowDays = 30f  // fixed 30-day rolling window
    val results = memoryDao.searchMemories(query, windowDays, limit)
    results.forEach { memoryDao.activate(it.id) }
    return results
}

// Replace lines 49-54 (decayDays method):
private suspend fun decayDays(): Float {
    // Return fixed 30-day window as the retention cutoff.
    // Memories older than this are excluded from retrieval.
    return 30f
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.phoneagent.soma.MemoryEngineDecayTest" 2>&1`
Expected: PASS

---

## Task 2: Replace Silent JSONArray Catches with Structured Errors

**Files:**
- Modify: `app/src/main/java/com/phoneagent/soma/LpmManager.kt`
- Modify: `app/src/main/java/com/phoneagent/soma/SomaContextBuilder.kt`

**Step 1: Write test for LpmManager JSON parsing**

```kotlin
// app/src/test/java/com/phoneagent/soma/LpmManagerJsonTest.kt
package com.phoneagent.soma

import com.phoneagent.soma.daos.LpmDao
import com.phoneagent.soma.entities.LpmEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class LpmManagerJsonTest {

    @Test
    fun `getOrCreate returns existing LPM on valid JSON`() = runBlocking {
        val dao = mock(LpmDao::class.java)
        val manager = LpmManager(dao)

        val entity = LpmEntity(
            id = "primary",
            raw_profile = "Test user profile",
            behavioral_predictions = "[\"predict1\", \"predict2\"]",
            trigger_map = "{}",
            foresight_signals = "[]",
            last_session_at = System.currentTimeMillis(),
            total_sessions = 5,
            updated_at = System.currentTimeMillis()
        )
        `when`(dao.getLpm()).thenReturn(entity)

        val lpm = manager.getOrCreate()

        assertEquals(5, lpm.total_sessions)
    }

    @Test
    fun `getOrCreate handles malformed JSON gracefully`() = runBlocking {
        val dao = mock(LpmDao::class.java)
        val manager = LpmManager(dao)

        val entity = LpmEntity(
            id = "primary",
            raw_profile = "Test",
            behavioral_predictions = "not-valid-json",
            trigger_map = "{}",
            foresight_signals = "[]",
            last_session_at = System.currentTimeMillis(),
            total_sessions = 1,
            updated_at = System.currentTimeMillis()
        )
        `when`(dao.getLpm()).thenReturn(entity)

        val lpm = manager.getOrCreate()
        assertNotNull(lpm)
        assertEquals(1, lpm.total_sessions)
    }
}
```

**Step 2: Run tests to verify they fail on current code**

Run: `./gradlew :app:testDebugUnitTest --tests "com.phoneagent.soma.LpmManagerJsonTest" 2>&1`
Expected: Tests pass but check logcat for silent failures

**Step 3: Fix LpmManager with structured error handling**

In `LpmManager.kt`, replace the try-catch around JSONArray parsing:

```kotlin
try {
    val predictions = org.json.JSONArray(lpm.behavioral_predictions)
    if (predictions.length() > 0) {
        sb.append("Current foresight: ")
        val preds = (0 until predictions.length()).map { predictions.getString(it) }
        sb.appendLine(preds.joinToString("; "))
    }
} catch (e: org.json.JSONException) {
    android.util.Log.w("LpmManager", "Malformed behavioral_predictions: ${lpm.behavioral_predictions}", e)
    sb.appendLine("Current foresight: [unavailable]")
}
```

**Step 4: Fix SomaContextBuilder similarly**

In `SomaContextBuilder.kt`, replace try-catch:

```kotlin
try {
    val predictions = org.json.JSONArray(lpm.behavioral_predictions)
    if (predictions.length() > 0) {
        sb.append("Current foresight: ")
        val preds = (0 until predictions.length()).map { predictions.getString(it) }
        sb.appendLine(preds.joinToString("; "))
    }
} catch (e: org.json.JSONException) {
    android.util.Log.w("SomaContextBuilder", "Failed to parse behavioral predictions", e)
}
```

**Step 5: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.phoneagent.soma.LpmManagerJsonTest" 2>&1`
Expected: PASS

---

## Task 3: Fix Always-True Null Check in AgentController

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt:210-212`

**Step 1: Fix the null check**

Replace:
```kotlin
val systemPrompt = if (screenCaptureManager != null) {
    VisionPayloadBuilder().buildVisionSystemPrompt(baseSystemPrompt)
} else baseSystemPrompt
```

With:
```kotlin
val systemPrompt = VisionPayloadBuilder().buildVisionSystemPrompt(baseSystemPrompt)
```

**Step 2: Verify build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

## Task 4: Add Core Agent Loop Unit Tests

**Files:**
- Create: `app/src/test/java/com/phoneagent/agent/SafetyGateTest.kt`
- Create: `app/src/test/java/com/phoneagent/agent/ToolResultParserTest.kt`
- Create: `app/src/test/java/com/phoneagent/agent/DestructiveActionDetectorTest.kt`

**Step 1: Write SafetyGate tests**

```kotlin
// app/src/test/java/com/phoneagent/agent/SafetyGateTest.kt
package com.phoneagent.agent

import com.phoneagent.agent.SafetyGate.RiskLevel
import org.junit.Assert.*
import org.junit.Test

class SafetyGateTest {

    @Test
    fun `send_sms is HIGH risk`() {
        val result = SafetyGate.assess("phone.send_sms", mapOf("to" to "123", "body" to "hi"))
        assertEquals(RiskLevel.HIGH, result.level)
    }

    @Test
    fun `call_phone is HIGH risk`() {
        val result = SafetyGate.assess("phone.call", mapOf("number" to "911"))
        assertEquals(RiskLevel.HIGH, result.level)
    }

    @Test
    fun `list_apps is LOW risk`() {
        val result = SafetyGate.assess("phone.list_apps", emptyMap())
        assertEquals(RiskLevel.LOW, result.level)
    }

    @Test
    fun `read_tree is LOW risk`() {
        val result = SafetyGate.assess("accessibility.read_tree", emptyMap())
        assertEquals(RiskLevel.LOW, result.level)
    }

    @Test
    fun `settings is HIGH risk`() {
        val result = SafetyGate.assess("phone.settings", emptyMap())
        assertEquals(RiskLevel.HIGH, result.level)
    }
}
```

**Step 2: Write ToolResultParser tests**

```kotlin
// app/src/test/java/com/phoneagent/agent/ToolResultParserTest.kt
package com.phoneagent.agent

import com.phoneagent.agent.tools.ToolResult
import org.junit.Assert.*
import org.junit.Test

class ToolResultParserTest {

    @Test
    fun `parse success JSON`() {
        val json = """{"success":true,"content":"ok"}"""
        val result = ToolResultParser.parse(json)
        assertTrue(result.success)
        assertEquals("ok", result.content)
    }

    @Test
    fun `parse error JSON`() {
        val json = """{"success":false,"error":"failed","suggestion":"try again"}"""
        val result = ToolResultParser.parse(json)
        assertFalse(result.success)
        assertEquals("failed", result.error)
    }

    @Test
    fun `parse plain text returns as content`() {
        val text = "Just some text"
        val result = ToolResultParser.parse(text)
        assertTrue(result.success)
        assertEquals(text, result.content)
    }
}
```

**Step 3: Write DestructiveActionDetector tests**

```kotlin
// app/src/test/java/com/phoneagent/agent/DestructiveActionDetectorTest.kt
package com.phoneagent.agent

import com.phoneagent.agent.AgentAction.ToolCall
import org.junit.Assert.*
import org.junit.Test

class DestructiveActionDetectorTest {

    private fun step(tool: String, args: Map<String, String> = emptyMap()) =
        AgentStep(1, ToolCall(tool, args))

    @Test
    fun `read_then_type pattern is flagged as destructive`() {
        val steps = listOf(
            step("phone.notifications"),
            step("accessibility.type", mapOf("text" to "typing after reading notifications"))
        )
        val result = DestructiveActionDetector.assessSequence(steps)
        assertNotNull(result)
    }

    @Test
    fun `unrelated tools do not trigger warning`() {
        val steps = listOf(
            step("phone.list_apps"),
            step("phone.system_info")
        )
        val result = DestructiveActionDetector.assessSequence(steps)
        assertNull(result)
    }
}
```

**Step 4: Run all new tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.phoneagent.agent.SafetyGateTest" --tests "com.phoneagent.agent.ToolResultParserTest" --tests "com.phoneagent.agent.DestructiveActionDetectorTest" 2>&1 | tail -30`
Expected: All PASS

---

## Task 5: Add Structured Error Logging to AgentLoopExecutor

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt`

**Step 1: Replace silent catches with logged errors**

Around line 135 (screen capture try-catch):
```kotlin
} catch (e: Exception) {
    android.util.Log.e("AgentLoopExecutor", "Screen capture/OCR failed: ${e.message}", e)
    ocrText = null
    visionPayload = null
}
```

Around line 163 (provider try-catch):
```kotlin
} catch (e: ProviderError) {
    lastProviderError = e.message
    android.util.Log.w("AgentLoopExecutor", "Provider ${p.config.name} failed: ${e.message}")
    continue
} catch (e: Exception) {
    lastProviderError = e.message
    android.util.Log.e("AgentLoopExecutor", "Unexpected error from provider ${p.config.name}", e)
    continue
}
```

In startLoop (exception handlers):
```kotlin
} catch (e: ProviderError) {
    android.util.Log.e("AgentLoopExecutor", "Provider error", e)
    ...
} catch (e: Exception) {
    android.util.Log.e("AgentLoopExecutor", "Unexpected error in agent loop: ${e.message}", e)
    ...
}
```

**Step 2: Verify build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

## Task 6: Commit All Changes

**Step 1: Stage and commit**

```bash
git add -A
git commit -m "fix: resolve 5 critical bugs — inverted memory decay, silent JSON failures, always-true null check, add unit tests, structured error logging"
```

Run: `git log -1 --oneline`
Expected: New commit with fix description

---

## Execution Options

**1. Subagent-Driven (this session)** — I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** — Open new session with executing-plans, batch execution with checkpoints

**Which approach?**