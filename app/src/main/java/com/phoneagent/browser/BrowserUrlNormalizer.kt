package com.phoneagent.browser

object BrowserUrlNormalizer {

    private val DANGEROUS_SCHEMES = setOf("javascript", "data", "file", "content", "intent", "blob", "filesystem")
    private val ALLOWED_SPECIAL_SCHEMES = setOf("tel", "mailto", "sms", "geo")

    private fun isInternalHost(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "0.0.0.0") return true
        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.16.")) return true
        if (host.startsWith("169.254.") || host.startsWith("fc") || host.startsWith("fd")) return true
        return false
    }

    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        if (trimmed.contains("://")) {
            val scheme = trimmed.substringBefore("://").lowercase()
            if (scheme in DANGEROUS_SCHEMES) return null
            if (scheme in ALLOWED_SPECIAL_SCHEMES) return trimmed
            try {
                val host = java.net.URI(trimmed).host ?: return null
                if (isInternalHost(host)) return null
            } catch (_: Exception) {
                return null
            }
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
