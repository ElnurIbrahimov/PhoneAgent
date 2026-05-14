package com.phoneagent.agent

import com.phoneagent.agent.tools.ToolResult
import com.phoneagent.perception.OcrManager
import com.phoneagent.providers.AiProvider
import com.phoneagent.providers.ProviderError
import com.phoneagent.perception.ScreenCaptureManager
import com.phoneagent.perception.VisionPayloadBuilder
import com.phoneagent.streaming.ChunkType
import com.phoneagent.streaming.ReasoningType
import com.phoneagent.streaming.ReasoningChunk
import com.phoneagent.streaming.StreamChunk
import com.phoneagent.streaming.StreamingState
import com.phoneagent.streaming.StreamingStatus
import com.phoneagent.streaming.ToolCallState
import com.phoneagent.streaming.ToolCallStatus
import com.phoneagent.worldmodel.PersonalWorldModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class AgentLoopExecutor(
    private val modelRouter: ModelRouter,
    private val toolRegistry: ToolRegistry,
    private val taskHistoryManager: TaskHistoryManager,
    private val applyState: ((AgentUiState) -> AgentUiState) -> Unit,
    private val screenCaptureManager: ScreenCaptureManager? = null,
    private val ocrManager: OcrManager? = null,
    private val onSpeak: ((String) -> Unit)? = null,
    private val onSessionComplete: (suspend (taskId: String, answer: String) -> Unit)? = null
) {

    companion object {
        const val DEFAULT_MAX_STEPS = 12
        const val MODEL_TIMEOUT_MS = 60_000L
    }

    private val random = java.util.Random()

    private fun retryDelay(attempt: Int, baseMs: Long = 500L): Long {
        val exponential = baseMs * (1L shl attempt)
        val jitter = (exponential * 0.2 * random.nextFloat()).toLong()
        return (exponential + jitter).coerceAtMost(10_000L)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var consecutiveFailures = 0
    private val CIRCUIT_BREAKER_THRESHOLD = 3

    private fun recordFailure() {
        consecutiveFailures++
    }

    private fun recordSuccess() {
        consecutiveFailures = 0
    }

    private fun isCircuitOpen(): Boolean {
        return consecutiveFailures >= CIRCUIT_BREAKER_THRESHOLD
    }

    fun startLoop(
        message: String,
        model: String,
        systemPrompt: String,
        temperature: Double = 0.5,
        styleInjection: String? = null,
        initialSteps: MutableList<AgentStep> = mutableListOf(),
        worldModel: PersonalWorldModel? = null,
        enableWorldModelUpdates: Boolean = true,
        onComplete: () -> Unit = {}
    ) {
        scope.launch {
            consecutiveFailures = 0
            applyState {
                it.copy(
                    isLoading = true,
                    error = null,
                    agentStepStatus = "Thinking",
                    currentSteps = emptyList(),
                    pendingConfirmation = null
                )
            }

            val taskId = taskHistoryManager.recordTask(message, "running")
            val steps = initialSteps.toMutableList()

            val fullSystemPrompt = if (styleInjection != null) {
                "$systemPrompt\n\n$styleInjection"
            } else systemPrompt

            try {
                val providers = modelRouter.getAllProvidersForModel(model)
                executeSteps(providers, taskId, message, model, fullSystemPrompt, temperature, steps, onComplete, worldModel, enableWorldModelUpdates)
            } catch (e: ProviderError) {
                android.util.Log.e("AgentLoopExecutor", "Provider error in startLoop", e)
                val message = when (e) {
                    is com.phoneagent.providers.ProviderError.AuthenticationError ->
                        "Authentication failed. Check your API key in Settings."
                    is com.phoneagent.providers.ProviderError.NetworkError ->
                        "Network error: ${e.message}. Check your connection and try again."
                    is com.phoneagent.providers.ProviderError.RateLimitError ->
                        "Rate limited. Wait a moment and try again."
                    is com.phoneagent.providers.ProviderError.ServerError ->
                        "Server error: ${e.message}. The provider may be down."
                    else -> e.message ?: "Unknown provider error"
                }
                applyState { it.copy(isLoading = false, agentStepStatus = null, error = message) }
                taskHistoryManager.recordSteps(taskId, steps)
                taskHistoryManager.updateTaskStatus(taskId, "failed", message)
                onComplete()
            } catch (e: Exception) {
                android.util.Log.e("AgentLoopExecutor", "Unexpected error in agent loop: ${e.message}", e)
                val message = e.message ?: "Unexpected error"
                applyState { it.copy(isLoading = false, agentStepStatus = null, error = message) }
                taskHistoryManager.recordSteps(taskId, steps)
                taskHistoryManager.updateTaskStatus(taskId, "failed", message)
                onComplete()
            }
        }
    }

    fun startLoopStream(
        message: String,
        model: String,
        systemPrompt: String,
        temperature: Double = 0.5,
        styleInjection: String? = null,
        initialSteps: MutableList<AgentStep> = mutableListOf(),
        worldModel: PersonalWorldModel? = null,
        enableWorldModelUpdates: Boolean = true,
        onStreamingState: (StreamingState) -> Unit,
        onComplete: () -> Unit = {}
    ) {
        scope.launch {
            consecutiveFailures = 0
            applyState {
                it.copy(
                    isLoading = true,
                    error = null,
                    agentStepStatus = "Thinking",
                    currentSteps = emptyList(),
                    pendingConfirmation = null,
                    streamingState = StreamingState(),
                    isReasoningCardVisible = true
                )
            }

            val taskId = taskHistoryManager.recordTask(message, "running")
            val steps = initialSteps.toMutableList()

            val fullSystemPrompt = if (styleInjection != null) {
                "$systemPrompt\n\n$styleInjection"
            } else systemPrompt

            try {
                val providers = modelRouter.getAllProvidersForModel(model)
                executeStepsStreaming(providers, taskId, message, model, fullSystemPrompt, temperature, steps, onComplete, worldModel, enableWorldModelUpdates, onStreamingState)
            } catch (e: ProviderError) {
                android.util.Log.e("AgentLoopExecutor", "Provider error in startLoopStream", e)
                val errorMessage = when (e) {
                    is com.phoneagent.providers.ProviderError.AuthenticationError ->
                        "Authentication failed. Check your API key in Settings."
                    is com.phoneagent.providers.ProviderError.NetworkError ->
                        "Network error: ${e.message}. Check your connection and try again."
                    is com.phoneagent.providers.ProviderError.RateLimitError ->
                        "Rate limited. Wait a moment and try again."
                    is com.phoneagent.providers.ProviderError.ServerError ->
                        "Server error: ${e.message}. The provider may be down."
                    else -> e.message ?: "Unknown provider error"
                }
                applyState { it.copy(isLoading = false, agentStepStatus = null, error = errorMessage, streamingState = StreamingState(status = StreamingStatus.ERROR, error = errorMessage)) }
                taskHistoryManager.recordSteps(taskId, steps)
                taskHistoryManager.updateTaskStatus(taskId, "failed", errorMessage)
                onComplete()
            } catch (e: Exception) {
                android.util.Log.e("AgentLoopExecutor", "Unexpected error in startLoopStream: ${e.message}", e)
                val errorMessage = e.message ?: "Unexpected error"
                applyState { it.copy(isLoading = false, agentStepStatus = null, error = errorMessage, streamingState = StreamingState(status = StreamingStatus.ERROR, error = errorMessage)) }
                taskHistoryManager.recordSteps(taskId, steps)
                taskHistoryManager.updateTaskStatus(taskId, "failed", errorMessage)
                onComplete()
            }
        }
    }

    private suspend fun executeStepsStreaming(
        providers: List<AiProvider>,
        taskId: String,
        message: String,
        model: String,
        systemPrompt: String,
        temperature: Double,
        steps: MutableList<AgentStep>,
        onComplete: () -> Unit,
        worldModel: PersonalWorldModel? = null,
        enableWorldModelUpdates: Boolean = true,
        startStepNumber: Int = 0,
        onStreamingState: (StreamingState) -> Unit = {}
    ) {
        var stepsTaken = startStepNumber
        var finalContent: String? = null
        var streamedText = ""
        var reasoningChunks = mutableListOf<ReasoningChunk>()
        var currentToolCall: ToolCallState? = null

        while (stepsTaken < DEFAULT_MAX_STEPS) {
            stepsTaken++
            val stepNum = stepsTaken
            applyState { it.copy(agentStepStatus = "Thinking (step $stepsTaken/$DEFAULT_MAX_STEPS)", currentSteps = steps.toList()) }
            onStreamingState(StreamingState(status = StreamingStatus.THINKING, currentStep = stepNum, streamedText = streamedText, reasoningChunks = reasoningChunks.toList(), toolCallInProgress = currentToolCall))

            val loopMessage = AgentPromptBuilder.buildLoopMessage(message, steps)

            val uiModifyingTools = setOf("phone.open_app", "phone.settings", "accessibility.tap_text",
                "accessibility.tap_at", "accessibility.swipe", "accessibility.back",
                "accessibility.home", "browser.open_url", "browser.click_text",
                "browser.click_selector", "accessibility.type")
            val lastStep = steps.lastOrNull()
            val uiChanged = lastStep?.action is AgentAction.ToolCall && lastStep.action.tool in uiModifyingTools
            val shouldCapture = (stepsTaken <= 1) || uiChanged || (stepsTaken % 3 == 0)
            val needsImage = (stepsTaken <= 1) || uiChanged

            var ocrText: String? = null
            var visionPayload: String? = null

            if (shouldCapture) {
                try {
                    val bytes = screenCaptureManager?.captureScreenshot()
                    if (bytes != null) {
                        ocrText = ocrManager?.recognizeText(bytes)?.take(4000)
                        if (needsImage) {
                            visionPayload = VisionPayloadBuilder().buildVisionContextPayload(
                                VisionPayloadBuilder.encodeImage(bytes),
                                "User request: $message\n\nOCR text from screen: ${ocrText?.take(2000) ?: "none"}"
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AgentLoopExecutor", "Screen capture/OCR failed: ${e.message}", e)
                    ocrText = null
                    visionPayload = null
                }
            }

            val augmentedMessage = if (ocrText != null && visionPayload == null) {
                "$loopMessage\n\n[Screen OCR]: $ocrText"
            } else loopMessage

            val request = AgentRequest(
                message = augmentedMessage,
                model = model,
                systemPrompt = systemPrompt,
                temperature = temperature,
                stream = false,
                visionPayload = visionPayload
            )

            var response: AgentResponse? = null
            var lastProviderError: String? = null

            for (p in providers) {
                try {
                    response = withTimeout(MODEL_TIMEOUT_MS) {
                        p.chatCompletion(request)
                    }
                    break
                } catch (e: ProviderError) {
                    lastProviderError = e.message
                    android.util.Log.w("AgentLoopExecutor", "Provider ${p.config.name} failed: ${e.message}")
                    continue
                } catch (e: Exception) {
                    lastProviderError = e.message
                    android.util.Log.e("AgentLoopExecutor", "Unexpected error from provider ${p.config.name}", e)
                    continue
                }
            }

            if (response == null) {
                val errorMsg = "All providers failed. Last error: ${lastProviderError ?: "Unknown"}"
                applyState { it.copy(isLoading = false, agentStepStatus = null, error = errorMsg) }
                taskHistoryManager.updateTaskStatus(taskId, "failed", errorMsg)
                onComplete()
                return
            }

            val action = parseAgentResponse(response!!.content)

            when (action) {
                is AgentAction.FinalAnswer -> {
                    finalContent = action.content
                    streamedText = action.content
                    reasoningChunks.add(ReasoningChunk(ReasoningType.PLAN, "Final answer ready"))
                    onStreamingState(StreamingState(status = StreamingStatus.DONE, currentStep = stepNum, streamedText = streamedText, reasoningChunks = reasoningChunks.toList(), toolCallInProgress = null))
                    steps.add(AgentStep(stepsTaken, action))
                    applyState { it.copy(agentStepStatus = "Done", currentSteps = steps.toList()) }
                    break
                }
                is AgentAction.ToolCall -> {
                    applyState { it.copy(agentStepStatus = "Calling tool: ${action.tool}", currentSteps = steps.toList()) }

                    val assessment = SafetyGate.assess(action.tool, action.args, worldModel)
                    if (assessment.level == SafetyGate.RiskLevel.HIGH || assessment.level == SafetyGate.RiskLevel.MEDIUM) {
                        val pending = PendingConfirmation(
                            toolName = action.tool,
                            args = action.args,
                            reason = assessment.reason,
                            riskLevel = assessment.level.name,
                            taskId = taskId,
                            userRequest = message,
                            stepsSoFar = steps.toList(),
                            systemPrompt = systemPrompt,
                            selectedModel = model,
                            temperature = temperature
                        )
                        steps.add(AgentStep(stepsTaken, action, observation = ToolResult.error(action.tool, "Waiting for user confirmation.", "Approve or deny the pending action to continue.")))
                        applyState {
                            it.copy(
                                agentStepStatus = "Waiting for confirmation",
                                currentSteps = steps.toList(),
                                pendingConfirmation = pending,
                                isLoading = false
                            )
                        }
                        currentToolCall = ToolCallState(action.tool, action.args, ToolCallStatus.STARTED)
                        onStreamingState(StreamingState(status = StreamingStatus.ACTING, currentStep = stepNum, streamedText = streamedText, reasoningChunks = reasoningChunks.toList(), toolCallInProgress = currentToolCall))
                        return
                    }

                    currentToolCall = ToolCallState(action.tool, action.args, ToolCallStatus.EXECUTING)
                    onStreamingState(StreamingState(status = StreamingStatus.ACTING, currentStep = stepNum, streamedText = streamedText, reasoningChunks = reasoningChunks.toList(), toolCallInProgress = currentToolCall))
                    val observation = executeTool(action.tool, action.args)
                    steps.add(AgentStep(stepsTaken, action, observation))
                    currentToolCall = ToolCallState(action.tool, action.args, ToolCallStatus.RESULT)
                    onStreamingState(StreamingState(status = StreamingStatus.OBSERVING, currentStep = stepNum, streamedText = streamedText, reasoningChunks = reasoningChunks.toList(), toolCallInProgress = currentToolCall))
                    val parsed = ToolResultParser.parse(observation)
                    if (parsed.success) {
                        recordSuccess()
                        if (enableWorldModelUpdates && worldModel != null) {
                            generateAndApplyStepObservation(providers, stepsTaken, action.tool, observation, worldModel)
                        }
                        reasoningChunks.add(ReasoningChunk(ReasoningType.OBSERVATION, "${action.tool}: ${observation.take(200)}"))
                        streamedText += "\n[${action.tool} result] ${observation.take(200)}\n"
                    } else {
                        recordFailure()
                    }
                    applyState {
                        it.copy(
                            agentStepStatus = if (parsed.success) "Tool succeeded" else "Tool failed: ${parsed.error?.take(120)}",
                            currentSteps = steps.toList()
                        )
                    }

                    val dangerWarning = DestructiveActionDetector.assessSequence(steps)
                    if (dangerWarning != null) {
                        applyState {
                            it.copy(agentStepStatus = "Warning: $dangerWarning")
                        }
                    }
                }
                is AgentAction.ParseError -> {
                    val content = response.content.trim()
                    if (!content.startsWith("{")) {
                        finalContent = content
                        steps.add(AgentStep(stepsTaken, AgentAction.FinalAnswer(content)))
                        applyState { it.copy(agentStepStatus = "Done", currentSteps = steps.toList()) }
                        break
                    }
                    val errorObs = ToolResult.error("parse", action.reason, "Check your JSON format and try again.")
                    steps.add(AgentStep(stepsTaken, action, errorObs))
                    applyState { it.copy(agentStepStatus = "Parse error, retrying...", currentSteps = steps.toList()) }
                }
            }
        }

        val answer = finalContent ?: "I reached the step limit without a final answer. Please summarize what you've accomplished so far."
        applyState {
            it.copy(
                messages = it.messages + ChatMessage(id = ChatMessage.nextId(), role = "assistant", content = answer),
                isLoading = false,
                agentStepStatus = "Done",
                error = null,
                currentSteps = steps.toList(),
                pendingConfirmation = null,
                streamingState = null,
                isReasoningCardVisible = false
            )
        }

        onComplete()
        onStreamingState(StreamingState(status = StreamingStatus.DONE, currentStep = stepsTaken, streamedText = answer, reasoningChunks = reasoningChunks.toList(), toolCallInProgress = null))

        taskHistoryManager.recordMessage(message, answer, model)
        onSessionComplete?.invoke(taskId, answer)
        onSpeak?.let { speak ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                speak(answer.take(500))
            }
        }

        taskHistoryManager.updateTaskStatus(taskId, "completed", answer)
        taskHistoryManager.recordSteps(taskId, steps)
    }

    fun resumeAfterConfirmation(
        pending: PendingConfirmation,
        approved: Boolean,
        worldModel: PersonalWorldModel? = null,
        enableWorldModelUpdates: Boolean = false,
        onComplete: () -> Unit = {}
    ) {
        scope.launch {
            val steps = pending.stepsSoFar.toMutableList()

            if (approved) {
                applyState { it.copy(isLoading = true, agentStepStatus = "Calling tool: ${pending.toolName}", pendingConfirmation = null) }
                val observation = executeTool(pending.toolName, pending.args)
                steps.add(AgentStep(steps.size + 1, AgentAction.ToolCall(pending.toolName, pending.args), observation))
                val parsed = ToolResultParser.parse(observation)
                if (parsed.success) {
                    recordSuccess()
                } else {
                    recordFailure()
                }
            } else {
                val cancelObs = ToolResult.error(pending.toolName, "User canceled the action.", "Consider an alternative approach to complete the task.")
                steps.add(AgentStep(steps.size + 1, AgentAction.ToolCall(pending.toolName, pending.args), cancelObs))
                applyState { it.copy(isLoading = true, agentStepStatus = "Thinking", pendingConfirmation = null, currentSteps = steps.toList()) }
            }

            try {
                val providers = modelRouter.getAllProvidersForModel(pending.selectedModel)
                executeSteps(providers, pending.taskId, pending.userRequest, pending.selectedModel, pending.systemPrompt, pending.temperature, steps, onComplete, worldModel, enableWorldModelUpdates, steps.size)
            } catch (e: ProviderError) {
                android.util.Log.e("AgentLoopExecutor", "Provider error in resumeAfterConfirmation", e)
                val message = when (e) {
                    is com.phoneagent.providers.ProviderError.AuthenticationError ->
                        "Authentication failed. Check your API key in Settings."
                    is com.phoneagent.providers.ProviderError.NetworkError ->
                        "Network error: ${e.message}. Check your connection and try again."
                    is com.phoneagent.providers.ProviderError.RateLimitError ->
                        "Rate limited. Wait a moment and try again."
                    is com.phoneagent.providers.ProviderError.ServerError ->
                        "Server error: ${e.message}. The provider may be down."
                    else -> e.message ?: "Unknown provider error"
                }
                applyState { it.copy(isLoading = false, agentStepStatus = null, error = message) }
                taskHistoryManager.updateTaskStatus(pending.taskId, "failed", message)
                onComplete()
            } catch (e: Exception) {
                android.util.Log.e("AgentLoopExecutor", "Unexpected error in resumeAfterConfirmation: ${e.message}", e)
                applyState { it.copy(isLoading = false, agentStepStatus = null, error = e.message) }
                taskHistoryManager.updateTaskStatus(pending.taskId, "failed", e.message)
                onComplete()
            }
        }
    }

    private suspend fun executeTool(toolName: String, args: Map<String, String>): String {
        if (isCircuitOpen()) {
            return ToolResult.error(toolName, "Circuit breaker open — tool disabled after $CIRCUIT_BREAKER_THRESHOLD consecutive failures", "Stop the current task and try a different approach.")
        }

        val tool = toolRegistry.getTool(toolName)
            ?: return ToolResult.error(toolName, "Unknown tool: $toolName", "Use only the tools listed in the system prompt.")

        var attempt = 0
        val maxRetries = 2
        var lastError: String? = null

        while (attempt <= maxRetries) {
            try {
                return withTimeout(20_000) { tool.execute(args) }
            } catch (e: Exception) {
                lastError = e.message ?: "Execution failed"
                attempt++
                if (attempt <= maxRetries) {
                    delay(retryDelay(attempt))
                }
            }
        }
        return ToolResult.error(toolName, lastError ?: "Execution failed after $maxRetries retries", "Try a different approach or report the issue to the user.").also {
            android.util.Log.e("AgentExecutor", "Tool $toolName failed after $maxRetries retries: $lastError")
        }
    }

    private suspend fun generateAndApplyStepObservation(
        providers: List<AiProvider>,
        step: Int,
        action: String,
        result: String,
        worldModel: PersonalWorldModel
    ) {
        try {
            val profile = worldModel.getProfile()
            val prompt = AgentPromptBuilder.buildStepObservationPrompt(step, action, result, profile)

            val request = AgentRequest(
                message = prompt,
                model = providers.firstOrNull()?.config?.model ?: "unknown",
                systemPrompt = "You are a helpful assistant.",
                temperature = 0.3,
                stream = false,
                visionPayload = null
            )

            var responseContent: String? = null
            for (p in providers) {
                try {
                    val response = withTimeout(15_000) {
                        p.chatCompletion(request)
                    }
                    responseContent = response.content
                    break
                } catch (e: Exception) {
                    android.util.Log.w("AgentLoopExecutor", "Observation provider ${p.config.name} failed: ${e.message}")
                    continue
                }
            }

            responseContent?.let { content ->
                val observation = AgentPromptBuilder.parseStepObservation(step, action, result, content)
                observation?.let {
                    try {
                        worldModel.updateFromStepObservation(it)
                    } catch (e: Exception) {
                        android.util.Log.e("AgentLoopExecutor", "Failed to update world model: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AgentLoopExecutor", "Step observation generation failed: ${e.message}")
        }
    }


    fun destroy() {
        scope.cancel()
    }
}
