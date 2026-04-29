package com.phoneagent.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyGateTest {

    @Test
    fun `low risk tools return LOW level`() {
        val result = SafetyGate.assess("browser.read_page", emptyMap())
        assertEquals(SafetyGate.RiskLevel.LOW, result.level)
    }

    @Test
    fun `high risk tools return HIGH level`() {
        val result = SafetyGate.assess("phone.send_sms", mapOf("to" to "123", "message" to "hi"))
        assertEquals(SafetyGate.RiskLevel.HIGH, result.level)

        val result2 = SafetyGate.assess("phone.call", mapOf("number" to "911"))
        assertEquals(SafetyGate.RiskLevel.HIGH, result2.level)

        val result3 = SafetyGate.assess("phone.open_app", mapOf("app" to "com.example"))
        assertEquals(SafetyGate.RiskLevel.HIGH, result3.level)
    }

    @Test
    fun `medium risk tools with safe args return LOW`() {
        val result = SafetyGate.assess("accessibility.back", emptyMap())
        assertEquals(SafetyGate.RiskLevel.LOW, result.level)
    }

    @Test
    fun `medium risk tools with sensitive args return MEDIUM`() {
        val result = SafetyGate.assess("browser.click_text", mapOf("text" to "Submit"))
        assertEquals(SafetyGate.RiskLevel.MEDIUM, result.level)
    }

    @Test
    fun `click_selector with password selector returns MEDIUM`() {
        val result = SafetyGate.assess("browser.click_selector", mapOf("selector" to "#password-field"))
        assertEquals(SafetyGate.RiskLevel.MEDIUM, result.level)
    }

    @Test
    fun `click_selector with safe selector returns LOW`() {
        val result = SafetyGate.assess("browser.click_selector", mapOf("selector" to ".nav-link"))
        assertEquals(SafetyGate.RiskLevel.LOW, result.level)
    }

    @Test
    fun `type_into_selector with credit card selector returns MEDIUM`() {
        val result = SafetyGate.assess("browser.type_into_selector", mapOf("selector" to "#credit-card"))
        assertEquals(SafetyGate.RiskLevel.MEDIUM, result.level)
    }

    @Test
    fun `type_into_focused with long text returns MEDIUM`() {
        val longText = "a".repeat(201)
        val result = SafetyGate.assess("browser.type_into_focused", mapOf("text" to longText))
        assertEquals(SafetyGate.RiskLevel.MEDIUM, result.level)
    }

    @Test
    fun `type_into_focused with short text returns LOW`() {
        val result = SafetyGate.assess("browser.type_into_focused", mapOf("text" to "hello"))
        assertEquals(SafetyGate.RiskLevel.LOW, result.level)
    }

    @Test
    fun `unknown tool returns HIGH risk as fail-safe`() {
        val result = SafetyGate.assess("unknown.dangerous_tool", emptyMap())
        assertEquals(SafetyGate.RiskLevel.HIGH, result.level)
    }

    @Test
    fun `phone screenshot returns LOW risk`() {
        val result = SafetyGate.assess("phone.screenshot", emptyMap())
        assertEquals(SafetyGate.RiskLevel.LOW, result.level)
    }

    @Test
    fun `phone clipboard returns MEDIUM risk`() {
        val result = SafetyGate.assess("phone.clipboard", mapOf("action" to "read"))
        assertEquals(SafetyGate.RiskLevel.MEDIUM, result.level)
    }

    @Test
    fun `phone settings returns LOW risk`() {
        val result = SafetyGate.assess("phone.settings", mapOf("page" to "wifi"))
        assertEquals(SafetyGate.RiskLevel.LOW, result.level)
    }

    @Test
    fun `accessibility tap requires confirmation for safety`() {
        val result = SafetyGate.assess("accessibility.tap_text", mapOf("text" to "Buy"))
        assertEquals(SafetyGate.RiskLevel.MEDIUM, result.level)
    }

    @Test
    fun `click_selector with card in selector name does not trigger false positive`() {
        val result = SafetyGate.assess("browser.click_selector", mapOf("selector" to "#product-card"))
        assertEquals(SafetyGate.RiskLevel.LOW, result.level)
    }

    @Test
    fun `accessibility swipe requires confirmation for safety`() {
        val result = SafetyGate.assess("accessibility.swipe", mapOf("direction" to "up"))
        assertEquals(SafetyGate.RiskLevel.MEDIUM, result.level)
    }

    @Test
    fun `accessibility type requires confirmation for safety`() {
        val result = SafetyGate.assess("accessibility.type", mapOf("text" to "hello"))
        assertEquals(SafetyGate.RiskLevel.MEDIUM, result.level)
    }
