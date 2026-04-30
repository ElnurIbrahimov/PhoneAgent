# PhoneAgent Ultra-Review Fixes

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix all critical (5) + high (12) + highest-impact medium findings from the 5-agent ultra review.

**Architecture:** 8 tasks touching ~22 files. Groups related fixes by file proximity. No architecture refactors.

**Tech Stack:** Kotlin, Jetpack Compose, Android Keystore, Room, OkHttp, Coroutines

---

### Task 1: Provider fixes — vision amnesia + double body consumption + error mapping

**Files:**
- Modify: `app/src/main/java/com/phoneagent/providers/BaseOpenAiProvider.kt`
- Modify: `app/src/main/java/com/phoneagent/perception/VisionPayloadBuilder.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt`

**C1: Vision payload dropped step history.** When `visionPayload` is non-null, `buildRequestBody` replaces the entire user message with just the image + context text. The `loopMessage` with full step history is discarded. Fix: include both the text (step history) and the image in the content array.

**C2: Response body consumed twice.** `body?.string()` called on line 41 for error check then again on line 43 for parsing. Second call returns empty string.

**C3: `detail:"high"` wastes tokens.** Switch to `"detail":"low"` which is 85 tokens flat vs 6800+ for high on a 1080p screenshot.

**Step 1: Fix C2 — double body consumption in chatCompletion**

In `BaseOpenAiProvider.kt`, replace lines 38-48:
```kotlin
        try {
            client.newCall(httpRequest).execute().use { response ->
                val bodyString = response.body?.string() ?: throw ProviderError.UnknownError("Empty response")
                if (!response.isSuccessful) {
                    throw mapHttpError(response.code, bodyString)
                }
                parseResponse(bodyString)
            }
        }
```

Applied edit:
```
old: 38-48 (entire try block)
new: capture bodyString once, then check success + parse
```

**Step 2: Fix C1 — Vision payload must include step history text**

In `BaseOpenAiProvider.kt`, find `buildRequestBody` (around line 82). Replace the vision payload branch:
```kotlin
        if (request.visionPayload != null) {
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray(request.visionPayload))
            })
        }
```
With:
```kotlin
        if (request.visionPayload != null) {
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", request.message)
                    })
                    val visionArr = JSONArray(request.visionPayload)
                    for (i in 0 until visionArr.length()) {
                        put(visionArr.get(i))
                    }
                })
            })
        }
```

**Step 3: Fix C3 — Switch vision detail from "high" to "low"**

In `VisionPayloadBuilder.kt`, lines 19 and 35: change `put("detail", "high")` to `put("detail", "low")` (both `buildVisionPayload` and `buildVisionContextPayload`).

**Step 4: Update VisionPayloadBuilder to not embed context in image text**

Now that step history is sent separately, `buildVisionContextPayload` can simplify. Change line 29:
```kotlin
put("text", "Current phone screen. Use this to understand the UI state. $context")
```
To:
```kotlin
put("text", "Current phone screen. Use this with the message below to understand the UI state.")
```

**Step 5: Add missing HTTP error codes**

In `BaseOpenAiProvider.kt`, `mapHttpError` method, add before `else`:
```kotlin
            403 -> ProviderError.AuthenticationError("Access denied. Check your API subscription.")
            404 -> ProviderError.InvalidRequestError("Endpoint not found. Check your base URL and model name.")
            408 -> ProviderError.NetworkError("Request timed out. The server took too long to respond.")
```

**Step 6: Add finish_reason handling**

In `BaseOpenAiProvider.kt`, `parseResponse`, after extracting `content`:
```kotlin
        val finishReason = choices.getJSONObject(0).optString("finish_reason", "stop")
        if (finishReason == "content_filter") {
            throw ProviderError.InvalidRequestError("Content filtered by provider safety system.")
        }
```

**Step 7: Commit**

```
git add app/src/main/java/com/phoneagent/providers/BaseOpenAiProvider.kt app/src/main/java/com/phoneagent/perception/VisionPayloadBuilder.kt
git commit -m "fix: vision payload keeps step history, fix double body consumption, switch to detail:low"
```

