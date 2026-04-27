package com.phoneagent.agent

import com.phoneagent.providers.AiProvider
import com.phoneagent.providers.CrofAiDefaults
import com.phoneagent.providers.OpenAiCompatibleProvider
import com.phoneagent.providers.ProviderConfig
import com.phoneagent.providers.ProviderRepository
import kotlinx.coroutines.flow.first

class ModelRouter(private val providerRepository: ProviderRepository) {

    suspend fun getProviderForModel(model: String): AiProvider {
        val providers = providerRepository.providers.first()
        val providerConfig = providers.find { it.availableModels.contains(model) && it.isEnabled }
            ?: providers.firstOrNull { it.isEnabled }
            ?: CrofAiDefaults.DEFAULT_CONFIG

        return createProvider(providerConfig)
    }

    suspend fun getDefaultProvider(): AiProvider {
        val config = providerRepository.getDefaultProvider()
        return createProvider(config)
    }

    private fun createProvider(config: ProviderConfig): AiProvider {
        return when (config.type) {
            com.phoneagent.providers.ProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(config)
            com.phoneagent.providers.ProviderType.OLLAMA -> com.phoneagent.providers.OllamaProvider(config)
        }
    }
}
