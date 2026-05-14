package com.phoneagent.worldmodel

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RelationshipDao {
    @Query("SELECT * FROM relationships ORDER BY importance DESC")
    fun getAll(): Flow<List<RelationshipEntity>>

    @Query("SELECT * FROM relationships WHERE relationshipType = :type")
    fun getByType(type: String): Flow<List<RelationshipEntity>>

    @Query("SELECT * FROM relationships WHERE id = :id")
    suspend fun getById(id: String): RelationshipEntity?

    @Query("SELECT * FROM relationships ORDER BY lastInteraction DESC LIMIT :limit")
    fun getRecentlyInteracted(limit: Int = 10): Flow<List<RelationshipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(relationship: RelationshipEntity)

    @Update
    suspend fun update(relationship: RelationshipEntity)

    @Delete
    suspend fun delete(relationship: RelationshipEntity)

    @Query("DELETE FROM relationships")
    suspend fun deleteAll()
}