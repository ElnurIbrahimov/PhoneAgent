package com.phoneagent.browser

import android.annotation.SuppressLint
import androidx.compose.runtime.DisposableEffect
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AgentWebView(
    modifier: Modifier = Modifier,
    initialUrl: String = "https://www.google.com",
    onPageLoaded: ((String?) -> Unit)? = null
) {
    var webViewRef: WebView? = null

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
            BrowserSessionManager.clear()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).also { webViewRef = it }.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadsImagesAutomatically = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        request?.url?.let { view?.loadUrl(it.toString()) }
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onPageLoaded?.invoke(url)
                    }
                }

                webChromeClient = WebChromeClient()

                BrowserSessionManager.setActiveWebView(this)
                loadUrl(initialUrl)
            }
        },
        update = { webView ->
            BrowserSessionManager.setActiveWebView(webView)
        }
    )
}
