# Post-Review Hardening Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix the 6 most impactful issues found in code review: Keystore crash, error distinction, localhost provider, missing core tests, and safety-gate UX around unknown tools.

**Architecture:** Surgical fixes only. No refactoring of AgentController (would break too much). Each task is independent and testable.

**Tech Stack:** Kotlin, JUnit 4, Android Keystore, DataStore

---

### Task 1: Fix AndroidKeystoreSecretStore init crash

**Files:**
- Modify: `app/src/main/java/com/phoneagent/security/AndroidKeystoreSecretStore.kt`

**Problem:** If `KeyStore.getInstance("AndroidKeyStore")` fails, the class init block crashes with `IllegalStateException`, taking down the app on startup.

**Fix:** Lazy-init the keystore. Provide a fallback `isAvailable(): Boolean` check so the controller can handle missing keystore gracefully instead of crashing.

**Step 1: Refactor keystore initialization**

Replace the eager property init with lazy init and add `isAvailable()`:

```kotlin
class AndroidKeystoreSecretStore(context: Context) : SecretStore {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var keyStore: KeyStore? = null
    private var initialized = false

    @Synchronized
    private fun ensureInitialized(): Boolean {
        if (initialized) return keyStore != null
        initialized = true
        keyStore = runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        }.getOrNull()
        if (keyStore != null && !keyStore!!.containsAlias(KEY_ALIAS)) {
            runCatching { generateKey() }
        }
        return keyStore != null
    }

    fun isAvailable(): Boolean {
        return ensureInitialized()
    }

    private fun requireKeyStore(): KeyStore {
        return keyStore ?: throw IllegalStateException("Android Keystore is unavailable")
    }
```

Then update `getSecretKey()` to use `requireKeyStore()`:

```kotlin
private fun getSecretKey(): SecretKey {
    val ks = requireKeyStore()
    return (ks.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
}
```

And guard `storeSecret`/`getSecret`/`hasSecret`/`deleteSecret` with `ensureInitialized()`:

```kotlin
override suspend fun storeSecret(key: String, value: String) = withContext(Dispatchers.Default) {
    if (!ensureInitialized()) return@withContext
    // ... existing body
}
```

**Step 2: Verify no compilation errors**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 2: Distinguish error types in AgentLoopExecutor

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt`

**Problem:** All provider errors (`AuthenticationError`, `NetworkError`, etc.) are caught identically with just `e.message`. The user sees unhelpful messages like "Invalid API key" with no guidance.

**Fix:** Show actionable error messages per error subtype.

**Step 1: Update the catch blocks**

Replace lines 55-63 with:

```kotlin
} catch (e: ProviderError) {
    val message = when (e) {
        is com.phoneagent.providers.ProviderError.AuthenticationError ->
            "Authentication failed. Check your API key in Settings."
        is com.phoneagent.providers.ProviderError.NetworkError ->
            "Network error: ${e.message}. Check your connection and try again."
        is com.phoneagent.providers.ProviderError.RateLimitError ->
            "Rate limited. Wait a moment and try again."
        is com.phoneagent.providers.ProviderError.ServerError ->
            "Server error: ${e.message}. The provider may be down."
        else -> e.message ?: "Unknown provider error"
    }
    applyState { it.copy(isLoading = false, agentStepStatus = null, error = message) }
    taskHistoryManager.updateTaskStatus(taskId, "failed", message)
    onComplete()
} catch (e: Exception) {
    val message = e.message ?: "Unexpected error"
    applyState { it.copy(isLoading = false, agentStepStatus = null, error = message) }
    taskHistoryManager.updateTaskStatus(taskId, "failed", message)
    onComplete()
}
```

Same pattern for the identical catch blocks in `resumeAfterConfirmation` (line 205).

**Step 2: Build and verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 3: Remove OpenCodeGo (localhost) from enabled defaults

**Files:**
- Modify: `app/src/main/java/com/phoneagent/providers/ProviderRepository.kt`
- Modify: `app/src/main/java/com/phoneagent/providers/OpenCodeGoDefaults.kt`

**Problem:** `OpenCodeGoDefaults` has `isEnabled = true` and points to `localhost:8080`, which fails on any real device. Users see a broken provider by default.

**Fix:** Set `isEnabled = false` in `OpenCodeGoDefaults.DEFAULT_CONFIG`. Keep the config available for local dev but disabled by default.

**Step 1: Disable OpenCodeGo by default**

Change `OpenCodeGoDefaults.kt` line 21:
```kotlin
isEnabled = false,  // was true - localhost will fail on real devices
```

**Step 2: Build and verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 4: Add SafetyGate unit tests

**Files:**
- Create: `app/src/test/java/com/phoneagent/agent/SafetyGateTest.kt`

**Goal:** Cover all risk levels. SafetyGate is pure logic with no Android dependencies.

**Step 1: Write the tests**

```kotlin
package com.phoneagent.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyGateTest {

    @Test
    fun `low risk tools return LOW level`() {
        val result = SafetyGate.assess("browser.read_page", emptyMap())
        assertEquals(SafetyGate.RiskLevel.LOW, result.level)
    }

    @Test
    fun `high risk tools return HIGH level`() {
        val result = SafetyGate.assess("phone.send_sms", mapOf("to" to "123", "message" to "hi"))
        assertEquals(SafetyGate.RiskLevel.HIGH, result.level)

        val result2 = SafetyGate.assess("phone.call", mapOf("number" to "911"))
        assertEquals(SafetyGate.RiskLevel.HIGH, result2.level)

        val result3 = SafetyGate.assess("phone.open_app", mapOf("app" to "com.example"))
        assertEquals(SafetyGate.RiskLevel.HIGH, result3.level)
    }

    @Test
    fun `medium risk tools with safe args return LOW`() {
        val result = SafetyGate.assess("accessibility.back", emptyMap())
        assertEquals(SafetyGate.RiskLevel.LOW, result.level)
    }

    @Test
    fun `medium risk tools with sensitive args return MEDIUM`() {
        val result = SafetyGate.assess("browser.click_text", mapOf("text" to "Submit"))
        assertEquals(SafetyGate.RiskLevel.MEDIUM, result.level)
    }

    @Test
    fun `click_selector with password selector returns MEDIUM`() {
        val result = SafetyGate.assess("browser.click_selector", mapOf("selector" to "#password-field"))
        assertEquals(SafetyGate.RiskLevel.MEDIUM, result.level)
    }

    @Test
    fun `click_selector with safe selector returns LOW`() {
        val result = SafetyGate.assess("browser.click_selector", mapOf("selector" to ".nav-link"))
        assertEquals(SafetyGate.RiskLevel.LOW, result.level)
    }

    @Test
    fun `type_into_selector with credit card selector returns MEDIUM`() {
        val result = SafetyGate.assess("browser.type_into_selector", mapOf("selector" to "#credit-card"))
        assertEquals(SafetyGate.RiskLevel.MEDIUM, result.level)
    }

    @Test
    fun `type_into_focused with long text returns MEDIUM`() {
        val longText = "a".repeat(201)
        val result = SafetyGate.assess("browser.type_into_focused", mapOf("text" to longText))
        assertEquals(SafetyGate.RiskLevel.MEDIUM, result.level)
    }

    @Test
    fun `type_into_focused with short text returns LOW`() {
        val result = SafetyGate.assess("browser.type_into_focused", mapOf("text" to "hello"))
        assertEquals(SafetyGate.RiskLevel.LOW, result.level)
    }

    @Test
    fun `unknown tool returns HIGH risk as fail-safe`() {
        val result = SafetyGate.assess("unknown.dangerous_tool", emptyMap())
        assertEquals(SafetyGate.RiskLevel.HIGH, result.level)
    }

    @Test
    fun `phone screenshot returns LOW risk`() {
        val result = SafetyGate.assess("phone.screenshot", emptyMap())
        assertEquals(SafetyGate.RiskLevel.LOW, result.level)
    }

    @Test
    fun `phone system info returns LOW risk`() {
        val result = SafetyGate.assess("phone.system_info", emptyMap())
        assertEquals(SafetyGate.RiskLevel.LOW, result.level)
    }
}
```

**Step 2: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.phoneagent.agent.SafetyGateTest"`
Expected: All 12 tests PASS

