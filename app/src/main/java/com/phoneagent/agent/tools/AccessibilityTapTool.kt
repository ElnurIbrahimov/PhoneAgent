package com.phoneagent.agent.tools

import com.phoneagent.accessibility.AgentAccessibilityService
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
class AccessibilityTapTextTool : Tool, DescribableTool {
    override val name: String = "accessibility.tap_text"
    override val description: String = "Tap an on-screen element by its visible text or content description."
    override val argsDescription: String = "text (String)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val text = arguments["text"] ?: return ToolResult.error(name, "Missing 'text' argument",
            "Provide the 'text' parameter to specify which element to find.")
        return try {
            val service = AgentAccessibilityService.instance
                ?: return ToolResult.error(name, "Accessibility service not running.",
                    "Tell the user to enable the Accessibility Service.")
            if (service.findAndTap(text)) ToolResult.success(name, "Tapped: $text")
            else ToolResult.error(name, "Element with text '$text' not found.",
                "Use accessibility.read_tree to see available elements on screen.")
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Tap failed",
                "Use accessibility.read_tree to check the current screen state.")
        }
    }
}

class AccessibilityTapAtTool : Tool, DescribableTool {
    override val name: String = "accessibility.tap_at"
    override val description: String = "Tap at specific screen coordinates."
    override val argsDescription: String = "x (Int), y (Int)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val x = arguments["x"]?.toIntOrNull() ?: return ToolResult.error(name, "Missing 'x'",
            "Provide the x coordinate for the tap position.")
        val y = arguments["y"]?.toIntOrNull() ?: return ToolResult.error(name, "Missing 'y'",
            "Provide the y coordinate for the tap position.")
        return try {
            val service = AgentAccessibilityService.instance
                ?: return ToolResult.error(name, "Accessibility service not running.",
                    "Tell the user to enable the Accessibility Service.")
            if (service.tapAt(x, y)) ToolResult.success(name, "Tapped at ($x, $y)")
            else ToolResult.error(name, "Tap at ($x, $y) failed.",
                "Check if coordinates are within screen bounds.")
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Tap failed",
                "Use accessibility.read_tree to check the current screen state.")
        }
    }
}
