package com.phoneagent.agent

import org.json.JSONObject

object AgentPromptBuilder {

    fun buildSystemPrompt(tools: List<Tool>): String {
        val toolDescriptions = tools.joinToString("\n") { tool ->
            val args = when (tool) {
                is DescribableTool -> tool.argsDescription
                else -> ""
            }
            "- ${tool.name}: ${tool.description}${if (args.isNotBlank()) " Args: $args" else ""}"
        }

        return """
You are PhoneAgent, an Android-native AI agent running directly on the user's phone.
You must respond ONLY with a single valid JSON object. Do not output markdown, explanations, or any text outside the JSON object.

Available tools:
$toolDescriptions

Response formats:

1. Final answer (when you are done):
{
  "type": "final_answer",
  "content": "Your answer here."
}

2. Tool call (when you need to act):
{
  "type": "tool_call",
  "tool": "tool.name",
  "args": {}
}

Rules:
- After each browser action, wait for the next observation before claiming completion.
- Use final_answer only when the task is fully done.
- Never ask for cookies, secrets, passwords, or credentials.
- Do not output natural language outside the JSON object.
- If a tool fails, you may try a different approach up to the step limit.
- If the user cancels an action, provide a final_answer explaining what happened.
- If a tool fails, read its "suggestion" field and try that approach.
- Never retry the exact same action more than twice.
        """.trimIndent()
    }

    fun buildLoopMessage(
        originalRequest: String,
        steps: List<AgentStep>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("User request: $originalRequest")
        if (steps.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Previous steps:")
            steps.forEach { step ->
                sb.appendLine("Step ${step.stepNumber}:")
                when (step.action) {
                    is AgentAction.ToolCall -> {
                        sb.appendLine("Action: {\"type\":\"tool_call\",\"tool\":\"${step.action.tool}\",\"args\":${step.action.args}}")
                        val rawObs = step.observation ?: "none"
                        val suggestion = extractSuggestion(rawObs)
                        val obsText = rawObs.take(4000)
                        sb.appendLine("Observation: $obsText")
                        if (suggestion != null) {
                            sb.appendLine("Suggestion: $suggestion")
                        }
                    }
                    is AgentAction.FinalAnswer -> {
                        sb.appendLine("Action: {\"type\":\"final_answer\",\"content\":\"${step.action.content}\"}")
                    }
                    is AgentAction.ParseError -> {
                        sb.appendLine("Action: (parse error: ${step.action.reason})")
                        val rawObs = step.observation ?: "none"
                        val suggestion = extractSuggestion(rawObs)
                        val obsText = rawObs.take(4000)
                        sb.appendLine("Observation: $obsText")
                        if (suggestion != null) {
                            sb.appendLine("Suggestion: $suggestion")
                        }
                    }
                }
                sb.appendLine()
            }
        }
        sb.appendLine("Respond with the next JSON action.")
        return sb.toString()
    }

    private fun extractSuggestion(jsonString: String): String? {
        return try {
            val json = org.json.JSONObject(jsonString)
            json.optString("suggestion", null)
        } catch (_: Exception) { null }
    }
}

interface DescribableTool {
    val argsDescription: String
}
