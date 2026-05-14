# PhoneAgent Streaming UI — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Real-time streaming UI — agent's reasoning visible as it happens via SSE tokens + floating draggable reasoning card.

**Architecture:** Provider SSE chunks → StreamRenderer parses type → StreamingState emits to UI via StateFlow → ChatScreen renders token-by-token + ReasoningCard shows structured steps.

**Tech Stack:** Kotlin, Jetpack Compose, StateFlow, SSE/okHttp, AiProvider interface

---

## Phase 3A — Core Streaming Infrastructure

### Task 1: StreamingState + StreamChunk data classes

**Files:**
- Create: `app/src/main/java/com/phoneagent/streaming/StreamingState.kt`
- Create: `app/src/main/java/com/phoneagent/streaming/StreamChunk.kt`

**Step 1: Create StreamChunk.kt**

```kotlin
package com.phoneagent.streaming

enum class ChunkType { TOKEN, REASONING, TOOL_CALL_START, TOOL_CALL_END, DONE }

data class StreamChunk(
    val type: ChunkType,
    val content: String,
    val toolName: String? = null,
    val toolArgs: Map<String, String>? = null
)
```

**Step 2: Create StreamingState.kt**

```kotlin
package com.phoneagent.streaming

enum class StreamingStatus { THINKING, ACTING, OBSERVING, DONE, ERROR }

enum class ReasoningType { THOUGHT, ACTION, OBSERVATION, PLAN }

data class ReasoningChunk(
    val type: ReasoningType,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ToolCallState(
    val toolName: String,
    val args: Map<String, String>,
    val status: ToolCallStatus = ToolCallStatus.STARTED
)

enum class ToolCallStatus { STARTED, EXECUTING, RESULT }

data class StreamingState(
    val status: StreamingStatus = StreamingStatus.THINKING,
    val currentStep: Int = 0,
    val streamedText: String = "",
    val reasoningChunks: List<ReasoningChunk> = emptyList(),
    val toolCallInProgress: ToolCallState? = null,
    val error: String? = null
)
```

**Step 3: Commit**
```
git add streaming/StreamChunk.kt streaming/StreamingState.kt
git commit -m "feat: add StreamingState and StreamChunk data classes (Phase 3A)"
```

---

### Task 2: Extend AiProvider with chatCompletionStream

**Files:**
- Modify: `app/src/main/java/com/phoneagent/providers/AiProvider.kt`

**Step 1: Add streaming method to interface**

```kotlin
interface AiProvider {
    suspend fun chatCompletion(request: AgentRequest): AgentResponse

    // NEW:
    suspend fun chatCompletionStream(
        request: AgentRequest,
        onChunk: (StreamChunk) -> Unit
    )
}
```

**Step 2: Commit**
```
git add providers/AiProvider.kt
git commit -m "feat: add chatCompletionStream to AiProvider interface (Phase 3A)"
```

---

### Task 3: Implement chatCompletionStream in BaseOpenAiProvider

**Files:**
- Modify: `app/src/main/java/com/phoneagent/providers/BaseOpenAiProvider.kt`

**Step 1: Add streaming implementation**

Find the existing `chatCompletionStream` method (already exists, unused). Verify it emits `StreamChunk` properly:

```kotlin
// Check existing method signature
// Already has: suspend fun chatCompletionStream(request: AgentRequest, callback: (String) -> Unit)
// We need to wrap callback to emit StreamChunk
```

Modify the existing `chatCompletionStream` to also have a `StreamChunk`-emitting variant, or add a new overload:

```kotlin
suspend fun chatCompletionStream(
    request: AgentRequest,
    onChunk: (StreamChunk) -> Unit
) {
    // Existing method uses callback: (String) -> Unit
    // We need to parse raw chunk into StreamChunk
    chatCompletionStream(request) { rawChunk ->
        val streamChunk = StreamingParser.parseChunk(rawChunk, config.name)
        if (streamChunk.type != ChunkType.TOKEN || streamChunk.content.isNotBlank()) {
            onChunk(streamChunk)
        }
    }
}
```

**Step 2: Commit**
```
git add providers/BaseOpenAiProvider.kt
git commit -m "feat: implement chatCompletionStream in BaseOpenAiProvider (Phase 3A)"
```

