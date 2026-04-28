package com.phoneagent.agent.tools

import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import com.phoneagent.browser.BrowserTool

class BrowserActionTool(
    override val name: String,
    override val description: String,
    private val browserTool: BrowserTool,
    private val action: String,
    override val argsDescription: String = ""
) : Tool, DescribableTool {

    override suspend fun execute(arguments: Map<String, String>): String {
        val merged = arguments.toMutableMap()
        merged["action"] = action
        return browserTool.execute(name, merged)
    }
}
