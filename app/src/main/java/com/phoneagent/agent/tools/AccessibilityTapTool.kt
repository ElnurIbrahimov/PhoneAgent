package com.phoneagent.agent.tools

import com.phoneagent.accessibility.AgentAccessibilityService
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import org.json.JSONObject

class AccessibilityTapTextTool : Tool, DescribableTool {
    override val name: String = "accessibility.tap_text"
    override val description: String = "Tap an on-screen element by its visible text or content description."
    override val argsDescription: String = "text (String)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val text = arguments["text"] ?: return errorResult("Missing 'text' argument",
            "Provide the 'text' parameter to specify which element to find.")
        return try {
            val service = AgentAccessibilityService.instance
                ?: return errorResult("Accessibility service not running.",
                    "Tell the user to enable the Accessibility Service.")
            if (service.findAndTap(text)) successResult("Tapped: $text")
            else errorResult("Element with text '$text' not found.",
                "Use accessibility.read_tree to see available elements on screen.")
        } catch (e: Exception) {
            errorResult(e.message ?: "Tap failed",
                "Use accessibility.read_tree to check the current screen state.")
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

class AccessibilityTapAtTool : Tool, DescribableTool {
    override val name: String = "accessibility.tap_at"
    override val description: String = "Tap at specific screen coordinates."
    override val argsDescription: String = "x (Int), y (Int)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val x = arguments["x"]?.toIntOrNull() ?: return errorResult("Missing 'x'",
            "Provide the x coordinate for the tap position.")
        val y = arguments["y"]?.toIntOrNull() ?: return errorResult("Missing 'y'",
            "Provide the y coordinate for the tap position.")
        return try {
            val service = AgentAccessibilityService.instance
                ?: return errorResult("Accessibility service not running.",
                    "Tell the user to enable the Accessibility Service.")
            if (service.tapAt(x, y)) successResult("Tapped at ($x, $y)")
            else errorResult("Tap at ($x, $y) failed.",
                "Check if coordinates are within screen bounds.")
        } catch (e: Exception) {
            errorResult(e.message ?: "Tap failed",
                "Use accessibility.read_tree to check the current screen state.")
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
