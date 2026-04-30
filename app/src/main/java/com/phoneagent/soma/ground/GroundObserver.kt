package com.phoneagent.soma.ground

import com.phoneagent.agent.AgentController
import com.phoneagent.accessibility.AgentAccessibilityService
import com.phoneagent.soma.entities.ObservationEntity
import kotlinx.coroutines.flow.first

class GroundObserver(private val controller: AgentController) {

    suspend fun observe() {
        val db = controller.database

        val obsDao = db.observationDao()
        val foreverApp = try {
            AgentAccessibilityService.instance?.getForegroundPackage()
        } catch (_: Exception) { null }

        val ocrText = captureOcr()
        val classification = classifyActivity(ocrText)

        obsDao.insert(ObservationEntity(
            app_package = foreverApp ?: "unknown",
            app_name = foreverApp?.substringAfterLast(".") ?: "unknown",
            ocr_text_snippet = ocrText.take(500),
            activity_classification = classification,
            emotional_tone = null
        ))

        val weekOld = System.currentTimeMillis() - 7 * 24 * 3600_000
        obsDao.deleteOlderThan(weekOld)
    }

    private suspend fun captureOcr(): String {
        return try {
            val capture = controller.getScreenCaptureManager()
            val bytes = capture.captureScreenshot()
            bytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) } ?: ""
        } catch (_: Exception) { "" }
    }

    private suspend fun classifyActivity(ocrText: String): String? {
        if (ocrText.length < 10) return null
        return when {
            ocrText.contains("code", ignoreCase = true) || ocrText.contains("fun ", ignoreCase = true) -> "coding"
            ocrText.contains("message", ignoreCase = true) || ocrText.contains("chat", ignoreCase = true) -> "messaging"
            ocrText.contains("http", ignoreCase = true) || ocrText.contains("www.", ignoreCase = true) -> "browsing"
            ocrText.contains("play", ignoreCase = true) || ocrText.contains("video", ignoreCase = true) -> "media"
            else -> "general"
        }
    }
}
