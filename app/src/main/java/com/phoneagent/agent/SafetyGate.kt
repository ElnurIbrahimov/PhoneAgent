package com.phoneagent.agent

object SafetyGate {

    private val sensitiveWords = setOf(
        "pay", "buy", "purchase", "checkout", "order",
        "submit", "send", "delete", "remove",
        "password", "login", "transfer", "confirm", "book", "reserve"
    )

    fun check(toolName: String, args: Map<String, String>): SafetyResult {
        val combined = buildString {
            append(toolName.lowercase())
            args.values.forEach { append(" "); append(it.lowercase()) }
        }
        val words = combined.split(Regex("[^a-z0-9]+"))
        val matched = sensitiveWords.filter { keyword ->
            words.any { it == keyword } || combined.contains(" $keyword ") || combined.startsWith("$keyword ") || combined.endsWith(" $keyword")
        }
        return if (matched.isNotEmpty()) {
            SafetyResult.Blocked(
                reason = "Sensitive keywords detected: ${matched.joinToString(", ")}. " +
                        "Tool: $toolName, Args: $args"
            )
        } else {
            SafetyResult.Allowed
        }
    }

    sealed class SafetyResult {
        object Allowed : SafetyResult()
        data class Blocked(val reason: String) : SafetyResult()
    }
}
