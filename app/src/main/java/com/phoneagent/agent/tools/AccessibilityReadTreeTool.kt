package com.phoneagent.agent.tools

import com.phoneagent.accessibility.AgentAccessibilityService
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import org.json.JSONObject

class AccessibilityReadTreeTool : Tool, DescribableTool {
    override val name: String = "accessibility.read_tree"
    override val description: String = "Read the current screen's UI tree with element text, descriptions, IDs, and tap targets."
    override val argsDescription: String = "none"

    override suspend fun execute(arguments: Map<String, String>): String {
        return try {
            val service = AgentAccessibilityService.instance
                ?: return errorResult("Accessibility service not running. Enable it in Settings > Accessibility.",
                    "Tell the user to enable it in Settings > Accessibility > PhoneAgent")
            val tree = service.readUITree()
            successResult(tree)
        } catch (e: Exception) {
            errorResult(e.message ?: "Failed to read UI tree",
                "Try accessibility.foreground_app to check what app is currently open.")
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
