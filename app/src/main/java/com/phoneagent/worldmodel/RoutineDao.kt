package com.phoneagent.worldmodel

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY timeSlot")
    fun getAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE dayOfWeek = :dayOfWeek ORDER BY timeSlot")
    fun getByDayOfWeek(dayOfWeek: Int): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE timeSlot = :timeSlot")
    fun getByTimeSlot(timeSlot: String): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: String): RoutineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(routine: RoutineEntity)

    @Delete
    suspend fun delete(routine: RoutineEntity)

    @Query("DELETE FROM routines")
    suspend fun deleteAll()
}