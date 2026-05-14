package com.phoneagent.agent

import org.json.JSONObject
import java.util.UUID

object AgentPromptBuilder {

    fun buildSystemPrompt(tools: List<Tool>): String {
        val toolDescriptions = tools.joinToString("\n") { tool ->
            val args = when (tool) {
                is DescribableTool -> tool.argsDescription
                else -> ""
            }
            "- ${tool.name}: ${tool.description}${if (args.isNotBlank()) " Args: $args" else ""}"
        }

        return """
You are PhoneAgent, an Android-native AI agent running directly on the user's phone.
You must respond ONLY with a single valid JSON object. Do not output markdown, explanations, or any text outside the JSON object.

Available tools:
$toolDescriptions

Response formats:

1. Final answer (when you are done):
{
  "type": "final_answer",
  "content": "Your answer here."
}

2. Tool call (when you need to act):
{
  "type": "tool_call",
  "tool": "tool.name",
  "args": {}
}

Rules:
- After each browser action, wait for the next observation before claiming completion.
- Use final_answer only when the task is fully done.
- Never ask for cookies, secrets, passwords, or credentials.
- Do not output natural language outside the JSON object.
- If a tool fails, you may try a different approach up to the step limit.
- If the user cancels an action, provide a final_answer explaining what happened.
- If a tool fails, read its "suggestion" field and try that approach.
- Never retry the exact same action more than twice.
        """.trimIndent()
    }

    fun buildLoopMessage(
        originalRequest: String,
        steps: List<AgentStep>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("User request: $originalRequest")
        if (steps.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Previous steps:")
            steps.forEach { step ->
                sb.appendLine("Step ${step.stepNumber}:")
                when (step.action) {
                    is AgentAction.ToolCall -> {
                        val argsJson = JSONObject(step.action.args).toString()
                        sb.appendLine("Action: {\"type\":\"tool_call\",\"tool\":\"${step.action.tool}\",\"args\":$argsJson}")
                        val rawObs = step.observation ?: "none"
                        val suggestion = extractSuggestion(rawObs)
                        val obsText = rawObs.take(4000)
                        sb.appendLine("Observation: $obsText")
                        if (suggestion != null) {
                            sb.appendLine("Suggestion: $suggestion")
                        }
                    }
                    is AgentAction.FinalAnswer -> {
                        sb.appendLine("Action: {\"type\":\"final_answer\",\"content\":\"${step.action.content}\"}")
                    }
                    is AgentAction.ParseError -> {
                        sb.appendLine("Action: (parse error: ${step.action.reason})")
                        val rawObs = step.observation ?: "none"
                        val suggestion = extractSuggestion(rawObs)
                        val obsText = rawObs.take(4000)
                        sb.appendLine("Observation: $obsText")
                        if (suggestion != null) {
                            sb.appendLine("Suggestion: $suggestion")
                        }
                    }
                }
                sb.appendLine()
            }
        }
        sb.appendLine("Respond with the next JSON action.")
        return sb.toString()
    }

    private fun extractSuggestion(jsonString: String): String? {
        return try {
            val json = org.json.JSONObject(jsonString)
            json.optString("suggestion", null)
        } catch (_: Exception) { null }
    }

    fun buildStepObservationPrompt(
        step: Int,
        action: String,
        result: String,
        profile: com.phoneagent.worldmodel.PersonalProfileEntity?
    ): String {
        val profileContext = profile?.let {
            """
            |User Profile:
            |- Communication style: ${it.communicationStyle}
            |- Risk tolerance: ${it.riskTolerance}
            """.trimMargin()
        } ?: "No user profile available."

        return """
You are an observer analyzing an agent step. Given the following information:

Step: $step
Action: $action
Result: ${result.take(2000)}

$profileContext

Analyze what happened and respond with ONLY a valid JSON object (no markdown, no explanation):
{
  "whatILearned": ["list of factual learnings from this step"],
  "preferenceHint": {"category": "category", "key": "key", "value": "value"} or null,
  "beliefHint": {"dimension": "dimension", "statement": "statement"} or null,
  "memoryToStore": {"content": "concise memory content", "importance": 0.5, "emotionalWeight": 0.5, "tags": ["tag1"]} or null
}

Rules:
- whatILearned: max 3 items, each under 50 chars
- preferenceHint: only set if user preference was clearly revealed
- beliefHint: only set if a clear belief was demonstrated
- memoryToStore: only set if something worth remembering occurred
- All fields are optional but object must be valid JSON
        """.trimIndent()
    }

