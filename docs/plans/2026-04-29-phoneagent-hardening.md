# PhoneAgent Hardening & Completion Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix all security/reliability issues, complete stub implementations (perception, voice), add missing phone tools, and harden the agent loop.

**Architecture:** Keep existing package structure. No new modules. All changes are within existing packages: `agent/`, `perception/`, `voice/`, `tools/`, `providers/`, `browser/`. New tools register via the existing `ToolRegistry`. SafetyGate becomes a risk-level system instead of keyword matching. Agent loop gets exponential backoff retry.

**Tech Stack:** Kotlin, Jetpack Compose, Room, OkHttp, Android MediaProjection, ML Kit Text Recognition, Android SpeechRecognizer/TextToSpeech

---

### Task 1: Rewrite SafetyGate — Risk-Based Classification

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/SafetyGate.kt`

**What's wrong:** Keyword string matching on `sensitiveWords` set (pay, buy, delete, etc.) is trivially bypassed by punctuation, spacing, or capitalization. It also false-positives on innocent requests like "pay attention" or "delete this message draft."

**What to build:** A 3-tier risk system that classifies every tool call as LOW, MEDIUM, or HIGH risk based on the tool+args combination:

```kotlin
package com.phoneagent.agent

object SafetyGate {

    enum class RiskLevel { LOW, MEDIUM, HIGH }

    data class RiskAssessment(
        val level: RiskLevel,
        val reason: String,
        val toolName: String,
        val args: Map<String, String>
    )

    private val highRiskTools = setOf("phone.open_app")
    private val mediumRiskTools = setOf(
        "browser.click_text", "browser.click_selector",
        "browser.type_into_selector", "browser.type_into_focused"
    )
    // browser.open_url, browser.read_page, browser.read_metadata, browser.scroll,
    // browser.back, browser.reload, phone.list_apps, phone.system_info are ALLOWED (LOW)

    // Regex per tool for args that should be confirmed
    private val mediumRiskArgs = mapOf<String, (Map<String, String>) -> Boolean>(
        "browser.click_text" to { args ->
            args["text"]?.let { text ->
                text.containsWord("submit") || text.containsWord("send") ||
                text.containsWord("book") || text.containsWord("purchase") ||
                text.containsWord("checkout") || text.containsWord("confirm") ||
                text.containsWord("buy") || text.containsWord("reserve") ||
                text.containsWord("delete") || text.containsWord("remove") ||
                text.containsWord("cancel") || text.containsWord("download")
            } ?: false
        },
        "browser.click_selector" to { args ->
            args["selector"]?.let { sel ->
                sel.contains("submit") || sel.contains("delete") || sel.contains("confirm")
            } ?: false
        },
        "browser.type_into_selector" to { args ->
            val selector = args["selector"] ?: ""
            val isSensitive = selector.contains("password") || selector.contains("credit") ||
                selector.contains("card") || selector.contains("cvv") || selector.contains("ssn")
            val text = args["text"] ?: ""
            isSensitive || text.length > 200
        },
        "browser.type_into_focused" to { args -> (args["text"]?.length ?: 0) > 200 }
    )

    fun assess(toolName: String, args: Map<String, String>): RiskAssessment {
        if (toolName in highRiskTools) {
            return RiskAssessment(RiskLevel.HIGH, "Tool '$toolName' requires confirmation per safety policy.", toolName, args)
        }

        if (toolName in mediumRiskTools) {
            val checker = mediumRiskArgs[toolName]
            if (checker != null && checker(args)) {
                val argsSummary = args.entries.joinToString(", ") { "${it.key}=${it.value.take(60)}" }
                return RiskAssessment(
                    RiskLevel.MEDIUM,
                    "Sensitive action detected in args: $argsSummary",
                    toolName, args
                )
            }
        }

        return RiskAssessment(RiskLevel.LOW, "", toolName, args)
    }

    private fun String.containsWord(word: String): Boolean {
        val pattern = Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(this)
    }
}
```

**Step 1:** Replace the entire content of `SafetyGate.kt` with the above.
**Step 2:** Run `./gradlew :app:testDebugUnitTest` to ensure nothing breaks (no existing tests reference SafetyGate yet).

---

### Task 2: Update AgentLoopExecutor — Wire Up New SafetyGate

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt`

