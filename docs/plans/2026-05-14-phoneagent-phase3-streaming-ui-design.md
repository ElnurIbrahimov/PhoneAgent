# PhoneAgent Phase 3: Streaming UI + Real-Time Agent Reasoning

## Status
**Draft** — For Elnur's review before implementation

---

## 1. Vision

The user sees the agent think in real-time. Instead of "Thinking..." followed by a complete response, the agent's reasoning过程 is visible as it happens — tokens stream in, reasoning steps appear (thought/action/observation/plan), and the agent's progress is transparent. The user can follow along, understand what the agent is doing, and trust the system more because they can see its reasoning.

---

## 2. Architecture Overview

```
User sends message
        │
        ▼
┌──────────────────────────────────────────────────────┐
│ AgentController.sendMessage()                          │
│  • Creates streaming AgentRequest (stream=true)    │
│  • Starts agent loop with streaming enabled         │
└──────────────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────────────┐
│ AgentLoopExecutor.executeSteps()                     │
│  • Captures screenshot + OCR                        │
│  • Builds vision payload                           │
│  • Calls provider.chatCompletionStream(request)   │
└──────────────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────────────┐
│ StreamRenderer (new)                                │
│  • Receives SSE chunks from provider               │
│  • Parses: token / reasoning / tool_call / observation
│  • Emits to UI via StateFlow<StreamingState>      │
└──────────────────────────────────────────────────────┘
        │
        ├──────────────────────────────┐
        ▼                              ▼
┌─────────────────┐           ┌────────────────────────┐
│ ChatScreen      │           │ ReasoningCard (Float) │
│ • MessageBubble │           │ • Draggable overlay   │
│   with streaming│           │ • Step-by-step stream │
│   text          │           │ • Collapsible/minimize │
└─────────────────┘           └────────────────────────┘
```

---

## 3. Core Components

### 3.1 StreamingState

```kotlin
data class StreamingState(
    val status: StreamingStatus,           // THINKING / ACTING / OBSERVING / DONE / ERROR
    val currentStep: Int = 0,
    val streamedText: String = "",         // accumulated text tokens
    val reasoningChunks: List<ReasoningChunk> = emptyList(),
    val toolCallInProgress: ToolCallState? = null,
    val error: String? = null
)

enum class StreamingStatus {
    THINKING,   // agent is reasoning
    ACTING,      // tool call in progress
    OBSERVING,   // tool result received, reasoning about it
    DONE,        // final answer complete
    ERROR
}

data class ReasoningChunk(
    val type: ReasoningType,              // THOUGHT / ACTION / OBSERVATION / PLAN
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ReasoningType { THOUGHT, ACTION, OBSERVATION, PLAN }

data class ToolCallState(
    val toolName: String,
    val args: Map<String, String>,
    val status: ToolCallStatus  // STARTED / EXECUTING / RESULT
)
```

### 3.2 StreamRenderer

The bridge between SSE chunks and UI state. Lives in the provider layer.

```kotlin
class StreamRenderer(
    private val onChunk: (StreamingState) -> Unit  // callback to update UI
) {
    private val reasoningBuilder = StringBuilder()
    private var currentStep = 0

    fun renderChunk(chunk: String, providerName: String) {
        when (providerName) {
            "openai", "openai-compatible" -> renderOpenAiChunk(chunk)
            "ollama" -> renderOllamaChunk(chunk)
        }
    }

    private fun renderOpenAiChunk(chunk: String) {
        // Parse delta.content → emit token
        // If reasoning chunks present (OpenAI o1/o3 style or custom format) → emit reasoning
        // Detect tool_call delta → emit ACTING status
        // Detect finish_reason → emit DONE
    }

    fun emit(status: StreamingStatus, text: String? = null, reasoning: ReasoningChunk? = null) {
        onChunk(StreamingState(
            status = status,
            currentStep = currentStep,
            streamedText = text ?: "",
            reasoningChunks = reasoning?.let { listOf(it) } ?: emptyList()
        ))
    }
}
```

### 3.3 SSE Provider Interface

Extend `AiProvider` with a streaming method:

```kotlin
interface AiProvider {
    // existing
    suspend fun chatCompletion(request: AgentRequest): AgentResponse

    // new
    suspend fun chatCompletionStream(
        request: AgentRequest,
        onChunk: (StreamChunk) -> Unit
    )
}

data class StreamChunk(
    val type: ChunkType,
    val content: String,      // token text or reasoning text
    val finishReason: String? = null
)

enum class ChunkType { TOKEN, REASONING, TOOL_CALL_START, TOOL_CALL_END, DONE }
```

