package com.phoneagent.browser

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.phoneagent.agent.tools.ToolResult
import kotlin.coroutines.resume

class BrowserTool(context: Context) {
    private val appContext: Context = context.applicationContext

    suspend fun execute(toolName: String, arguments: Map<String, String>): String {
        val action = arguments["action"] ?: return errorResult(toolName, "Missing 'action' argument.",
            "Available actions: open_url, read_page, read_metadata, click_text, click_selector, type_into_selector, type_into_focused, scroll, back, reload")

        return when (action) {
            "open_url" -> {
                val url = arguments["url"] ?: return errorResult(toolName, "Missing 'url' for open_url.")
                openUrl(toolName, url)
            }
            "read_page" -> readPage(toolName)
            "read_metadata" -> readMetadata(toolName)
            "click_text" -> {
                val text = arguments["text"] ?: return errorResult(toolName, "Missing 'text' for click_text.")
                clickText(toolName, text)
            }
            "click_selector" -> {
                val selector = arguments["selector"] ?: return errorResult(toolName, "Missing 'selector' for click_selector.")
                clickSelector(toolName, selector)
            }
            "type_into_selector" -> {
                val selector = arguments["selector"] ?: return errorResult(toolName, "Missing 'selector' for type_into_selector.")
                val text = arguments["text"] ?: return errorResult(toolName, "Missing 'text' for type_into_selector.")
                typeIntoSelector(toolName, selector, text)
            }
            "type_into_focused" -> {
                val text = arguments["text"] ?: return errorResult(toolName, "Missing 'text' for type_into_focused.")
                typeIntoFocused(toolName, text)
            }
            "scroll" -> {
                val direction = arguments["direction"] ?: "down"
                scroll(toolName, direction)
            }
            "back" -> back(toolName)
            "reload" -> reload(toolName)
            else -> errorResult(toolName, "Unknown browser action: $action",
                "Available actions: open_url, read_page, read_metadata, click_text, click_selector, type_into_selector, type_into_focused, scroll, back, reload")
        }
    }

