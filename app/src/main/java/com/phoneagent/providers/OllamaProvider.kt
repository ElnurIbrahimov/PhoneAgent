package com.phoneagent.providers

class OllamaProvider(override val config: ProviderConfig) : BaseOpenAiProvider() {
    override val readTimeoutSeconds: Long = 120L
}
