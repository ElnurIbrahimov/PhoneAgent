package com.phoneagent.worldmodel

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.phoneagent.memory.AppDatabase
import com.phoneagent.memory.MemoryDao
import com.phoneagent.memory.MemoryEntity
import com.phoneagent.soma.BeliefEngine
import com.phoneagent.worldmodel.PersonalWorldModel
import com.phoneagent.worldmodel.PreferenceEntity
import com.phoneagent.worldmodel.PersonalProfileEntity
import com.phoneagent.worldmodel.RoutineEntity
import com.phoneagent.worldmodel.RelationshipEntity
import com.phoneagent.worldmodel.GoalEntity
import com.phoneagent.worldmodel.PhoneStateCacheEntity
import com.phoneagent.worldmodel.PreferenceDao
import com.phoneagent.worldmodel.PersonalProfileDao
import com.phoneagent.worldmodel.RoutineDao
import com.phoneagent.worldmodel.RelationshipDao
import com.phoneagent.worldmodel.GoalDao
import com.phoneagent.worldmodel.PhoneStateCacheDao
import com.phoneagent.soma.daos.BeliefDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PersonalWorldModelTest {
    private lateinit var db: AppDatabase
    private lateinit var wm: PersonalWorldModel

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        INSERT INTO personal_profile (id, communicationStyle, riskTolerance, specialRequirements, createdAt, updatedAt)
                        VALUES ('self', 'CASUAL', 'MEDIUM', '[]', ${System.currentTimeMillis()}, ${System.currentTimeMillis()})
                    """)
                }
            })
            .build()

        wm = PersonalWorldModel(
            profileDao = db.personalProfileDao(),
            preferenceDao = db.preferenceDao(),
            beliefDao = db.beliefDao(),
            memoryDao = db.memoryDao(),
            routineDao = db.routineDao(),
            relationshipDao = db.relationshipDao(),
            goalDao = db.goalDao(),
            phoneStateDao = db.phoneStateCacheDao()
        )
    }

    @Test
    fun `profile defaults to CASUAL MEDIUM on first access`() = runBlocking {
        val profile = wm.getProfile()
        assertNotNull(profile)
        assertEquals("CASUAL", profile?.communicationStyle)
        assertEquals("MEDIUM", profile?.riskTolerance)
    }

    @Test
    fun `preference persists and retrieves`() = runBlocking {
        wm.setPreference("routing", "style", "brief")
        val retrieved = wm.getPreference("routing", "style")
        assertNotNull(retrieved)
        assertEquals("brief", retrieved?.value)
    }

    @Test
    fun `preference persists and retrieves with default confidence`() = runBlocking {
        wm.setPreference("notifications", "sound", "silent", 0.8f)
        val retrieved = wm.getPreference("notifications", "sound")
        assertNotNull(retrieved)
        assertEquals("silent", retrieved?.value)
        assertEquals(0.8f, retrieved?.confidence ?: 0f, 0.01f)
    }

    @Test
    fun `non-existent preference returns null`() = runBlocking {
        val retrieved = wm.getPreference("nonexistent", "key")
        assertNull(retrieved)
    }

    @Test
    fun `update profile changes communication style`() = runBlocking {
        val original = wm.getProfile()
        assertNotNull(original)

        val updated = original!!.copy(communicationStyle = "FORMAL")
        wm.updateProfile(updated)

        val retrieved = wm.getProfile()
        assertEquals("FORMAL", retrieved?.communicationStyle)
    }

    @After
    fun tearDown() {
        db.close()
    }
}