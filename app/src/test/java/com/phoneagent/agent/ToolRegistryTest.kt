package com.phoneagent.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ToolRegistryTest {

    private val registry = ToolRegistry()

    @Test
    fun `register and retrieve tool`() {
        val tool = FakeTool("test_tool", "A test tool")
        registry.register(tool)
        assertEquals(tool, registry.getTool("test_tool"))
    }

    @Test
    fun `getTool returns null for unknown tool`() {
        assertNull(registry.getTool("nonexistent"))
    }

    @Test
    fun `listTools returns all registered tools`() {
        registry.register(FakeTool("tool1", "desc1"))
        registry.register(FakeTool("tool2", "desc2"))
        assertEquals(2, registry.listTools().size)
    }

    @Test
    fun `unregister removes tool`() {
        val tool = FakeTool("temp", "temp")
        registry.register(tool)
        registry.unregister("temp")
        assertNull(registry.getTool("temp"))
    }

    @Test
    fun `clear removes all tools`() {
        registry.register(FakeTool("a", "a"))
        registry.register(FakeTool("b", "b"))
        registry.clear()
        assertEquals(0, registry.listTools().size)
    }

    @Test
    fun `registerAll adds multiple tools`() {
        val tools = listOf(FakeTool("a", "a"), FakeTool("b", "b"))
        registry.registerAll(tools)
        assertEquals(2, registry.listTools().size)
    }

    class FakeTool(override val name: String, override val description: String) : Tool {
        override suspend fun execute(arguments: Map<String, String>): String = "ok"
    }
}
