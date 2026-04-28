package com.phoneagent.voice

import android.content.Context

interface SpeechOutputManager {
    fun isAvailable(): Boolean
    fun speak(text: String)
    fun stop()
}

class SpeechOutputManagerImpl(@Suppress("UNUSED_PARAMETER") context: Context) : SpeechOutputManager {

    override fun isAvailable(): Boolean {
        // Phase 1: Not implemented
        return false
    }

    override fun speak(text: String) {
        // Phase 1: Placeholder
    }

    override fun stop() {
        // Phase 1: Placeholder
    }
}
