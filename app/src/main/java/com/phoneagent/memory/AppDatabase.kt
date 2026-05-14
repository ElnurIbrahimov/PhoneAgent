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
import com.phoneagent.worldmodel.PersonalProfileEntity
import com.phoneagent.worldmodel.PreferenceEntity
import com.phoneagent.worldmodel.RoutineEntity
import com.phoneagent.worldmodel.RelationshipEntity
import com.phoneagent.worldmodel.GoalEntity
import com.phoneagent.worldmodel.PhoneStateCacheEntity
import com.phoneagent.worldmodel.RoutingDecisionEntity
import com.phoneagent.worldmodel.PersonalProfileDao
import com.phoneagent.worldmodel.PreferenceDao
import com.phoneagent.worldmodel.RoutineDao
import com.phoneagent.worldmodel.RelationshipDao
import com.phoneagent.worldmodel.GoalDao
import com.phoneagent.worldmodel.PhoneStateCacheDao
import com.phoneagent.worldmodel.RoutingDecisionDao

@Database(
    entities = [
        MemoryEntity::class, TaskEntity::class,
        BeliefEntity::class, SomaMemoryEntity::class, MemSceneEntity::class,
        ObservationEntity::class, DaemonLogEntity::class, LpmEntity::class,
        SafetyAuditEntity::class,
        PersonalProfileEntity::class, PreferenceEntity::class, RoutineEntity::class,
        RelationshipEntity::class, GoalEntity::class, PhoneStateCacheEntity::class,
        RoutingDecisionEntity::class
    ],
    version = 5,
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
    abstract fun personalProfileDao(): PersonalProfileDao
    abstract fun preferenceDao(): PreferenceDao
    abstract fun routineDao(): RoutineDao
    abstract fun relationshipDao(): RelationshipDao
    abstract fun goalDao(): GoalDao
    abstract fun phoneStateCacheDao(): PhoneStateCacheDao
    abstract fun routingDecisionDao(): RoutingDecisionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS personal_profile (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT,
                        communicationStyle TEXT NOT NULL DEFAULT 'CASUAL',
                        riskTolerance TEXT NOT NULL DEFAULT 'MEDIUM',
                        specialRequirements TEXT NOT NULL DEFAULT '[]',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS preferences (
                        id TEXT PRIMARY KEY NOT NULL,
                        category TEXT NOT NULL,
                        `key` TEXT NOT NULL,
                        value TEXT NOT NULL,
                        confidence REAL NOT NULL DEFAULT 0.5,
                        lastUpdated INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS routines (
                        id TEXT PRIMARY KEY NOT NULL,
                        timeSlot TEXT NOT NULL,
                        dayOfWeek INTEGER,
                        typicalActivities TEXT NOT NULL DEFAULT '[]',
                        energyLevel TEXT NOT NULL DEFAULT 'MEDIUM',
                        interruptTolerance TEXT NOT NULL DEFAULT 'MEDIUM',
                        location TEXT
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS relationships (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        relationshipType TEXT NOT NULL DEFAULT 'UNKNOWN',
                        importance REAL NOT NULL DEFAULT 0.5,
                        contactFrequency TEXT NOT NULL DEFAULT 'WEEKLY',
                        context TEXT NOT NULL DEFAULT '',
                        lastInteraction INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS goals (
                        id TEXT PRIMARY KEY NOT NULL,
                        description TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        deadline INTEGER,
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        progress REAL NOT NULL DEFAULT 0,
                        relatedMemoryIds TEXT NOT NULL DEFAULT '[]'
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS phone_state_cache (
                        id TEXT PRIMARY KEY NOT NULL,
                        foregroundApp TEXT NOT NULL DEFAULT '',
                        appHierarchy TEXT NOT NULL DEFAULT '[]',
                        screenType TEXT NOT NULL DEFAULT 'UNKNOWN',
                        screenPurpose TEXT NOT NULL DEFAULT '',
                        confidence REAL NOT NULL DEFAULT 0,
                        lastUpdated INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS iris_routing_history (
                        id TEXT PRIMARY KEY NOT NULL,
                        messagePreview TEXT NOT NULL,
                        profile TEXT NOT NULL,
                        temperature REAL NOT NULL,
                        style TEXT NOT NULL,
                        depth INTEGER NOT NULL,
                        outcome TEXT,
                        sessionId TEXT,
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
                .addMigrations(MIGRATION_4_5)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}