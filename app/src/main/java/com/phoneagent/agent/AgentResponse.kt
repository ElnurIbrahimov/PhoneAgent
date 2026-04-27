package com.phoneagent.agent

data class AgentResponse(
    val content: String,
    val model: String,
    val usage: String? = null,
    val finishReason: String? = null
)