---

### Task 2: Parse fix + SafetyGate fix

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentStep.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/SafetyGate.kt`

**C3: `getString("content")` throws on JSON null.** `json.has("content")` returns true for null values, then `getString` throws.

**C4: `phone.settings` is LOW risk.** Move to HIGH.

**Step 1: Fix null-safe content extraction**

In `AgentStep.kt`, line 38-41, replace:
```kotlin
            "final_answer" -> {
                if (!json.has("content")) {
                    AgentAction.ParseError("final_answer is missing required 'content' field.")
                } else {
                    AgentAction.FinalAnswer(json.getString("content"))
                }
            }
```
With:
```kotlin
            "final_answer" -> {
                val content = json.optString("content", null)
                if (content == null || json.isNull("content")) {
                    AgentAction.ParseError("final_answer is missing required 'content' field.")
                } else {
                    AgentAction.FinalAnswer(content)
                }
            }
```

**Step 2: Move phone.settings to highRiskTools**

In `SafetyGate.kt`, remove `"phone.settings"` from `lowRiskTools` (line 19) and add it to `highRiskTools` (line 22):
```kotlin
    private val highRiskTools = setOf("phone.open_app", "phone.send_sms", "phone.call", "phone.settings")
```

And remove from lowRiskTools (line 19 should no longer end with `"phone.settings"`):
```kotlin
    private val lowRiskTools = setOf(
        "browser.open_url", "browser.read_page", "browser.read_metadata",
        "browser.scroll", "browser.back", "browser.reload",
        "phone.list_apps", "phone.system_info", "phone.notifications",
        "phone.screenshot", "accessibility.read_tree", "accessibility.foreground_app"
    )
```

**Step 3: Commit**

```
git add app/src/main/java/com/phoneagent/agent/AgentStep.kt app/src/main/java/com/phoneagent/agent/SafetyGate.kt
git commit -m "fix: null-safe content parsing, move phone.settings to high risk"
```

---

### Task 3: Browser security — JS injection + SSRF + SafeBrowsing

**Files:**
- Modify: `app/src/main/java/com/phoneagent/browser/DomActionExecutor.kt`
- Modify: `app/src/main/java/com/phoneagent/browser/BrowserUrlNormalizer.kt`
- Modify: `app/src/main/java/com/phoneagent/browser/AgentWebView.kt`

**C5: Unsanitized LLM arguments into WebView JS.** Text/selectors injected directly into `evaluateJavascript()`.

**H1: No SSRF protection.** Loopback/private IPs allowed.

**H2: WebView JS callbacks hang.** No `withTimeout` in BrowserTool.

**Step 1: Sanitize JS arguments in DomActionExecutor**

In `DomActionExecutor.kt`, add a utility function at the top of the `object`:
```kotlin
    private fun sanitizeJsString(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("</script>", "<\\/script>")
            .replace("</", "<\\/")
    }
```

Then, in each function (`clickText`, `clickSelector`, `typeIntoSelector`, `typeIntoFocused`), before putting the text/selector into `JSONArray`, wrap with `sanitizeJsString()`:

For `clickText`: `JSONArray().apply { put(sanitizeJsString(text)) }`
For `clickSelector`: `JSONArray().apply { put(sanitizeJsString(selector)) }`
For `typeIntoSelector`: `JSONArray().apply { put(sanitizeJsString(selector)); put(sanitizeJsString(text)) }`
For `typeIntoFocused`: `JSONArray().apply { put(sanitizeJsString(text)) }`

**Step 2: Add SSRF protection to BrowserUrlNormalizer**

Before returning the URL, add a host check helper. Add to the object:
```kotlin
    private fun isInternalHost(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "0.0.0.0") return true
        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.16.")) return true
        if (host.startsWith("169.254.") || host.startsWith("fc") || host.startsWith("fd")) return true
        return false
    }
