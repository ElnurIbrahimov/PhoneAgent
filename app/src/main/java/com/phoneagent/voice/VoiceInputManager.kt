package com.phoneagent.voice

import android.content.Context

interface VoiceInputManager {
    fun isAvailable(): Boolean
    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun stopListening()
}

class VoiceInputManagerImpl(context: Context) : VoiceInputManager {

    override fun isAvailable(): Boolean {
        // Phase 1: Not implemented
        return false
    }

    override fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        // Phase 1: Placeholder
    }

    override fun stopListening() {
        // Phase 1: Placeholder
    }
}
