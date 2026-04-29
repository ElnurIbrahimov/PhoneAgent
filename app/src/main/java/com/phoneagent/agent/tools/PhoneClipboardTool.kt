package com.phoneagent.agent.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
class PhoneClipboardTool(private val context: Context) : Tool, DescribableTool {
    override val name: String = "phone.clipboard"
    override val description: String = "Read or write the system clipboard."
    override val argsDescription: String = "action (String: read/write), text (String, for write)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val action = arguments["action"]?.lowercase() ?: "read"
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            when (action) {
                "read" -> {
                    val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    ToolResult.success(name, text)
                }
                "write" -> {
                    val text = arguments["text"] ?: return ToolResult.error(name, "Missing 'text' for write.")
                    clipboard.setPrimaryClip(ClipData.newPlainText("PhoneAgent", text))
                    ToolResult.success(name, "Clipboard set.")
                }
                else -> ToolResult.error(name, "Invalid action: $action. Use 'read' or 'write'.")
            }
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Clipboard operation failed")
        }
    }
}
