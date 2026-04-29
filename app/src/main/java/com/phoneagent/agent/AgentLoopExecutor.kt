package com.phoneagent.agent

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
import org.json.JSONObject

class AgentLoopExecutor(
    private val modelRouter: ModelRouter,
    private val toolRegistry: ToolRegistry,
    private val taskHistoryManager: TaskHistoryManager,
    private val applyState: ((AgentUiState) -> AgentUiState) -> Unit,
    private val screenCaptureManager: ScreenCaptureManager? = null
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

            try {
                val provider = modelRouter.getProviderForModel(model)
                executeSteps(provider, taskId, message, model, systemPrompt, steps, onComplete)
            } catch (e: ProviderError) {
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
                taskHistoryManager.updateTaskStatus(taskId, "failed", message)
                onComplete()
            } catch (e: Exception) {
                val message = e.message ?: "Unexpected error"
                applyState { it.copy(isLoading = false, agentStepStatus = null, error = message) }
                taskHistoryManager.updateTaskStatus(taskId, "failed", message)
                onComplete()
            }
        }
    }

    private suspend fun executeSteps(
        provider: AiProvider,
        taskId: String,
        message: String,
        model: String,
        systemPrompt: String,
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

            val visionPayload = try {
                val bytes = screenCaptureManager?.captureScreenshot()
                if (bytes != null) {
                    val encoder = VisionPayloadBuilder()
                    encoder.buildVisionContextPayload(
                        VisionPayloadBuilder.encodeImage(bytes),
                        "User request: $message"
                    )
                } else null
            } catch (_: Exception) { null }

            val request = AgentRequest(
                message = loopMessage,
                model = model,
                systemPrompt = systemPrompt,
                temperature = 0.3,
                stream = false,
                visionPayload = visionPayload
            )

            val response = withTimeout(MODEL_TIMEOUT_MS) {
                provider.chatCompletion(request)
            }
            val action = parseAgentResponse(response.content)

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
                            selectedModel = model
                        )
                        steps.add(AgentStep(stepsTaken, action, observation = buildErrorJson(action.tool, "Waiting for user confirmation.", "Approve or deny the pending action to continue.")))
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
                }
                is AgentAction.ParseError -> {
                    val content = response.content.trim()
                    if (!content.startsWith("{")) {
                        finalContent = content
                        steps.add(AgentStep(stepsTaken, AgentAction.FinalAnswer(content)))
                        applyState { it.copy(agentStepStatus = "Done", currentSteps = steps.toList()) }
                        break
                    }
                    val errorObs = buildErrorJson("parse", action.reason, "Check your JSON format and try again.")
                    steps.add(AgentStep(stepsTaken, action, errorObs))
                    applyState { it.copy(agentStepStatus = "Parse error, retrying...", currentSteps = steps.toList()) }
                }
            }
        }

        val answer = finalContent ?: "I reached the step limit without a final answer. Please summarize what you've accomplished so far."
        applyState {
            it.copy(
                messages = it.messages + ChatMessage("assistant", answer),
                isLoading = false,
                agentStepStatus = "Done",
                error = null,
                currentSteps = steps.toList(),
                pendingConfirmation = null
            )
        }

        onComplete()

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
                val cancelObs = buildErrorJson(pending.toolName, "User canceled the action.", "Consider an alternative approach to complete the task.")
                steps.add(AgentStep(steps.size + 1, AgentAction.ToolCall(pending.toolName, pending.args), cancelObs))
                applyState { it.copy(isLoading = true, agentStepStatus = "Thinking", pendingConfirmation = null, currentSteps = steps.toList()) }
            }

            try {
                val provider = modelRouter.getProviderForModel(pending.selectedModel)
                executeSteps(provider, pending.taskId, pending.userRequest, pending.selectedModel, pending.systemPrompt, steps, onComplete, steps.size)
            } catch (e: ProviderError) {
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
                applyState { it.copy(isLoading = false, agentStepStatus = null, error = e.message) }
                taskHistoryManager.updateTaskStatus(pending.taskId, "failed", e.message)
                onComplete()
            }
        }
    }

    private suspend fun executeTool(toolName: String, args: Map<String, String>): String {
        val tool = toolRegistry.getTool(toolName)
            ?: return buildErrorJson(toolName, "Unknown tool: $toolName", "Use only the tools listed in the system prompt.")

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
        return buildErrorJson(toolName, lastError ?: "Execution failed after $maxRetries retries", "Try a different approach or report the issue to the user.")
    }

    private fun buildErrorJson(tool: String, error: String, suggestion: String? = null): String {
        return JSONObject().apply {
            put("type", "tool_result")
            put("tool", tool)
            put("success", false)
            put("content", JSONObject.NULL)
            put("error", error)
            put("suggestion", suggestion ?: JSONObject.NULL)
        }.toString()
    }

    fun destroy() {
        scope.cancel()
    }
}
