package com.phoneagent.agent

import android.content.Context
import com.phoneagent.agent.tools.BrowserActionTool
import com.phoneagent.agent.tools.PhoneListAppsTool
import com.phoneagent.agent.tools.PhoneOpenAppTool
import com.phoneagent.agent.tools.PhoneSystemInfoTool
import com.phoneagent.browser.BrowserTool
import com.phoneagent.overlay.OverlayState
import com.phoneagent.memory.AppDatabase
import com.phoneagent.memory.MemoryRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentController(context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val providerRepository = ProviderRepository(context)
    private val secretStore: SecretStore = AndroidKeystoreSecretStore(context)
    private val modelRouter = ModelRouter(providerRepository, secretStore)

    private val database = AppDatabase.getDatabase(context)
    private val memoryRepository = MemoryRepository(database.memoryDao(), database.taskDao())
    val taskHistoryManager = TaskHistoryManager(memoryRepository)

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private val _overlayState = MutableStateFlow<OverlayState>(OverlayState.Hidden)
    val overlayState: StateFlow<OverlayState> = _overlayState.asStateFlow()

    val toolRegistry = ToolRegistry()

    private val loopExecutor: AgentLoopExecutor

    init {
        val browserTool = BrowserTool(context)
        toolRegistry.registerAll(
            listOf(
                PhoneListAppsTool(context),
                PhoneOpenAppTool(context),
                PhoneSystemInfoTool(context),
                BrowserActionTool("browser.open_url", "Open a URL in the agent browser.", browserTool, "open_url", "url (String)"),
                BrowserActionTool("browser.read_page", "Read the text content of the current browser page.", browserTool, "read_page", ""),
                BrowserActionTool("browser.read_metadata", "Read page metadata including title, URL, links, buttons, and inputs.", browserTool, "read_metadata", ""),
                BrowserActionTool("browser.click_text", "Click an element by its visible text.", browserTool, "click_text", "text (String)"),
                BrowserActionTool("browser.click_selector", "Click an element by CSS selector.", browserTool, "click_selector", "selector (String)"),
                BrowserActionTool("browser.type_into_selector", "Type text into an input identified by CSS selector.", browserTool, "type_into_selector", "selector (String), text (String)"),
                BrowserActionTool("browser.type_into_focused", "Type text into the currently focused input element.", browserTool, "type_into_focused", "text (String)"),
                BrowserActionTool("browser.scroll", "Scroll the page up or down.", browserTool, "scroll", "direction (String: up/down)"),
                BrowserActionTool("browser.back", "Go back in browser history.", browserTool, "back", ""),
                BrowserActionTool("browser.reload", "Reload the current page.", browserTool, "reload", "")
            )
        )

        loopExecutor = AgentLoopExecutor(
            modelRouter = modelRouter,
            toolRegistry = toolRegistry,
            taskHistoryManager = taskHistoryManager,
            applyState = { block -> _uiState.update(block) }
        )

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
        val selectedModel = model ?: _uiState.value.currentModel
        if (selectedModel.isBlank()) {
            _uiState.update { it.copy(error = "No model selected") }
            return
        }

        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage("user", message),
                isLoading = true,
                error = null,
                agentStepStatus = "Thinking",
                currentSteps = emptyList(),
                pendingConfirmation = null
            )
        }

        val systemPrompt = AgentPromptBuilder.buildSystemPrompt(toolRegistry.listTools())
        loopExecutor.startLoop(
            message = message,
            model = selectedModel,
            systemPrompt = systemPrompt
        )
    }

    fun approvePendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.update { it.copy(pendingConfirmation = null) }
        loopExecutor.resumeAfterConfirmation(pending, approved = true)
    }

    fun cancelPendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.update { it.copy(pendingConfirmation = null) }
        loopExecutor.resumeAfterConfirmation(pending, approved = false)
    }

    fun clearChat() {
        _uiState.update {
            it.copy(
                messages = emptyList(),
                error = null,
                agentStepStatus = null,
                currentSteps = emptyList(),
                pendingConfirmation = null
            )
        }
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

    suspend fun getAllAvailableModels(): List<String> {
        return providerRepository.providers.first()
            .filter { it.isEnabled }
            .flatMap { it.availableModels }
            .distinct()
    }

    fun getProviderRepository(): ProviderRepository = providerRepository

    fun destroy() {
        scope.cancel()
        loopExecutor.destroy()
    }
}
