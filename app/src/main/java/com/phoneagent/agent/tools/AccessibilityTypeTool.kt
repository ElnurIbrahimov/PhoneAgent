package com.phoneagent.agent.tools

import com.phoneagent.accessibility.AgentAccessibilityService
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
class AccessibilityTypeTool : Tool, DescribableTool {
    override val name: String = "accessibility.type"
    override val description: String = "Type text into the currently focused input field."
    override val argsDescription: String = "text (String)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val text = arguments["text"] ?: return ToolResult.error(name, "Missing 'text' argument",
            "Provide the text you want to type.")
        return try {
            val service = AgentAccessibilityService.instance
                ?: return ToolResult.error(name, "Accessibility service not running.",
                    "Tell the user to enable the Accessibility Service.")
            if (service.isForegroundPackageDenied()) {
                return ToolResult.error(name,
                    service.getDenyReason() ?: "Access to this app is restricted.",
                    "This app is protected from AI interaction. Switch to a different app.")
            }
            if (service.typeText(text)) ToolResult.success(name, "Typed text into focused field.")
            else ToolResult.error(name, "No focused input field found.",
                "Use accessibility.tap_text to tap an input field first, then try typing.")
        } catch (e: RuntimeException) {
            ToolResult.error(name, "Accessibility service disconnected. Ask the user to re-enable it.",
                "The accessibility service may have been stopped by the system.")
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Type failed",
                "Use accessibility.tap_text to tap an input field first, then try typing.")
        }
    }
}
