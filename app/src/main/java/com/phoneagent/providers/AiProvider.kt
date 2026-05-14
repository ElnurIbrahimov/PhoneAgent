package com.phoneagent.providers

import com.phoneagent.agent.AgentRequest
import com.phoneagent.agent.AgentResponse
import com.phoneagent.streaming.StreamChunk
import kotlinx.coroutines.flow.Flow

interface AiProvider {
    val config: ProviderConfig
    suspend fun chatCompletion(request: AgentRequest): AgentResponse
    fun chatCompletionStream(request: AgentRequest): Flow<String>
    suspend fun chatCompletionStream(request: AgentRequest, onChunk: (StreamChunk) -> Unit)
    fun isAvailable(): Boolean
}
