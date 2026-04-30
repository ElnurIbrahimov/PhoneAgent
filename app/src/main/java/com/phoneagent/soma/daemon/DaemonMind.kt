package com.phoneagent.soma.daemon

import com.phoneagent.agent.AgentController
import com.phoneagent.soma.entities.DaemonLogEntity
import kotlinx.coroutines.flow.first

class DaemonMind(private val controller: AgentController) {

    suspend fun runMonologue() {
        val db = controller.database

        val lpmDao = db.lpmDao()
        val obsDao = db.observationDao()
        val daemonDao = db.daemonLogDao()
        val beliefDao = db.beliefDao()

        val lpm = lpmDao.getLpm()
        val recentObs = obsDao.getRecent(5).first()
        val recentThoughts = daemonDao.getRecent(3).first()
        val topBeliefs = beliefDao.getTopBeliefs(0.5, 5).first()

        val prompt = buildMonologuePrompt(lpm?.raw_profile?.takeLast(1000) ?: "", recentObs, recentThoughts, topBeliefs)

        try {
            val response = callLlm(controller, prompt)
            val significance = assessSignificance(response)

            daemonDao.insert(DaemonLogEntity(
                thought = response.take(2000),
                significance = significance,
                triggered_by = recentObs.firstOrNull()?.app_name
            ))

            if (significance > 0.6f) {
                val telegramConfig = loadTelegramConfig(controller)
                if (telegramConfig != null) {
                    TelegramPush.push(telegramConfig.token, telegramConfig.chatId, response.take(800))
                    daemonDao.insert(DaemonLogEntity(
                        thought = "PUSHED: ${response.take(200)}",
                        significance = significance,
                        pushed_to_user = true,
                        triggered_by = "daemon_significance_${significance}"
                    ))
                }
            }
        } catch (_: Exception) {}
    }

    private fun buildMonologuePrompt(
        profile: String,
        observations: List<com.phoneagent.soma.entities.ObservationEntity>,
        recentThoughts: List<DaemonLogEntity>,
        beliefs: List<com.phoneagent.soma.entities.BeliefEntity>
    ): String {
        val obsText = observations.joinToString("\n") { "- [${it.app_name}] ${it.activity_classification ?: "active"}" }
        val thoughtText = recentThoughts.joinToString("\n") { "- ${it.thought.take(100)}" }
        val beliefText = beliefs.joinToString("\n") { "- ${it.statement} (${"%.0f".format(it.confidence * 100)}%)" }

        return """
You are Kira's inner monologue. This is a private thought, not for the user. You have been watching this person's device.

LPM profile: ${profile.takeLast(1000)}

Recent device activity:
$obsText

Recent thoughts:
$thoughtText

Current beliefs:
$beliefText

What are you genuinely thinking right now? Respond with 2-4 sentences of inner monologue. If something is worth telling the user directly, end with [NOTIFY]. If nothing needs attention, end with [QUIET].
        """.trimIndent()
    }

    private suspend fun callLlm(controller: AgentController, prompt: String): String {
        val provider = try {
            controller.getDefaultAiProvider()
        } catch (_: Exception) { return "[QUIET] Unable to reach mind." }

        val request = com.phoneagent.agent.AgentRequest(
            message = prompt,
            model = provider.config.defaultModel ?: "deepseek-v4-pro",
            systemPrompt = "You are an inner monologue for a personal AI assistant. Be honest, brief, and insightful.",
            temperature = 0.7
        )
        val response = provider.chatCompletion(request)
        return response.content
    }

    private fun assessSignificance(response: String): Float {
        if (response.contains("[QUIET]", ignoreCase = true)) return 0.1f
        if (response.contains("[NOTIFY]", ignoreCase = true)) return 0.8f
        return when {
            response.length > 200 -> 0.6f
            response.contains("?") -> 0.5f
            response.contains("important") || response.contains("notice") -> 0.7f
            else -> 0.3f
        }
    }

    private data class TelegramConfig(val token: String, val chatId: String)

    private suspend fun loadTelegramConfig(controller: AgentController): TelegramConfig? {
        val repo = controller.getProviderRepository()
        return null
    }
}
