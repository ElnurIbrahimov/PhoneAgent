package com.phoneagent.worldmodel

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhoneStateCacheDao {
    @Query("SELECT * FROM phone_state_cache WHERE id = :id")
    suspend fun getById(id: String = "current"): PhoneStateCacheEntity?

    @Query("SELECT * FROM phone_state_cache WHERE id = :id")
    fun getByIdFlow(id: String = "current"): Flow<PhoneStateCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PhoneStateCacheEntity)

    @Query("DELETE FROM phone_state_cache WHERE id = :id")
    suspend fun delete(id: String = "current")

    @Query("DELETE FROM phone_state_cache")
    suspend fun deleteAll()
}