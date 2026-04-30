package com.phoneagent.soma

import com.phoneagent.soma.daos.LpmDao
import com.phoneagent.soma.entities.LpmEntity
import org.json.JSONArray
import org.json.JSONObject

class LpmManager(private val lpmDao: LpmDao) {

    suspend fun getOrCreate(): LpmEntity {
        return lpmDao.getLpm() ?: LpmEntity()
    }

    suspend fun updateFromSession(
        newProfile: String,
        predictions: List<String> = emptyList(),
        triggerMap: Map<String, String> = emptyMap(),
        foresightSignals: List<String> = emptyList()
    ) {
        val existing = getOrCreate()
        val merged = LpmEntity(
            id = "singleton",
            raw_profile = mergeProfiles(existing.raw_profile, newProfile),
            behavioral_predictions = JSONArray(predictions.ifEmpty {
                try { JSONArray(existing.behavioral_predictions).let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } } catch (_: Exception) { emptyList() }
            }).toString(),
            trigger_map = JSONObject(triggerMap.ifEmpty {
                try {
                    JSONObject(existing.trigger_map).let { obj ->
                        obj.keys().asSequence().associateWith { obj.getString(it) }
                    }
                } catch (_: Exception) { emptyMap() }
            }).toString(),
            foresight_signals = JSONArray(foresightSignals).toString(),
            last_session_at = System.currentTimeMillis(),
            total_sessions = existing.total_sessions + 1,
            updated_at = System.currentTimeMillis()
        )
        lpmDao.upsert(merged)
    }

    private fun mergeProfiles(existing: String, new: String): String {
        if (existing.isBlank()) return new
        if (new.isBlank()) return existing
        return "$existing\n\n--- Session ${System.currentTimeMillis()} ---\n$new".takeLast(8000)
    }
}
