package com.phoneagent.agent

data class ChatMessage(
    val role: String,
    val content: String
)

data class AgentUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentModel: String = "",
    val currentProvider: String = ""
)
