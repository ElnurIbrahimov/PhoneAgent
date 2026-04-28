package com.phoneagent.browser

import org.junit.Assert.assertEquals
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
}
