package com.phoneagent.soma.daos

import androidx.room.*
import com.phoneagent.soma.entities.DaemonLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DaemonLogDao {
    @Query("SELECT * FROM soma_daemon_log ORDER BY created_at DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<DaemonLogEntity>>

    @Query("SELECT * FROM soma_daemon_log WHERE pushed_to_user = 1 ORDER BY created_at DESC LIMIT :limit")
    fun getPushed(limit: Int = 20): Flow<List<DaemonLogEntity>>

    @Query("SELECT * FROM soma_daemon_log WHERE significance >= :minSig ORDER BY created_at DESC LIMIT :limit")
    fun getSignificant(minSig: Float = 0.5f, limit: Int = 10): Flow<List<DaemonLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DaemonLogEntity)

    @Query("DELETE FROM soma_daemon_log WHERE created_at < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM soma_daemon_log")
    suspend fun deleteAll()
}
