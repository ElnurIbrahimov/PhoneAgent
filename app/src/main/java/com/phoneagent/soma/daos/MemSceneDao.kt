package com.phoneagent.soma.daos

import androidx.room.*
import com.phoneagent.soma.entities.MemSceneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemSceneDao {
    @Query("SELECT * FROM soma_scenes ORDER BY created_at DESC")
    fun getAll(): Flow<List<MemSceneEntity>>

    @Query("SELECT * FROM soma_scenes WHERE session_id = :sessionId ORDER BY created_at DESC")
    fun getBySession(sessionId: String): Flow<List<MemSceneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(scene: MemSceneEntity)

    @Query("DELETE FROM soma_scenes")
    suspend fun deleteAll()
}
