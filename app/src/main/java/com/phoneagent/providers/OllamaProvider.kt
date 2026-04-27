package com.phoneagent.providers

import com.phoneagent.agent.AgentRequest
import com.phoneagent.agent.AgentResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class OllamaProvider(
    override val config: ProviderConfig
) : AiProvider {

    override suspend fun chatCompletion(request: AgentRequest): AgentResponse {
        // Phase 1: Ollama provider is a placeholder
        return AgentResponse(
            content = "Ollama provider is not yet implemented in Phase 1.",
            model = request.model
        )
    }

    override fun chatCompletionStream(request: AgentRequest): Flow<String> = flow {
        emit("Ollama streaming is not yet implemented in Phase 1.")
    }

    override fun isAvailable(): Boolean {
        return config.baseUrl.isNotBlank()
    }
}
