package com.phoneagent.agent.tools

import android.telephony.SmsManager
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
class PhoneSendSmsTool : Tool, DescribableTool {
    override val name: String = "phone.send_sms"
    override val description: String = "Send an SMS text message."
    override val argsDescription: String = "phone_number (String), message (String)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val phoneNumber = arguments["phone_number"] ?: return ToolResult.error(name, "Missing 'phone_number'")
        val message = arguments["message"] ?: return ToolResult.error(name, "Missing 'message'")
        return try {
            val smsManager = SmsManager.getDefault()
                ?: return ToolResult.error(name, "Device does not support SMS.")
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            ToolResult.success(name, "SMS sent to $phoneNumber")
        } catch (e: SecurityException) {
            ToolResult.error(name, "SMS permission not granted.")
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "SMS send failed")
        }
    }
}
