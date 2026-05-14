package com.phoneagent.agent

import com.phoneagent.worldmodel.PersonalWorldModel

object SafetyGate {

    enum class RiskLevel { LOW, MEDIUM, HIGH }

    data class RiskAssessment(
        val level: RiskLevel,
        val reason: String,
        val toolName: String,
        val args: Map<String, String>,
        val escalationFactors: List<String> = emptyList()
    )

    private val lowRiskTools = setOf(
        "browser.open_url", "browser.read_page", "browser.read_metadata",
        "browser.scroll", "browser.back", "browser.reload",
        "phone.list_apps", "phone.system_info", "phone.notifications",
        "phone.screenshot", "accessibility.read_tree", "accessibility.foreground_app"
    )

    private val highRiskTools = setOf("phone.open_app", "phone.send_sms", "phone.call", "phone.settings")
    private val mediumRiskTools = setOf(
        "browser.click_text", "browser.click_selector",
        "browser.type_into_selector", "browser.type_into_focused",
        "accessibility.tap_text", "accessibility.tap_at", "accessibility.swipe",
        "accessibility.type", "accessibility.back", "accessibility.home",
        "phone.clipboard"
    )

    private val privacySensitiveTools = setOf(
        "phone.clipboard", "phone.notifications", "accessibility.read_tree",
        "browser.type_into_selector", "browser.type_into_focused"
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

    suspend fun assess(
        toolName: String,
        args: Map<String, String>,
        worldModel: PersonalWorldModel? = null
    ): RiskAssessment {
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
                val baseAssessment = RiskAssessment(
                    RiskLevel.MEDIUM,
                    "Potentially sensitive action: $argsSummary",
                    toolName, args
                )
                return escalateIfNeeded(baseAssessment, worldModel)
            }
            val baseAssessment = RiskAssessment(RiskLevel.LOW, "", toolName, args)
            return escalateIfNeeded(baseAssessment, worldModel)
        }

        // Unknown tool: default to HIGH risk (fail-safe)
        val baseAssessment = RiskAssessment(
            RiskLevel.HIGH,
            "Unknown tool '$toolName' requires user confirmation.",
            toolName, args
        )
        return escalateIfNeeded(baseAssessment, worldModel)
    }

    private suspend fun escalateIfNeeded(
        baseAssessment: RiskAssessment,
        worldModel: PersonalWorldModel?
    ): RiskAssessment {
        if (worldModel == null) return baseAssessment

        val escalationFactors = mutableListOf<String>()

        val profile = worldModel.getProfile()
        val riskTolerance = profile?.riskTolerance ?: "MEDIUM"

        if (riskTolerance == "LOW" && baseAssessment.level != RiskLevel.LOW) {
            escalationFactors.add("User has LOW risk tolerance")
        }

        val deniedToolsPrefs = worldModel.getPreferencesForCategory("safety_denied")
        if (deniedToolsPrefs.any { it.key == baseAssessment.toolName }) {
            escalationFactors.add("Tool '$toolName' was previously denied by user")
        }

        val privacyBelief = worldModel.getBelief("privacy", "concerned")
        if (privacyBelief > 0.7 && baseAssessment.toolName in privacySensitiveTools) {
            escalationFactors.add("User has high privacy concern ($privacyBelief) and tool is privacy-sensitive")
        }

        if (escalationFactors.isEmpty()) return baseAssessment

        return RiskAssessment(
            level = RiskLevel.HIGH,
            reason = baseAssessment.reason,
            toolName = baseAssessment.toolName,
            args = baseAssessment.args,
            escalationFactors = escalationFactors
        )
    }
}
