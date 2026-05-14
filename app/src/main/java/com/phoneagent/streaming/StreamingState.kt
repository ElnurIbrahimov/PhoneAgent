package com.phoneagent.streaming

enum class StreamingStatus {
    THINKING,
    ACTING,
    OBSERVING,
    DONE,
    ERROR
}

enum class ReasoningType {
    THOUGHT,
    ACTION,
    OBSERVATION,
    PLAN
}

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

enum class ToolCallStatus {
    STARTED,
    EXECUTING,
    RESULT
}

data class StreamingState(
    val status: StreamingStatus = StreamingStatus.THINKING,
    val currentStep: Int = 0,
    val streamedText: String = "",
    val reasoningChunks: List<ReasoningChunk> = emptyList(),
    val toolCallInProgress: ToolCallState? = null,
    val error: String? = null
)