**What to change:** The `executeSteps` method currently calls `SafetyGate.check()` which returns `SafetyResult.Blocked`. Update it to call `SafetyGate.assess()` and use the new `RiskAssessment` model. Also add exponential backoff retry.

Replace the safety check block inside `executeSteps` (roughly lines 58-73 in the current file):

```kotlin
val assessment = SafetyGate.assess(action.tool, action.args)
if (assessment.level == SafetyGate.RiskLevel.HIGH || assessment.level == SafetyGate.RiskLevel.MEDIUM) {
    val pending = PendingConfirmation(
        toolName = action.tool,
        args = action.args,
        reason = assessment.reason,
        taskId = taskId,
        userRequest = message,
        stepsSoFar = steps.toList(),
        systemPrompt = systemPrompt,
        selectedModel = model
    )
    steps.add(AgentStep(stepsTaken, action, observation = buildErrorJson(action.tool, "Waiting for user confirmation.")))
    applyState {
        it.copy(
            agentStepStatus = "Waiting for confirmation",
            currentSteps = steps.toList(),
            pendingConfirmation = pending,
            isLoading = false
        )
    }
    return
}
```

Also add retry with exponential backoff in `executeTool`:

```kotlin
private suspend fun executeTool(toolName: String, args: Map<String, String>): String {
    val tool = toolRegistry.getTool(toolName)
        ?: return buildErrorJson(toolName, "Unknown tool: $toolName")

    var attempt = 0
    val maxRetries = 2
    var lastError: String? = null

    while (attempt <= maxRetries) {
        try {
            return withTimeout(20_000) { tool.execute(args) }
        } catch (e: Exception) {
            lastError = e.message ?: "Execution failed"
            attempt++
            if (attempt <= maxRetries) {
                kotlinx.coroutines.delay((500L * Math.pow(2.0, attempt.toDouble())).toLong())
            }
        }
    }
    return buildErrorJson(toolName, lastError ?: "Execution failed after $maxRetries retries")
}
```

**Step 1:** Make the safety check replacement.
**Step 2:** Replace `executeTool` with the retry version.
**Step 3:** Run `./gradlew :app:testDebugUnitTest` to verify.

---

### Task 3: Complete ScreenCaptureManager — MediaProjection-Based Screenshot

**Files:**
- Modify: `app/src/main/java/com/phoneagent/perception/ScreenCaptureManager.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add FOREGROUND_SERVICE_MEDIA_PROJECTION)

**What to build:** Real screen capture using Android MediaProjection API. The manager captures a screenshot on demand and returns it as a JPEG byte array.

```kotlin
package com.phoneagent.perception

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScreenCaptureManagerImpl(private val context: Context) : ScreenCaptureManager {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var resultCode: Int = 0
    private var resultData: Intent? = null
    private var latestBitmap: Bitmap? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun isAvailable(): Boolean = resultData != null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE) {
            this.resultCode = resultCode
            this.resultData = data
        }
    }

    override fun startCapture(activity: Activity) {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        activity.startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_CODE
        )
    }

    override fun stopCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        latestBitmap?.recycle()
        latestBitmap = null
    }

    override suspend fun captureScreenshot(): ByteArray = withTimeout(15_000) {
        val data = resultData
            ?: throw IllegalStateException("Screen capture not initialized. Call startCapture first.")
        if (resultCode != Activity.RESULT_OK) {
            throw IllegalStateException("Screen capture permission denied (resultCode=$resultCode).")
        }

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "PhoneAgentScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, handler
            )
        }

        suspendCancellableCoroutine { continuation ->
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width
                        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                        bitmap.copyPixelsFromBuffer(buffer)
                        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        latestBitmap = cropped
                        bitmap.recycle()

                        val baos = ByteArrayOutputStream()
                        cropped.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        val bytes = baos.toByteArray()
                        baos.close()

                        continuation.resume(bytes)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    } finally {
                        image.close()
                    }
                }
            }, handler)
        }
    }

    override fun getLatestBitmap(): Bitmap? = latestBitmap

    override fun getLastCapture(): ByteArray? {
        latestBitmap?.let {
            val baos = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            return baos.toByteArray()
        }
        return null
    }

    companion object {
        const val REQUEST_CODE = 9001
    }
}
```

Also update the `ScreenCaptureManager` interface to add the new methods:

```kotlin
interface ScreenCaptureManager {
    fun isAvailable(): Boolean
    fun startCapture(activity: Activity)
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    fun stopCapture()
    suspend fun captureScreenshot(): ByteArray
    fun getLatestBitmap(): Bitmap?
    fun getLastCapture(): ByteArray?
}
```

**Step 1:** Update the interface in `ScreenCaptureManager.kt`.
**Step 2:** Replace the `ScreenCaptureManagerImpl` class body.
**Step 3:** Add imports to the file.
**Step 4:** Add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />` to `AndroidManifest.xml`.
**Step 5:** Update `MainActivity.kt` to forward `onActivityResult` to `ScreenCaptureManagerImpl`.
**Step 6:** Run `./gradlew :app:assembleDebug` to verify it compiles.

