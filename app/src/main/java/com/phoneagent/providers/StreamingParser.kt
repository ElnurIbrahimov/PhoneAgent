package com.phoneagent.providers

import com.phoneagent.streaming.ChunkType
import com.phoneagent.streaming.StreamChunk
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

    fun parseChunk(raw: String, providerName: String): StreamChunk {
        return when {
            raw.startsWith("data: ") -> parseSSEData(raw.removePrefix("data: ").trim())
            raw.startsWith("{") -> parseJSONChunk(raw)
            raw.trim() == "[DONE]" -> StreamChunk(ChunkType.DONE, "")
            else -> StreamChunk(ChunkType.TOKEN, raw)
        }
    }

    private fun parseSSEData(data: String): StreamChunk {
        if (data == "[DONE]") return StreamChunk(ChunkType.DONE, "")
        return try {
            val json = JSONObject(data)
            val choices = json.optJSONArray("choices")?.optJSONObject(0)
            val delta = choices?.optJSONObject("delta")

            // OpenAI o1/o3 reasoning content
            delta?.optString("reasoning_content", null)
                ?.takeIf { it.isNotBlank() }
                ?.let { return StreamChunk(ChunkType.REASONING, it) }

            // Regular content token
            delta?.optString("content", null)
                ?.takeIf { it.isNotBlank() }
                ?.let { return StreamChunk(ChunkType.TOKEN, it) }

            // Tool call end
            choices?.optString("finish_reason")?.let {
                if (it == "tool_calls") return StreamChunk(ChunkType.TOOL_CALL_END, "")
            }

            StreamChunk(ChunkType.TOKEN, "")
        } catch (e: Exception) {
            StreamChunk(ChunkType.TOKEN, "")
        }
    }

    private fun parseJSONChunk(json: String): StreamChunk {
        return try {
            val obj = JSONObject(json)
            val choices = obj.optJSONArray("choices")?.optJSONObject(0)
            val delta = choices?.optJSONObject("delta")

            delta?.optString("content", null)
                ?.takeIf { it.isNotBlank() }
                ?.let { return StreamChunk(ChunkType.TOKEN, it) }

            delta?.optString("reasoning_content", null)
                ?.takeIf { it.isNotBlank() }
                ?.let { return StreamChunk(ChunkType.REASONING, it) }

            StreamChunk(ChunkType.TOKEN, "")
        } catch (e: Exception) {
            StreamChunk(ChunkType.TOKEN, "")
        }
    }
}