package com.phoneagent.soma

import kotlinx.coroutines.flow.first

class SomaContextBuilder(
    private val lpmManager: LpmManager,
    private val beliefEngine: BeliefEngine,
    private val memoryEngine: MemoryEngine
) {
    suspend fun buildSomaContext(userMessage: String): String {
        val sb = StringBuilder()

        val lpm = lpmManager.getOrCreate()
        if (lpm.total_sessions > 0) {
            sb.appendLine("[KIRA_MIND — session ${lpm.total_sessions}]")
            val profileExcerpt = lpm.raw_profile.takeLast(1200)
            if (profileExcerpt.isNotBlank()) {
                sb.appendLine("Who this person is: $profileExcerpt")
            }
        }

        try {
            val predictions = org.json.JSONArray(lpm.behavioral_predictions)
            if (predictions.length() > 0) {
                sb.append("Current foresight: ")
                val preds = (0 until predictions.length()).map { predictions.getString(it) }
                sb.appendLine(preds.joinToString("; "))
            }
        } catch (e: org.json.JSONException) {
            android.util.Log.w("SomaContextBuilder", "Failed to parse behavioral predictions", e)
        }

        val relevantMemories = memoryEngine.retrieve(userMessage, limit = 5)
        if (relevantMemories.isNotEmpty()) {
            sb.appendLine("Relevant memories:")
            relevantMemories.forEach { mem ->
                sb.appendLine("- ${mem.content.take(200)} [importance: ${"%.2f".format(mem.importance)}]")
            }
        }

        return sb.toString()
    }
}
