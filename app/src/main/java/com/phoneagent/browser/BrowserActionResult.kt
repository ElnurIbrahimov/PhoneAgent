package com.phoneagent.browser

data class BrowserActionResult(
    val success: Boolean,
    val url: String? = null,
    val title: String? = null,
    val content: String? = null,
    val metadata: String? = null,
    val error: String? = null
)
