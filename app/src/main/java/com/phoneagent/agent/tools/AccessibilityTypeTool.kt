package com.phoneagent.agent.tools

import com.phoneagent.accessibility.AgentAccessibilityService
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import org.json.JSONObject

class AccessibilityTypeTool : Tool, DescribableTool {
    override val name: String = "accessibility.type"
    override val description: String = "Type text into the currently focused input field."
    override val argsDescription: String = "text (String)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val text = arguments["text"] ?: return errorResult("Missing 'text' argument",
            "Provide the text you want to type.")
        return try {
            val service = AgentAccessibilityService.instance
                ?: return errorResult("Accessibility service not running.",
                    "Tell the user to enable the Accessibility Service.")
            if (service.typeText(text)) successResult("Typed text into focused field.")
            else errorResult("No focused input field found.",
                "Use accessibility.tap_text to tap an input field first, then try typing.")
        } catch (e: Exception) {
            errorResult(e.message ?: "Type failed",
                "Use accessibility.tap_text to tap an input field first, then try typing.")
        }
    }

    private fun successResult(content: String): String = JSONObject().apply {
        put("type", "tool_result"); put("tool", name); put("success", true)
        put("content", content); put("error", JSONObject.NULL)
    }.toString()

    private fun errorResult(error: String, suggestion: String? = null): String = JSONObject().apply {
        put("type", "tool_result"); put("tool", name); put("success", false)
        put("content", JSONObject.NULL); put("error", error)
        put("suggestion", suggestion ?: JSONObject.NULL)
    }.toString()
}
