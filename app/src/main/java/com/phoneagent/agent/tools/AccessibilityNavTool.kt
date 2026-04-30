package com.phoneagent.agent.tools

import com.phoneagent.accessibility.AgentAccessibilityService
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
class AccessibilityBackTool : Tool, DescribableTool {
    override val name: String = "accessibility.back"
    override val description: String = "Press the system back button."
    override val argsDescription: String = "none"

    override suspend fun execute(arguments: Map<String, String>): String {
        return try {
            val service = AgentAccessibilityService.instance
                ?: return ToolResult.error(name, "Accessibility service not running.")
            if (service.pressBack()) ToolResult.success(name, "Pressed back.") else ToolResult.error(name, "Back failed.")
        } catch (e: RuntimeException) {
            ToolResult.error(name, "Accessibility service disconnected. Ask the user to re-enable it.",
                "The accessibility service may have been stopped by the system.")
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Back failed")
        }
    }
}

class AccessibilityHomeTool : Tool, DescribableTool {
    override val name: String = "accessibility.home"
    override val description: String = "Press the system home button."
    override val argsDescription: String = "none"

    override suspend fun execute(arguments: Map<String, String>): String {
        return try {
            val service = AgentAccessibilityService.instance
                ?: return ToolResult.error(name, "Accessibility service not running.")
            if (service.pressHome()) ToolResult.success(name, "Pressed home.") else ToolResult.error(name, "Home failed.")
        } catch (e: RuntimeException) {
            ToolResult.error(name, "Accessibility service disconnected. Ask the user to re-enable it.",
                "The accessibility service may have been stopped by the system.")
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Home failed")
        }
    }
}
