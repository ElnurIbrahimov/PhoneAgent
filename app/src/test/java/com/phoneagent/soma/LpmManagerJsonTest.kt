package com.phoneagent.soma

import com.phoneagent.soma.daos.LpmDao
import com.phoneagent.soma.entities.LpmEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class LpmManagerJsonTest {

    @Test
    fun `getOrCreate returns existing LPM on valid JSON`() = runBlocking {
        val dao = mock(LpmDao::class.java)
        val manager = LpmManager(dao)

        val entity = LpmEntity(
            id = "singleton",
            raw_profile = "Test user profile",
            behavioral_predictions = "[\"predict1\", \"predict2\"]",
            trigger_map = "{}",
            foresight_signals = "[]",
            last_session_at = System.currentTimeMillis(),
            total_sessions = 5,
            updated_at = System.currentTimeMillis()
        )
        `when`(dao.getLpm()).thenReturn(entity)

        val lpm = manager.getOrCreate()

        assertEquals(5, lpm.total_sessions)
    }

    @Test
    fun `getOrCreate handles malformed JSON gracefully`() = runBlocking {
        val dao = mock(LpmDao::class.java)
        val manager = LpmManager(dao)

        val entity = LpmEntity(
            id = "singleton",
            raw_profile = "Test",
            behavioral_predictions = "not-valid-json",
            trigger_map = "{}",
            foresight_signals = "[]",
            last_session_at = System.currentTimeMillis(),
            total_sessions = 1,
            updated_at = System.currentTimeMillis()
        )
        `when`(dao.getLpm()).thenReturn(entity)

        val lpm = manager.getOrCreate()
        assertNotNull(lpm)
        assertEquals(1, lpm.total_sessions)
    }

    @Test
    fun `getOrCreate returns default entity when none exists`() = runBlocking {
        val dao = mock(LpmDao::class.java)
        val manager = LpmManager(dao)

        `when`(dao.getLpm()).thenReturn(null)

        val lpm = manager.getOrCreate()

        assertEquals(0, lpm.total_sessions)
        assertEquals("", lpm.raw_profile)
    }
}