---

### Task 4: Extend StreamingParser

**Files:**
- Modify: `app/src/main/java/com/phoneagent/providers/StreamingParser.kt`

**Step 1: Add parseChunk method**

```kotlin
fun parseChunk(raw: String, providerName: String): StreamChunk {
    return when {
        raw.startsWith("data: [DONE]") || raw.trim() == "[DONE]" ->
            StreamChunk(ChunkType.DONE, "")
        raw.startsWith("data: ") -> parseSSEData(raw.removePrefix("data: ").trim())
        raw.startsWith("{") -> parseJSONChunk(raw)
        else -> StreamChunk(ChunkType.TOKEN, raw)
    }
}

private fun parseSSEData(data: String): StreamChunk {
    if (data == "[DONE]") return StreamChunk(ChunkType.DONE, "")
    return try {
        val json = JSONObject(data)
        val choices = json.optJSONArray("choices")?.optJSONObject(0)
        val delta = choices?.optJSONObject("delta")

        // Check reasoning content (OpenAI o1/o3)
        delta?.optString("reasoning_content", null)?.takeIf { it.isNotBlank() }
            ?.let { return StreamChunk(ChunkType.REASONING, it) }

        // Regular token
        delta?.optString("content", null)?.takeIf { it.isNotBlank() }
            ?.let { return StreamChunk(ChunkType.TOKEN, it) }

        // Tool call detection
        choices?.optString("finish_reason")?.let {
            if (it == "tool_calls") return StreamChunk(ChunkType.TOOL_CALL_END, "")
        }

        StreamChunk(ChunkType.TOKEN, "")
    } catch (e: Exception) {
        StreamChunk(ChunkType.TOKEN, "")
    }
}

private fun parseJSONChunk(json: String): StreamChunk {
    return try {
        val obj = JSONObject(json)
        val choices = obj.optJSONArray("choices")?.optJSONObject(0)
        val delta = choices?.optJSONObject("delta")

        delta?.optString("content", null)?.takeIf { it.isNotBlank() }
            ?.let { return StreamChunk(ChunkType.TOKEN, it) }

        StreamChunk(ChunkType.TOKEN, "")
    } catch (e: Exception) {
        StreamChunk(ChunkType.TOKEN, "")
    }
}
```

**Step 2: Commit**
```
git add providers/StreamingParser.kt
git commit -m "feat: extend StreamingParser for structured chunk types (Phase 3A)"
```

---

### Task 5: Wire streaming into AgentUiState + AgentController

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentUiState.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt`

**Step 1: Add streamingState to AgentUiState**

```kotlin
data class AgentUiState(
    // ... existing fields ...
    val streamingState: StreamingState? = null,
    val isReasoningCardVisible: Boolean = false
)
```

**Step 2: Add streaming to AgentController**

In `sendMessage()`, add a callback for streaming state:

```kotlin
// In sendMessage(), add streaming state callback to loopExecutor.startLoopStream()
loopExecutor.startLoopStream(
    message = message,
    model = selectedModel,
    systemPrompt = augmentedPrompt,
    temperature = routingTemp,
    styleInjection = routingStyle,
    worldModel = personalWorldModel,
    onStreamingState = { state ->
        _uiState.update { it.copy(
            streamingState = state,
            isReasoningCardVisible = state.status != StreamingStatus.DONE
        )}
    },
    onComplete = { finalAnswer ->
        _uiState.update { it.copy(streamingState = null, isReasoningCardVisible = false) }
        loopRunning.set(false)
    }
)
```

**Step 3: Commit**
```
git add agent/AgentUiState.kt agent/AgentController.kt
git commit -m "feat: add streamingState to AgentUiState and AgentController (Phase 3A)"
```

---

## Phase 3B — Agent Loop Streaming

### Task 6: Add streaming loop to AgentLoopExecutor

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt`

**Step 1: Add startLoopStream() method**

