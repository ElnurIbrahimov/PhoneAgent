# PhoneAgent Code Quality Fixes

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix 10 issues: memory leak, security leak, dead code, unused features, provider failover, and task history UI.

**Architecture:** Targeted fixes across ~14 files. No refactoring of architecture. Each task is independent after Task 1.

**Tech Stack:** Kotlin, Jetpack Compose, Android Keystore, Room, OkHttp, Coroutines

---

### Task 1: Fix AccessibilityService memory leak

**Files:**
- Modify: `app/src/main/java/com/phoneagent/accessibility/AgentAccessibilityService.kt:62-77`

**Bug:** In `findAndTap()`, when the found node IS the root node, `root.recycle()` is skipped (line 70 condition `node !== root` is false), and `node.recycle()` is also skipped (line 75 condition `node !== root` is false). Root is never recycled.

**Step 1: Rewrite findAndTap recycling logic**

Replace lines 61-77 with:

```kotlin
    fun findAndTap(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByText(root, text)
        if (node != null) {
            performTapOnNode(node)
            if (node !== root) {
                root.recycle()
            }
            return true
        }
        root.recycle()
        return false
    }
```

**Step 2: Verify no compile errors**

Run: `cd C:\Users\asus\Desktop\PhoneAgent && gradle compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
git add app/src/main/java/com/phoneagent/accessibility/AgentAccessibilityService.kt
git commit -m "fix: recycle root node in findAndTap when found node is root"
```

---

### Task 2: Fix ProviderConfig API key leak via toString

**Files:**
- Modify: `app/src/main/java/com/phoneagent/providers/ProviderConfig.kt`

**Bug:** `ProviderConfig` is a data class. If `toString()` is ever called (logging, debugging), the API key is exposed in plaintext.

**Step 1: Override toString to mask API key**

Replace the entire file with:

```kotlin
package com.phoneagent.providers

data class ProviderConfig(
    val id: String,
    val name: String,
    val type: ProviderType,
    val baseUrl: String,
    val apiKey: String? = null,
    val defaultModel: String? = null,
    val availableModels: List<String> = emptyList(),
    val isEnabled: Boolean = true,
    val streamEnabled: Boolean = false
) {
    override fun toString(): String {
        return "ProviderConfig(id=$id, name=$name, type=$type, baseUrl=$baseUrl, apiKey=${if (apiKey != null) "***" else "null"}, defaultModel=$defaultModel, availableModels=$availableModels, isEnabled=$isEnabled, streamEnabled=$streamEnabled)"
    }
}
```

**Step 2: Verify compile**

Run: `cd C:\Users\asus\Desktop\PhoneAgent && gradle compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
git add app/src/main/java/com/phoneagent/providers/ProviderConfig.kt
git commit -m "fix: mask API key in ProviderConfig.toString()"
```

---

### Task 3: Remove dead imports in PhoneSettingsTool

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/tools/PhoneSettingsTool.kt:4-5`

**Step 1: Delete the two unused imports**

Remove lines 4-5:
```kotlin
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
```

**Step 2: Verify compile**

Run: `cd C:\Users\asus\Desktop\PhoneAgent && gradle compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
git add app/src/main/java/com/phoneagent/agent/tools/PhoneSettingsTool.kt
git commit -m "chore: remove unused imports from PhoneSettingsTool"
```

---

### Task 4: Remove unused OverlayState from AgentController

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt`

**Background:** `OverlayState` sealed class is defined but `_overlayState`/`overlayState`/`setOverlayState` in AgentController are never read by anything. The OverlayService manages its own state independently. Keep the OverlayState.kt file itself (useful for future use).

**Step 1: Remove the import**

Remove line 29:
```kotlin
import com.phoneagent.overlay.OverlayState
```

**Step 2: Remove the state fields and setter**

Remove lines 66-68:
```kotlin
    private val _overlayState = MutableStateFlow<OverlayState>(OverlayState.Hidden)
    val overlayState: StateFlow<OverlayState> = _overlayState.asStateFlow()
```

Remove lines 164-166:
```kotlin
    fun setOverlayState(state: OverlayState) {
        _overlayState.value = state
    }
```

**Step 3: Remove unused imports left behind**

After removing `_overlayState` and `overlayState`, the imports for `MutableStateFlow`, `StateFlow`, and `asStateFlow` may still be needed by `_uiState`. Check and keep them.

**Step 4: Verify compile**

