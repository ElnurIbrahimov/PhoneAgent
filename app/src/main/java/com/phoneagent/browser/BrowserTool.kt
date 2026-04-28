package com.phoneagent.browser

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

class BrowserTool(private val context: Context) {

    suspend fun execute(toolName: String, arguments: Map<String, String>): String {
        val action = arguments["action"] ?: return errorResult(toolName, "Missing 'action' argument.")

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
            else -> errorResult(toolName, "Unknown browser action: $action")
        }
    }

    private suspend fun openUrl(toolName: String, url: String): String = withContext(Dispatchers.Main) {
        val normalizedUrl = BrowserUrlNormalizer.normalize(url)
        if (!BrowserSessionManager.hasActiveBrowser()) {
            try {
                val intent = Intent(context, AgentBrowserActivity::class.java).apply {
                    putExtra("url", normalizedUrl)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
                successResult(toolName, "Browser launched with URL: $normalizedUrl")
            } catch (e: Exception) {
                errorResult(toolName, "Browser not active and could not launch activity: ${e.message}")
            }
        } else {
            BrowserSessionManager.openUrl(normalizedUrl)
            successResult(toolName, "Navigated to: $normalizedUrl")
        }
    }

    private suspend fun readPage(toolName: String): String = withContext(Dispatchers.Main) {
        val webView = BrowserSessionManager.getActiveWebView()
            ?: return@withContext errorResult(toolName, "Browser not active.")
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
            ?: return@withContext errorResult(toolName, "Browser not active.")
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
            ?: return@withContext errorResult(toolName, "Browser not active.")
        suspendCancellableCoroutine { continuation ->
            DomActionExecutor.clickText(webView, text) { success, error ->
                continuation.resume(
                    if (success) successResult(toolName, "Clicked element with text: $text")
                    else errorResult(toolName, error ?: "Click failed.")
                )
            }
        }
    }

    private suspend fun clickSelector(toolName: String, selector: String): String = withContext(Dispatchers.Main) {
        val webView = BrowserSessionManager.getActiveWebView()
            ?: return@withContext errorResult(toolName, "Browser not active.")
        suspendCancellableCoroutine { continuation ->
            DomActionExecutor.clickSelector(webView, selector) { success, error ->
                continuation.resume(
                    if (success) successResult(toolName, "Clicked element with selector: $selector")
                    else errorResult(toolName, error ?: "Click failed.")
                )
            }
        }
    }

    private suspend fun typeIntoSelector(toolName: String, selector: String, text: String): String = withContext(Dispatchers.Main) {
        val webView = BrowserSessionManager.getActiveWebView()
            ?: return@withContext errorResult(toolName, "Browser not active.")
        suspendCancellableCoroutine { continuation ->
            DomActionExecutor.typeIntoSelector(webView, selector, text) { success, error ->
                continuation.resume(
                    if (success) successResult(toolName, "Typed into $selector.")
                    else errorResult(toolName, error ?: "Type failed.")
                )
            }
        }
    }

    private suspend fun typeIntoFocused(toolName: String, text: String): String = withContext(Dispatchers.Main) {
        val webView = BrowserSessionManager.getActiveWebView()
            ?: return@withContext errorResult(toolName, "Browser not active.")
        suspendCancellableCoroutine { continuation ->
            DomActionExecutor.typeIntoFocused(webView, text) { success, error ->
                continuation.resume(
                    if (success) successResult(toolName, "Typed into focused element.")
                    else errorResult(toolName, error ?: "Type into focused element failed.")
                )
            }
        }
    }

    private suspend fun scroll(toolName: String, direction: String): String = withContext(Dispatchers.Main) {
        val webView = BrowserSessionManager.getActiveWebView()
            ?: return@withContext errorResult(toolName, "Browser not active.")
        suspendCancellableCoroutine { continuation ->
            DomActionExecutor.scroll(webView, direction) { success, error ->
                continuation.resume(
                    if (success) successResult(toolName, "Scrolled $direction.")
                    else errorResult(toolName, error ?: "Scroll failed.")
                )
            }
        }
    }

    private suspend fun back(toolName: String): String = withContext(Dispatchers.Main) {
        if (BrowserSessionManager.canGoBack()) {
            BrowserSessionManager.goBack()
            successResult(toolName, "Navigated back.")
        } else {
            errorResult(toolName, "Cannot go back.")
        }
    }

    private suspend fun reload(toolName: String): String = withContext(Dispatchers.Main) {
        if (!BrowserSessionManager.hasActiveBrowser()) {
            return@withContext errorResult(toolName, "Browser not active.")
        }
        BrowserSessionManager.reload()
        successResult(toolName, "Page reloaded.")
    }

    private fun successResult(toolName: String, content: String): String {
        return JSONObject().apply {
            put("type", "tool_result")
            put("tool", toolName)
            put("success", true)
            put("content", content)
            put("error", JSONObject.NULL)
        }.toString()
    }

    private fun errorResult(toolName: String, error: String): String {
        return JSONObject().apply {
            put("type", "tool_result")
            put("tool", toolName)
            put("success", false)
            put("content", JSONObject.NULL)
            put("error", error)
        }.toString()
    }
}
