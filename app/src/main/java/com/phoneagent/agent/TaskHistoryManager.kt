package com.phoneagent.agent

import com.phoneagent.memory.MemoryRepository
import com.phoneagent.memory.TaskEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TaskHistoryManager(private val memoryRepository: MemoryRepository) {

    val tasks: Flow<List<TaskEntity>> = memoryRepository.allTasks

    suspend fun recordTask(
        description: String,
        status: String = "pending",
        result: String? = null
    ): String {
        val id = UUID.randomUUID().toString()
        val task = TaskEntity(
            id = id,
            description = description,
            status = status,
            result = result
        )
        memoryRepository.insertTask(task)
        return id
    }

    suspend fun updateTaskStatus(id: String, status: String, result: String? = null) {
        memoryRepository.updateTaskStatus(id, status, result)
    }

    suspend fun getTask(id: String): TaskEntity? {
        return memoryRepository.getTask(id)
    }

    suspend fun clearHistory() {
        memoryRepository.deleteAllTasks()
    }
}
