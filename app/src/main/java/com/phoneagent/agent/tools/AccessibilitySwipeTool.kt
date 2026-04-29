package com.phoneagent.agent.tools

import com.phoneagent.accessibility.AgentAccessibilityService
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import org.json.JSONObject

class AccessibilitySwipeTool : Tool, DescribableTool {
    override val name: String = "accessibility.swipe"
    override val description: String = "Swipe up or down on the screen."
    override val argsDescription: String = "direction (String: up/down)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val direction = arguments["direction"]?.lowercase() ?: "down"
        return try {
            val service = AgentAccessibilityService.instance
                ?: return errorResult("Accessibility service not running.",
                    "Tell the user to enable the Accessibility Service.")
            val success = when (direction) {
                "up" -> service.swipeUp()
                "down" -> service.swipeDown()
                else -> return errorResult("Invalid direction: $direction. Use 'up' or 'down'.",
                    "Use 'up' or 'down' only for the direction parameter.")
            }
            if (success) successResult("Swiped $direction") else errorResult("Swipe $direction failed.",
                "Try accessibility.read_tree to verify the screen has scrollable content.")
        } catch (e: Exception) {
            errorResult(e.message ?: "Swipe failed",
                "Try accessibility.read_tree to verify the screen has scrollable content.")
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
