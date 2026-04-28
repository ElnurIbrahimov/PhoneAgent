package com.phoneagent.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolResultParserTest {

    @Test
    fun `parse returns structured tool result fields`() {
        val parsed = ToolResultParser.parse(
            """{"type":"tool_result","tool":"browser.read_page","success":true,"content":"hello","error":null}"""
        )

        assertTrue(parsed.success)
        assertEquals("hello", parsed.content)
        assertEquals("browser.read_page", parsed.tool)
        assertNull(parsed.error)
    }

    @Test
    fun `parse reports invalid json consistently`() {
        val parsed = ToolResultParser.parse("not json")

        assertFalse(parsed.success)
        assertEquals("Invalid tool result JSON", parsed.error)
        assertNull(parsed.content)
        assertNull(parsed.tool)
    }
}
