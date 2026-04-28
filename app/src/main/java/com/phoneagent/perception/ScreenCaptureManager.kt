package com.phoneagent.perception

import android.content.Context

interface ScreenCaptureManager {
    fun isAvailable(): Boolean
    fun startCapture()
    fun stopCapture()
    fun getLastCapture(): ByteArray?
}

class ScreenCaptureManagerImpl(@Suppress("UNUSED_PARAMETER") context: Context) : ScreenCaptureManager {

    override fun isAvailable(): Boolean {
        // Phase 1: Not implemented
        return false
    }

    override fun startCapture() {
        // Phase 1: Placeholder
    }

    override fun stopCapture() {
        // Phase 1: Placeholder
    }

    override fun getLastCapture(): ByteArray? {
        // Phase 1: Placeholder
        return null
    }
}
