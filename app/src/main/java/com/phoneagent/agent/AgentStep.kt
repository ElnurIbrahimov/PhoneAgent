package com.phoneagent.agent

import org.json.JSONObject

sealed class AgentAction {
    data class FinalAnswer(val content: String) : AgentAction()
    data class ToolCall(val tool: String, val args: Map<String, String>) : AgentAction()
    data class ParseError(val reason: String) : AgentAction()
}

data class AgentStep(
    val stepNumber: Int,
    val action: AgentAction,
    val observation: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

fun parseAgentResponse(content: String): AgentAction {
    val trimmed = content.trim()
    if (trimmed.isBlank()) {
        return AgentAction.ParseError("Model returned empty response.")
    }

    val jsonString = when {
        trimmed.startsWith("```json") -> trimmed.removePrefix("```json").removeSuffix("```").trim()
        trimmed.startsWith("```") -> trimmed.removePrefix("```").removeSuffix("```").trim()
        else -> trimmed
    }

    if (!jsonString.startsWith("{")) {
        return AgentAction.ParseError("Response does not start with a JSON object. Response: ${jsonString.take(200)}")
    }

    return try {
        val json = JSONObject(jsonString)
        when (json.optString("type")) {
            "final_answer" -> {
                val content = json.optString("content", null)
                if (content == null || json.isNull("content")) {
                    AgentAction.ParseError("final_answer is missing required 'content' field.")
                } else {
                    AgentAction.FinalAnswer(content)
                }
            }
            "tool_call" -> {
                val tool = json.optString("tool", "")
                if (tool.isBlank()) {
                    AgentAction.ParseError("tool_call is missing required 'tool' field.")
                } else {
                    val argsObj = json.optJSONObject("args")
                    val args = mutableMapOf<String, String>()
                    argsObj?.keys()?.forEach { key ->
                        args[key] = argsObj.optString(key, "")
                    }
                    AgentAction.ToolCall(tool, args)
                }
            }
            else -> AgentAction.ParseError("Unknown or missing 'type' field. Expected 'final_answer' or 'tool_call'.")
        }
    } catch (e: org.json.JSONException) {
        AgentAction.ParseError("Invalid JSON syntax: ${e.message}. Response: ${jsonString.take(200)}")
    } catch (e: Exception) {
        AgentAction.ParseError("Unexpected error parsing JSON: ${e.message}. Response: ${jsonString.take(200)}")
    }
}
