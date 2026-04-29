package com.phoneagent.providers

import org.json.JSONObject

object StreamingParser {

    fun extractContentFromChunk(chunk: String): String? {
        if (chunk.isBlank()) return null
        return try {
            val json = JSONObject(chunk)
            val delta = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta") ?: return null
            if (!delta.has("content") || delta.isNull("content")) return null
            delta.getString("content").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