Run: `cd C:\Users\asus\Desktop\PhoneAgent && gradle compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```
git add app/src/main/java/com/phoneagent/agent/AgentController.kt
git commit -m "chore: remove unused OverlayState from AgentController"
```

---

### Task 5: Register browser.read_summary tool using PageExtractor.extractVisibleSummary

**Files:**
- Modify: `app/src/main/java/com/phoneagent/browser/BrowserTool.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt`

**Background:** `PageExtractor.extractVisibleSummary()` exists but no tool uses it. It combines text excerpt + metadata in one call. Register as `browser.read_summary`.

**Step 1: Add readSummary method to BrowserTool**

Add after the `readMetadata` method (after line 99), before `clickText`:

```kotlin
    private suspend fun readSummary(toolName: String): String = withContext(Dispatchers.Main) {
        val webView = BrowserSessionManager.getActiveWebView()
            ?: return@withContext errorResult(toolName, "Browser not active.",
                "Use browser.open_url to launch the browser first.")
        suspendCancellableCoroutine { continuation ->
            PageExtractor.extractVisibleSummary(webView) { summary ->
                continuation.resume(
                    successResult(toolName, summary.take(8000))
                )
            }
        }
    }
```

**Step 2: Add "read_summary" case to the when block in execute()**

After the `"read_metadata"` case (line 24), add:
```kotlin
            "read_summary" -> readSummary(toolName)
```

Also update the `else` error message on line 49 to include `read_summary` in the available actions list.

**Step 3: Register the tool in AgentController**

Add after the last `BrowserActionTool` entry (after `browser.reload` line):
```kotlin
                BrowserActionTool("browser.read_summary", "Read a combined summary of the current page including text excerpt, title, links, buttons, and inputs.", browserTool, "read_summary", ""),
```

**Step 4: Verify compile**

Run: `cd C:\Users\asus\Desktop\PhoneAgent && gradle compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```
git add app/src/main/java/com/phoneagent/browser/BrowserTool.kt app/src/main/java/com/phoneagent/agent/AgentController.kt
git commit -m "feat: register browser.read_summary tool using PageExtractor.extractVisibleSummary"
```

---

### Task 6: Wire up insertMessage to persist chat history

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/TaskHistoryManager.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt`

**Background:** `MemoryRepository.insertMessage()` is defined but never called. Chat history stays in-memory only (AgentUiState.messages). Wire it so each completed conversation gets persisted to Room.

**Step 1: Add recordMessage to TaskHistoryManager**

Add after `clearHistory()` method (after line 85):

```kotlin
    suspend fun recordMessage(userMessage: String, assistantMessage: String, model: String) {
        try {
            memoryRepository.insertMessage(userMessage, assistantMessage, model)
        } catch (e: Exception) {
            Log.e("TaskHistoryManager", "Failed to record message", e)
        }
    }
```

**Step 2: Call recordMessage in AgentLoopExecutor when task completes**

In `executeSteps()`, there are two places where the final answer is set:
1. Normal completion via `FinalAnswer` → breaks loop, sets `finalContent`
2. Step limit exceeded → fallback message

After both paths, the answer is applied to UI state via `applyState`. Add `recordMessage` call right before `onComplete()`.

In the existing code around lines 139-148 in `executeSteps()`, find the block:
```kotlin
        val answer = finalContent ?: "I reached the step limit..."
        applyState {
            it.copy(
                messages = it.messages + ChatMessage(id = ChatMessage.nextId(), role = "assistant", content = answer),
                ...
            )
        }
        onComplete()
```

Add after `applyState { ... }` and before `onComplete()`:
```kotlin
        taskHistoryManager.recordMessage(message, answer, model)
```

Wait — `message` is the original user request and `model` is available. Let me verify the parameter names in `executeSteps` — `message` is the user message, `model` is the model name. Yes, both are available.

**Step 3: Verify compile**

Run: `cd C:\Users\asus\Desktop\PhoneAgent && gradle compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
git add app/src/main/java/com/phoneagent/agent/TaskHistoryManager.kt app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt
git commit -m "feat: persist chat messages to Room on task completion"
```

---

### Task 7: Wire up speakResponse — TTS on agent completion

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt`

**Background:** `SpeechOutputManager` and `speakResponse()` are fully implemented but never called. Wire it so the agent reads its final answer aloud.

**Step 1: Add onSpeak callback to AgentLoopExecutor**

In `AgentLoopExecutor.kt`, add a constructor parameter:

```kotlin
class AgentLoopExecutor(
    private val modelRouter: ModelRouter,
    private val toolRegistry: ToolRegistry,
    private val taskHistoryManager: TaskHistoryManager,
    private val applyState: ((AgentUiState) -> AgentUiState) -> Unit,
    private val screenCaptureManager: ScreenCaptureManager? = null,
    private val onSpeak: ((String) -> Unit)? = null
) {
```