```kotlin
fun startLoopStream(
    message: String,
    model: String,
    systemPrompt: String,
    temperature: Double = 0.5,
    styleInjection: String? = null,
    worldModel: PersonalWorldModel? = null,
    onStreamingState: (StreamingState) -> Unit,
    onComplete: (String) -> Unit
) {
    scope.launch {
        // Similar to startLoop() but uses streaming provider
        // instead of chatCompletion, uses chatCompletionStream
        executeStepsStreaming(...)
    }
}

private suspend fun executeStepsStreaming(
    providers: List<AiProvider>,
    ...
    onStreamingState: (StreamingState) -> Unit,
    onComplete: (String) -> Unit
) {
    // Build request with stream = true
    val request = AgentRequest(..., stream = true)

    for (p in providers) {
        try {
            p.chatCompletionStream(request) { chunk ->
                handleStreamingChunk(chunk, onStreamingState)
            }
            break
        } catch (e: Exception) {
            continue
        }
    }
}

private fun handleStreamingChunk(chunk: StreamChunk, onStreamingState: (StreamingState) -> Unit) {
    when (chunk.type) {
        ChunkType.TOKEN -> {
            onStreamingState(StreamingState(
                status = StreamingStatus.THINKING,
                streamedText = currentText + chunk.content
            ))
            currentText += chunk.content
        }
        ChunkType.REASONING -> {
            onStreamingState(StreamingState(
                status = StreamingStatus.THINKING,
                reasoningChunks = currentChunks + ReasoningChunk(THOUGHT, chunk.content)
            ))
            currentChunks += ReasoningChunk(THOUGHT, chunk.content)
        }
        ChunkType.TOOL_CALL_START -> {
            onStreamingState(StreamingState(
                status = StreamingStatus.ACTING,
                toolCallInProgress = ToolCallState(chunk.toolName ?: "", chunk.toolArgs ?: emptyMap())
            ))
        }
        ChunkType.TOOL_CALL_END -> {
            // Execute tool
            val result = executeTool(chunk.toolName ?: "", chunk.toolArgs ?: emptyMap())
            onStreamingState(StreamingState(
                status = StreamingStatus.OBSERVING,
                reasoningChunks = currentChunks + ReasoningChunk(OBSERVATION, result.take(200)),
                toolCallInProgress = ToolCallState(chunk.toolName ?: "", chunk.toolArgs ?: emptyMap(), RESULT)
            ))
        }
        ChunkType.DONE -> {
            onStreamingState(StreamingState(status = StreamingStatus.DONE))
        }
    }
}
```

**Step 2: Keep existing loop for non-streaming fallback**
The existing `startLoop()` stays as-is for providers that don't support streaming. `startLoopStream()` uses it when streaming fails.

**Step 3: Commit**
```
git add agent/AgentLoopExecutor.kt
git commit -m "feat: add streaming loop to AgentLoopExecutor (Phase 3B)"
```

---

## Phase 3C — Reasoning Card UI

### Task 7: ReasoningCard composable

**Files:**
- Create: `app/src/main/java/com/phoneagent/ui/streaming/ReasoningCard.kt`
- Create: `app/src/main/java/com/phoneagent/ui/streaming/ReasoningCardPosition.kt`

**Step 1: Create ReasoningCardPosition.kt**

```kotlin
data class ReasoningCardPosition(
    val x: Float = 0f,
    val y: Float = 100f  // offset from default bottom-right
)

@Composable
fun rememberReasoningCardPosition(): State<ReasoningCardPosition> {
    val context = ambientContext()
    val prefs = context.dataStore.data
    return prefs.collectAsState(initial = ReasoningCardPosition())
}
```

**Step 2: Create ReasoningCard.kt**

