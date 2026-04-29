package com.phoneagent.providers

object OpenCodeGoDefaults {
    const val BASE_URL = "http://localhost:8080/v1"
    const val NAME = "OpenCode Go"
    const val ID = "opencodego_default"
    val PROVIDER_TYPE = ProviderType.OPENAI_COMPATIBLE

    val MODELS = listOf(
        "opencode-go-default"
    )

    val DEFAULT_CONFIG = ProviderConfig(
        id = ID,
        name = NAME,
        type = PROVIDER_TYPE,
        baseUrl = BASE_URL,
        defaultModel = MODELS.first(),
        availableModels = MODELS,
        isEnabled = false,
        streamEnabled = false
    )
}