---

### Task 4: Complete OCR Manager — ML Kit Text Recognition

**Files:**
- Modify: `app/src/main/java/com/phoneagent/perception/OcrManager.kt`
- Modify: `app/build.gradle.kts` (add ML Kit dependency)

**What to build:** Real OCR using Google ML Kit Text Recognition v16 (on-device, no cloud).

Add dependency to `app/build.gradle.kts`:
```kotlin
implementation("com.google.mlkit:text-recognition:16.0.1")
```

Replace `OcrManagerImpl`:
```kotlin
package com.phoneagent.perception

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

interface OcrManager {
    suspend fun recognizeText(image: ByteArray): String
    suspend fun recognizeText(bitmap: Bitmap): String
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
            val result = recognizer.process(inputImage).await()
            result.textBlocks.joinToString("\n") { block ->
                block.lines.joinToString("\n") { line ->
                    line.text
                }
            }
        } catch (e: Exception) {
            "OCR failed: ${e.message}"
        }
    }
}
```

**Step 1:** Add the ML Kit dependency to `app/build.gradle.kts`.
**Step 2:** Replace `OcrManager.kt` with the above.
**Step 3:** Run `./gradlew :app:assembleDebug` to verify it compiles.

---

### Task 5: Complete VisionPayloadBuilder — Send Screenshots to Vision Models

**Files:**
- Modify: `app/src/main/java/com/phoneagent/perception/VisionPayloadBuilder.kt`

**What to build:** Build a vision payload that appends a base64-encoded screenshot to the user message so vision-capable models can "see" the screen.

```kotlin
package com.phoneagent.perception

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class VisionPayloadBuilder {

    fun buildVisionPayload(imageBase64: String, text: String): String {
        val contentArray = JSONArray()
        contentArray.put(JSONObject().apply {
            put("type", "text")
            put("text", text)
        })
        contentArray.put(JSONObject().apply {
            put("type", "image_url")
            put("image_url", JSONObject().apply {
                put("url", "data:image/jpeg;base64,$imageBase64")
                put("detail", "high")
            })
        })
        return contentArray.toString()
    }

    fun buildVisionSystemPrompt(baseSystemPrompt: String): String {
        return "$baseSystemPrompt\n\nYou can also see a screenshot of the phone screen. " +
               "Use it to understand the current UI state and guide your actions."
    }

    companion object {
        fun encodeImage(bytes: ByteArray): String {
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }
}
```

**Step 1:** Replace `VisionPayloadBuilder.kt` with the above.
**Step 2:** No compile dependencies needed — uses existing `org.json` and `android.util.Base64`.

---

### Task 6: Add Phone Screenshot Tool

**Files:**
- Create: `app/src/main/java/com/phoneagent/agent/tools/PhoneScreenshotTool.kt`

**What to build:** A new tool that captures a screenshot and returns the base64-encoded image + OCR text.

