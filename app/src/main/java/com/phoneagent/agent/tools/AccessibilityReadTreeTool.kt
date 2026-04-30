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
            if (service.isForegroundPackageDenied()) {
                return ToolResult.error(name,
                    service.getDenyReason() ?: "Access to this app is restricted.",
                    "This app is protected from AI interaction. Switch to a different app.")
            }
            val tree = service.readUITree()
            ToolResult.success(name, tree)
        } catch (e: RuntimeException) {
            ToolResult.error(name, "Accessibility service disconnected. Ask the user to re-enable it.",
                "The accessibility service may have been stopped by the system.")
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Failed to read UI tree",
                "Try accessibility.foreground_app to check what app is currently open.")
        }
    }
}
