package com.phoneagent.agent

import com.phoneagent.agent.AgentAction.ToolCall
import org.junit.Assert.*
import org.junit.Test

class DestructiveActionDetectorTest {

    private fun step(tool: String, args: Map<String, String> = emptyMap()) =
        AgentStep(1, ToolCall(tool, args))

    @Test
    fun `read_notification followed by type is flagged as destructive`() {
        val steps = listOf(
            step("phone.notifications"),
            step("accessibility.type", mapOf("text" to "password123"))
        )
        val result = DestructiveActionDetector.assessSequence(steps)
        assertNotNull("Expected destructive pattern to be detected", result)
    }

    @Test
    fun `read_notification followed by tap is not flagged as destructive`() {
        val steps = listOf(
            step("phone.notifications"),
            step("accessibility.tap_text", mapOf("text" to "Confirm"))
        )
        val result = DestructiveActionDetector.assessSequence(steps)
        assertNull("tap_text is not in any pattern", result)
    }

    @Test
    fun `unrelated tools do not trigger warning`() {
        val steps = listOf(
            step("phone.list_apps"),
            step("phone.system_info")
        )
        val result = DestructiveActionDetector.assessSequence(steps)
        assertNull("Unrelated tools should not trigger", result)
    }

    @Test
    fun `single sensitive action is not flagged as destructive`() {
        val steps = listOf(
            step("accessibility.type", mapOf("text" to "hello"))
        )
        val result = DestructiveActionDetector.assessSequence(steps)
        assertNull("Single action should not be flagged as destructive", result)
    }

    @Test
    fun `browser_scroll after notification does not trigger false positive`() {
        val steps = listOf(
            step("phone.notifications"),
            step("browser.scroll", mapOf("direction" to "down"))
        )
        val result = DestructiveActionDetector.assessSequence(steps)
        assertNull("Non-destructive followup should not trigger", result)
    }

    @Test
    fun `notification followed by type and click is flagged`() {
        val steps = listOf(
            step("phone.notifications"),
            step("accessibility.type", mapOf("text" to "password")),
            step("browser.click_text", mapOf("text" to "Submit"))
        )
        val result = DestructiveActionDetector.assessSequence(steps)
        assertNotNull("Three-step destructive pattern should be detected", result)
    }
}