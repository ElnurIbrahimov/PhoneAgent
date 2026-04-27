package com.phoneagent.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY timestamp DESC")
    fun getByCategory(category: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}
