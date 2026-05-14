package com.phoneagent.worldmodel

import com.phoneagent.memory.MemoryEntity
import java.util.UUID

data class MemoryEntry(
    val content: String,
    val importance: Float = 0.5f,
    val emotionalWeight: Float = 0.5f,
    val timestamp: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList()
) {
    fun toEntity(): MemoryEntity {
        val key = generateKey()
        val category = tags.firstOrNull() ?: "general"
        return MemoryEntity(
            key = key,
            value = content,
            category = category
        )
    }

    private fun generateKey(): String {
        val tagPart = tags.joinToString("_").take(30)
        val timePart = (timestamp / 1000).toString()
        return if (tagPart.isNotEmpty()) {
            "${tagPart}_${timePart}_${UUID.randomUUID().toString().take(8)}"
        } else {
            "memory_${timePart}_${UUID.randomUUID().toString().take(8)}"
        }
    }
}