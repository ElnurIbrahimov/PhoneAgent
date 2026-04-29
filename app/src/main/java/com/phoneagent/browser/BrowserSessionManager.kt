package com.phoneagent.browser

import android.webkit.WebView
import java.lang.ref.WeakReference

object BrowserSessionManager {

    private var activeWebView: WeakReference<WebView>? = null

    @Synchronized
    fun setActiveWebView(webView: WebView) {
        activeWebView = WeakReference(webView)
    }

    @Synchronized
    fun getActiveWebView(): WebView? {
        return activeWebView?.get()
    }

    @Synchronized
    fun hasActiveBrowser(): Boolean {
        return activeWebView?.get() != null
    }

    @Synchronized
    fun openUrl(url: String) {
        getActiveWebView()?.loadUrl(url)
    }

    @Synchronized
    fun canGoBack(): Boolean {
        return getActiveWebView()?.canGoBack() ?: false
    }

    @Synchronized
    fun goBack() {
        getActiveWebView()?.goBack()
    }

    @Synchronized
    fun reload() {
        getActiveWebView()?.reload()
    }

    @Synchronized
    fun clear() {
        activeWebView = null
    }
}
