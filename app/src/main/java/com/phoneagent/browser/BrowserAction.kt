package com.phoneagent.browser

sealed class BrowserAction {
    data class OpenUrl(val url: String) : BrowserAction()
    object ReadPage : BrowserAction()
    object ReadMetadata : BrowserAction()
    data class ClickText(val text: String) : BrowserAction()
    data class ClickSelector(val selector: String) : BrowserAction()
    data class TypeIntoSelector(val selector: String, val text: String) : BrowserAction()
    data class Scroll(val direction: String) : BrowserAction()
    object Back : BrowserAction()
    object Reload : BrowserAction()
}