`BaseOpenAiProvider` implements `chatCompletionStream()` using the existing `chatCompletionStream()` method (already exists but unused). For providers that don't support streaming, fallback to regular `chatCompletion()` with simulated streaming (token-by-token emission with small delay).

### 3.4 Reasoning Card Component

A floating, draggable overlay that shows the agent's reasoning in real-time.

**Position**: Bottom-right corner by default, draggable anywhere on screen.
**States**: Expanded (default), minimized (just icon), collapsed (hidden).
**Content**: Shows reasoning chunks in a scrollable list.

```
┌─────────────────────────────────┐
│ ▼ Reasoning          [−] [×]   │  ← header with collapse/close
├─────────────────────────────────┤
│ [Thought] Let me check your cal  │  ← streaming text
│ [Action] → phone.read_calendar  │  ← tool call highlighted
│ [Observation] 2 meetings at 10am│  ← result appears
│ [Plan] I'll send them an SMS    │  ← next step
└─────────────────────────────────┘
```

**Interactions**:
- **Drag** — reposition anywhere on screen
- **Minimize** ( **[−]** ) — collapse to a small badge showing step count
- **Expand** — click badge to expand again
- **Close** ( **[×]** ) — hide card entirely (can be reopened via button)
- **Auto-show/hide** — card appears when agent starts thinking, stays for 5s after completion then auto-hides

**Animation**:
- New chunks slide in from bottom with 150ms ease-out
- Tool calls pulse briefly with green highlight
- Collapsed badge pulses gently while ACTING

---

## 4. Data Flow

### 4.1 Agent Loop with Streaming

In `AgentLoopExecutor.executeSteps()`:

```kotlin
// Instead of waiting for full response:
val response = withTimeout(MODEL_TIMEOUT_MS) {
    p.chatCompletion(request)
}

// We stream:
p.chatCompletionStream(request) { chunk ->
    when (chunk.type) {
        TOKEN -> streamingState.update { it.copy(
            status = StreamingStatus.THINKING,
            streamedText = it.streamedText + chunk.content
        )}
        REASONING -> streamingState.update { it.copy(
            status = StreamingStatus.THINKING,
            reasoningChunks = it.reasoningChunks + parseReasoning(chunk.content)
        )}
        TOOL_CALL_START -> streamingState.update { it.copy(
            status = StreamingStatus.ACTING,
            toolCallInProgress = ToolCallState(chunk.content, emptyMap(), STARTED)
        )}
        TOOL_CALL_END -> {
            // execute tool
            val result = executeTool(...)
            streamingState.update { it.copy(
                status = StreamingStatus.OBSERVING,
                reasoningChunks = it.reasoningChunks + ReasoningChunk(OBSERVATION, result)
            )}
        }
        DONE -> streamingState.update { it.copy(status = StreamingStatus.DONE) }
    }
}
```

### 4.2 AgentController Integration

```kotlin
// Add streaming state to AgentUiState
data class AgentUiState(
    // ... existing fields ...
    val streamingState: StreamingState? = null,
    val isReasoningCardVisible: Boolean = false
)

// sendMessage() starts streaming loop
loopExecutor.startLoopStreaming(
    message = message,
    model = selectedModel,
    systemPrompt = augmentedPrompt,
    temperature = routingTemp,
    worldModel = personalWorldModel,
    onStreamingState = { state ->
        _uiState.update { it.copy(
            streamingState = state,
            isReasoningCardVisible = state.status != StreamingStatus.DONE
        )}
    },
    onComplete = { finalAnswer ->
        _uiState.update { it.copy(streamingState = null, isReasoningCardVisible = false) }
        // append final answer to messages
    }
)
```

### 4.3 UI Updates

In `ChatScreen.kt`:

```kotlin
// Stream tokens into message bubble as they arrive
streamingState?.let { state ->
    // Update reasoning card
    if (state.isReasoningCardVisible) {
        ReasoningCard(
            state = state,
            onMinimize = { viewModel.minimizeReasoningCard() },
            onClose = { viewModel.hideReasoningCard() },
            onDragEnd = { offset -> viewModel.moveReasoningCard(offset) }
        )
    }

    // Stream text into the latest message bubble
    if (state.streamedText.isNotEmpty()) {
        latestMessageBunch.update { it.copy(content = state.streamedText) }
    }
}
```

---

## 5. Provider Streaming Implementation

### 5.1 BaseOpenAiProvider