```kotlin
@Composable
fun ReasoningCard(
    state: StreamingState,
    position: ReasoningCardPosition,
    onPositionChange: (ReasoningCardPosition) -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(position.x) }
    var offsetY by remember { mutableFloatStateOf(position.y) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        onPositionChange(ReasoningCardPosition(offsetX, offsetY))
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
    ) {
        Card(
            elevation = 8.dp,
            modifier = Modifier.width(300.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Reasoning",
                        style = MaterialTheme.typography.Subtitle2
                    )
                    Row {
                        TextButton(onClick = onMinimize) { Text("−") }
                        TextButton(onClick = onClose) { Text("×") }
                    }
                }
                HorizontalDivider()
                // Status
                Text(
                    text = state.status.name,
                    style = MaterialTheme.typography.Caption,
                    color = statusColor(state.status),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                // Reasoning chunks
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .padding(8.dp)
                ) {
                    items(state.reasoningChunks) { chunk ->
                        ReasoningChunkRow(chunk)
                    }
                }
                // Tool call
                state.toolCallInProgress?.let { tool ->
                    ToolCallRow(tool)
                }
            }
        }
    }
}

@Composable
private fun ReasoningChunkRow(chunk: ReasoningChunk) {
    val color = when (chunk.type) {
        ReasoningType.THOUGHT -> Color(0xFF1976D2)
        ReasoningType.ACTION -> Color(0xFF388E3C)
        ReasoningType.OBSERVATION -> Color(0xFF757575)
        ReasoningType.PLAN -> Color(0xFF7B1FA2)
    }
    Text(
        text = "[${chunk.type.name.take(3)}] ${chunk.content}",
        style = MaterialTheme.typography.Body2,
        color = color,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun ToolCallRow(tool: ToolCallState) {
    val color = when (tool.status) {
        ToolCallStatus.STARTED -> Color.Yellow
        ToolCallStatus.EXECUTING -> Color.Green
        ToolCallStatus.RESULT -> Color.Gray
    }
    Text(
        text = "→ ${tool.toolName}(${tool.args.entries.joinToString { "${it.key}=${it.value}" }})",
        style = MaterialTheme.typography.Body2,
        color = Color.Green,
        modifier = Modifier.padding(4.dp)
    )
}

@Composable
private fun statusColor(status: StreamingStatus): Color = when (status) {
    StreamingStatus.THINKING -> Color.Blue
    StreamingStatus.ACTING -> Color.Green
    StreamingStatus.OBSERVING -> Color(0xFFFF9800)
    StreamingStatus.DONE -> Color.Gray
    StreamingStatus.ERROR -> Color.Red
}
```

**Step 3: Commit**
```
git add ui/streaming/ReasoningCard.kt ui/streaming/ReasoningCardPosition.kt
git commit -m "feat: add ReasoningCard composable with drag gesture (Phase 3C)"
```

---

### Task 8: Minimized badge + position persistence

**Files:**
- Create: `app/src/main/java/com/phoneagent/ui/streaming/MinimizedReasoningBadge.kt`
- Modify: `app/src/main/java/com/phoneagent/ui/streaming/ReasoningCardPosition.kt`

**Step 1: Create MinimizedReasoningBadge**

```kotlin
@Composable
fun MinimizedReasoningBadge(
    state: StreamingState,
    onExpand: () -> Unit
) {
    val backgroundColor = when (state.status) {
        StreamingStatus.THINKING -> Color(0xFF1976D2)
        StreamingStatus.ACTING -> Color(0xFF388E3C)
        StreamingStatus.OBSERVING -> Color(0xFFFF9800)
        else -> Color.Gray
    }

    Badge(
        backgroundColor = backgroundColor,
        modifier = Modifier.clickable { onExpand() }
    ) {
        Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = "Thinking",
            tint = Color.White
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "Step ${state.currentStep}",
            color = Color.White,
            style = MaterialTheme.typography.Caption
        )
    }
}
```

**Step 2: Add DataStore persistence for position**

```kotlin
// In ReasoningCardPosition.kt
private val REASONING_CARD_POSITION = stringPreferencesKey("reasoning_card_position")

suspend fun savePosition(position: ReasoningCardPosition, dataStore: DataStore) {
    dataStore.edit {
        it[REASONING_CARD_POSITION] = "${position.x},${position.y}"
    }
}

@Composable
fun rememberReasoningCardPosition(dataStore: DataStore): State<ReasoningCardPosition> {
    val position = dataStore.data.map { prefs ->
        prefs[REASONING_CARD_POSITION]?.let { saved ->
            val parts = saved.split(",")
            ReasoningCardPosition(parts[0].toFloat(), parts[1].toFloat())
        } ?: ReasoningCardPosition()
    }.collectAsState(initial = ReasoningCardPosition())
    return position
}
```

**Step 3: Commit**
```
git add ui/streaming/MinimizedReasoningBadge.kt
git commit -m "feat: add MinimizedReasoningBadge with position persistence (Phase 3C)"
```

---