---

### Task 5: Add ToolRegistry unit tests

**Files:**
- Create: `app/src/test/java/com/phoneagent/agent/ToolRegistryTest.kt`

**Step 1: Write the tests**

```kotlin
package com.phoneagent.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ToolRegistryTest {

    private val registry = ToolRegistry()

    @Test
    fun `register and retrieve tool`() {
        val tool = FakeTool("test_tool", "A test tool")
        registry.register(tool)
        assertEquals(tool, registry.getTool("test_tool"))
    }

    @Test
    fun `getTool returns null for unknown tool`() {
        assertNull(registry.getTool("nonexistent"))
    }

    @Test
    fun `listTools returns all registered tools`() {
        registry.register(FakeTool("tool1", "desc1"))
        registry.register(FakeTool("tool2", "desc2"))
        assertEquals(2, registry.listTools().size)
    }

    @Test
    fun `unregister removes tool`() {
        val tool = FakeTool("temp", "temp")
        registry.register(tool)
        registry.unregister("temp")
        assertNull(registry.getTool("temp"))
    }

    @Test
    fun `clear removes all tools`() {
        registry.register(FakeTool("a", "a"))
        registry.register(FakeTool("b", "b"))
        registry.clear()
        assertEquals(0, registry.listTools().size)
    }

    @Test
    fun `registerAll adds multiple tools`() {
        val tools = listOf(FakeTool("a", "a"), FakeTool("b", "b"))
        registry.registerAll(tools)
        assertEquals(2, registry.listTools().size)
    }

    class FakeTool(override val name: String, override val description: String) : Tool {
        override suspend fun execute(arguments: Map<String, String>): String = "ok"
    }
}
```

**Step 2: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.phoneagent.agent.ToolRegistryTest"`
Expected: All 6 tests PASS

---

### Task 6: Disable OpenCodeGo default in ProviderRepository

**Files:**
- Modify: `app/src/main/java/com/phoneagent/providers/OpenCodeGoDefaults.kt`

**Already done in Task 3.** This is a verification step to ensure the change flows correctly through the app.

**Step 1: Verify the default provider list no longer includes OpenCodeGo as active**

The `getDefaultProvider()` method in `ProviderRepository` picks the first enabled provider. After Task 3, OpenCodeGo is disabled, so it won't be selected.

**Step 2: Run the full test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: All tests PASS (3 test classes: BrowserUrlNormalizerTest + SafetyGateTest + ToolRegistryTest)

---

## Summary

| Task | Type | Impact |
|------|------|--------|
| 1. Keystore crash fix | Bug fix | Prevents app crash on devices with broken keystore |
| 2. Error distinction | UX improvement | Users see actionable error messages instead of raw exception text |
| 3. Disable OpenCodeGo | Bug fix | Removes broken localhost provider from defaults |
| 4. SafetyGate tests | Quality | 12 tests covering all risk levels |
| 5. ToolRegistry tests | Quality | 6 tests covering register/get/list/unregister/clear |
| 6. Verification | Validation | Full test suite run |

**Total:** 3 bug fixes, 1 UX improvement, 18 new unit tests. No architectural changes. All fixes are backward-compatible.
