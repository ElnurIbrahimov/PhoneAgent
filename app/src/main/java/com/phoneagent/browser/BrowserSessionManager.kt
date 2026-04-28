package com.phoneagent.browser

import android.webkit.WebView
import java.lang.ref.WeakReference

object BrowserSessionManager {

    private var activeWebView: WeakReference<WebView>? = null
    @Volatile
    private var isActive = false

    fun setActiveWebView(webView: WebView) {
        activeWebView = WeakReference(webView)
        isActive = true
    }

    fun getActiveWebView(): WebView? {
        val webView = activeWebView?.get()
        if (webView == null) {
            clear()
        }
        return webView
    }

    fun hasActiveBrowser(): Boolean {
        return isActive && activeWebView?.get() != null
    }

    fun openUrl(url: String) {
        getActiveWebView()?.loadUrl(url)
    }

    fun canGoBack(): Boolean {
        return getActiveWebView()?.canGoBack() ?: false
    }

    fun goBack() {
        getActiveWebView()?.goBack()
    }

    fun reload() {
        getActiveWebView()?.reload()
    }

    fun clear() {
        activeWebView = null
        isActive = false
    }
}
