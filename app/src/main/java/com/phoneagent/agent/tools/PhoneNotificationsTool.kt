package com.phoneagent.agent.tools

import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import com.phoneagent.perception.PhoneAgentNotificationListener
import org.json.JSONArray
import org.json.JSONObject

class PhoneNotificationsTool : Tool, DescribableTool {

    override val name: String = "phone.notifications"
    override val description: String = "Get recent phone notifications with app name, title, and text."
    override val argsDescription: String = "count (Int, optional, default 10)"

    override suspend fun execute(arguments: Map<String, String>): String {
        return try {
            val count = arguments["count"]?.toIntOrNull() ?: 10
            val notifications = PhoneAgentNotificationListener.getRecent(count)
            val array = JSONArray()
            notifications.forEach { entry ->
                array.put(JSONObject().apply {
                    put("appName", entry.appName)
                    put("title", entry.title)
                    put("text", entry.text)
                    put("packageName", entry.packageName)
                })
            }
            ToolResult.success(name, array.toString())
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Notification read failed")
        }
    }
}
