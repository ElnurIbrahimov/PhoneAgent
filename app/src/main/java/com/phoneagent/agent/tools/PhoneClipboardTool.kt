package com.phoneagent.agent.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import org.json.JSONObject

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
                    JSONObject().apply {
                        put("type", "tool_result"); put("tool", name); put("success", true)
                        put("content", text); put("error", JSONObject.NULL)
                    }.toString()
                }
                "write" -> {
                    val text = arguments["text"] ?: return errorResult("Missing 'text' for write.")
                    clipboard.setPrimaryClip(ClipData.newPlainText("PhoneAgent", text))
                    JSONObject().apply {
                        put("type", "tool_result"); put("tool", name); put("success", true)
                        put("content", "Clipboard set."); put("error", JSONObject.NULL)
                    }.toString()
                }
                else -> errorResult("Invalid action: $action. Use 'read' or 'write'.")
            }
        } catch (e: Exception) {
            errorResult(e.message ?: "Clipboard operation failed")
        }
    }

    private fun errorResult(error: String): String = JSONObject().apply {
        put("type", "tool_result"); put("tool", name); put("success", false)
        put("content", JSONObject.NULL); put("error", error)
    }.toString()
}
