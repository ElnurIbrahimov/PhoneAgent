package com.phoneagent.streaming

enum class ChunkType {
    TOKEN,
    REASONING,
    TOOL_CALL_START,
    TOOL_CALL_END,
    DONE
}

data class StreamChunk(
    val type: ChunkType,
    val content: String,
    val toolName: String? = null,
    val toolArgs: Map<String, String>? = null
)
