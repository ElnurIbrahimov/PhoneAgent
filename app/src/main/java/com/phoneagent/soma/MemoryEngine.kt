package com.phoneagent.soma

import com.phoneagent.soma.daos.SomaMemoryDao
import com.phoneagent.soma.entities.SomaMemoryEntity

class MemoryEngine(private val memoryDao: SomaMemoryDao) {

    suspend fun store(
        content: String,
        theme: String? = null,
        emotionalWeight: Float = 0.5f,
        importance: Float = 0.5f,
        tensionScore: Float = 0.5f,
        connectionDepth: Float = 0.5f,
        sourceType: String = "conversation"
    ): Long {
        val memory = SomaMemoryEntity(
            content = content,
            theme = theme,
            emotional_weight = emotionalWeight,
            importance = importance,
            tension_score = tensionScore,
            connection_depth = connectionDepth,
            source_type = sourceType
        )
        memoryDao.upsert(memory)
        return memory.id
    }

    suspend fun retrieve(query: String, limit: Int = 10): List<SomaMemoryEntity> {
        val days = decayDays()
        val results = memoryDao.searchMemories(query, days, limit)
        results.forEach { memoryDao.activate(it.id) }
        return results
    }

    suspend fun getById(id: Long): SomaMemoryEntity? {
        val mem = memoryDao.getById(id)
        if (mem != null) memoryDao.activate(mem.id)
        return mem
    }

    suspend fun applyGlobalDecay() {
        val allMemories = memoryDao.searchMemories("", 0f, 200)
        allMemories.forEach { memoryDao.applyDecay(it.id) }
        memoryDao.forget(0.05f)
    }

    private suspend fun decayDays(): Float {
        val oldest = memoryDao.searchMemories("", 0f, 1).firstOrNull()
        if (oldest == null) return 0f
        val daysSince = (System.currentTimeMillis() - oldest.created_at) / (24f * 3600_000)
        return daysSince.coerceAtMost(30f)
    }
}
