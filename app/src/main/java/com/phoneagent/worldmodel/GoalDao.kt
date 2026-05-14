package com.phoneagent.worldmodel

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    fun getAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE status = :status ORDER BY deadline ASC")
    fun getByStatus(status: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE deadline IS NOT NULL AND deadline < :timestamp AND status = 'ACTIVE'")
    fun getOverdue(timestamp: Long): Flow<List<GoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity)

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("UPDATE goals SET progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float)

    @Query("UPDATE goals SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Delete
    suspend fun delete(goal: GoalEntity)

    @Query("DELETE FROM goals")
    suspend fun deleteAll()
}