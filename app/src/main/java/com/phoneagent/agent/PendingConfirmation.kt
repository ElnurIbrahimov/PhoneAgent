package com.phoneagent.agent

data class PendingConfirmation(
    val toolName: String,
    val args: Map<String, String>,
    val reason: String,
    val taskId: String,
    val userRequest: String,
    val stepsSoFar: List<AgentStep>,
    val systemPrompt: String,
    val selectedModel: String,
    val temperature: Double = 0.5
)
