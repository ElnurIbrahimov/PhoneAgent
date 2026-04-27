package com.phoneagent.agent

import android.content.Context
import com.phoneagent.overlay.OverlayState
import com.phoneagent.memory.AppDatabase
import com.phoneagent.memory.MemoryRepository
import com.phoneagent.providers.AiProvider
import com.phoneagent.providers.ProviderError
import com.phoneagent.providers.ProviderRepository
import com.phoneagent.security.AndroidKeystoreSecretStore
import com.phoneagent.security.SecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentController(context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val providerRepository = ProviderRepository(context)
    private val modelRouter = ModelRouter(providerRepository)
    private val secretStore: SecretStore = AndroidKeystoreSecretStore(context)

    private val database = AppDatabase.getDatabase(context)
    private val memoryRepository = MemoryRepository(database.memoryDao(), database.taskDao())
    val taskHistoryManager = TaskHistoryManager(memoryRepository)

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private val _overlayState = MutableStateFlow<OverlayState>(OverlayState.Hidden)
    val overlayState: StateFlow<OverlayState> = _overlayState.asStateFlow()

    val toolRegistry = ToolRegistry()

    init {
        scope.launch {
            providerRepository.initializeDefaults()
            val defaultProvider = modelRouter.getDefaultProvider()
            _uiState.update {
                it.copy(
                    currentProvider = defaultProvider.config.name,
                    currentModel = defaultProvider.config.defaultModel ?: ""
                )
            }
        }
    }

    fun sendMessage(message: String, model: String? = null) {
        scope.launch {
            val selectedModel = model ?: _uiState.value.currentModel
            if (selectedModel.isBlank()) {
                _uiState.update { it.copy(error = "No model selected") }
                return@launch
            }

            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage("user", message),
                    isLoading = true,
                    error = null
                )
            }

            try {
                val provider = modelRouter.getProviderForModel(selectedModel)
                val request = AgentRequest(
                    message = message,
                    model = selectedModel,
                    stream = provider.config.streamEnabled
                )

                val response = if (provider.config.streamEnabled) {
                    handleStreamingResponse(provider, request)
                } else {
                    provider.chatCompletion(request)
                }

                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage("assistant", response.content),
                        isLoading = false,
                        error = null
                    )
                }

                memoryRepository.insertMessage(message, response.content, selectedModel)
            } catch (e: ProviderError) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Provider error"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    private suspend fun handleStreamingResponse(provider: AiProvider, request: AgentRequest): AgentResponse {
        val sb = StringBuilder()
        provider.chatCompletionStream(request).collect { chunk ->
            sb.append(chunk)
            _uiState.update {
                val messages = it.messages.toMutableList()
                if (messages.isNotEmpty() && messages.last().role == "assistant") {
                    messages[messages.lastIndex] = ChatMessage("assistant", sb.toString())
                } else {
                    messages.add(ChatMessage("assistant", sb.toString()))
                }
                it.copy(messages = messages)
            }
        }
        return AgentResponse(content = sb.toString(), model = request.model)
    }

    fun clearChat() {
        _uiState.update { it.copy(messages = emptyList(), error = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setOverlayState(state: OverlayState) {
        _overlayState.value = state
    }

    suspend fun storeApiKey(providerId: String, apiKey: String) {
        withContext(Dispatchers.IO) {
            secretStore.storeSecret("api_key_$providerId", apiKey)
        }
    }

    suspend fun getApiKey(providerId: String): String? {
        return withContext(Dispatchers.IO) {
            secretStore.getSecret("api_key_$providerId")
        }
    }

    fun destroy() {
        scope.cancel()
    }
}
