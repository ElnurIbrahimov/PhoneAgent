package com.phoneagent.browser

object BrowserUrlNormalizer {

    private val DANGEROUS_SCHEMES = setOf("javascript", "data", "file", "content", "intent", "blob", "filesystem")

    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        if (trimmed.contains("://")) {
            val scheme = trimmed.substringBefore("://").lowercase()
            if (scheme in DANGEROUS_SCHEMES) return null
            return trimmed
        }

        if (trimmed.contains(":")) {
            val scheme = trimmed.substringBefore(":").lowercase()
            if (scheme in DANGEROUS_SCHEMES) return null
        }

        if (trimmed.startsWith("about:")) return trimmed

        return "https://$trimmed"
    }
}
