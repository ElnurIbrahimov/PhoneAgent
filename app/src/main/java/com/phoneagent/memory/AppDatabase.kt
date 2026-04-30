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
import com.phoneagent.soma.daos.BeliefDao
import com.phoneagent.soma.daos.DaemonLogDao
import com.phoneagent.soma.daos.LpmDao
import com.phoneagent.soma.daos.MemSceneDao
import com.phoneagent.soma.daos.ObservationDao
import com.phoneagent.soma.daos.SomaMemoryDao

@Database(
    entities = [
        MemoryEntity::class, TaskEntity::class,
        BeliefEntity::class, SomaMemoryEntity::class, MemSceneEntity::class,
        ObservationEntity::class, DaemonLogEntity::class, LpmEntity::class
    ],
    version = 3,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS soma_beliefs (
                        id TEXT NOT NULL PRIMARY KEY,
                        dimension TEXT NOT NULL,
                        statement TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        evidence_count INTEGER NOT NULL,
                        last_updated INTEGER NOT NULL,
                        source TEXT NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS soma_memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        content TEXT NOT NULL,
                        theme TEXT,
                        emotional_weight REAL NOT NULL,
                        importance REAL NOT NULL,
                        activation_count INTEGER NOT NULL,
                        tension_score REAL NOT NULL,
                        connection_depth REAL NOT NULL,
                        created_at INTEGER NOT NULL,
                        last_activated_at INTEGER NOT NULL,
                        decay_rate REAL NOT NULL,
                        source_type TEXT NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS soma_scenes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        theme TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        memory_ids TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        session_id TEXT NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS soma_observations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        app_package TEXT NOT NULL,
                        app_name TEXT NOT NULL,
                        ocr_text_snippet TEXT NOT NULL,
                        activity_classification TEXT,
                        emotional_tone TEXT,
                        observed_at INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS soma_daemon_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        thought TEXT NOT NULL,
                        significance REAL NOT NULL,
                        pushed_to_user INTEGER NOT NULL,
                        triggered_by TEXT,
                        created_at INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS soma_lpm (
                        id TEXT NOT NULL PRIMARY KEY,
                        raw_profile TEXT NOT NULL,
                        behavioral_predictions TEXT NOT NULL,
                        trigger_map TEXT NOT NULL,
                        foresight_signals TEXT NOT NULL,
                        last_session_at INTEGER NOT NULL,
                        total_sessions INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
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
                .addMigrations(MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
