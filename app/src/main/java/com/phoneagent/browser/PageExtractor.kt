package com.phoneagent.browser

import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object PageExtractor {

    fun extractText(webView: WebView, callback: (String) -> Unit) {
        val js = """
            (function() {
                var txt = document.body ? document.body.innerText : '';
                txt = txt.replace(/\s+/g, ' ').trim();
                return txt;
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            val text = parseJsString(result)
            callback(text)
        }
    }

    fun extractMetadata(webView: WebView, callback: (String) -> Unit) {
        val js = """
            (function() {
                var links = [];
                var buttons = [];
                var inputs = [];
                var linkEls = document.querySelectorAll('a');
                var btnEls = document.querySelectorAll('button, input[type=button], input[type=submit]');
                var inputEls = document.querySelectorAll('input, textarea');
                for (var i = 0; i < Math.min(linkEls.length, 50); i++) {
                    links.push({ text: (linkEls[i].innerText || '').trim(), href: linkEls[i].href || '' });
                }
                for (var i = 0; i < Math.min(btnEls.length, 50); i++) {
                    buttons.push({ text: (btnEls[i].innerText || btnEls[i].value || '').trim() });
                }
                for (var i = 0; i < Math.min(inputEls.length, 50); i++) {
                    inputs.push({
                        tag: inputEls[i].tagName.toLowerCase(),
                        type: inputEls[i].type || '',
                        name: inputEls[i].name || '',
                        placeholder: inputEls[i].placeholder || '',
                        id: inputEls[i].id || ''
                    });
                }
                return {
                    title: document.title || '',
                    url: window.location.href || '',
                    links: links,
                    buttons: buttons,
                    inputs: inputs
                };
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            callback(parseJsJson(result))
        }
    }

    fun extractVisibleSummary(webView: WebView, callback: (String) -> Unit) {
        val js = """
            (function() {
                var txt = document.body ? document.body.innerText : '';
                txt = txt.replace(/\s+/g, ' ').trim();
                var excerpt = txt.substring(0, 2000);
                if (txt.length > 2000) excerpt += '... [truncated]';
                var links = [];
                var buttons = [];
                var inputs = [];
                var linkEls = document.querySelectorAll('a');
                var btnEls = document.querySelectorAll('button, input[type=button], input[type=submit]');
                var inputEls = document.querySelectorAll('input, textarea');
                for (var i = 0; i < Math.min(linkEls.length, 20); i++) {
                    links.push({ text: (linkEls[i].innerText || '').trim(), href: linkEls[i].href || '' });
                }
                for (var i = 0; i < Math.min(btnEls.length, 20); i++) {
                    buttons.push({ text: (btnEls[i].innerText || btnEls[i].value || '').trim() });
                }
                for (var i = 0; i < Math.min(inputEls.length, 20); i++) {
                    inputs.push({
                        tag: inputEls[i].tagName.toLowerCase(),
                        type: inputEls[i].type || '',
                        name: inputEls[i].name || '',
                        placeholder: inputEls[i].placeholder || '',
                        id: inputEls[i].id || ''
                    });
                }
                return {
                    title: document.title || '',
                    url: window.location.href || '',
                    excerpt: excerpt,
                    links: links,
                    buttons: buttons,
                    inputs: inputs
                };
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            callback(parseJsJson(result))
        }
    }

    private fun parseJsString(result: String?): String {
        if (result.isNullOrBlank() || result == "null") return ""
        return try {
            JSONTokener(result).nextValue() as? String ?: ""
        } catch (e: Exception) {
            result.trim('"').replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
        }
    }

    private fun parseJsJson(result: String?): String {
        if (result.isNullOrBlank() || result == "null") return "{}"
        return try {
            val value = JSONTokener(result).nextValue()
            if (value is JSONObject) value.toString() else result
        } catch (e: Exception) {
            result
        }
    }
}
