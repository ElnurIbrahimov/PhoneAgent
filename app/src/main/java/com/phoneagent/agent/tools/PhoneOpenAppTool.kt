package com.phoneagent.agent.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
class PhoneOpenAppTool(private val context: Context) : Tool, DescribableTool {

    override val name: String = "phone.open_app"
    override val description: String = "Open an app by app_name or package_name."
    override val argsDescription: String = "app_name (String, optional), package_name (String, optional)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val appName = arguments["app_name"]?.lowercase()?.trim()
        val packageName = arguments["package_name"]?.trim()

        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val activities = pm.queryIntentActivities(intent, 0)

            val matches = activities.filter { resolveInfo ->
                val label = resolveInfo.loadLabel(pm).toString()
                val pkg = resolveInfo.activityInfo.packageName
                (appName != null && label.lowercase().contains(appName)) ||
                        (packageName != null && pkg == packageName)
            }

            when {
                matches.isEmpty() -> {
                    ToolResult.error(name, "No matching app found.", "Use phone.list_apps to see installed apps.")
                }
                matches.size > 1 -> {
                    val ambiguity = matches.take(5).joinToString(", ") {
                        "${it.loadLabel(pm)} (${it.activityInfo.packageName})"
                    }
                    ToolResult.error(name, "Multiple matches found: $ambiguity. Please specify package_name.", "Specify a more precise package_name from the list.")
                }
                else -> {
                    val target = matches.first()
                    val launchIntent = pm.getLaunchIntentForPackage(target.activityInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        ToolResult.success(name, "Opened ${target.loadLabel(pm)}.")
                    } else {
                        ToolResult.error(name, "Could not create launch intent for ${target.activityInfo.packageName}.", "Use phone.list_apps to verify the app is installed.")
                    }
                }
            }
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Unknown error", "Use phone.list_apps to see installed apps.")
        }
    }
}
