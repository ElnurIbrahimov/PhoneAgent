package com.phoneagent.agent

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.phoneagent.agent.tools.AccessibilityBackTool
import com.phoneagent.agent.tools.AccessibilityForegroundTool
import com.phoneagent.agent.tools.AccessibilityHomeTool
import com.phoneagent.agent.tools.AccessibilityReadTreeTool
import com.phoneagent.agent.tools.AccessibilitySwipeTool
import com.phoneagent.agent.tools.AccessibilityTapAtTool
import com.phoneagent.agent.tools.AccessibilityTapTextTool
import com.phoneagent.agent.tools.AccessibilityTypeTool
import com.phoneagent.agent.tools.BrowserActionTool
import com.phoneagent.agent.tools.PhoneCallTool
import com.phoneagent.agent.tools.PhoneClipboardTool
import com.phoneagent.agent.tools.PhoneListAppsTool
import com.phoneagent.agent.tools.PhoneNotificationsTool
import com.phoneagent.agent.tools.PhoneOpenAppTool
import com.phoneagent.agent.tools.PhoneScreenshotTool
import com.phoneagent.agent.tools.PhoneSendSmsTool
import com.phoneagent.agent.tools.PhoneSettingsTool
import com.phoneagent.agent.tools.PhoneSystemInfoTool
import com.phoneagent.browser.BrowserTool
import com.phoneagent.kira.KiraBridge
import com.phoneagent.memory.AppDatabase
import com.phoneagent.memory.MemoryRepository
import com.phoneagent.perception.OcrManager
import com.phoneagent.perception.OcrManagerImpl
import com.phoneagent.perception.ScreenCaptureManager
import com.phoneagent.perception.ScreenCaptureManagerImpl
import com.phoneagent.perception.VisionPayloadBuilder
import com.phoneagent.providers.ProviderRepository
import com.phoneagent.security.AndroidKeystoreSecretStore
import com.phoneagent.security.SecretStore
import com.phoneagent.soma.BeliefEngine
import com.phoneagent.soma.LpmManager
import com.phoneagent.soma.MemScenesEngine
import com.phoneagent.soma.MemoryEngine
import com.phoneagent.soma.SomaContextBuilder
import com.phoneagent.voice.SpeechOutputManager
import com.phoneagent.voice.SpeechOutputManagerImpl
import com.phoneagent.voice.VoiceInputManager
import com.phoneagent.voice.VoiceInputManagerImpl
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
import java.util.UUID

