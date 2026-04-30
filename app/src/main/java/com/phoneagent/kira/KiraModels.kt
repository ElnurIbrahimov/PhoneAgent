package com.phoneagent.kira

data class KiraHealth(
    val status: String = "",
    val mood: String = "neutral",
    val iris: String = ""
)

data class KiraContext(
    val context: String = ""
)

data class KiraIrisRouting(
    val profile: String = "balanced",
    val temperature: Double = 0.5,
    val maxTokens: Int = 2048,
    val style: String = "clear and complete",
    val depth: String = "medium",
    val warmthBoost: Boolean = false,
    val styleInjection: String = "",
    val reasoning: String = ""
)

data class KiraOk(
    val received: Boolean = true,
    val messagesIngested: Int = 0
)

data class KiraEmotion(
    val tension: Double = 0.0,
    val connection: Double = 0.5,
    val energy: Double = 0.8,
    val focus: Double = 0.5
)
