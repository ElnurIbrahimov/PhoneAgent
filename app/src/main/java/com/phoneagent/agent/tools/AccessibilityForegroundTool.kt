package com.phoneagent.agent.tools

import com.phoneagent.accessibility.AgentAccessibilityService
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
class AccessibilityForegroundTool : Tool, DescribableTool {
    override val name: String = "accessibility.foreground_app"
    override val description: String = "Get the package name of the currently open app."
    override val argsDescription: String = "none"

    override suspend fun execute(arguments: Map<String, String>): String {
        return try {
            val service = AgentAccessibilityService.instance
                ?: return ToolResult.error(name, "Accessibility service not running.")
            val pkg = service.getForegroundPackage() ?: "Unknown"
            ToolResult.success(name, "Foreground app: $pkg")
        } catch (e: RuntimeException) {
            ToolResult.error(name, "Accessibility service disconnected. Ask the user to re-enable it.",
                "The accessibility service may have been stopped by the system.")
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Failed to get foreground app")
        }
    }
}
