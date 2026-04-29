package com.phoneagent.agent.tools

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import org.json.JSONObject

class PhoneSettingsTool(private val context: Context) : Tool, DescribableTool {
    override val name: String = "phone.settings"
    override val description: String = "Open system settings pages or check device info."
    override val argsDescription: String = "action (String: wifi_settings/bluetooth_settings/brightness/airplane_mode)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val action = arguments["action"]?.lowercase() ?: return errorResult("Missing 'action' argument")
        return try {
            val intent = when (action) {
                "wifi_settings" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                "bluetooth_settings" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                "brightness" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                "airplane_mode" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                else -> return errorResult("Unknown action: $action. Use wifi_settings, bluetooth_settings, brightness, airplane_mode, location, or battery.", "Try one of: wifi_settings, bluetooth_settings, brightness, airplane_mode, location, battery.")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            JSONObject().apply {
                put("type", "tool_result"); put("tool", name); put("success", true)
                put("content", "Opened $action settings."); put("error", JSONObject.NULL)
            }.toString()
        } catch (e: Exception) {
            errorResult(e.message ?: "Settings operation failed", "Try a different settings action.")
        }
    }

    private fun errorResult(error: String, suggestion: String? = null): String = JSONObject().apply {
        put("type", "tool_result"); put("tool", name); put("success", false)
        put("content", JSONObject.NULL); put("error", error)
        if (suggestion != null) put("suggestion", suggestion)
    }.toString()
}
