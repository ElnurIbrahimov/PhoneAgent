package com.phoneagent.agent.tools

import android.telephony.SmsManager
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import org.json.JSONObject

class PhoneSendSmsTool : Tool, DescribableTool {
    override val name: String = "phone.send_sms"
    override val description: String = "Send an SMS text message."
    override val argsDescription: String = "phone_number (String), message (String)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val phoneNumber = arguments["phone_number"] ?: return errorResult("Missing 'phone_number'")
        val message = arguments["message"] ?: return errorResult("Missing 'message'")
        return try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            JSONObject().apply {
                put("type", "tool_result"); put("tool", name); put("success", true)
                put("content", "SMS sent to $phoneNumber"); put("error", JSONObject.NULL)
            }.toString()
        } catch (e: SecurityException) {
            errorResult("SMS permission not granted.")
        } catch (e: Exception) {
            errorResult(e.message ?: "SMS send failed")
        }
    }

    private fun errorResult(error: String): String = JSONObject().apply {
        put("type", "tool_result"); put("tool", name); put("success", false)
        put("content", JSONObject.NULL); put("error", error)
    }.toString()
}