```kotlin
override suspend fun chatCompletionStream(
    request: AgentRequest,
    onChunk: (StreamChunk) -> Unit
) {
    // Already has SSE streaming via chatCompletionStream() — just pipe through
    chatCompletionStream(request) { chunk ->
        val streamChunk = StreamingParser.parse(chunk)
        onChunk(streamChunk)
    }
}

// Existing method to extend:
private suspend fun chatCompletionStream(
    request: AgentRequest,
    callback: (String) -> Unit
) {
    // uses okHttp to make POST request with EventSourceListener
    // chunks come in via onEvent()
    // callback emits raw chunk string
}
```

### 5.2 StreamingParser Extension

```kotlin
object StreamingParser {

    fun parseChunk(chunk: String, providerName: String): StreamChunk {
        return when (providerName) {
            "openai", "openai-compatible" -> parseOpenAiChunk(chunk)
            "ollama" -> parseOllamaChunk(chunk)
            else -> parseGenericChunk(chunk)
        }
    }

    private fun parseOpenAiChunk(chunk: String): StreamChunk {
        val json = JSONObject(chunk)
        val delta = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?: return StreamChunk(ChunkType.TOKEN, "")

        // Check for reasoning content (OpenAI o1/o3 reasoning model format)
        delta.optString("reasoning_content", null)?.let {
            if (it.isNotBlank()) return StreamChunk(ChunkType.REASONING, it)
        }

        // Regular token
        delta.optString("content", null)?.let {
            if (it.isNotBlank()) return StreamChunk(ChunkType.TOKEN, it)
        }

        // Tool call detection
        val finishReason = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optString("finish_reason")

        if (finishReason == "tool_calls") {
            return StreamChunk(ChunkType.TOOL_CALL_END, "")
        }

        return StreamChunk(ChunkType.TOKEN, "")
    }
}
```

### 5.3 Ollama Streaming

Ollama uses SSE with `data: ` prefix and `done` signal:

```kotlin
private fun parseOllamaChunk(line: String): StreamChunk {
    if (!line.startsWith("data: ")) return StreamChunk(ChunkType.TOKEN, "")
    val json = line.removePrefix("data: ").trim()
    if (json == "[DONE]") return StreamChunk(ChunkType.DONE, "")

    val obj = JSONObject(json)
    val content = obj.optJSONArray("message")
        ?.optJSONObject(0)
        ?.optString("content", null)
        ?: obj.optString("content", null)
        ?: ""

    return StreamChunk(ChunkType.TOKEN, content)
}
```

### 5.4 Fallback for Non-Streaming Providers

If provider doesn't support streaming:

```kotlin
private suspend fun streamWithFallback(
    request: AgentRequest,
    onChunk: (StreamChunk) -> Unit
) {
    val response = chatCompletion(request)
    // Simulate streaming: emit tokens with 20ms delay
    val words = response.content.split(" ")
    for (word in words) {
        onChunk(StreamChunk(ChunkType.TOKEN, word + " "))
        delay(20)
    }
    onChunk(StreamChunk(ChunkType.DONE, ""))
}
```

---

## 6. Reasoning Card UI

### 6.1 PositionState

```kotlin
data class ReasoningCardPosition(
    val x: Float = 0f,
    val y: Float = 0f  // offset from bottom-right
)

// Remember position in DataStore
val reasoningCardPosition = dataStore.data.map {
    it[REASONING_CARD_POSITION]?.let { pos ->
        val parts = pos.split(",")
        ReasoningCardPosition(parts[0].toFloat(), parts[1].toFloat())
    } ?: ReasoningCardPosition(0f, 100f)
}
```

### 6.2 Floating Card Implementation

Uses Compose `Modifier.draggable()` or pointer input for drag:

```kotlin
@Composable
fun ReasoningCard(
    state: StreamingState,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onDragEnd: (Offset) -> Unit
) {
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDragEnd(offset) },
                    onDrag = { change, dragAmount ->
                        offset += dragAmount
                    }
                )
            }
    ) {
        Card(
            elevation = 8.dp,
            backgroundColor = MaterialTheme.colors.surface
        ) {
            Column {
                // Header row with drag handle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Reasoning", style = MaterialTheme.typography.Subtitle2)
                    Row {
                        IconButton(onClick = onMinimize) { Text("−") }
                        IconButton(onClick = onClose) { Text("×") }
                    }
                }
                HorizontalDivider()
                // Reasoning chunks list
                LazyColumn {
                    items(state.reasoningChunks) { chunk ->
                        ReasoningChunkRow(chunk)
                    }
                }
                // Tool call state if active
                state.toolCallInProgress?.let { tool ->
                    ToolCallRow(tool)
                }
            }
        }
    }
}

@Composable
fun ReasoningChunkRow(chunk: ReasoningChunk) {
    val color = when (chunk.type) {
        THOUGHT -> Color.Blue
        ACTION -> Color.Green
        OBSERVATION -> Color.Gray
        PLAN -> Color.Magenta
    }
    val prefix = when (chunk.type) {
        THOUGHT -> "[Thought]"
        ACTION -> "[Action]"
        OBSERVATION -> "[Obs]"
        PLAN -> "[Plan]"
    }
    Text(
        text = "$prefix ${chunk.content}",
        color = color,
        style = MaterialTheme.typography.Body2
    )
}
```

