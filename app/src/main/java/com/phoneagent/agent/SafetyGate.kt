package com.phoneagent.agent

object SafetyGate {

    enum class RiskLevel { LOW, MEDIUM, HIGH }

    data class RiskAssessment(
        val level: RiskLevel,
        val reason: String,
        val toolName: String,
        val args: Map<String, String>
    )

    private val lowRiskTools = setOf(
        "browser.open_url", "browser.read_page", "browser.read_metadata",
        "browser.scroll", "browser.back", "browser.reload",
        "phone.list_apps", "phone.system_info", "phone.notifications",
        "phone.screenshot", "accessibility.read_tree", "accessibility.foreground_app",
        "phone.settings"
    )

    private val highRiskTools = setOf("phone.open_app", "phone.send_sms", "phone.call")
    private val mediumRiskTools = setOf(
        "browser.click_text", "browser.click_selector",
        "browser.type_into_selector", "browser.type_into_focused",
        "accessibility.tap_text", "accessibility.tap_at", "accessibility.swipe",
        "accessibility.type", "accessibility.back", "accessibility.home",
        "phone.clipboard"
    )

    private val sensitiveWordRegex = Regex(
        "\\b(submit|send|book|purchase|checkout|confirm|buy|reserve|delete|remove|cancel|download)\\b",
        RegexOption.IGNORE_CASE
    )

    private val sensitiveSelectorRegex = Regex(
        "(?:\\.|#)(submit|delete|confirm|purchase)",
        RegexOption.IGNORE_CASE
    )

    private val mediumRiskArgs = mapOf<String, (Map<String, String>) -> Boolean>(
        "browser.click_text" to { args ->
            args["text"]?.let { sensitiveWordRegex.containsMatchIn(it) } ?: false
        },
        "browser.click_selector" to { args ->
            args["selector"]?.let { sel ->
                sensitiveSelectorRegex.containsMatchIn(sel) ||
                sel.contains("password", ignoreCase = true) ||
                sel.contains("credit", ignoreCase = true)
            } ?: false
        },
        "browser.type_into_selector" to { args ->
            val selector = args["selector"] ?: ""
            val isSensitive = selector.contains("password", ignoreCase = true) ||
                selector.contains("credit", ignoreCase = true) ||
                selector.contains("card", ignoreCase = true) ||
                selector.contains("cvv", ignoreCase = true) ||
                selector.contains("ssn", ignoreCase = true)
            val text = args["text"] ?: ""
            isSensitive || text.length > 200
        },
        "browser.type_into_focused" to { args -> (args["text"]?.length ?: 0) > 200 }
    )

    fun assess(toolName: String, args: Map<String, String>): RiskAssessment {
        if (toolName in lowRiskTools) {
            return RiskAssessment(RiskLevel.LOW, "", toolName, args)
        }

        if (toolName in highRiskTools) {
            return RiskAssessment(RiskLevel.HIGH, "Tool '$toolName' requires user confirmation per safety policy.", toolName, args)
        }

        if (toolName in mediumRiskTools) {
            val checker = mediumRiskArgs[toolName]
            if (checker != null && checker(args)) {
                val argsSummary = args.entries.joinToString(", ") { "${it.key}=${it.value.take(60)}" }
                return RiskAssessment(
                    RiskLevel.MEDIUM,
                    "Potentially sensitive action: $argsSummary",
                    toolName, args
                )
            }
            return RiskAssessment(RiskLevel.LOW, "", toolName, args)
        }

        // Unknown tool: default to HIGH risk (fail-safe)
        return RiskAssessment(
            RiskLevel.HIGH,
            "Unknown tool '$toolName' requires user confirmation.",
            toolName, args
        )
    }
}
