package com.phoneagent.agent.tools

import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import com.phoneagent.perception.OcrManager
import com.phoneagent.perception.ScreenCaptureManager
import com.phoneagent.perception.VisionPayloadBuilder
import org.json.JSONObject

class PhoneScreenshotTool(
    private val screenCaptureManager: ScreenCaptureManager,
    private val ocrManager: OcrManager
) : Tool, DescribableTool {

    override val name: String = "phone.screenshot"
    override val description: String = "Capture a screenshot of the phone screen and return OCR text from it."
    override val argsDescription: String = "none"

    override suspend fun execute(arguments: Map<String, String>): String {
        return try {
            val imageBytes = screenCaptureManager.captureScreenshot()
            val base64 = VisionPayloadBuilder.encodeImage(imageBytes)
            val ocrText = ocrManager.recognizeText(imageBytes)

            val content = JSONObject().apply {
                put("ocr_text", ocrText.take(4000))
                put("image_base64_preview", base64.take(200))
                put("image_size_bytes", imageBytes.size)
            }

            ToolResult.success(name, content.toString())
        } catch (e: Exception) {
            ToolResult.error(name, e.message ?: "Screenshot failed", "Screen capture may need permission. Use accessibility.read_tree for UI info without screenshot.")
        }
    }
}