**Step 2: Call onSpeak in executeSteps after final answer**

In `executeSteps()`, after the `answer` variable is set and just before `onComplete()` is called (same location as Task 6), add:

```kotlin
        if (answer != finalContent || finalContent != null) {
            onSpeak?.invoke(answer.take(500))
        }
```

(The condition ensures we speak only real answers, not the step-limit fallback message — actually, let's simplify: just speak whenever there's an answer.)

Actually simpler: right before `onComplete()`, add `onSpeak?.invoke(answer.take(500))` — truncate to avoid reading massive responses.

**Step 3: Wire the callback in AgentController.init**

In `AgentController.kt`, modify the `loopExecutor` construction:

```kotlin
        loopExecutor = AgentLoopExecutor(
            modelRouter = modelRouter,
            toolRegistry = toolRegistry,
            taskHistoryManager = taskHistoryManager,
            applyState = { block -> _uiState.update(block) },
            screenCaptureManager = screenCaptureManager,
            onSpeak = { text -> speakResponse(text) }
        )
```

**Step 4: Verify compile**

Run: `cd C:\Users\asus\Desktop\PhoneAgent && gradle compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```
git add app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt app/src/main/java/com/phoneagent/agent/AgentController.kt
git commit -m "feat: speak final answers via TTS on task completion"
```

---

### Task 8: Provider failover — try next enabled provider on failure

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/ModelRouter.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt`

**Background:** If a provider call fails (network error, rate limit, auth error), the entire task dies. The agent should try the next enabled provider for the same model.

**Step 1: Add getAllProvidersForModel to ModelRouter**

Add this method to `ModelRouter.kt` (after `getProviderForModel`):

```kotlin
    suspend fun getAllProvidersForModel(model: String): List<AiProvider> {
        val providers = providerRepository.providers.first()
        return providers
            .filter { it.availableModels.contains(model) && it.isEnabled }
            .map { createProvider(injectApiKey(it)) }
            .ifEmpty {
                val fallback = providers.firstOrNull { it.isEnabled }
                if (fallback != null) {
                    listOf(createProvider(injectApiKey(fallback)))
                } else {
                    throw IllegalStateException("No enabled provider available. Configure a provider in Settings.")
                }
            }
    }
```

**Step 2: Modify startLoop to use getAllProvidersForModel**

In `AgentLoopExecutor.startLoop()`, change:

```kotlin
            try {
                val provider = modelRouter.getProviderForModel(model)
                executeSteps(provider, taskId, message, model, systemPrompt, steps, onComplete)
            } catch (e: ProviderError) {
```

To:

```kotlin
            try {
                val providers = modelRouter.getAllProvidersForModel(model)
                executeSteps(providers, taskId, message, model, systemPrompt, steps, onComplete)
            } catch (e: ProviderError) {
```

**Step 3: Modify executeSteps to accept list of providers with failover**

Change the signature to accept `providers: List<AiProvider>` instead of `provider: AiProvider`. Inside the while loop, where the provider is called, wrap it in a failover loop:

Replace this block (around lines 95-110 in the current `executeSteps`):
```kotlin
            val response = withTimeout(MODEL_TIMEOUT_MS) {
                provider.chatCompletion(request)
            }
```

With:
```kotlin
            var response: AgentResponse? = null
            var lastError: String? = null

            for (p in providers) {
                try {
                    response = withTimeout(MODEL_TIMEOUT_MS) {
                        p.chatCompletion(request)
                    }
                    break
                } catch (e: ProviderError) {
                    lastError = e.message
                    continue
                } catch (e: Exception) {
                    lastError = e.message
                    continue
                }
            }

            if (response == null) {
                val errorMsg = "All providers failed. Last error: ${lastError ?: "Unknown"}"
                applyState { it.copy(isLoading = false, agentStepStatus = null, error = errorMsg) }
                taskHistoryManager.updateTaskStatus(taskId, "failed", errorMsg)
                onComplete()
                return
            }

            val action = parseAgentResponse(response!!.content)
```

**Step 4: Same failover for resumeAfterConfirmation**

In `resumeAfterConfirmation()`, apply the same change where the provider is resolved (around line 170):
```kotlin
            try {
                val provider = modelRouter.getProviderForModel(pending.selectedModel)
                executeSteps(provider, pending.taskId, ...)
```

Change to:
```kotlin
            try {
                val providers = modelRouter.getAllProvidersForModel(pending.selectedModel)
                executeSteps(providers, pending.taskId, ...)
```

**Step 5: Verify compile**

