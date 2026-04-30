package com.phoneagent.agent

object DestructiveActionDetector {

    private data class DangerPattern(
        val sequence: List<String>,
        val reason: String
    )

    private val patterns = listOf(
        DangerPattern(
            listOf("phone.notifications", "accessibility.type*", "browser.click_text"),
            "Reading notifications then typing into fields may indicate credential interception."
        ),
        DangerPattern(
            listOf("phone.notifications", "accessibility.type*"),
            "Reading notifications then typing may indicate automated account access."
        ),
        DangerPattern(
            listOf("accessibility.read_tree", "accessibility.type*", "browser.click_text"),
            "Reading app UI, typing into fields, then clicking may indicate automated account access."
        ),
        DangerPattern(
            listOf("phone.notifications", "accessibility.read_tree", "phone.send_sms"),
            "Reading notifications + app UI then sending SMS may indicate OTP interception."
        ),
        DangerPattern(
            listOf("phone.notifications", "browser.type_into_selector", "browser.click_text"),
            "Reading notifications then filling browser forms may indicate credential theft."
        )
    )

    fun assessSequence(steps: List<AgentStep>): String? {
        val toolSequence = steps.mapNotNull { step ->
            when (step.action) {
                is AgentAction.ToolCall -> step.action.tool
                else -> null
            }
        }

        for (pattern in patterns) {
            if (matchesPattern(toolSequence, pattern.sequence)) {
                return pattern.reason
            }
        }
        return null
    }

    private fun matchesPattern(actual: List<String>, pattern: List<String>): Boolean {
        if (actual.size < pattern.size) return false
        val recent = actual.takeLast(pattern.size)
        return recent.zip(pattern).all { (a, p) ->
            if (p.endsWith("*")) a.startsWith(p.dropLast(1))
            else a == p
        }
    }
}