    private suspend fun openUrl(toolName: String, url: String): String = withContext(Dispatchers.Main) {
        val normalizedUrl = BrowserUrlNormalizer.normalize(url)
            ?: return@withContext errorResult(toolName, "Invalid or blocked URL.",
                "Provide a valid http/https URL to navigate to.")
        if (!BrowserSessionManager.hasActiveBrowser()) {
            try {
                val intent = Intent(appContext, AgentBrowserActivity::class.java).apply {
                    putExtra("url", normalizedUrl)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                appContext.startActivity(intent)
                successResult(toolName, "Browser launched with URL: $normalizedUrl")
            } catch (e: Exception) {
                errorResult(toolName, "Browser not active and could not launch activity: ${e.message}",
                    "Use browser.open_url to launch the browser first.")
            }
        } else {
            BrowserSessionManager.openUrl(normalizedUrl)
            successResult(toolName, "Navigated to: $normalizedUrl")
        }
    }

    private suspend fun readPage(toolName: String): String = withContext(Dispatchers.Main) {
        val webView = BrowserSessionManager.getActiveWebView()
            ?: return@withContext errorResult(toolName, "Browser not active.",
                "Use browser.open_url to launch the browser first.")
        suspendCancellableCoroutine { continuation ->
            PageExtractor.extractText(webView) { text ->
                continuation.resume(
                    successResult(toolName, text.take(8000))
                )
            }
        }
    }

    private suspend fun readMetadata(toolName: String): String = withContext(Dispatchers.Main) {
        val webView = BrowserSessionManager.getActiveWebView()
            ?: return@withContext errorResult(toolName, "Browser not active.",
                "Use browser.open_url to launch the browser first.")
        suspendCancellableCoroutine { continuation ->
            PageExtractor.extractMetadata(webView) { meta ->
                continuation.resume(
                    successResult(toolName, meta.take(8000))
                )
            }
        }
    }

    private suspend fun clickText(toolName: String, text: String): String = withContext(Dispatchers.Main) {
        val webView = BrowserSessionManager.getActiveWebView()
            ?: return@withContext errorResult(toolName, "Browser not active.",
                "Use browser.open_url to launch the browser first.")
        suspendCancellableCoroutine { continuation ->
            DomActionExecutor.clickText(webView, text) { success, error ->
                continuation.resume(
                    if (success) successResult(toolName, "Clicked element with text: $text")
                    else errorResult(toolName, error ?: "Click failed.",
                        "Use browser.read_page to read the page and find clickable elements.")
                )
            }
        }
    }

    private suspend fun clickSelector(toolName: String, selector: String): String = withContext(Dispatchers.Main) {
        val webView = BrowserSessionManager.getActiveWebView()
            ?: return@withContext errorResult(toolName, "Browser not active.",
                "Use browser.open_url to launch the browser first.")
        suspendCancellableCoroutine { continuation ->
            DomActionExecutor.clickSelector(webView, selector) { success, error ->
                continuation.resume(
                    if (success) successResult(toolName, "Clicked element with selector: $selector")
                    else errorResult(toolName, error ?: "Click failed.",
                        "Use browser.read_page to verify the selector exists on the page.")
                )
            }
        }
    }

    private suspend fun typeIntoSelector(toolName: String, selector: String, text: String): String = withContext(Dispatchers.Main) {
        val webView = BrowserSessionManager.getActiveWebView()
            ?: return@withContext errorResult(toolName, "Browser not active.",
                "Use browser.open_url to launch the browser first.")
        suspendCancellableCoroutine { continuation ->
            DomActionExecutor.typeIntoSelector(webView, selector, text) { success, error ->
                continuation.resume(
                    if (success) successResult(toolName, "Typed into $selector.")
                    else errorResult(toolName, error ?: "Type failed.",
                        "Use browser.read_page to verify the selector targets an input element.")
                )
            }
        }
    }

    private suspend fun typeIntoFocused(toolName: String, text: String): String = withContext(Dispatchers.Main) {
        val webView = BrowserSessionManager.getActiveWebView()
            ?: return@withContext errorResult(toolName, "Browser not active.",
                "Use browser.open_url to launch the browser first.")
        suspendCancellableCoroutine { continuation ->
            DomActionExecutor.typeIntoFocused(webView, text) { success, error ->
                continuation.resume(
                    if (success) successResult(toolName, "Typed into focused element.")
                    else errorResult(toolName, error ?: "Type into focused element failed.",
                        "Use browser.click_selector to focus an input element first, then try typing.")
                )
            }
        }
    }

    private suspend fun scroll(toolName: String, direction: String): String = withContext(Dispatchers.Main) {
        val webView = BrowserSessionManager.getActiveWebView()
            ?: return@withContext errorResult(toolName, "Browser not active.",
                "Use browser.open_url to launch the browser first.")
        suspendCancellableCoroutine { continuation ->
            DomActionExecutor.scroll(webView, direction) { success, error ->
                continuation.resume(
                    if (success) successResult(toolName, "Scrolled $direction.")
                    else errorResult(toolName, error ?: "Scroll failed.",
                        "Use browser.read_page to verify the page has scrollable content.")
                )
            }
        }
    }

    private suspend fun back(toolName: String): String = withContext(Dispatchers.Main) {
        if (BrowserSessionManager.canGoBack()) {
            BrowserSessionManager.goBack()
            successResult(toolName, "Navigated back.")
        } else {
            errorResult(toolName, "Cannot go back.",
                "No previous page in browser history. Use browser.open_url to navigate to a new page.")
        }
    }

    private suspend fun reload(toolName: String): String = withContext(Dispatchers.Main) {
        if (!BrowserSessionManager.hasActiveBrowser()) {
            return@withContext errorResult(toolName, "Browser not active.",
                "Use browser.open_url to launch the browser first.")
        }
        BrowserSessionManager.reload()
        successResult(toolName, "Page reloaded.")
    }

    private fun successResult(toolName: String, content: String): String = ToolResult.success(toolName, content)

    private fun errorResult(toolName: String, error: String, suggestion: String? = null): String = ToolResult.error(toolName, error, suggestion)
}
