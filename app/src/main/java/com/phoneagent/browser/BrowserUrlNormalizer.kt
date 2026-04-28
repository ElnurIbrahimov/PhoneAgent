package com.phoneagent.browser

object BrowserUrlNormalizer {

    fun normalize(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return trimmed
        }
        return if (URL_SCHEME_REGEX.containsMatchIn(trimmed)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private val URL_SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
}
