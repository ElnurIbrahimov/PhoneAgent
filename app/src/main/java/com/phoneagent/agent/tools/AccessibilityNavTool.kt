package com.phoneagent.agent.tools

import com.phoneagent.accessibility.AgentAccessibilityService
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import org.json.JSONObject

class AccessibilityBackTool : Tool, DescribableTool {
    override val name: String = "accessibility.back"
    override val description: String = "Press the system back button."
    override val argsDescription: String = "none"

    override suspend fun execute(arguments: Map<String, String>): String {
        return try {
            val service = AgentAccessibilityService.instance
                ?: return errorResult("Accessibility service not running.")
            if (service.pressBack()) successResult("Pressed back.") else errorResult("Back failed.")
        } catch (e: Exception) {
            errorResult(e.message ?: "Back failed")
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

class AccessibilityHomeTool : Tool, DescribableTool {
    override val name: String = "accessibility.home"
    override val description: String = "Press the system home button."
    override val argsDescription: String = "none"

    override suspend fun execute(arguments: Map<String, String>): String {
        return try {
            val service = AgentAccessibilityService.instance
                ?: return errorResult("Accessibility service not running.")
            if (service.pressHome()) successResult("Pressed home.") else errorResult("Home failed.")
        } catch (e: Exception) {
            errorResult(e.message ?: "Home failed")
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
