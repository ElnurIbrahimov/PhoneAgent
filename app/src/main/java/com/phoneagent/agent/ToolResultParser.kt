package com.phoneagent.agent

import org.json.JSONObject

data class ParsedToolResult(
    val success: Boolean,
    val content: String?,
    val error: String?,
    val tool: String?
)

object ToolResultParser {

    fun parse(jsonString: String): ParsedToolResult {
        return try {
            val json = JSONObject(jsonString)
            ParsedToolResult(
                success = json.optBoolean("success", false),
                content = if (json.isNull("content")) null else json.optString("content", null),
                error = if (json.isNull("error")) null else json.optString("error", null),
                tool = json.optString("tool", null)
            )
        } catch (e: Exception) {
            ParsedToolResult(success = false, content = null, error = "Invalid tool result JSON", tool = null)
        }
    }
}
