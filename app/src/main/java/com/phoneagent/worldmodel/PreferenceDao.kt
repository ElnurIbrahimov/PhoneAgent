package com.phoneagent.worldmodel

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences ORDER BY lastUpdated DESC")
    fun getAll(): Flow<List<PreferenceEntity>>

    @Query("SELECT * FROM preferences WHERE category = :category")
    fun getByCategory(category: String): Flow<List<PreferenceEntity>>

    @Query("SELECT * FROM preferences WHERE category = :category AND `key` = :key LIMIT 1")
    suspend fun get(category: String, key: String): PreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: PreferenceEntity)

    @Delete
    suspend fun delete(preference: PreferenceEntity)

    @Query("DELETE FROM preferences WHERE category = :category")
    suspend fun deleteByCategory(category: String)

    @Query("DELETE FROM preferences")
    suspend fun deleteAll()
}