```kotlin
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
    override val description: String = "Capture a screenshot of the phone screen and return OCR text."
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

            JSONObject().apply {
                put("type", "tool_result")
                put("tool", name)
                put("success", true)
                put("content", content.toString())
                put("error", JSONObject.NULL)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("type", "tool_result")
                put("tool", name)
                put("success", false)
                put("content", JSONObject.NULL)
                put("error", e.message ?: "Screenshot failed")
            }.toString()
        }
    }
}
```

**Step 1:** Create the file.
**Step 2:** In `AgentController.kt`, add `PhoneScreenshotTool` to the `toolRegistry.registerAll` call. The `ScreenCaptureManager` and `OcrManager` need to be created in `AgentController.init` (or passed in).

This requires the `AgentController` to own `ScreenCaptureManager` and `OcrManager` instances. Update `AgentController.kt`:

```kotlin
// Add fields:
private val screenCaptureManager: ScreenCaptureManager = ScreenCaptureManagerImpl(context)
private val ocrManager: OcrManager = OcrManagerImpl()

// Add to toolRegistry.registerAll:
PhoneScreenshotTool(screenCaptureManager, ocrManager),
```

**Step 3:** Run `./gradlew :app:assembleDebug` to verify.

---

### Task 7: Complete Voice Input — Android SpeechRecognizer

**Files:**
- Modify: `app/src/main/java/com/phoneagent/voice/VoiceInputManager.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add RECORD_AUDIO if missing)

**What to build:** Real voice input using Android's built-in `SpeechRecognizer`.

```kotlin
package com.phoneagent.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

interface VoiceInputManager {
    fun isAvailable(): Boolean
    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun stopListening()
}

class VoiceInputManagerImpl(private val context: Context) : VoiceInputManager {

    private var recognizer: SpeechRecognizer? = null

    override fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    override fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResult(matches[0])
                    } else {
                        onError("No speech recognized.")
                    }
                }
                override fun onError(error: Int) {
                    onError("Speech recognition error: $error")
                }
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            startListening(intent)
        }
    }

    override fun stopListening() {
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }
}
```

**Step 1:** Replace `VoiceInputManager.kt`.
**Step 2:** Add `<uses-permission android:name="android.permission.RECORD_AUDIO" />` to `AndroidManifest.xml` if not present.
**Step 3:** Run `./gradlew :app:assembleDebug`.

---

### Task 8: Complete Voice Output — Android TextToSpeech

**Files:**
- Modify: `app/src/main/java/com/phoneagent/voice/SpeechOutputManager.kt`

**What to build:** Real TTS using Android's built-in `TextToSpeech`.

```kotlin
package com.phoneagent.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

interface SpeechOutputManager {
    fun isAvailable(): Boolean
    fun speak(text: String)
    fun stop()
}

class SpeechOutputManagerImpl(private val context: Context) : SpeechOutputManager {

    private var tts: TextToSpeech? = null
    private var initialized = false

    override fun isAvailable(): Boolean = true

