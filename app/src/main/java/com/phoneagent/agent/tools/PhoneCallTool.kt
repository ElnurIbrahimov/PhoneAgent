package com.phoneagent.agent.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import org.json.JSONObject

class PhoneCallTool(private val context: Context) : Tool, DescribableTool {
    override val name: String = "phone.call"
    override val description: String = "Initiate a phone call. Requires user confirmation."
    override val argsDescription: String = "phone_number (String)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val phoneNumber = arguments["phone_number"] ?: return errorResult("Missing 'phone_number'")
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            JSONObject().apply {
                put("type", "tool_result"); put("tool", name); put("success", true)
                put("content", "Calling $phoneNumber"); put("error", JSONObject.NULL)
            }.toString()
        } catch (e: SecurityException) {
            errorResult("Phone call permission not granted.")
        } catch (e: Exception) {
            errorResult(e.message ?: "Call failed")
        }
    }

    private fun errorResult(error: String): String = JSONObject().apply {
        put("type", "tool_result"); put("tool", name); put("success", false)
        put("content", JSONObject.NULL); put("error", error)
    }.toString()
}