```

Then in `normalize()`, before the `if (trimmed.startsWith("about:"))` line (line 22), add:
```kotlin
        if (trimmed.contains("://")) {
            try {
                val host = java.net.URI(trimmed).host ?: return null
                if (isInternalHost(host)) return null
            } catch (_: Exception) {
                return null
            }
        }
```

**Step 3: Add tel/mailto/sms as allowed special schemes**

Add at top of object:
```kotlin
    private val ALLOWED_SPECIAL_SCHEMES = setOf("tel", "mailto", "sms", "geo")
```

In `normalize()`, after the `DANGEROUS_SCHEMES` check (after line 13), add:
```kotlin
            if (scheme in ALLOWED_SPECIAL_SCHEMES) return trimmed
```

**Step 4: Add SafeBrowsing and mixedContentMode to AgentWebView**

In `AgentWebView.kt`, in the `settings.apply` block (after line 48 `allowContentAccess = false`):
```kotlin
                    safeBrowsingEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
```

Need to add import: `import android.webkit.WebSettings` (already imported, check).

**Step 5: Commit**

```
git add app/src/main/java/com/phoneagent/browser/DomActionExecutor.kt app/src/main/java/com/phoneagent/browser/BrowserUrlNormalizer.kt app/src/main/java/com/phoneagent/browser/AgentWebView.kt
git commit -m "fix: sanitize JS arguments, SSRF protection, SafeBrowsing, allow tel/mailto/sms"
```

---

### Task 4: Tool system hardening — a11y safety + WebView timeouts + ToolRegistry thread safety

**Files:**
- Modify: `app/src/main/java/com/phoneagent/browser/BrowserTool.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/ToolRegistry.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/tools/AccessibilityReadTreeTool.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/tools/AccessibilityTapTextTool.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/tools/AccessibilityTapAtTool.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/tools/AccessibilitySwipeTool.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/tools/AccessibilityTypeTool.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/tools/AccessibilityBackTool.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/tools/AccessibilityHomeTool.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/tools/AccessibilityForegroundTool.kt`

**H2: BrowserTool WebView callbacks hang indefinitely.**
**H3: AccessibilityService use-after-destroy.**
**H4: ToolRegistry no thread safety.**

**Step 1: Add timeouts to BrowserTool JS callback calls**

In `BrowserTool.kt`, wrap each `suspendCancellableCoroutine` block with `withTimeout(10_000)`.

For `readPage` (around line 79-85):
```kotlin
    private suspend fun readPage(toolName: String): String = withContext(Dispatchers.Main) {
        val webView = BrowserSessionManager.getActiveWebView()
            ?: return@withContext errorResult(toolName, "Browser not active.",
                "Use browser.open_url to launch the browser first.")
        withTimeout(10_000) {
            suspendCancellableCoroutine { continuation ->
                PageExtractor.extractText(webView) { text ->
                    continuation.resume(successResult(toolName, text.take(8000)))
                }
            }
        }
    }
```

Apply same pattern to: `readMetadata`, `readSummary`, `clickText`, `clickSelector`, `typeIntoSelector`, `typeIntoFocused`, `scroll`.

Import `kotlinx.coroutines.withTimeout` if not already present.

**Step 2: Make ToolRegistry thread-safe**

Replace `mutableMapOf` with `ConcurrentHashMap`:
```kotlin
import java.util.concurrent.ConcurrentHashMap

class ToolRegistry {
    private val tools = ConcurrentHashMap<String, Tool>()

    fun register(tool: Tool) { tools[tool.name] = tool }
    fun unregister(name: String) { tools.remove(name) }
    fun getTool(name: String): Tool? = tools[name]
    fun listTools(): List<Tool> = tools.values.toList()
    fun clear() = tools.clear()
    fun registerAll(toolsList: List<Tool>) { toolsList.forEach { register(it) } }
}
```

**Step 3: Add accessibility service lifecycle guards**

In each accessibility tool (8 files), wrap the service access in a try-catch for runtime exceptions. Pattern:
```kotlin
    override suspend fun execute(arguments: Map<String, String>): String {
        return try {
            val service = AgentAccessibilityService.instance
                ?: return ToolResult.error(name, "Accessibility service not running. Enable it in Settings > Accessibility.",
                    "Tell the user to enable it in Settings > Accessibility > PhoneAgent")
            // existing logic...
        } catch (e: RuntimeException) {
            ToolResult.error(name, "Accessibility service disconnected. Ask the user to re-enable it.",
                "The accessibility service may have been stopped by the system. The user should re-enable it in Settings.")
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Unknown accessibility error")
        }
    }
