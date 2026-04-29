package com.phoneagent.agent.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
class PhoneCallTool(private val context: Context) : Tool, DescribableTool {
    override val name: String = "phone.call"
    override val description: String = "Initiate a phone call. Requires user confirmation."
    override val argsDescription: String = "phone_number (String)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val phoneNumber = arguments["phone_number"] ?: return ToolResult.error(name, "Missing 'phone_number'")
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.success(name, "Calling $phoneNumber")
        } catch (e: SecurityException) {
            ToolResult.error(name, "Phone call permission not granted.")
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Call failed")
        }
    }
}