    override fun speak(text: String) {
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                initialized = (status == TextToSpeech.SUCCESS)
                if (initialized) {
                    tts?.language = Locale.getDefault()
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        } else if (initialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun stop() {
        tts?.stop()
    }
}
```

**Step 1:** Replace `SpeechOutputManager.kt`.
**Step 2:** Run `./gradlew :app:assembleDebug`.

---

### Task 9: Add Phone Notification Tool

**Files:**
- Create: `app/src/main/java/com/phoneagent/agent/tools/PhoneNotificationsTool.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add BIND_NOTIFICATION_LISTENER_SERVICE)
- Create: `app/src/main/java/com/phoneagent/perception/NotificationListenerService.kt`
- Create: `app/src/main/res/xml/notification_listener_service.xml`

**What to build:** Read recent notifications via `NotificationListenerService`.

`notification_listener_service.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<notification-listener-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:label="PhoneAgent Notifications"
    android:description="@string/notification_listener_description" />
```

`NotificationListenerService.kt`:
```kotlin
package com.phoneagent.perception

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PhoneAgentNotificationListener : NotificationListenerService() {

    companion object {
        private val recentNotifications = mutableListOf<NotificationEntry>()
        private const val MAX_STORED = 50

        fun getRecent(count: Int = 10): List<NotificationEntry> {
            return recentNotifications.takeLast(count)
        }

        fun clear() {
            recentNotifications.clear()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val entry = NotificationEntry(
            packageName = sbn.packageName,
            title = extras.getString("android.title") ?: "",
            text = extras.getString("android.text") ?: "",
            appName = extras.getString("android.appName") ?: sbn.packageName,
            timestamp = sbn.postTime
        )
        recentNotifications.add(entry)
        if (recentNotifications.size > MAX_STORED) {
            recentNotifications.removeAt(0)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    data class NotificationEntry(
        val packageName: String,
        val title: String,
        val text: String,
        val appName: String,
        val timestamp: Long
    )
}
```

`PhoneNotificationsTool.kt`:
```kotlin
package com.phoneagent.agent.tools

import com.phoneagent.agent.DescribableTool
import com.phoneagent.agent.Tool
import com.phoneagent.perception.PhoneAgentNotificationListener
import org.json.JSONArray
import org.json.JSONObject

class PhoneNotificationsTool : Tool, DescribableTool {

    override val name: String = "phone.notifications"
    override val description: String = "Get recent phone notifications."
    override val argsDescription: String = "count (Int, optional, default 10)"

    override suspend fun execute(arguments: Map<String, String>): String {
        val count = arguments["count"]?.toIntOrNull() ?: 10
        val notifications = PhoneAgentNotificationListener.getRecent(count)
        val array = JSONArray()
        notifications.forEach { entry ->
            array.put(JSONObject().apply {
                put("appName", entry.appName)
                put("title", entry.title)
                put("text", entry.text)
                put("packageName", entry.packageName)
            })
        }
        return JSONObject().apply {
            put("type", "tool_result")
            put("tool", name)
            put("success", true)
            put("content", array.toString())
            put("error", JSONObject.NULL)
        }.toString()
    }
}
```

In `AndroidManifest.xml`, add inside `<application>`:
```xml
<service
    android:name=".perception.PhoneAgentNotificationListener"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

Add string resource in `res/values/strings.xml`:
```xml
<string name="notification_listener_description">Allows PhoneAgent to read notifications for AI assistance</string>
```

**Step 1:** Create the XML resource directory and file.
**Step 2:** Create `NotificationListenerService.kt`.
**Step 3:** Create `PhoneNotificationsTool.kt`.
**Step 4:** Update `AndroidManifest.xml`.
**Step 5:** Register tool in `AgentController.kt`.
**Step 6:** Run `./gradlew :app:assembleDebug`.

---

### Task 10: Agent Prompt Update — Include New Tools

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentPromptBuilder.kt`

**What to change:** The system prompt is fine as-is since it dynamically lists all registered tools via `toolRegistry.listTools()`. No changes needed — the new tools will appear automatically.

**Step 1:** Verify — read the prompt builder, confirm it uses `tools.joinToString`, and skip this task. Nothing to change.

---

### Task 11: Final Integration — Wire Everything in AgentController

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt`

**What to change:** After all the above tasks, the `AgentController` needs minor updates:

1. Create `ScreenCaptureManager` and `OcrManager` instances
2. Register new tools (`PhoneScreenshotTool`, `PhoneNotificationsTool`)
3. Add a `getScreenCaptureManager()` accessor (needed by `MainActivity` for `onActivityResult`)

The updated init block:
```kotlin
private val screenCaptureManager: ScreenCaptureManager = ScreenCaptureManagerImpl(context)
private val ocrManager: OcrManager = OcrManagerImpl()

init {
    val browserTool = BrowserTool(context)
    toolRegistry.registerAll(
        listOf(
            PhoneListAppsTool(context),
            PhoneOpenAppTool(context),
            PhoneSystemInfoTool(context),
            PhoneScreenshotTool(screenCaptureManager, ocrManager),
            PhoneNotificationsTool(),
            BrowserActionTool("browser.open_url", "Open a URL in the agent browser.", browserTool, "open_url", "url (String)"),
            BrowserActionTool("browser.read_page", "Read the text content of the current browser page.", browserTool, "read_page", ""),
            BrowserActionTool("browser.read_metadata", "Read page metadata including title, URL, links, buttons, and inputs.", browserTool, "read_metadata", ""),
            BrowserActionTool("browser.click_text", "Click an element by its visible text.", browserTool, "click_text", "text (String)"),
            BrowserActionTool("browser.click_selector", "Click an element by CSS selector.", browserTool, "click_selector", "selector (String)"),
            BrowserActionTool("browser.type_into_selector", "Type text into an input identified by CSS selector.", browserTool, "type_into_selector", "selector (String), text (String)"),
            BrowserActionTool("browser.type_into_focused", "Type text into the currently focused input element.", browserTool, "type_into_focused", "text (String)"),
            BrowserActionTool("browser.scroll", "Scroll the page up or down.", browserTool, "scroll", "direction (String: up/down)"),
            BrowserActionTool("browser.back", "Go back in browser history.", browserTool, "back", ""),
            BrowserActionTool("browser.reload", "Reload the current page.", browserTool, "reload", "")
        )
    )
    // ... rest stays the same
```

Add this at the class level:
```kotlin
fun getScreenCaptureManager(): ScreenCaptureManager = screenCaptureManager
```

**Step 1:** Add the two field declarations and the accessor method.
**Step 2:** Add the two new tools to the `registerAll` call.
**Step 3:** Update imports to include new classes.
**Step 4:** Run `./gradlew :app:assembleDebug`.

---

### Task 12: Update MainActivity — Forward onActivityResult

**Files:**
- Modify: `app/src/main/java/com/phoneagent/MainActivity.kt`

**What to change:** Forward `onActivityResult` to `ScreenCaptureManager` so it can receive the media projection permission result.

```kotlin
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    (agentController.getScreenCaptureManager() as? ScreenCaptureManagerImpl)?.onActivityResult(requestCode, resultCode, data)
}
```

**Step 1:** Add the `onActivityResult` override and import `android.content.Intent` and `ScreenCaptureManagerImpl`.
**Step 2:** Run `./gradlew :app:assembleDebug`.

---

### Task 13: Run Full Build Verification

**Step 1:** Run `./gradlew :app:assembleDebug` from project root. Expected: BUILD SUCCESSFUL.
**Step 2:** Run `./gradlew :app:testDebugUnitTest`. Expected: All tests pass (2 existing + whatever new tests we add).
**Step 3:** Fix any compilation errors.

---

### Summary of Changes

| # | Task | Files Affected |
|---|------|---------------|
| 1 | SafetyGate rewrite | `agent/SafetyGate.kt` |
| 2 | Agent loop resilience + new SafetyGate wiring | `agent/AgentLoopExecutor.kt` |
| 3 | Screen capture (MediaProjection) | `perception/ScreenCaptureManager.kt`, `AndroidManifest.xml` |
| 4 | OCR (ML Kit) | `perception/OcrManager.kt`, `app/build.gradle.kts` |
| 5 | Vision payload builder | `perception/VisionPayloadBuilder.kt` |
| 6 | Screenshot tool | `agent/tools/PhoneScreenshotTool.kt` (NEW) |
| 7 | Voice input (SpeechRecognizer) | `voice/VoiceInputManager.kt`, `AndroidManifest.xml` |
| 8 | Voice output (TTS) | `voice/SpeechOutputManager.kt` |
| 9 | Notification tool | `agent/tools/PhoneNotificationsTool.kt` (NEW), `perception/NotificationListenerService.kt` (NEW), `res/xml/notification_listener_service.xml` (NEW), `res/values/strings.xml`, `AndroidManifest.xml` |
| 10 | Prompt update | N/A (auto-generated from tool registry) |
| 11 | Integration wiring | `agent/AgentController.kt` |
| 12 | MainActivity forwarding | `MainActivity.kt` |
| 13 | Build verification | `./gradlew :app:assembleDebug` |
