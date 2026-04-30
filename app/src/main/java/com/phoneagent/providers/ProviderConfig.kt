package com.phoneagent.providers

data class ProviderConfig(
    val id: String,
    val name: String,
    val type: ProviderType,
    val baseUrl: String,
    val apiKey: String? = null,
    val defaultModel: String? = null,
    val availableModels: List<String> = emptyList(),
    val isEnabled: Boolean = true,
    val streamEnabled: Boolean = false
) {
    override fun toString(): String {
        return "ProviderConfig(id=$id, name=$name, type=$type, baseUrl=$baseUrl, apiKey=${if (apiKey != null) "***" else "null"}, defaultModel=$defaultModel, availableModels=$availableModels, isEnabled=$isEnabled, streamEnabled=$streamEnabled)"
    }
}
