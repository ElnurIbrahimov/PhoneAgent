package com.phoneagent.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

interface SpeechOutputManager {
    fun isAvailable(): Boolean
    fun speak(text: String)
    fun stop()
    fun shutdown()
}

class SpeechOutputManagerImpl(context: Context) : SpeechOutputManager {
    private val appContext: Context = context.applicationContext
    private var tts: TextToSpeech? = null
    private var initialized = false
    private val pendingQueue = mutableListOf<String>()

    override fun isAvailable(): Boolean = initialized

    override fun speak(text: String) {
        if (initialized) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        } else {
            pendingQueue.add(text)
            if (tts == null) {
                tts = TextToSpeech(appContext) { status ->
                    initialized = (status == TextToSpeech.SUCCESS)
                    if (initialized) {
                        tts?.language = Locale.getDefault()
                        pendingQueue.forEach { tts?.speak(it, TextToSpeech.QUEUE_ADD, null, null) }
                        pendingQueue.clear()
                    }
                }
            }
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        initialized = false
    }
}
