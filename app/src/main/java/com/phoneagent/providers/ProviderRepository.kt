package com.phoneagent.providers

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "provider_settings")

class ProviderRepository(private val context: Context) {

    private val providersKey = stringPreferencesKey("providers")

    private val defaultProviders = listOf(
        CrofAiDefaults.DEFAULT_CONFIG,
        OllamaDefaults.DEFAULT_CONFIG,
        OpenCodeGoDefaults.DEFAULT_CONFIG
    )

    val providers: Flow<List<ProviderConfig>> = context.dataStore.data.map { prefs ->
        val json = prefs[providersKey] ?: return@map defaultProviders
        parseProvidersJson(json)
    }

    suspend fun getDefaultProvider(): ProviderConfig {
        return providers.first().firstOrNull { it.isEnabled } ?: CrofAiDefaults.DEFAULT_CONFIG
    }

    suspend fun saveProvider(config: ProviderConfig) {
        context.dataStore.edit { prefs ->
            val current = if (prefs[providersKey] != null) {
                parseProvidersJson(prefs[providersKey]!!).toMutableList()
            } else {
                defaultProviders.toMutableList()
            }
            val index = current.indexOfFirst { it.id == config.id }
            if (index >= 0) {
                current[index] = config
            } else {
                current.add(config)
            }
            prefs[providersKey] = providersToJson(current)
        }
    }

    suspend fun removeProvider(id: String) {
        context.dataStore.edit { prefs ->
            val current = parseProvidersJson(prefs[providersKey] ?: "[]").toMutableList()
            current.removeAll { it.id == id }
            prefs[providersKey] = providersToJson(current)
        }
    }

    suspend fun initializeDefaults() {
        context.dataStore.edit { prefs ->
            if (prefs[providersKey] == null) {
                prefs[providersKey] = providersToJson(defaultProviders)
            }
        }
    }

    private fun parseProvidersJson(json: String): List<ProviderConfig> {
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                ProviderConfig(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    type = ProviderType.valueOf(obj.getString("type")),
                    baseUrl = obj.getString("baseUrl"),
                    apiKey = obj.optString("apiKey", null),
                    defaultModel = obj.optString("defaultModel", null),
                    availableModels = obj.optJSONArray("availableModels")?.let { arr ->
                        List(arr.length()) { arr.getString(it) }
                    } ?: emptyList(),
                    isEnabled = obj.optBoolean("isEnabled", true),
                    streamEnabled = obj.optBoolean("streamEnabled", false)
                )
            }
        } catch (e: Exception) {
            defaultProviders
        }
    }

    private fun providersToJson(configs: List<ProviderConfig>): String {
        val array = JSONArray()
        configs.forEach { config ->
            array.put(JSONObject().apply {
                put("id", config.id)
                put("name", config.name)
                put("type", config.type.name)
                put("baseUrl", config.baseUrl)
                put("apiKey", config.apiKey)
                put("defaultModel", config.defaultModel)
                put("availableModels", JSONArray(config.availableModels))
                put("isEnabled", config.isEnabled)
                put("streamEnabled", config.streamEnabled)
            })
        }
        return array.toString()
    }
}
