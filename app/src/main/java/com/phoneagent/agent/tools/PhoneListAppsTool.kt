package com.phoneagent.agent.tools

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import org.json.JSONArray
import org.json.JSONObject

class PhoneListAppsTool(private val context: Context) : Tool, DescribableTool {

    override val name: String = "phone.list_apps"
    override val description: String = "List installed launchable apps with label and package name."
    override val argsDescription: String = "none"

    override suspend fun execute(arguments: Map<String, String>): String {
        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val activities = pm.queryIntentActivities(intent, 0)
            val array = JSONArray()
            activities.forEach { resolveInfo ->
                val label = resolveInfo.loadLabel(pm).toString()
                val packageName = resolveInfo.activityInfo.packageName
                array.put(JSONObject().apply {
                    put("label", label)
                    put("packageName", packageName)
                })
            }
            ToolResult.success(name, array.toString())
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Unknown error")
        }
    }
}
