package com.phoneagent.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.phoneagent.soma.entities.BeliefEntity
import com.phoneagent.soma.entities.DaemonLogEntity
import com.phoneagent.soma.entities.LpmEntity
import com.phoneagent.soma.entities.MemSceneEntity
import com.phoneagent.soma.entities.ObservationEntity
import com.phoneagent.soma.entities.SomaMemoryEntity
import com.phoneagent.soma.entities.SafetyAuditEntity
import com.phoneagent.soma.daos.BeliefDao
import com.phoneagent.soma.daos.DaemonLogDao
import com.phoneagent.soma.daos.LpmDao
import com.phoneagent.soma.daos.MemSceneDao
import com.phoneagent.soma.daos.ObservationDao
import com.phoneagent.soma.daos.SomaMemoryDao
import com.phoneagent.soma.daos.SafetyAuditDao

@Database(
    entities = [
        MemoryEntity::class, TaskEntity::class,
        BeliefEntity::class, SomaMemoryEntity::class, MemSceneEntity::class,
        ObservationEntity::class, DaemonLogEntity::class, LpmEntity::class,
        SafetyAuditEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun taskDao(): TaskDao
    abstract fun beliefDao(): BeliefDao
    abstract fun somaMemoryDao(): SomaMemoryDao
    abstract fun memSceneDao(): MemSceneDao
    abstract fun observationDao(): ObservationDao
    abstract fun daemonLogDao(): DaemonLogDao
    abstract fun lpmDao(): LpmDao
    abstract fun safetyAuditDao(): SafetyAuditDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS safety_audit (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        toolName TEXT NOT NULL,
                        riskLevel TEXT NOT NULL,
                        riskReason TEXT NOT NULL,
                        argsSummary TEXT NOT NULL,
                        decision TEXT NOT NULL,
                        taskId TEXT,
                        timestamp INTEGER NOT NULL
                    )
                """)
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "phoneagent_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
