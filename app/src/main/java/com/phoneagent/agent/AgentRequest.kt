package com.phoneagent.agent

data class AgentRequest(
    val message: String,
    val model: String,
    val systemPrompt: String = "You are PhoneAgent, a concise Android-native assistant running from the user's phone.",
    val temperature: Double = 0.7,
    val stream: Boolean = false,
    val visionPayload: String? = null
)
