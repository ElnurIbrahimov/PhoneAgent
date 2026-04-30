package com.phoneagent.agent.tools

import com.phoneagent.accessibility.AgentAccessibilityService
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
class AccessibilitySwipeTool : Tool, DescribableTool {
    override val name: String = "accessibility.swipe"
    override val description: String = "Swipe up or down on the screen."
    override val argsDescription: String = "direction (String: up/down)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val direction = arguments["direction"]?.lowercase() ?: "down"
        return try {
            val service = AgentAccessibilityService.instance
                ?: return ToolResult.error(name, "Accessibility service not running.",
                    "Tell the user to enable the Accessibility Service.")
            if (service.isForegroundPackageDenied()) {
                return ToolResult.error(name,
                    service.getDenyReason() ?: "Access to this app is restricted.",
                    "This app is protected from AI interaction. Switch to a different app.")
            }
            val success = when (direction) {
                "up" -> service.swipeUp()
                "down" -> service.swipeDown()
                else -> return ToolResult.error(name, "Invalid direction: $direction. Use 'up' or 'down'.",
                    "Use 'up' or 'down' only for the direction parameter.")
            }
            if (success) ToolResult.success(name, "Swiped $direction") else ToolResult.error(name, "Swipe $direction failed.",
                "Try accessibility.read_tree to verify the screen has scrollable content.")
        } catch (e: RuntimeException) {
            ToolResult.error(name, "Accessibility service disconnected. Ask the user to re-enable it.",
                "The accessibility service may have been stopped by the system.")
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Swipe failed",
                "Try accessibility.read_tree to verify the screen has scrollable content.")
        }
    }
}
