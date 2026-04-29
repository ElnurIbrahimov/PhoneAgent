package com.phoneagent.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserUrlNormalizerTest {

    @Test
    fun `normalize adds https scheme when missing`() {
        assertEquals("https://google.com", BrowserUrlNormalizer.normalize("google.com"))
    }

    @Test
    fun `normalize preserves existing scheme`() {
        assertEquals("http://localhost:8080", BrowserUrlNormalizer.normalize("http://localhost:8080"))
    }

    @Test
    fun `normalize preserves paths and query strings`() {
        assertEquals("https://example.com/path?q=test", BrowserUrlNormalizer.normalize("example.com/path?q=test"))
    }

    @Test
    fun `normalize rejects javascript scheme`() {
        assertNull(BrowserUrlNormalizer.normalize("javascript:alert(1)"))
    }

    @Test
    fun `normalize rejects data scheme`() {
        assertNull(BrowserUrlNormalizer.normalize("data:text/html,<script>alert(1)</script>"))
    }

    @Test
    fun `normalize rejects file scheme`() {
        assertNull(BrowserUrlNormalizer.normalize("file:///etc/passwd"))
    }

    @Test
    fun `normalize rejects content scheme`() {
        assertNull(BrowserUrlNormalizer.normalize("content://com.android.contacts/contacts"))
    }

    @Test
    fun `normalize rejects blob scheme`() {
        assertNull(BrowserUrlNormalizer.normalize("blob:https://evil.com/abc-123"))
    }

    @Test
    fun `normalize rejects filesystem scheme`() {
        assertNull(BrowserUrlNormalizer.normalize("filesystem:https://evil.com/temporary/"))
    }

    @Test
    fun `normalize returns null for blank input`() {
        assertNull(BrowserUrlNormalizer.normalize(""))
        assertNull(BrowserUrlNormalizer.normalize("   "))
    }

    @Test
    fun `normalize preserves about blank`() {
        assertEquals("about:blank", BrowserUrlNormalizer.normalize("about:blank"))
    }

    @Test
    fun `normalize preserves localhost`() {
        assertEquals("https://localhost", BrowserUrlNormalizer.normalize("localhost"))
    }
}