```

All 8 tools need this. Files are:
- `AccessibilityReadTreeTool.kt`
- `AccessibilityTapTextTool.kt`
- `AccessibilityTapAtTool.kt`
- `AccessibilitySwipeTool.kt`
- `AccessibilityTypeTool.kt`
- `AccessibilityBackTool.kt`
- `AccessibilityHomeTool.kt`
- `AccessibilityForegroundTool.kt`

**Step 4: Commit**

```
git add app/src/main/java/com/phoneagent/browser/BrowserTool.kt app/src/main/java/com/phoneagent/agent/ToolRegistry.kt app/src/main/java/com/phoneagent/agent/tools/Accessibility*.kt
git commit -m "fix: WebView JS timeouts, thread-safe ToolRegistry, a11y lifecycle guards"
```

---

### Task 5: AgentLoopExecutor fixes — speak on main + smart screenshots + error JSON + steps on failure

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt`

**H5: `onSpeak` called from IO dispatcher.** TTS needs main thread.
**H6: Screenshot sent every step.** Only needed when UI changed.
**M: Duplicate error JSON builder.**
**M: Steps not persisted on failure.**

**Step 1: Fix onSpeak to use Dispatchers.Main**

In `AgentLoopExecutor.kt`, line 221. Replace:
```kotlin
        onSpeak?.invoke(answer.take(500))
```
With:
```kotlin
        onSpeak?.let { speak ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                speak(answer.take(500))
            }
        }
```

**Step 2: Smart screenshot — only send every 3 steps or after UI-modifying tools**

In `executeSteps()`, add a tracking variable. In the while loop, before the screenshot capture block (around line 97):
```kotlin
            val uiModifyingTools = setOf("phone.open_app", "phone.settings", "accessibility.tap_text",
                "accessibility.tap_at", "accessibility.swipe", "accessibility.back",
                "accessibility.home", "browser.open_url", "browser.click_text",
                "browser.click_selector", "accessibility.type")
            val lastStep = steps.lastOrNull()
            val uiChanged = lastStep?.action is AgentAction.ToolCall && lastStep.action.tool in uiModifyingTools
            val shouldSendScreenshot = stepsTaken <= 1 || uiChanged || stepsTaken % 3 == 0
```

Then wrap the vision capture block with `if (shouldSendScreenshot)`:
```kotlin
            val visionPayload = if (shouldSendScreenshot) {
                try { ... existing capture code ... } catch (_: Exception) { null }
            } else null
```

**Step 3: Remove duplicate error JSON builder, use ToolResult.error directly**

In three places where `buildErrorJson(tool, error, suggestion)` is called, replace with:
```kotlin
ToolResult.error(tool, error, suggestion)
```

Remove the `buildErrorJson` method entirely (lines ~287-295).

**Step 4: Persist steps on failure too**

In the error catch blocks of `startLoop()` (ProviderError and Exception), before `applyState` and `onComplete`, add:
```kotlin
                taskHistoryManager.recordSteps(taskId, steps)
```

**Step 5: Commit**

```
git add app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt
git commit -m "fix: onSpeak on main, smart screenshots, remove duplicate JSON builder, persist steps on failure"
```

---

