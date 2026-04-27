package com.phoneagent.agent

import com.phoneagent.providers.AiProvider
import com.phoneagent.providers.CrofAiDefaults
import com.phoneagent.providers.OpenAiCompatibleProvider
import com.phoneagent.providers.ProviderConfig
import com.phoneagent.providers.ProviderRepository
import com.phoneagent.security.SecretStore
import kotlinx.coroutines.flow.first

class ModelRouter(
    private val providerRepository: ProviderRepository,
    private val secretStore: SecretStore? = null
) {

    suspend fun getProviderForModel(model: String): AiProvider {
        val providers = providerRepository.providers.first()
        val providerConfig = providers.find { it.availableModels.contains(model) && it.isEnabled }
            ?: providers.firstOrNull { it.isEnabled }
            ?: CrofAiDefaults.DEFAULT_CONFIG

        return createProvider(injectApiKey(providerConfig))
    }

    suspend fun getDefaultProvider(): AiProvider {
        val config = providerRepository.getDefaultProvider()
        return createProvider(injectApiKey(config))
    }

    private suspend fun injectApiKey(config: ProviderConfig): ProviderConfig {
        val secretStore = this.secretStore ?: return config
        val key = secretStore.getSecret("api_key_${config.id}")
        return if (key != null) config.copy(apiKey = key) else config
    }

    private fun createProvider(config: ProviderConfig): AiProvider {
        return when (config.type) {
            com.phoneagent.providers.ProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(config)
            com.phoneagent.providers.ProviderType.OLLAMA -> com.phoneagent.providers.OllamaProvider(config)
        }
    }
}
