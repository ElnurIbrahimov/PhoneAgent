package com.phoneagent.agent

data class AgentResponse(
    val content: String,
    val model: String,
    val finishReason: String? = null
)
