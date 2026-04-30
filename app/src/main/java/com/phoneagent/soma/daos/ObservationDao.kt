package com.phoneagent.soma.daos

import androidx.room.*
import com.phoneagent.soma.entities.ObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservationDao {
    @Query("SELECT * FROM soma_observations ORDER BY observed_at DESC LIMIT :limit")
    fun getRecent(limit: Int = 100): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM soma_observations WHERE app_package = :packageName ORDER BY observed_at DESC LIMIT :limit")
    fun getByApp(packageName: String, limit: Int = 20): Flow<List<ObservationEntity>>

    @Query("SELECT COUNT(*) FROM soma_observations WHERE observed_at > :since")
    suspend fun countSince(since: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(observation: ObservationEntity)

    @Query("DELETE FROM soma_observations WHERE observed_at < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM soma_observations")
    suspend fun deleteAll()
}
