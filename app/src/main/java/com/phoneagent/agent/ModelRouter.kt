package com.phoneagent.agent

import com.phoneagent.providers.AiProvider
import com.phoneagent.providers.OpenAiCompatibleProvider
import com.phoneagent.providers.ProviderConfig
import com.phoneagent.providers.ProviderRepository
import com.phoneagent.security.SecretStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

class ModelRouter(
    private val providerRepository: ProviderRepository,
    private val secretStore: SecretStore? = null
) {

    private suspend fun getProviders() = withTimeout(5_000) {
        providerRepository.providers.first()
    }

    suspend fun getProviderForModel(model: String): AiProvider {
        val providers = getProviders()
        val normalizedModel = model.trim().lowercase()
        val providerConfig = providers.find {
            it.isEnabled && it.availableModels.any { m -> m.trim().lowercase() == normalizedModel }
        }
        if (providerConfig == null) {
            android.util.Log.w("ModelRouter", "Model '$model' not found in any enabled provider, falling back to default")
        }
        val selectedConfig = providerConfig
            ?: providers.firstOrNull { it.isEnabled }
            ?: throw IllegalStateException("No enabled provider available. Configure a provider in Settings.")

        return createProvider(injectApiKey(selectedConfig))
    }

    suspend fun getAllProvidersForModel(model: String): List<AiProvider> {
        val providers = getProviders()
        val normalizedModel = model.trim().lowercase()
        val matching = providers
            .filter { it.isEnabled && it.availableModels.any { m -> m.trim().lowercase() == normalizedModel } }
            .map { createProvider(injectApiKey(it)) }
        if (matching.isNotEmpty()) return matching

        val fallback = providers.firstOrNull { it.isEnabled }
        if (fallback != null) {
            return listOf(createProvider(injectApiKey(fallback)))
        }
        throw IllegalStateException("No enabled provider available. Configure a provider in Settings.")
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
