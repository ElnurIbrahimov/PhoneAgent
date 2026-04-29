package com.phoneagent.agent.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhoneSystemInfoTool(private val context: Context) : Tool, DescribableTool {

    override val name: String = "phone.system_info"
    override val description: String = "Return basic device info: battery, time, Android version, device model, network."
    override val argsDescription: String = "none"

    override suspend fun execute(arguments: Map<String, String>): String {
        return try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else null
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork
            val networkCapabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            val networkType = when {
                networkCapabilities == null -> "unknown"
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                else -> "other"
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val currentTime = sdf.format(Date())

            val info = JSONObject().apply {
                put("batteryLevel", batteryPct ?: JSONObject.NULL)
                put("isCharging", isCharging)
                put("currentTime", currentTime)
                put("androidVersion", Build.VERSION.RELEASE)
                put("sdkInt", Build.VERSION.SDK_INT)
                put("deviceModel", Build.MODEL)
                put("deviceManufacturer", Build.MANUFACTURER)
                put("networkType", networkType)
            }

            ToolResult.success(name, info.toString())
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Unknown error")
        }
    }
}
