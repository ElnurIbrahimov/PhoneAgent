package com.phoneagent.soma.daos

import androidx.room.*
import com.phoneagent.soma.entities.BeliefEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BeliefDao {
    @Query("SELECT * FROM soma_beliefs ORDER BY confidence DESC")
    fun getAll(): Flow<List<BeliefEntity>>

    @Query("SELECT * FROM soma_beliefs WHERE dimension = :dimension ORDER BY confidence DESC")
    fun getByDimension(dimension: String): Flow<List<BeliefEntity>>

    @Query("SELECT * FROM soma_beliefs WHERE id = :id")
    suspend fun getById(id: String): BeliefEntity?

    @Query("SELECT * FROM soma_beliefs WHERE confidence >= :minConfidence ORDER BY confidence DESC LIMIT :limit")
    fun getTopBeliefs(minConfidence: Double = 0.5, limit: Int = 20): Flow<List<BeliefEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(belief: BeliefEntity)

    @Delete
    suspend fun delete(belief: BeliefEntity)

    @Query("DELETE FROM soma_beliefs")
    suspend fun deleteAll()
}
