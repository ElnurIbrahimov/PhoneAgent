package com.phoneagent.agent

import com.phoneagent.agent.tools.ToolResult
import com.phoneagent.perception.OcrManager
import com.phoneagent.providers.AiProvider
import com.phoneagent.providers.ProviderError
import com.phoneagent.perception.ScreenCaptureManager
import com.phoneagent.perception.VisionPayloadBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startLoop(
        message: String,
        model: String,
        systemPrompt: String,
        temperature: Double = 0.5,
        styleInjection: String? = null,
        initialSteps: MutableList<AgentStep> = mutableListOf(),
        onComplete: () -> Unit = {}
    ) {
        scope.launch {
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
                executeSteps(providers, taskId, message, model, fullSystemPrompt, temperature, steps, onComplete)
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

    private suspend fun executeSteps(
        providers: List<AiProvider>,
        taskId: String,
        message: String,
        model: String,
        systemPrompt: String,
        temperature: Double,
        steps: MutableList<AgentStep>,
        onComplete: () -> Unit,
        startStepNumber: Int = 0
    ) {
        var stepsTaken = startStepNumber
        var finalContent: String? = null

        while (stepsTaken < DEFAULT_MAX_STEPS) {
            stepsTaken++
            applyState { it.copy(agentStepStatus = "Thinking (step $stepsTaken/$DEFAULT_MAX_STEPS)", currentSteps = steps.toList()) }

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
                    steps.add(AgentStep(stepsTaken, action))
                    applyState { it.copy(agentStepStatus = "Done", currentSteps = steps.toList()) }
                    break
                }
                is AgentAction.ToolCall -> {
                    applyState { it.copy(agentStepStatus = "Calling tool: ${action.tool}", currentSteps = steps.toList()) }

                    val assessment = SafetyGate.assess(action.tool, action.args)
                    if (assessment.level == SafetyGate.RiskLevel.HIGH || assessment.level == SafetyGate.RiskLevel.MEDIUM) {
                        val pending = PendingConfirmation(
                            toolName = action.tool,
                            args = action.args,
                            reason = assessment.reason,
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
                        return
                    }

                    val observation = executeTool(action.tool, action.args)
                    steps.add(AgentStep(stepsTaken, action, observation))
                    val parsed = ToolResultParser.parse(observation)
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
                pendingConfirmation = null
            )
        }

        onComplete()

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

    fun resumeAfterConfirmation(pending: PendingConfirmation, approved: Boolean, onComplete: () -> Unit = {}) {
        scope.launch {
            val steps = pending.stepsSoFar.toMutableList()

            if (approved) {
                applyState { it.copy(isLoading = true, agentStepStatus = "Calling tool: ${pending.toolName}", pendingConfirmation = null) }
                val observation = executeTool(pending.toolName, pending.args)
                steps.add(AgentStep(steps.size + 1, AgentAction.ToolCall(pending.toolName, pending.args), observation))
            } else {
                val cancelObs = ToolResult.error(pending.toolName, "User canceled the action.", "Consider an alternative approach to complete the task.")
                steps.add(AgentStep(steps.size + 1, AgentAction.ToolCall(pending.toolName, pending.args), cancelObs))
                applyState { it.copy(isLoading = true, agentStepStatus = "Thinking", pendingConfirmation = null, currentSteps = steps.toList()) }
            }

            try {
                val providers = modelRouter.getAllProvidersForModel(pending.selectedModel)
                executeSteps(providers, pending.taskId, pending.userRequest, pending.selectedModel, pending.systemPrompt, pending.temperature, steps, onComplete, steps.size)
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
                    delay(500L * (1L shl attempt))
                }
            }
        }
        return ToolResult.error(toolName, lastError ?: "Execution failed after $maxRetries retries", "Try a different approach or report the issue to the user.").also {
            android.util.Log.e("AgentExecutor", "Tool $toolName failed after $maxRetries retries: $lastError")
        }
    }


    fun destroy() {
        scope.cancel()
    }
}
