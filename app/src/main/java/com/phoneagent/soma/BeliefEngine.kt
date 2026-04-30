package com.phoneagent.soma

import com.phoneagent.soma.daos.BeliefDao
import com.phoneagent.soma.entities.BeliefEntity

class BeliefEngine(private val beliefDao: BeliefDao) {

    suspend fun observe(dimension: String, statement: String, source: String = "observed") {
        val existing = beliefDao.getById("$dimension:${statement.take(60)}")
        if (existing != null) {
            val newCount = existing.evidence_count + 1
            val newConfidence = bayesianUpdate(existing.confidence, newCount)
            beliefDao.upsert(existing.copy(
                confidence = newConfidence,
                evidence_count = newCount,
                last_updated = System.currentTimeMillis(),
                source = source
            ))
        } else {
            val id = "$dimension:${statement.take(60)}"
            beliefDao.upsert(BeliefEntity(
                id = id,
                dimension = dimension,
                statement = statement,
                confidence = 0.6,
                evidence_count = 1,
                source = source
            ))
        }
    }

    suspend fun contradict(dimension: String, statement: String) {
        val existing = beliefDao.getById("$dimension:${statement.take(60)}") ?: return
        val newConfidence = existing.confidence * 0.7
        if (newConfidence < 0.15) {
            beliefDao.delete(existing)
        } else {
            beliefDao.upsert(existing.copy(confidence = newConfidence, last_updated = System.currentTimeMillis()))
        }
    }

    suspend fun getConfidence(dimension: String, statement: String): Double {
        return beliefDao.getById("$dimension:${statement.take(60)}")?.confidence ?: 0.0
    }

    private fun bayesianUpdate(prior: Double, n: Int): Double {
        if (n <= 0) return prior
        val likelihood = 0.8
        val alpha = prior * 10.0
        val beta = (1.0 - prior) * 10.0
        val posterior = (alpha + n * likelihood) / (alpha + beta + n)
        return posterior.coerceIn(0.1, 0.99)
    }
}
