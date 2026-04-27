package com.phoneagent.perception

interface OcrManager {
    suspend fun recognizeText(image: ByteArray): String
}

class OcrManagerImpl : OcrManager {

    override suspend fun recognizeText(image: ByteArray): String {
        // Phase 1: Placeholder
        return "OCR not implemented in Phase 1"
    }
}
