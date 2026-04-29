package com.phoneagent.agent.tools

import com.phoneagent.accessibility.AgentAccessibilityService
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
class AccessibilityReadTreeTool : Tool, DescribableTool {
    override val name: String = "accessibility.read_tree"
    override val description: String = "Read the current screen's UI tree with element text, descriptions, IDs, and tap targets."
    override val argsDescription: String = "none"

    override suspend fun execute(arguments: Map<String, String>): String {
        return try {
            val service = AgentAccessibilityService.instance
                ?: return ToolResult.error(name, "Accessibility service not running. Enable it in Settings > Accessibility.",
                    "Tell the user to enable it in Settings > Accessibility > PhoneAgent")
            val tree = service.readUITree()
            ToolResult.success(name, tree)
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Failed to read UI tree",
                "Try accessibility.foreground_app to check what app is currently open.")
        }
    }
}
