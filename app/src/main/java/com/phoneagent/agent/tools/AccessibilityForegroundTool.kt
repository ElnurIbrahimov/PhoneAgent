package com.phoneagent.agent.tools

import com.phoneagent.accessibility.AgentAccessibilityService
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import org.json.JSONObject

class AccessibilityForegroundTool : Tool, DescribableTool {
    override val name: String = "accessibility.foreground_app"
    override val description: String = "Get the package name of the currently open app."
    override val argsDescription: String = "none"

    override suspend fun execute(arguments: Map<String, String>): String {
        return try {
            val service = AgentAccessibilityService.instance
                ?: return errorResult("Accessibility service not running.")
            val pkg = service.getForegroundPackage() ?: "Unknown"
            successResult("Foreground app: $pkg")
        } catch (e: Exception) {
            errorResult(e.message ?: "Failed to get foreground app")
        }
    }

    private fun successResult(content: String): String = JSONObject().apply {
        put("type", "tool_result"); put("tool", name); put("success", true)
        put("content", content); put("error", JSONObject.NULL)
    }.toString()

    private fun errorResult(error: String): String = JSONObject().apply {
        put("type", "tool_result"); put("tool", name); put("success", false)
        put("content", JSONObject.NULL); put("error", error)
    }.toString()
}
