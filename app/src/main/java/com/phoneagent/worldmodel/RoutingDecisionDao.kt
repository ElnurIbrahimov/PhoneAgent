package com.phoneagent.worldmodel

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutingDecisionDao {
    @Query("SELECT * FROM iris_routing_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<RoutingDecisionEntity>>

    @Query("SELECT * FROM iris_routing_history WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getBySession(sessionId: String): Flow<List<RoutingDecisionEntity>>

    @Query("SELECT * FROM iris_routing_history WHERE timestamp > :since ORDER BY timestamp DESC")
    fun getSince(since: Long): Flow<List<RoutingDecisionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(decision: RoutingDecisionEntity)

    @Query("DELETE FROM iris_routing_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM iris_routing_history")
    suspend fun deleteAll()
}