    fun parseStepObservation(step: Int, action: String, result: String, jsonString: String): com.phoneagent.worldmodel.StepObservation? {
        return try {
            val json = org.json.JSONObject(jsonString)

            val whatILearned = mutableListOf<String>()
            json.optJSONArray("whatILearned")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getString(i)
                    if (item.isNotBlank()) whatILearned.add(item.take(50))
                }
            }

            val preferenceHint = json.optJSONObject("preferenceHint")?.let { ph ->
                com.phoneagent.worldmodel.PreferenceHint(
                    category = ph.optString("category", "general"),
                    key = ph.optString("key", "unknown"),
                    value = ph.optString("value", "")
                )
            }

            val beliefHint = json.optJSONObject("beliefHint")?.let { bh ->
                com.phoneagent.worldmodel.BeliefHint(
                    dimension = bh.optString("dimension", "general"),
                    statement = bh.optString("statement", "")
                )
            }

            val memoryToStore = json.optJSONObject("memoryToStore")?.let { ms ->
                com.phoneagent.worldmodel.MemoryEntry(
                    content = ms.optString("content", ""),
                    importance = ms.optDouble("importance", 0.5).toFloat(),
                    emotionalWeight = ms.optDouble("emotionalWeight", 0.5).toFloat(),
                    tags = ms.optJSONArray("tags")?.let { tagsArr ->
                        (0 until tagsArr.length()).map { tagsArr.getString(it) }
                    } ?: emptyList()
                )
            }

            com.phoneagent.worldmodel.StepObservation(
                step = step,
                action = action,
                result = result,
                whatILearned = whatILearned,
                preferenceHint = preferenceHint,
                beliefHint = beliefHint,
                memoryToStore = memoryToStore
            )
        } catch (e: Exception) {
            android.util.Log.w("AgentPromptBuilder", "Failed to parse step observation: ${e.message}")
            null
        }
    }

    fun parseSessionReflection(content: String): com.phoneagent.worldmodel.SessionReflection {
        return try {
            val json = org.json.JSONObject(content)
            SessionReflection(
                sessionId = UUID.randomUUID().toString(),
                whatWentWell = json.optJSONArray("whatWentWell")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                whatCouldImprove = json.optJSONArray("whatCouldImprove")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                userFrustrations = json.optJSONArray("userFrustrations")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                newPreferences = json.optJSONArray("newPreferences")?.let { arr ->
                    (0 until arr.length()).map { o ->
                        PreferenceHint(o.getString("category"), o.getString("key"), o.getString("value"))
                    }
                } ?: emptyList(),
                newBeliefs = json.optJSONArray("newBeliefs")?.let { arr ->
                    (0 until arr.length()).map { o ->
                        BeliefHint(o.getString("dimension"), o.getString("statement"))
                    }
                } ?: emptyList(),
                memoriesToConsolidate = emptyList(),
                goalsAchieved = json.optJSONArray("goalsAchieved")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                goalsSuggested = emptyList()
            )
        } catch (e: Exception) {
            android.util.Log.w("AgentPromptBuilder", "Failed to parse session reflection: ${e.message}")
            SessionReflection(sessionId = UUID.randomUUID().toString())
        }
    }
}

interface DescribableTool {
    val argsDescription: String
}
