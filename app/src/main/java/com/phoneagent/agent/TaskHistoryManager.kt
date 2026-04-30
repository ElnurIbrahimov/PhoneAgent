package com.phoneagent.agent

import android.util.Log
import com.phoneagent.memory.MemoryRepository
import com.phoneagent.memory.TaskEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class TaskHistoryManager(private val memoryRepository: MemoryRepository) {

    val tasks: Flow<List<TaskEntity>> = memoryRepository.allTasks

    suspend fun recordTask(
        description: String,
        status: String = "pending",
        result: String? = null
    ): String {
        val id = UUID.randomUUID().toString()
        try {
            val task = TaskEntity(
                id = id,
                description = description,
                status = status,
                result = result
            )
            memoryRepository.insertTask(task)
        } catch (e: Exception) {
            Log.e("TaskHistoryManager", "Failed to record task", e)
        }
        return id
    }

    suspend fun updateTaskStatus(id: String, status: String, result: String? = null) {
        try {
            memoryRepository.updateTaskStatus(id, status, result)
        } catch (e: Exception) {
            Log.e("TaskHistoryManager", "Failed to update task status", e)
        }
    }

    suspend fun getTask(id: String): TaskEntity? {
        return memoryRepository.getTask(id)
    }

    suspend fun recordSteps(id: String, steps: List<AgentStep>) {
        try {
            val json = JSONArray()
            steps.forEach { step ->
                val obj = JSONObject().apply {
                    put("stepNumber", step.stepNumber)
                    put("timestamp", step.timestamp)
                    put("observation", step.observation)
                    when (step.action) {
                        is AgentAction.FinalAnswer -> {
                            put("type", "final_answer")
                            put("content", step.action.content)
                        }
                        is AgentAction.ToolCall -> {
                            put("type", "tool_call")
                            put("tool", step.action.tool)
                            put("args", JSONObject(step.action.args as Map<*, *>))
                        }
                        is AgentAction.ParseError -> {
                            put("type", "parse_error")
                            put("reason", step.action.reason)
                        }
                    }
                }
                json.put(obj)
            }
            memoryRepository.updateTaskSteps(id, json.toString())
        } catch (e: Exception) {
            Log.e("TaskHistoryManager", "Failed to record steps", e)
        }
    }

    suspend fun clearHistory() {
        try {
            memoryRepository.deleteAllTasks()
        } catch (e: Exception) {
            Log.e("TaskHistoryManager", "Failed to clear history", e)
        }
    }

    suspend fun recordMessage(userMessage: String, assistantMessage: String, model: String) {
        try {
            memoryRepository.insertMessage(userMessage, assistantMessage, model)
        } catch (e: Exception) {
            Log.e("TaskHistoryManager", "Failed to record message", e)
        }
    }
}
