package com.phoneagent.iris

data class IrisState(
    val hour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
    val tensionScore: Float = 0.5f,
    val energyScore: Float = 0.5f,
    val messageLength: Int = 0,
    val messageType: String = "conversation", // "command", "question", "emotional", "conversation"
    val isLateNight: Boolean = hour in 0..5 || hour in 22..23,
    val isWorkHours: Boolean = hour in 9..17
) {
    fun toPromptContext(): String {
        return "Time: ${hour}:00. User state: tension=${"%.2f".format(tensionScore)}, energy=${"%.2f".format(energyScore)}. Message type: $messageType."
    }
}
