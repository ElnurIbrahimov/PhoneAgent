package com.phoneagent.providers

object CrofAiDefaults {
    const val BASE_URL = "https://crof.ai/v1"
    const val NAME = "CrofAI"
    const val ID = "crofai_default"
    val PROVIDER_TYPE = ProviderType.OPENAI_COMPATIBLE

    val MODELS = listOf(
        "deepseek-v4-pro",
        "deepseek-v3.2",
        "glm-5.1",
        "glm-5.1-precision",
        "kimi-k2.6",
        "kimi-k2.6-precision",
        "kimi-k2.5",
        "kimi-k2.5-lightning",
        "gemma-4-31b-it",
        "minimax-m2.5",
        "qwen3.6-27b",
        "qwen3.5-397b-a17b",
        "qwen3.5-9b",
        "qwen3.5-9b-chat"
    )

    val DEFAULT_CONFIG = ProviderConfig(
        id = ID,
        name = NAME,
        type = PROVIDER_TYPE,
        baseUrl = BASE_URL,
        defaultModel = MODELS.first(),
        availableModels = MODELS,
        isEnabled = true,
        streamEnabled = false
    )
}
