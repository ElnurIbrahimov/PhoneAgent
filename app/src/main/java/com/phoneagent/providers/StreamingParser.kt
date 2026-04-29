package com.phoneagent.providers

import org.json.JSONObject

object StreamingParser {

    fun extractContentFromChunk(chunk: String): String? {
        if (chunk.isBlank() || chunk == "[DONE]") return null
        return try {
            val json = JSONObject(chunk)
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta")
                ?.optString("content", null)
        } catch (e: Exception) {
            null
        }
    }
}
