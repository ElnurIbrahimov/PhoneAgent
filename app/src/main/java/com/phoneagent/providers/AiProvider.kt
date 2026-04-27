package com.phoneagent.providers

import kotlinx.coroutines.flow.Flow

interface AiProvider {
    val config: ProviderConfig
    suspend fun chatCompletion(request: com.phoneagent.agent.AgentRequest): com.phoneagent.agent.AgentResponse
    fun chatCompletionStream(request: com.phoneagent.agent.AgentRequest): Flow<String>
    fun isAvailable(): Boolean
}
