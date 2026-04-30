package com.phoneagent.browser

import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

object DomActionExecutor {

    private fun sanitizeJsString(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("</script>", "<\\/script>")
            .replace("</", "<\\/")
    }

    fun clickText(webView: WebView, text: String, callback: ((Boolean, String?) -> Unit)? = null) {
        val args = JSONArray().apply { put(sanitizeJsString(text)) }.toString()
        val js = """
            (function() {
                var args = $args;
                var text = args[0];
                var selectors = ['button', 'a', 'div', 'span', 'input[type=button]', 'input[type=submit]', 'label', '[role=button]', '[role=link]'];
                for (var s = 0; s < selectors.length; s++) {
                    var els = document.querySelectorAll(selectors[s]);
                    for (var i = 0; i < els.length; i++) {
                        var el = els[i];
                        var txt = (el.innerText || el.value || el.getAttribute('aria-label') || el.getAttribute('title') || el.getAttribute('placeholder') || '').trim();
                        if (txt.toLowerCase() === text.toLowerCase()) {
                            el.click();
                            return 'clicked';
                        }
                    }
                }
                for (var s = 0; s < selectors.length; s++) {
                    var els = document.querySelectorAll(selectors[s]);
                    for (var i = 0; i < els.length; i++) {
                        var el = els[i];
                        var txt = (el.innerText || el.value || el.getAttribute('aria-label') || el.getAttribute('title') || el.getAttribute('placeholder') || '').trim();
                        if (txt.toLowerCase().indexOf(text.toLowerCase()) !== -1) {
                            el.click();
                            return 'clicked_partial';
                        }
                    }
                }
                return 'not_found';
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            val success = result?.contains("clicked") == true || result?.contains("clicked_partial") == true
            val error = if (success) null else "Element with text '$text' not found."
            callback?.invoke(success, error)
        }
    }

    fun clickSelector(webView: WebView, selector: String, callback: ((Boolean, String?) -> Unit)? = null) {
        val args = JSONArray().apply { put(sanitizeJsString(selector)) }.toString()
        val js = """
            (function() {
                var args = $args;
                var selector = args[0];
                var el = document.querySelector(selector);
                if (el) {
                    el.click();
                    return 'clicked';
                }
                return 'not_found';
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            val success = result?.contains("clicked") == true
            val error = if (success) null else "Element with selector '$selector' not found."
            callback?.invoke(success, error)
        }
    }

    fun typeIntoSelector(webView: WebView, selector: String, text: String, callback: ((Boolean, String?) -> Unit)? = null) {
        val args = JSONArray().apply { put(sanitizeJsString(selector)); put(sanitizeJsString(text)) }.toString()
        val js = """
            (function() {
                var args = $args;
                var selector = args[0];
                var text = args[1];
                var el = document.querySelector(selector);
                if (!el) return 'not_found';
                el.focus();
                if (el.tagName.toLowerCase() === 'input' || el.tagName.toLowerCase() === 'textarea') {
                    el.value = text;
                } else {
                    el.innerText = text;
                }
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return 'typed';
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            val success = result?.contains("typed") == true
            val error = if (success) null else "Element with selector '$selector' not found."
            callback?.invoke(success, error)
        }
    }

    fun typeIntoFocused(webView: WebView, text: String, callback: ((Boolean, String?) -> Unit)? = null) {
        val args = JSONArray().apply { put(sanitizeJsString(text)) }.toString()
        val js = """
            (function() {
                var args = $args;
                var text = args[0];
                var el = document.activeElement;
                if (!el) return 'not_found';
                el.focus();
                if (el.tagName.toLowerCase() === 'input' || el.tagName.toLowerCase() === 'textarea') {
                    el.value = text;
                } else {
                    el.innerText = text;
                }
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return 'typed';
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            val success = result?.contains("typed") == true
            val error = if (success) null else "No focused element found."
            callback?.invoke(success, error)
        }
    }

    fun scroll(webView: WebView, direction: String, callback: ((Boolean, String?) -> Unit)? = null) {
        val amount = if (direction.lowercase() == "up") -500 else 500
        val js = "window.scrollBy(0, $amount); 'scrolled';"
        webView.evaluateJavascript(js) { result ->
            val success = result?.contains("scrolled") == true
            callback?.invoke(success, if (success) null else "Scroll failed.")
        }
    }
}
