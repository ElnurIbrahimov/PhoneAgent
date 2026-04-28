package com.phoneagent.providers

object OllamaDefaults {
    const val BASE_URL = "https://api.ollama.com/v1"
    const val NAME = "Ollama Cloud"
    const val ID = "ollama_cloud_default"

    val MODELS = listOf(
        "minimax-m2.7",
        "qwen3.5-32b",
        "deepseek-r1-distill-qwen-32b",
        "llama4-12b"
    )

    val DEFAULT_CONFIG = ProviderConfig(
        id = ID,
        name = NAME,
        type = ProviderType.OLLAMA,
        baseUrl = BASE_URL,
        defaultModel = "minimax-m2.7",
        availableModels = MODELS,
        isEnabled = true,
        streamEnabled = false
    )
}
