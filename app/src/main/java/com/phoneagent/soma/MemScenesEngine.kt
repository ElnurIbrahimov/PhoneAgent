package com.phoneagent.soma

import com.phoneagent.soma.daos.MemSceneDao
import com.phoneagent.soma.entities.MemSceneEntity
import org.json.JSONArray

class MemScenesEngine(
    private val sceneDao: MemSceneDao,
    private val memoryEngine: MemoryEngine
) {
    suspend fun clusterSessionMemories(sessionId: String, recentMemories: List<String>, llmCall: suspend (String) -> String) {
        if (recentMemories.isEmpty()) return

        val prompt = """
Analyze these memories from a recent conversation session. Group them into 1-3 psychological themes (not "daily activities" — things like "avoidance under pressure", "curiosity about systems", "loyalty to the attempt itself", "need for recognition").

Memories:
${recentMemories.joinToString("\n") { "- $it" }}

Respond ONLY with JSON array of {theme, summary}:
        """.trimIndent()

        try {
            val response = llmCall(prompt)
            val scenes = JSONArray(response)
            for (i in 0 until scenes.length()) {
                val obj = scenes.getJSONObject(i)
                sceneDao.upsert(MemSceneEntity(
                    theme = obj.getString("theme"),
                    summary = obj.getString("summary"),
                    session_id = sessionId
                ))
            }
        } catch (_: Exception) {}
    }
}
