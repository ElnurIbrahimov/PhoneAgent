package com.phoneagent.agent.tools

import org.json.JSONObject

object ToolResult {
    fun success(toolName: String, content: String): String = JSONObject().apply {
        put("type", "tool_result")
        put("tool", toolName)
        put("success", true)
        put("content", content)
        put("error", JSONObject.NULL)
    }.toString()

    fun error(toolName: String, error: String, suggestion: String? = null): String = JSONObject().apply {
        put("type", "tool_result")
        put("tool", toolName)
        put("success", false)
        put("content", JSONObject.NULL)
        put("error", error)
        put("suggestion", suggestion ?: JSONObject.NULL)
    }.toString()
}