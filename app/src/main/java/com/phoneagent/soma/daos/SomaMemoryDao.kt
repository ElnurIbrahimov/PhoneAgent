package com.phoneagent.soma.daos

import androidx.room.*
import com.phoneagent.soma.entities.SomaMemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SomaMemoryDao {
    @Query("SELECT * FROM soma_memories ORDER BY importance * activation_count DESC LIMIT :limit")
    fun getTopMemories(limit: Int = 50): Flow<List<SomaMemoryEntity>>

    @Query("SELECT * FROM soma_memories WHERE theme = :theme ORDER BY created_at DESC")
    fun getByTheme(theme: String): Flow<List<SomaMemoryEntity>>

    @Query("SELECT * FROM soma_memories WHERE id = :id")
    suspend fun getById(id: Long): SomaMemoryEntity?

    @Query("""
        SELECT * FROM soma_memories 
        WHERE (content LIKE '%' || :query || '%' OR theme LIKE '%' || :query || '%')
        ORDER BY importance * (1.0 - :decayDays * decay_rate) * activation_count DESC 
        LIMIT :limit
    """)
    suspend fun searchMemories(query: String, decayDays: Float = 0f, limit: Int = 20): List<SomaMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: SomaMemoryEntity)

    @Query("UPDATE soma_memories SET activation_count = activation_count + 1, last_activated_at = :now WHERE id = :id")
    suspend fun activate(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE soma_memories SET importance = importance * (1.0 - decay_rate) WHERE id = :id")
    suspend fun applyDecay(id: Long)

    @Query("DELETE FROM soma_memories WHERE importance < :threshold")
    suspend fun forget(threshold: Float = 0.05f)

    @Query("DELETE FROM soma_memories")
    suspend fun deleteAll()
}
