package com.phoneagent.agent

import java.util.concurrent.ConcurrentHashMap

interface Tool {
    val name: String
    val description: String
    suspend fun execute(arguments: Map<String, String>): String
}

class ToolRegistry {

    private val tools = ConcurrentHashMap<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun getTool(name: String): Tool? = tools[name]

    fun listTools(): List<Tool> = tools.values.toList()

    fun clear() = tools.clear()

    fun registerAll(toolsList: List<Tool>) {
        toolsList.forEach { register(it) }
    }
}