Run: `cd C:\Users\asus\Desktop\PhoneAgent && gradle compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```
git add app/src/main/java/com/phoneagent/agent/ModelRouter.kt app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt
git commit -m "feat: provider failover — try next enabled provider on API failure"
```

---

### Task 9: Task history screen

**Files:**
- Create: `app/src/main/java/com/phoneagent/ui/TaskHistoryScreen.kt`
- Modify: `app/src/main/java/com/phoneagent/ui/AppRoot.kt`
- Modify: `app/src/main/java/com/phoneagent/ui/MainScreen.kt`

**Background:** Tasks are persisted to Room but no UI displays them. Add a screen and navigation.

**Step 1: Create TaskHistoryScreen.kt**

```kotlin
package com.phoneagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.AgentController
import com.phoneagent.memory.TaskEntity
import com.phoneagent.ui.components.StatusChip
import com.phoneagent.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHistoryScreen(
    agentController: AgentController,
    onBack: () -> Unit
) {
    val tasks by agentController.taskHistoryManager.tasks.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var expandedTaskId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task History", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { agentController.taskHistoryManager.clearHistory() }
                    }) {
                        Icon(Icons.Filled.Delete, "Clear all", tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background, titleContentColor = OnBackground
                )
            )
        },
        containerColor = Background
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No tasks yet.", color = OnSurfaceMuted, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        isExpanded = expandedTaskId == task.id,
                        onToggle = {
                            expandedTaskId = if (expandedTaskId == task.id) null else task.id
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = CardShape
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.description.take(80),
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnBackground,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusChip(
                    text = task.status.replaceFirstChar { it.uppercase() },
                    isActive = task.status == "running"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateFormat.format(Date(task.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceMuted
                )
                if (task.result != null) {
                    Text(
                        text = task.result.take(60).replace("\n", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (task.result != null) {
                        Text("Result:", style = MaterialTheme.typography.labelMedium, color = OnSurfaceMuted)
                        Text(
                            text = task.result,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (!task.stepsJson.isNullOrBlank()) {
                        Text("Steps:", style = MaterialTheme.typography.labelMedium, color = OnSurfaceMuted)
                        Text(
                            text = task.stepsJson.take(1000),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDim
                        )
                    }
                }
            }
        }
    }
}
```

**Step 2: Add history route to AppRoot.kt**

Add this composable block after the "chat" route (after the closing `)` of the chat composable block, before the closing `}` of NavHost):

```kotlin
        composable(
            "history",
            enterTransition = { PhoneAgentEnterTransition },
            exitTransition = { PhoneAgentExitTransition },
            popEnterTransition = { PhoneAgentPopEnterTransition },
            popExitTransition = { PhoneAgentPopExitTransition }
        ) {
            TaskHistoryScreen(
                agentController = agentController,
                onBack = { navController.popBackStack() }
            )
        }
```

**Step 3: Add Task History quick action card to MainScreen**

In `MainScreen.kt`, update the `actions` list to include a Task History entry. After the Permissions card:

```kotlin
                ActionCardData(
                    icon = Icons.Default.History,
                    title = "Task History",
                    subtitle = "View past agent tasks",
                    gradient = null,
                    onClick = onNavigateToHistory
                )
```

Add `onNavigateToHistory: () -> Unit` to the `MainScreen` function signature:

```kotlin
fun MainScreen(
    agentController: AgentController,
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
```

Then in `AppRoot.kt`, wire it in the main composable call:
```kotlin
            MainScreen(
                agentController = agentController,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPermissions = { navController.navigate("permissions") },
                onNavigateToChat = { navController.navigate("chat") },
                onNavigateToHistory = { navController.navigate("history") }
            )
```

And add the History icon import in MainScreen.kt:
```kotlin
import androidx.compose.material.icons.filled.History
```

**Step 4: Verify compile**

Run: `cd C:\Users\asus\Desktop\PhoneAgent && gradle compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```
git add app/src/main/java/com/phoneagent/ui/TaskHistoryScreen.kt app/src/main/java/com/phoneagent/ui/AppRoot.kt app/src/main/java/com/phoneagent/ui/MainScreen.kt
git commit -m "feat: add task history screen with expandable task details"
```

---

### Build check after all tasks

Run: `cd C:\Users\asus\Desktop\PhoneAgent && gradle assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Execution handoff

**Plan complete.** Two execution options:

1. **Subagent-Driven (this session)** — I dispatch fresh subagent per task, review between tasks, fast iteration
2. **Parallel Session (separate)** — Open new session with executing-plans, batch execution with checkpoints

**Which approach?**
