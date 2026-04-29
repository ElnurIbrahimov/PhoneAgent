package com.phoneagent.perception

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface OcrManager {
    suspend fun recognizeText(image: ByteArray): String
    suspend fun recognizeText(bitmap: Bitmap): String
    fun close()
}

class OcrManagerImpl : OcrManager {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeText(image: ByteArray): String = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)
        recognizeText(bitmap)
    }

    override suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = Tasks.await(recognizer.process(inputImage))
            result.textBlocks.joinToString("\n") { block ->
                block.lines.joinToString("\n") { line ->
                    line.text
                }
            }
        } catch (e: Exception) {
            "OCR failed: ${e.message}"
        }
    }

    override fun close() {
        runCatching { recognizer.close() }
    }
}
