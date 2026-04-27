package com.phoneagent.memory

import kotlinx.coroutines.flow.Flow

class MemoryRepository(
    private val memoryDao: MemoryDao,
    private val taskDao: TaskDao
) {

    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAll()
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAll()

    suspend fun insertMemory(key: String, value: String, category: String = "general") {
        memoryDao.insert(MemoryEntity(key = key, value = value, category = category))
    }

    suspend fun insertMessage(userMessage: String, assistantMessage: String, model: String) {
        memoryDao.insert(
            MemoryEntity(
                key = "chat_${System.currentTimeMillis()}",
                value = "User: $userMessage\nAssistant: $assistantMessage",
                category = "chat_$model"
            )
        )
    }

    suspend fun getMemory(key: String): MemoryEntity? {
        return memoryDao.getByKey(key)
    }

    suspend fun insertTask(task: TaskEntity) {
        taskDao.insert(task)
    }

    suspend fun updateTaskStatus(id: String, status: String, result: String? = null) {
        taskDao.updateStatus(id, status, result, if (status == "completed") System.currentTimeMillis() else null)
    }

    suspend fun getTask(id: String): TaskEntity? {
        return taskDao.getById(id)
    }

    suspend fun deleteAllTasks() {
        taskDao.deleteAll()
    }

    suspend fun deleteAllMemories() {
        memoryDao.deleteAll()
    }
}
