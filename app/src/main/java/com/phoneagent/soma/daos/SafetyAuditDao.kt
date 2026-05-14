package com.phoneagent.soma.daos

import androidx.room.*
import com.phoneagent.soma.entities.SafetyAuditEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyAuditDao {
    @Insert
    suspend fun insert(audit: SafetyAuditEntity)

    @Query("SELECT * FROM safety_audit ORDER BY timestamp DESC LIMIT 100")
    fun getRecent(): Flow<List<SafetyAuditEntity>>

    @Query("DELETE FROM safety_audit WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