class AgentController(context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val providerRepository = ProviderRepository(context)
    private val secretStore: SecretStore = AndroidKeystoreSecretStore(context)
    private val modelRouter = ModelRouter(providerRepository, secretStore)

    val database = AppDatabase.getDatabase(context)
    private val memoryRepository = MemoryRepository(database.memoryDao(), database.taskDao())
    val taskHistoryManager = TaskHistoryManager(memoryRepository)

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    val toolRegistry = ToolRegistry()

    private val screenCaptureManager: ScreenCaptureManager = ScreenCaptureManagerImpl(context)
    private val ocrManager: OcrManager = OcrManagerImpl()

    private val voiceInputManager: VoiceInputManager = VoiceInputManagerImpl(context)
    private val speechOutputManager: SpeechOutputManager = SpeechOutputManagerImpl(context)

    var onVoiceResult: ((String) -> Unit)? = null
    var onVoiceError: ((String) -> Unit)? = null

    private val loopExecutor: AgentLoopExecutor
    private val loopRunning = java.util.concurrent.atomic.AtomicBoolean(false)

    val beliefEngine = BeliefEngine(database.beliefDao())
    val memoryEngine = MemoryEngine(database.somaMemoryDao())
    val lpmManager = LpmManager(database.lpmDao())
    val memScenesEngine = MemScenesEngine(database.memSceneDao(), memoryEngine)
    val somaContextBuilder = SomaContextBuilder(lpmManager, beliefEngine, memoryEngine)
    val irisRouter = com.phoneagent.iris.IrisRouter()

    val kiraBridge = KiraBridge()

    private var currentSessionId: String = UUID.randomUUID().toString()
    private var sessionMessages: MutableList<String> = mutableListOf()

    private val connectivityCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scope.launch {
                _uiState.update { it.copy(isOnline = true) }
            }
        }
        override fun onLost(network: Network) {
            scope.launch {
                _uiState.update { it.copy(isOnline = false) }
            }
        }
    }

    init {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, connectivityCallback)
            val activeNetwork = connectivityManager.activeNetwork
            val isConnected = activeNetwork != null
            _uiState.update { it.copy(isOnline = isConnected) }
        } catch (_: Exception) {}

        val browserTool = BrowserTool(context)
        toolRegistry.registerAll(
            listOf(
                // Phone system tools
                PhoneListAppsTool(context),
                PhoneOpenAppTool(context),
                PhoneSystemInfoTool(context),
                PhoneScreenshotTool(screenCaptureManager, ocrManager),
                PhoneNotificationsTool(),
                PhoneClipboardTool(context),
                PhoneSettingsTool(context),
                PhoneSendSmsTool(),
                PhoneCallTool(context),
                // Accessibility tools
                AccessibilityReadTreeTool(),
                AccessibilityTapTextTool(),
                AccessibilityTapAtTool(),
                AccessibilitySwipeTool(),
                AccessibilityTypeTool(),
                AccessibilityBackTool(),
                AccessibilityHomeTool(),
                AccessibilityForegroundTool(),
                BrowserActionTool("browser.open_url", "Open a URL in the agent browser.", browserTool, "open_url", "url (String)"),
                BrowserActionTool("browser.read_page", "Read the text content of the current browser page.", browserTool, "read_page", ""),
                BrowserActionTool("browser.read_metadata", "Read page metadata including title, URL, links, buttons, and inputs.", browserTool, "read_metadata", ""),
                BrowserActionTool("browser.click_text", "Click an element by its visible text.", browserTool, "click_text", "text (String)"),
                BrowserActionTool("browser.click_selector", "Click an element by CSS selector.", browserTool, "click_selector", "selector (String)"),
                BrowserActionTool("browser.type_into_selector", "Type text into an input identified by CSS selector.", browserTool, "type_into_selector", "selector (String), text (String)"),
                BrowserActionTool("browser.type_into_focused", "Type text into the currently focused input element.", browserTool, "type_into_focused", "text (String)"),
                BrowserActionTool("browser.scroll", "Scroll the page up or down.", browserTool, "scroll", "direction (String: up/down)"),
                BrowserActionTool("browser.back", "Go back in browser history.", browserTool, "back", ""),
                BrowserActionTool("browser.reload", "Reload the current page.", browserTool, "reload", ""),
                BrowserActionTool("browser.read_summary", "Read a combined summary of the current page including text excerpt, title, links, buttons, and inputs.", browserTool, "read_summary", "")
            )
        )

        loopExecutor = AgentLoopExecutor(
            modelRouter = modelRouter,
            toolRegistry = toolRegistry,
            taskHistoryManager = taskHistoryManager,
            applyState = { block -> _uiState.update(block) },
            screenCaptureManager = screenCaptureManager,
            ocrManager = ocrManager,
            onSpeak = { text -> speakResponse(text) },
            onSessionComplete = { taskId, answer ->
                scope.launch {
                    onSessionComplete(taskId, answer)
                }
            }
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

        if (!loopRunning.compareAndSet(false, true)) {
            _uiState.update { it.copy(error = "Agent is busy. Wait for current task to complete.") }
            return
        }

        sessionMessages.add("User: $message")

        // pre-fetch routing + context from Kira (non-blocking, update UI later)
        var routingTemp = 0.5
        var routingStyle: String? = null
        var routingProfile = "thinking"

        scope.launch {
            try {
                if (kiraBridge.isAvailable()) {
                    val routing = kiraBridge.getIrisRouting(message)
                    routingTemp = routing.temperature
                    routingStyle = routing.styleInjection
                    routingProfile = routing.profile
                    _uiState.update { it.copy(agentStepStatus = "${routingProfile} mode") }
                } else {
                    val iris = com.phoneagent.iris.IrisRouter()
                    val state = iris.classifyState(message, 0.5f, 0.6f)
                    val profile = iris.selectProfile(state)
                    routingTemp = profile.temperature
                    routingProfile = profile.name
                    _uiState.update { it.copy(agentStepStatus = "${profile.name} mode") }
                }
            } catch (_: Exception) {}
        }

        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage(id = ChatMessage.nextId(), role = "user", content = message),
                isLoading = true,
                error = null,
                agentStepStatus = "Thinking",
                currentSteps = emptyList(),
                pendingConfirmation = null
            )
        }

        val baseSystemPrompt = AgentPromptBuilder.buildSystemPrompt(toolRegistry.listTools())
        val systemPrompt = VisionPayloadBuilder().buildVisionSystemPrompt(baseSystemPrompt)

        scope.launch {
            try {
                // try Kira context first, fall back to local Soma
                val context = if (kiraBridge.isAvailable()) {
                    kiraBridge.getContext(message).context
                } else {
                    somaContextBuilder.buildSomaContext(message)
                }

                val augmentedPrompt = if (context.isNotBlank()) {
                    "$systemPrompt\n\n$context"
                } else systemPrompt

                loopExecutor.startLoop(
                    message = message,
                    model = selectedModel,
                    systemPrompt = augmentedPrompt,
                    temperature = routingTemp,
                    styleInjection = routingStyle,
                    onComplete = { loopRunning.set(false) }
                )
            } catch (_: Exception) {
                loopExecutor.startLoop(
                    message = message,
                    model = selectedModel,
                    systemPrompt = systemPrompt,
                    onComplete = { loopRunning.set(false) }
                )
            }
        }
    }

    private suspend fun onSessionComplete(taskId: String, answer: String) {
        try {
            sessionMessages.add("Assistant: $answer")

            if (kiraBridge.isAvailable()) {
                val messages = sessionMessages.map { it ->
                    val colon = it.indexOf(':')
                    if (colon > 0) {
                        it.substring(0, colon) to it.substring(colon + 1).trim()
                    } else "unknown" to it
                }
                kiraBridge.postSessionDone(messages)
            } else {
                // fallback: local Soma (less reliable, but works offline)
                beliefEngine.observe("pattern", "Last conversation topic: ${sessionMessages.last().take(100)}")
                memoryEngine.store(
                    content = sessionMessages.last().take(500),
                    emotionalWeight = 0.6f,
                    sourceType = "conversation"
                )
                memScenesEngine.clusterSessionMemories(
                    sessionId = currentSessionId,
                    recentMemories = sessionMessages.takeLast(10)
                ) { prompt ->
                    try {
                        val provider = modelRouter.getDefaultProvider()
                        val request = AgentRequest(
                            message = prompt,
                            model = provider.config.defaultModel ?: "deepseek-v4-pro",
                            systemPrompt = "You analyze conversation memories. Respond ONLY with valid JSON array.",
                            temperature = 0.3
                        )
                        provider.chatCompletion(request).content
                    } catch (_: Exception) { "[]" }
                }
                lpmManager.updateFromSession(
                    newProfile = sessionMessages.takeLast(6).joinToString("\n"),
                    foresightSignals = listOf("Session completed: ${System.currentTimeMillis()}")
                )
            }

            sessionMessages.clear()
            currentSessionId = UUID.randomUUID().toString()
        } catch (_: Exception) {}
    }

    fun approvePendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.update { it.copy(pendingConfirmation = null) }
        loopRunning.set(true)
        scope.launch {
            try {
                database.safetyAuditDao().insert(
                    com.phoneagent.soma.entities.SafetyAuditEntity(
                        toolName = pending.toolName,
                        riskLevel = pending.riskLevel,
                        riskReason = pending.reason,
                        argsSummary = pending.args.toString().take(200),
                        decision = "APPROVED",
                        taskId = pending.taskId
                    )
                )
            } catch (_: Exception) {}
        }
        loopExecutor.resumeAfterConfirmation(pending, approved = true, onComplete = { loopRunning.set(false) })
    }

    fun cancelPendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.update { it.copy(pendingConfirmation = null) }
        loopRunning.set(true)
        scope.launch {
            try {
                database.safetyAuditDao().insert(
                    com.phoneagent.soma.entities.SafetyAuditEntity(
                        toolName = pending.toolName,
                        riskLevel = pending.riskLevel,
                        riskReason = pending.reason,
                        argsSummary = pending.args.toString().take(200),
                        decision = "DENIED",
                        taskId = pending.taskId
                    )
                )
            } catch (_: Exception) {}
        }
        loopExecutor.resumeAfterConfirmation(pending, approved = false, onComplete = { loopRunning.set(false) })
    }

    fun clearChat() {
        loopRunning.set(false)
        sessionMessages.clear()
        currentSessionId = UUID.randomUUID().toString()
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

    fun getScreenCaptureManager(): ScreenCaptureManager = screenCaptureManager

    suspend fun getDefaultAiProvider(): com.phoneagent.providers.AiProvider {
        return modelRouter.getDefaultProvider()
    }

    fun startVoiceInput() {
        if (!voiceInputManager.isAvailable()) {
            _uiState.update { it.copy(error = "Voice input not available on this device") }
            return
        }
        voiceInputManager.startListening(
            onResult = { text ->
                onVoiceResult?.invoke(text)
                if (text.isNotBlank()) sendMessage(text)
            },
            onError = { error ->
                onVoiceError?.invoke(error)
                _uiState.update { it.copy(error = error) }
            }
        )
    }

    fun stopVoiceInput() {
        voiceInputManager.stopListening()
    }

    fun speakResponse(text: String) {
        if (speechOutputManager.isAvailable()) {
            speechOutputManager.speak(text)
        }
    }

    fun destroy() {
        scope.cancel()
        loopExecutor.destroy()
        (ocrManager as? OcrManagerImpl)?.close()
        speechOutputManager.shutdown()
    }
}