### Task 9: Wire ReasoningCard into ChatScreen

**Files:**
- Modify: `app/src/main/java/com/phoneagent/ui/ChatScreen.kt`

**Step 1: Add streaming state observation**

```kotlin
val streamingState = agentController.uiState.collectAsState().value.streamingState
val isReasoningCardVisible = agentController.uiState.collectAsState().value.isReasoningCardVisible
```

**Step 2: Show ReasoningCard when visible**

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // Existing chat content...

    // Reasoning card or minimized badge
    if (isReasoningCardVisible) {
        if (streamingState?.status == StreamingStatus.DONE) {
            // Show minimized badge briefly then auto-hide
            LaunchedEffect(Unit) {
                delay(5000)
                agentController.hideReasoningCard()
            }
            MinimizedReasoningBadge(
                state = streamingState,
                onExpand = { agentController.showReasoningCard() }
            )
        } else {
            ReasoningCard(
                state = streamingState ?: StreamingState(),
                position = reasoningCardPosition,
                onPositionChange = { agentController.saveReasoningCardPosition(it) },
                onMinimize = { agentController.minimizeReasoningCard() },
                onClose = { agentController.hideReasoningCard() }
            )
        }
    }

    // Streaming text in message bubble
    streamingState?.let { state ->
        if (state.streamedText.isNotBlank()) {
            // Update the last message bubble with streaming text
            val messages = uiState.messages.toMutableList()
            if (messages.isNotEmpty()) {
                messages[messages.lastIndex] = messages.last().copy(content = state.streamedText)
                uiState = uiState.copy(messages = messages)
            }
        }
    }
}
```

**Step 3: Add controller methods**

In `AgentController`, add:

```kotlin
fun showReasoningCard() {
    _uiState.update { it.copy(isReasoningCardVisible = true) }
}

fun hideReasoningCard() {
    _uiState.update { it.copy(isReasoningCardVisible = false, streamingState = null) }
}

fun minimizeReasoningCard() {
    // Just keep isReasoningCardVisible = true but show minimized
}

fun saveReasoningCardPosition(pos: ReasoningCardPosition) {
    // Save to DataStore
}
```

**Step 4: Commit**
```
git add ui/ChatScreen.kt agent/AgentController.kt
git commit -m "feat: wire ReasoningCard into ChatScreen with streaming text (Phase 3C)"
```

---

## Phase 3D — Provider Coverage + Error Handling

### Task 10: Ollama streaming support

**Files:**
- Modify: `app/src/main/java/com/phoneagent/providers/OllamaProvider.kt`

**Step 1: Add chatCompletionStream to OllamaProvider**

```kotlin
override suspend fun chatCompletionStream(
    request: AgentRequest,
    onChunk: (StreamChunk) -> Unit
) {
    chatCompletionStream(request) { rawLine ->
        val chunk = when {
            rawLine.startsWith("data: ") -> {
                val data = rawLine.removePrefix("data: ").trim()
                if (data == "[DONE]") StreamChunk(ChunkType.DONE, "")
                else {
                    try {
                        val json = JSONObject(data)
                        val content = json.optJSONArray("message")
                            ?.optJSONObject(0)
                            ?.optString("content")
                            ?: json.optString("content", "")
                        StreamChunk(ChunkType.TOKEN, content)
                    } catch (_: Exception) {
                        StreamChunk(ChunkType.TOKEN, "")
                    }
                }
            }
            else -> StreamChunk(ChunkType.TOKEN, rawLine)
        }
        onChunk(chunk)
    }
}
```

**Step 2: Commit**
```
git add providers/OllamaProvider.kt
git commit -m "feat: add streaming support to OllamaProvider (Phase 3D)"
```

---

### Task 11: Non-streaming fallback + error handling

**Files:**
- Modify: `app/src/main/java/com/phoneagent/providers/BaseOpenAiProvider.kt`

**Step 1: Add fallback in chatCompletionStream**

```kotlin
suspend fun chatCompletionStream(
    request: AgentRequest,
    onChunk: (StreamChunk) -> Unit
) {
    try {
        // Try streaming first
        chatCompletionStream(request) { rawChunk ->
            val streamChunk = StreamingParser.parseChunk(rawChunk, config.name)
            onChunk(streamChunk)
        }
    } catch (e: Exception) {
        // Fallback: use regular chatCompletion with simulated streaming
        val response = chatCompletion(request)
        simulateStreaming(response.content, onChunk)
    }
}

