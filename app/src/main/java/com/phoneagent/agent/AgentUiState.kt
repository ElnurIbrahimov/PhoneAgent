package com.phoneagent.agent

data class ChatMessage(
    val id: Int = 0,
    val role: String,
    val content: String
) {
    companion object {
        private var counter = 0
        fun nextId(): Int = counter++
    }
}

data class AgentUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentModel: String = "",
    val currentProvider: String = "",
    val agentStepStatus: String? = null,
    val currentSteps: List<AgentStep> = emptyList(),
    val pendingConfirmation: PendingConfirmation? = null
)
