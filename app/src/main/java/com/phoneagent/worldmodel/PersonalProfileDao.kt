package com.phoneagent.worldmodel

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalProfileDao {
    @Query("SELECT * FROM personal_profile WHERE id = :id")
    suspend fun getById(id: String = "self"): PersonalProfileEntity?

    @Query("SELECT * FROM personal_profile WHERE id = :id")
    fun getByIdFlow(id: String = "self"): Flow<PersonalProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: PersonalProfileEntity)

    @Update
    suspend fun update(profile: PersonalProfileEntity)

    @Query("DELETE FROM personal_profile WHERE id = :id")
    suspend fun delete(id: String = "self")
}