### Task 6: AgentController fixes — TOCTOU + vision system prompt + pending confirmation crash recovery

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt`

**H7: `loopRunning` TOCTOU race.** Use `AtomicBoolean`.
**H9: PendingConfirmation cleared before tool execution.** If crash, unrecoverable.
**H10: Vision system prompt never called.** Wire it.

**Step 1: Fix TOCTOU with AtomicBoolean**

In `AgentController.kt`:
- Remove `@Volatile private var loopRunning = false`
- Add: `private val loopRunning = java.util.concurrent.atomic.AtomicBoolean(false)`

Replace `if (loopRunning)` with `if (!loopRunning.compareAndSet(false, true))`
Replace `loopRunning = true` with (done by compareAndSet)
Replace `loopRunning = false` with `loopRunning.set(false)`

**Step 2: Wire vision system prompt**

In `AgentController.kt`, `sendMessage()`, after `AgentPromptBuilder.buildSystemPrompt(toolRegistry.listTools())`:
```kotlin
        val baseSystemPrompt = AgentPromptBuilder.buildSystemPrompt(toolRegistry.listTools())
        val systemPrompt = if (screenCaptureManager != null) {
            VisionPayloadBuilder().buildVisionSystemPrompt(baseSystemPrompt)
        } else baseSystemPrompt
