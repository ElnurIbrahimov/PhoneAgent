package com.phoneagent.agent

import com.phoneagent.providers.AiProvider
import com.phoneagent.providers.ProviderError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.IOException

class AgentLoopExecutor(
    private val modelRouter: ModelRouter,
    private val toolRegistry: ToolRegistry,
    private val taskHistoryManager: TaskHistoryManager,
    private val applyState: ((AgentUiState) -> AgentUiState) -> Unit
) {

    companion object {
        const val DEFAULT_MAX_STEPS = 12
        const val MODEL_TIMEOUT_MS = 60_000L
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startLoop(
        message: String,
        model: String,
        systemPrompt: String,
        initialSteps: MutableList<AgentStep> = mutableListOf()
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
            val steps = initialSteps

            try {
                val provider = modelRouter.getProviderForModel(model)
                executeSteps(provider, taskId, message, model, systemPrompt, steps)
            } catch (e: ProviderError) {
                applyState { it.copy(isLoading = false, agentStepStatus = null, error = e.message) }
                taskHistoryManager.updateTaskStatus(taskId, "failed", e.message)
            } catch (e: Exception) {
                applyState { it.copy(isLoading = false, agentStepStatus = null, error = e.message) }
                taskHistoryManager.updateTaskStatus(taskId, "failed", e.message)
            }
        }
    }

    private suspend fun executeSteps(
        provider: AiProvider,
        taskId: String,
        message: String,
        model: String,
        systemPrompt: String,
        steps: MutableList<AgentStep>
    ) {
        var stepsTaken = 0
        var finalContent: String? = null

        while (stepsTaken < DEFAULT_MAX_STEPS) {
            stepsTaken++
            applyState { it.copy(agentStepStatus = "Thinking (step $stepsTaken/$DEFAULT_MAX_STEPS)", currentSteps = steps.toList()) }

            val loopMessage = AgentPromptBuilder.buildLoopMessage(message, steps)
            val request = AgentRequest(
                message = loopMessage,
                model = model,
                systemPrompt = systemPrompt,
                temperature = 0.3,
                stream = false
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

                    val safety = SafetyGate.check(action.tool, action.args)
                    if (safety is SafetyGate.SafetyResult.Blocked) {
                        val pending = PendingConfirmation(
                            toolName = action.tool,
                            args = action.args,
                            reason = safety.reason,
                            taskId = taskId,
                            userRequest = message,
                            stepsSoFar = steps.toList(),
                            systemPrompt = systemPrompt,
                            selectedModel = model
                        )
                        steps.add(AgentStep(stepsTaken, action, observation = buildErrorJson(action.tool, "Waiting for user confirmation.")))
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
                    val errorObs = buildErrorJson("parse", action.reason)
                    steps.add(AgentStep(stepsTaken, action, errorObs))
                    applyState { it.copy(agentStepStatus = "Parse error, retrying...", currentSteps = steps.toList()) }
                }
            }
        }

        val answer = finalContent ?: "I reached the step limit without a final answer."
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

        taskHistoryManager.updateTaskStatus(taskId, "completed", answer)
        taskHistoryManager.recordSteps(taskId, steps)
    }

    fun resumeAfterConfirmation(pending: PendingConfirmation, approved: Boolean) {
        scope.launch {
            val steps = pending.stepsSoFar.toMutableList()

            if (approved) {
                applyState { it.copy(isLoading = true, agentStepStatus = "Calling tool: ${pending.toolName}", pendingConfirmation = null) }
                val observation = executeTool(pending.toolName, pending.args)
                steps.add(AgentStep(steps.size + 1, AgentAction.ToolCall(pending.toolName, pending.args), observation))
            } else {
                val cancelObs = buildErrorJson(pending.toolName, "User canceled the action.")
                steps.add(AgentStep(steps.size + 1, AgentAction.ToolCall(pending.toolName, pending.args), cancelObs))
                applyState { it.copy(isLoading = true, agentStepStatus = "Thinking", pendingConfirmation = null, currentSteps = steps.toList()) }
            }

            try {
                val provider = modelRouter.getProviderForModel(pending.selectedModel)
                executeSteps(provider, pending.taskId, pending.userRequest, pending.selectedModel, pending.systemPrompt, steps)
            } catch (e: Exception) {
                applyState { it.copy(isLoading = false, agentStepStatus = null, error = e.message) }
                taskHistoryManager.updateTaskStatus(pending.taskId, "failed", e.message)
            }
        }
    }

    private suspend fun executeTool(toolName: String, args: Map<String, String>): String {
        val tool = toolRegistry.getTool(toolName)
            ?: return buildErrorJson(toolName, "Unknown tool: $toolName")

        return try {
            withTimeout(20_000) { tool.execute(args) }
        } catch (e: IOException) {
            try {
                withTimeout(20_000) { tool.execute(args) }
            } catch (e2: Exception) {
                buildErrorJson(toolName, e2.message ?: "Execution failed after retry")
            }
        } catch (e: Exception) {
            buildErrorJson(toolName, e.message ?: "Execution failed")
        }
    }

    private fun buildErrorJson(tool: String, error: String): String {
        return JSONObject().apply {
            put("type", "tool_result")
            put("tool", tool)
            put("success", false)
            put("content", JSONObject.NULL)
            put("error", error)
        }.toString()
    }

    fun destroy() {
        scope.cancel()
    }
}
