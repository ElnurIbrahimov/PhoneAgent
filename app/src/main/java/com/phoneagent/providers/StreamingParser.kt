package com.phoneagent.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONException
import org.json.JSONObject

object StreamingParser {

    fun parseStream(lines: Flow<String>): Flow<String> = flow {
        val buffer = StringBuilder()
        lines.collect { line ->
            if (line.startsWith("data: ")) {
                val data = line.substring(6)
                if (data == "[DONE]") {
                    return@collect
                }
                try {
                    val json = JSONObject(data)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val delta = choices.getJSONObject(0).optJSONObject("delta")
                        val content = delta?.optString("content", "")
                        if (!content.isNullOrEmpty()) {
                            buffer.append(content)
                            emit(content)
                        }
                    }
                } catch (e: JSONException) {
                    // Ignore malformed JSON in stream
                }
            }
        }
    }

    fun extractContentFromChunk(chunk: String): String? {
        return try {
            val lines = chunk.lines()
            var content: String? = null
            for (line in lines) {
                if (line.startsWith("data: ")) {
                    val data = line.substring(6)
                    if (data == "[DONE]") continue
                    val json = JSONObject(data)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val delta = choices.getJSONObject(0).optJSONObject("delta")
                        val piece = delta?.optString("content", "")
                        if (!piece.isNullOrEmpty()) {
                            content = (content ?: "") + piece
                        }
                    }
                }
            }
            content
        } catch (e: JSONException) {
            null
        }
    }
}