```

Need to add import: `import com.phoneagent.perception.VisionPayloadBuilder`

**Step 3: PendingConfirmation crash recovery**

In `AgentLoopExecutor.kt`, `resumeAfterConfirmation`, wrap tool execution in try-catch and restore pendingConfirmation on failure. Replace lines 231-234:
```kotlin
            if (approved) {
                applyState { it.copy(isLoading = true, agentStepStatus = "Calling tool: ${pending.toolName}", pendingConfirmation = null) }
                val observation = executeTool(pending.toolName, pending.args)
                steps.add(AgentStep(steps.size + 1, AgentAction.ToolCall(pending.toolName, pending.args), observation))
```

With:
```kotlin
            if (approved) {
                applyState { it.copy(isLoading = true, agentStepStatus = "Calling tool: ${pending.toolName}", pendingConfirmation = null) }
                try {
                    val observation = executeTool(pending.toolName, pending.args)
                    steps.add(AgentStep(steps.size + 1, AgentAction.ToolCall(pending.toolName, pending.args), observation))
                } catch (e: Exception) {
                    applyState {
                        it.copy(isLoading = false, agentStepStatus = null,
                            error = "Tool execution failed: ${e.message}",
                            pendingConfirmation = pending)
                    }
                    onComplete()
                    return@launch
                }
```

Also in AgentController, `approvePendingAction()` and `cancelPendingAction()`: add `loopRunning = true` BEFORE `_uiState.update` (currently line 169-170).

**Step 4: Commit**

```
git add app/src/main/java/com/phoneagent/agent/AgentController.kt app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt
git commit -m "fix: AtomicBoolean TOCTOU, vision system prompt, pending confirmation crash recovery"
```

---

### Task 7: ModelRouter fixes — failover model mismatch + DataStore timeout + model name trimming

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/ModelRouter.kt`

**H8: Failover returns provider that may not support requested model.**
**M: `providers.first()` hangs indefinitely on DataStore init.**

**Step 1: Fix failover to use provider's default model**

In `getAllProvidersForModel`, the fallback path returns providers with the wrong model. Instead, when falling back, use `providerConfig.defaultModel`:
```kotlin
    suspend fun getAllProvidersForModel(model: String): List<AiProvider> {
        val providers = providerRepository.providers.first()
        val normalizedModel = model.trim().lowercase()
        val matching = providers
            .filter { it.isEnabled && it.availableModels.any { m -> m.trim().lowercase() == normalizedModel } }
            .map { p -> createProvider(injectApiKey(p)) }
        if (matching.isNotEmpty()) return matching

        val fallback = providers.firstOrNull { it.isEnabled }
        if (fallback != null) {
            return listOf(createProvider(injectApiKey(fallback)))
        }
        throw IllegalStateException("No enabled provider available. Configure a provider in Settings.")
    }
```

**Step 2: Add DataStore timeout**

Wrap `providers.first()` with a timeout in both `getProviderForModel` and `getAllProvidersForModel`:
```kotlin
    private suspend fun getProviders() = kotlinx.coroutines.withTimeout(5_000) {
        providerRepository.providers.first()
    }
```

Then replace all `providerRepository.providers.first()` calls with `getProviders()`.

**Step 3: Also add model name trimming to getProviderForModel**

```kotlin
    suspend fun getProviderForModel(model: String): AiProvider {
        val providers = getProviders()
        val normalizedModel = model.trim().lowercase()
        val providerConfig = providers.find {
            it.isEnabled && it.availableModels.any { m -> m.trim().lowercase() == normalizedModel }
        }
        // ... rest unchanged
    }
```

**Step 4: Commit**

```
git add app/src/main/java/com/phoneagent/agent/ModelRouter.kt
git commit -m "fix: failover model mismatch, DataStore timeout, model name trimming"
```

---

### Task 8: UI fixes — contrast + overlay key + SafeBrowsing + provider settings validation

**Files:**
- Modify: `app/src/main/java/com/phoneagent/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/phoneagent/overlay/OverlayChatContent.kt`
- Modify: `app/src/main/java/com/phoneagent/ui/ProviderSettingsScreen.kt`
- Modify: `app/src/main/java/com/phoneagent/browser/AgentWebView.kt` (SafeBrowsing already done in Task 3)

**H11: OnSurfaceDim fails WCAG AA contrast.**
**H12: OverlayChatContent LazyColumn missing keys.**
**M: ProviderSettings no save feedback or validation.**

**Step 1: Fix color contrast**

In `Color.kt`, line 24, change `OnSurfaceDim` from `#6B6B74` (3.2:1 on Surface #1A1A1E) to `#8E8E99` (4.5:1):
```kotlin
val OnSurfaceDim = Color(0xFF8E8E99)
```

**Step 2: Fix overlay LazyColumn keys**

In `OverlayChatContent.kt`, line 134, change:
```kotlin
                        items(uiState.messages) { msg ->
```
To:
```kotlin
                        items(uiState.messages, key = { it.id }) { msg ->
```

**Step 3: Add save feedback to ProviderSettingsScreen**

Find `composableScope` or `rememberCoroutineScope()`. Add a save success state:
```kotlin
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
```

In the save button's `onClick`, after saving:
```kotlin
    scope.launch {
        try {
            agentController.storeApiKey(selectedProviderId, apiKey)
            providerRepository.saveProvider(
                config.copy(baseUrl = baseUrl, apiKey = null, defaultModel = selectedModel, streamEnabled = streamEnabled)
            )
            saveMessage = "Settings saved"
            saveError = null
        } catch (e: Exception) {
            saveError = e.message ?: "Save failed"
            saveMessage = null
        }
    }
```

And display message/error near the save button:
```kotlin
    saveMessage?.let { Text(it, color = Success, style = MaterialTheme.typography.bodySmall) }
    saveError?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }
```

**Step 4: Add URL validation to ProviderSettingsScreen**

Before saving, add:
```kotlin
    if (baseUrl.isBlank() || !baseUrl.startsWith("http")) {
        saveError = "Base URL must start with http:// or https://"
        return@launch
    }
```

**Step 5: Replace the Button("Back") with IconButton**

In `ProviderSettingsScreen.kt`, line 73-76, replace:
```kotlin
                    Button(onClick = onBack) {
                        Text("Back")
                    }
```
With:
```kotlin
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnBackground)
                    }
```

And add `colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = OnBackground)` to the TopAppBar for dark theme consistency.

**Step 6: Commit**

```
git add app/src/main/java/com/phoneagent/ui/theme/Color.kt app/src/main/java/com/phoneagent/overlay/OverlayChatContent.kt app/src/main/java/com/phoneagent/ui/ProviderSettingsScreen.kt
git commit -m "fix: WCAG contrast, overlay LazyColumn keys, provider save feedback and validation"
```

---

### Build check after all tasks

Run: `cd C:\Users\asus\Desktop\PhoneAgent && gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Execution handoff

**Plan complete.** 8 tasks, ~22 files. All critical + high + key medium findings covered.

Two execution options:

1. **Subagent-Driven (this session)** — I dispatch fresh subagent per task, review between tasks, fast iteration
2. **Parallel Session (separate)** — Open new session with executing-plans, batch execution with checkpoints

**Which approach?**
