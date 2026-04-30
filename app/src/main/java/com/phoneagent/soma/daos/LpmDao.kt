package com.phoneagent.soma.daos

import androidx.room.*
import com.phoneagent.soma.entities.LpmEntity

@Dao
interface LpmDao {
    @Query("SELECT * FROM soma_lpm WHERE id = 'singleton'")
    suspend fun getLpm(): LpmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lpm: LpmEntity)
}