private suspend fun simulateStreaming(
    content: String,
    onChunk: (StreamChunk) -> Unit
) {
    val words = content.split(" ")
    for (word in words) {
        onChunk(StreamChunk(ChunkType.TOKEN, "$word "))
        delay(20)
    }
    onChunk(StreamChunk(ChunkType.DONE, ""))
}
```

**Step 2: Commit**
```
git add providers/BaseOpenAiProvider.kt
git commit -m "feat: add non-streaming fallback to chatCompletionStream (Phase 3D)"
```

---

### Task 12: ReasoningCard auto-show/hide logic

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt`

**Step 1: Auto-hide after completion**

In `sendMessage()`, the reasoning card should:
- Auto-show when first streaming chunk arrives
- Auto-hide 5 seconds after DONE status

In ChatScreen:

```kotlin
LaunchedEffect(streamingState?.status) {
    if (streamingState?.status == StreamingStatus.DONE) {
        delay(5000)
        hideReasoningCard()
    }
}
```

**Step 2: Commit**
```
git add agent/AgentController.kt ui/ChatScreen.kt
git commit -m "feat: add ReasoningCard auto-show/hide timing (Phase 3D)"
```

---

## Phase 3E — Tests

### Task 13: StreamingParser tests

**Files:**
- Create: `app/src/test/java/com/phoneagent/streaming/StreamingParserTest.kt`

**Step 1: Write tests**

```kotlin
class StreamingParserTest {
    @Test
    fun `parseChunk token from SSE data`() {
        val chunk = """data: {"choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}"""
        val result = StreamingParser.parseChunk(chunk, "openai-compatible")
        assertEquals(ChunkType.TOKEN, result.type)
        assertEquals("Hello", result.content)
    }

    @Test
    fun `parseChunk DONE from SSE`() {
        val result = StreamingParser.parseChunk("data: [DONE]", "openai-compatible")
        assertEquals(ChunkType.DONE, result.type)
    }

    @Test
    fun `parseChunk reasoning content`() {
        val chunk = """data: {"choices":[{"delta":{"reasoning_content":"let me think"},"finish_reason":null}]}"""
        val result = StreamingParser.parseChunk(chunk, "openai-compatible")
        assertEquals(ChunkType.REASONING, result.type)
        assertEquals("let me think", result.content)
    }

    @Test
    fun `parseChunk tool_call end`() {
        val chunk = """data: {"choices":[{"finish_reason":"tool_calls"}]}"""
        val result = StreamingParser.parseChunk(chunk, "openai-compatible")
        assertEquals(ChunkType.TOOL_CALL_END, result.type)
    }
}
```

**Step 2: Run tests**
```
.\gradlew.bat testDebugUnitTest --tests "*StreamingParserTest" -q
```

**Step 3: Commit**
```
git add streaming/StreamingParserTest.kt
git commit -m "feat: add StreamingParser unit tests (Phase 3E)"
```

---

## Summary

| Task | Phase | Status |
|------|-------|--------|
| StreamingState + StreamChunk data classes | 3A | pending |
| AiProvider chatCompletionStream interface | 3A | pending |
| BaseOpenAiProvider streaming impl | 3A | pending |
| StreamingParser chunk parsing | 3A | pending |
| AgentUiState + AgentController streaming | 3A | pending |
| AgentLoopExecutor streaming loop | 3B | pending |
| ReasoningCard composable (draggable) | 3C | pending |
| Minimized badge + position persistence | 3C | pending |
| ChatScreen wiring | 3C | pending |
| Ollama streaming | 3D | pending |
| Non-streaming fallback | 3D | pending |
| Auto-show/hide timing | 3D | pending |
| StreamingParser tests | 3E | pending |

---

## Execution Options

**Plan complete and saved to `docs/plans/2026-05-14-phoneagent-phase3-streaming-ui-implementation-plan.md`.**

Two execution options:

**1. Subagent-Driven (this session)** — I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** — Open new session with executing-plans, batch execution with checkpoints

Which approach?