### 6.3 Minimized State

When minimized, show a small floating badge:

```kotlin
@Composable
fun MinimizedReasoningBadge(
    stepCount: Int,
    status: StreamingStatus,
    onExpand: () -> Unit
) {
    val badgeColor = when (status) {
        StreamingStatus.THINKING -> Color.Blue
        StreamingStatus.ACTING -> Color.Green
        else -> Color.Gray
    }

    Badge(
        backgroundColor = badgeColor,
        modifier = Modifier.clickable { onExpand() }
    ) {
        Text("$stepCount steps", color = Color.White)
    }
}
```

---

## 7. Error Handling

| Scenario | Behavior |
|-----------|----------|
| Provider doesn't support streaming | Fall back to `chatCompletion()` with simulated token streaming |
| Stream disconnects mid-response | Show partial result, mark as truncated, allow retry |
| Parsing error in chunk | Skip chunk, log warning, continue |
| Tool execution fails during stream | Emit error in reasoning card, allow agent to continue or abort |

```kotlin
// In stream rendering:
try {
    renderChunk(chunk)
} catch (e: Exception) {
    android.util.Log.w(TAG, "Chunk parse error: ${e.message}")
    // Skip this chunk, continue
}
```

---

## 8. Testing

### Manual Testing Checklist
- [ ] Text streams token-by-token in message bubble
- [ ] Reasoning card shows chunks as they arrive
- [ ] Draggable card can be moved and persists position
- [ ] Minimize button collapses to badge
- [ ] Close button hides card
- [ ] Card auto-appears when agent starts, auto-hides 5s after done
- [ ] Ollama streaming works (local provider)
- [ ] OpenAI streaming works (cloud provider)
- [ ] Fallback for non-streaming providers works

### Automated Tests
- `StreamingParserTest` — parse each chunk type correctly
- `StreamRendererTest` — state transitions and chunk accumulation
- `ReasoningCardPositionTest` — position persistence

---

## 9. Implementation Phases

### Phase 3A — Core Streaming
- [ ] Extend `AiProvider` with `chatCompletionStream()` interface
- [ ] Implement `chatCompletionStream()` in `BaseOpenAiProvider`
- [ ] Add `streamingState` to `AgentUiState`
- [ ] Add `StreamRenderer` class
- [ ] Wire streaming into `AgentLoopExecutor`
- [ ] Add streaming variants of `startLoop()` and `resumeAfterConfirmation()`

### Phase 3B — UI Components
- [ ] Create `ReasoningCard` composable (draggable, collapsible)
- [ ] Create `MinimizedReasoningBadge` composable
- [ ] Add position persistence (DataStore)
- [ ] Update `ChatScreen` to show streaming message bubble
- [ ] Auto-show/hide logic for reasoning card

### Phase 3C — Provider Coverage
- [ ] Ollama streaming support
- [ ] Fallback for non-streaming providers
- [ ] Error handling and reconnection

---

## 10. Key Files to Modify

| File | Change |
|------|--------|
| `AiProvider.kt` | Add `chatCompletionStream()` to interface |
| `BaseOpenAiProvider.kt` | Implement streaming, wire `StreamingParser` |
| `StreamingParser.kt` | Extend to parse reasoning chunks, tool calls |
| `AgentUiState.kt` | Add `streamingState: StreamingState?` field |
| `AgentLoopExecutor.kt` | Add streaming variant of loop methods |
| `AgentController.kt` | Wire streaming state to UI, add callback |
| `ChatScreen.kt` | Add streaming message bubble, reasoning card |
| `ReasoningCard.kt` | NEW: draggable reasoning card composable |
| `ReasoningCardState.kt` | NEW: position state, persistence |

---

## 11. Success Metrics

- User sees first token within 500ms of agent starting
- Reasoning card shows step progress as it happens
- No visible lag between tokens appearing
- Card can be dragged to any position and stays there
- Streaming works with both cloud (OpenAI) and local (Ollama) providers