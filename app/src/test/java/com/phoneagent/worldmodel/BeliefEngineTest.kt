package com.phoneagent.worldmodel

import com.phoneagent.soma.BeliefEngine
import com.phoneagent.soma.daos.BeliefDao
import com.phoneagent.soma.entities.BeliefEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class BeliefEngineTest {
    private lateinit var beliefDao: BeliefDao
    private lateinit var engine: BeliefEngine

    @Before
    fun setup() {
        beliefDao = mock(BeliefDao::class.java)
        engine = BeliefEngine(beliefDao)
    }

    @Test
    fun `observe creates new belief with initial confidence`() = runBlocking {
        `when`(beliefDao.getById("test:hello")).thenReturn(null)

        engine.observe("test", "hello", "manual")

        verify(beliefDao).upsert(argThat { belief ->
            belief.dimension == "test" &&
            belief.statement == "hello" &&
            belief.confidence >= 0.5 &&
            belief.evidence_count == 1
        })
    }

    @Test
    fun `observe increases confidence for existing belief`() = runBlocking {
        val existing = BeliefEntity(
            id = "test:hello",
            dimension = "test",
            statement = "hello",
            confidence = 0.6,
            evidence_count = 1,
            source = "manual"
        )
        `when`(beliefDao.getById("test:hello")).thenReturn(existing)

        engine.observe("test", "hello", "manual")

        verify(beliefDao).upsert(argThat { belief ->
            belief.evidence_count == 2 &&
            belief.confidence > 0.6
        })
    }

    @Test
    fun `contradict decreases confidence by multiplier`() = runBlocking {
        val existing = BeliefEntity(
            id = "test:world",
            dimension = "test",
            statement = "world",
            confidence = 0.8,
            evidence_count = 2,
            source = "manual"
        )
        `when`(beliefDao.getById("test:world")).thenReturn(existing)

        engine.contradict("test", "world")

        verify(beliefDao).upsert(argThat { belief ->
            belief.confidence == 0.56f &&
            belief.evidence_count == 2
        })
    }

    @Test
    fun `contradict deletes belief when confidence drops below threshold`() = runBlocking {
        val existing = BeliefEntity(
            id = "test:low",
            dimension = "test",
            statement = "low",
            confidence = 0.2,
            evidence_count = 1,
            source = "manual"
        )
        `when`(beliefDao.getById("test:low")).thenReturn(existing)

        engine.contradict("test", "low")

        verify(beliefDao).delete(existing)
    }

    @Test
    fun `contradict does nothing for non-existent belief`() = runBlocking {
        `when`(beliefDao.getById("test:nonexistent")).thenReturn(null)

        engine.contradict("test", "nonexistent")

        verify(beliefDao, never()).upsert(any())
        verify(beliefDao, never()).delete(any())
    }

    @Test
    fun `getConfidence returns stored confidence`() = runBlocking {
        val existing = BeliefEntity(
            id = "dim:stmt",
            dimension = "dim",
            statement = "stmt",
            confidence = 0.75,
            evidence_count = 3,
            source = "test"
        )
        `when`(beliefDao.getById("dim:stmt")).thenReturn(existing)

        val confidence = engine.getConfidence("dim", "stmt")

        assertEquals(0.75, confidence, 0.001)
    }

    @Test
    fun `getConfidence returns zero for non-existent belief`() = runBlocking {
        `when`(beliefDao.getById("dim:none")).thenReturn(null)

        val confidence = engine.getConfidence("dim", "none")

        assertEquals(0.0, confidence, 0.001)
    }
}