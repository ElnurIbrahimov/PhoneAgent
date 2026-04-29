# Remaining Audit Fixes Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix all remaining critical, high, and medium severity issues from the full-project audit.

**Architecture:** Surgical fixes only. Extract shared utilities (ToolResult, theme gradient constant), add thread safety (Mutex, synchronizedList), fix lifecycle bugs (deferred init, stable keys), and clean up dead code.

**Tech Stack:** Kotlin, Coroutines, Room, Compose

---

### Task 1: Fix ScreenCaptureManager thread safety

**Files:**
- Modify: `app/src/main/java/com/phoneagent/perception/ScreenCaptureManager.kt:84-133`

**Problem:** `imageReader` and `virtualDisplay` are instance fields. Two concurrent calls to `captureScreenshot()` will close each other's reader/display.

**Fix:** Make `imageReader` and `virtualDisplay` local variables inside `captureScreenshot()`. Remove the instance fields. Use a single `Mutex` to serialize concurrent calls.

```kotlin
class ScreenCaptureManagerImpl(private val context: Context) : ScreenCaptureManager {

    private var mediaProjection: MediaProjection? = null
    @Volatile private var latestBitmap: Bitmap? = null
    private var initialized = false
    private var displayWidth = 0
    private var displayHeight = 0
    private var displayDensity = 0
    private val handler = Handler(Looper.getMainLooper())
    private val captureMutex = Mutex()

    // ... (rest of fields unchanged)

    override suspend fun captureScreenshot(): ByteArray = withTimeout(15_000) {
        if (!initialized || mediaProjection == null) {
            throw IllegalStateException("Screen capture not initialized. Grant permission first.")
        }

        val mediaProj = mediaProjection ?: throw IllegalStateException("MediaProjection not available")
        captureMutex.withLock {
            val reader = ImageReader.newInstance(displayWidth, displayHeight, PixelFormat.RGBA_8888, 2)
            val display = mediaProj.createVirtualDisplay(
                "PhoneAgentScreenCapture",
                displayWidth, displayHeight, displayDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, handler
            )

            try {
                suspendCancellableCoroutine { continuation ->
                    var imageAcquired = false
                    reader.setOnImageAvailableListener({ r ->
                        if (imageAcquired) return@setOnImageAvailableListener
                        val image = r.acquireLatestImage()
                        if (image != null) {
                            imageAcquired = true
                            try {
                                // ... existing image processing unchanged ...
                                val planes = image.planes
                                val buffer = planes[0].buffer
                                val pixelStride = planes[0].pixelStride
                                val rowStride = planes[0].rowStride
                                val rowPadding = rowStride - pixelStride * displayWidth
                                val bitmapPadding = if (pixelStride > 0) rowPadding / pixelStride else 0
                                val bitmap = Bitmap.createBitmap(displayWidth + bitmapPadding, displayHeight, Bitmap.Config.ARGB_8888)
                                bitmap.copyPixelsFromBuffer(buffer)
                                val cropped = Bitmap.createBitmap(bitmap, 0, 0, displayWidth, displayHeight)
                                synchronized(this) { latestBitmap = cropped }
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
            } finally {
                display.release()
                reader.close()
            }
        }
    }
```

Also remove the instance fields `imageReader` and `virtualDisplay` from the class, remove `releaseVirtualDisplayAndReader()` and `releaseResources()` methods.

---

### Task 2: Fix ChatScreen LazyColumn stable key

**Files:**
- Modify: `app/src/main/java/com/phoneagent/ui/ChatScreen.kt:116-119`

**Problem:** `key = { it.hashCode() }` is unstable during streaming because content changes → hashCode changes → LazyColumn treats every update as a new item.

**Fix:** Use the list index as the key (messages are append-only, never reordered):

```kotlin
itemsIndexed(uiState.messages, key = { index, _ -> index }) { _, message ->
    MessageBubble(message = message)
}
```

Replace the existing `items(...)` block.

---

### Task 3: Extract ToolResult utility to eliminate JSON duplication

**Files:**
- Create: `app/src/main/java/com/phoneagent/agent/tools/ToolResult.kt`
- Modify: All 17 tool files to use the new utility

**Problem:** 600+ lines of duplicated `JSONObject().apply { put("type","tool_result"); put("tool", name); put("success", ...); put("content", ...); put("error", ...) }.toString()` across every tool file.

**Fix:** Create a shared utility:

```kotlin
package com.phoneagent.agent.tools

import org.json.JSONObject

object ToolResult {
    fun success(toolName: String, content: String): String = JSONObject().apply {
        put("type", "tool_result")
        put("tool", toolName)
        put("success", true)
        put("content", content)
        put("error", JSONObject.NULL)
    }.toString()

    fun error(toolName: String, error: String, suggestion: String? = null): String = JSONObject().apply {
        put("type", "tool_result")
        put("tool", toolName)
        put("success", false)
        put("content", JSONObject.NULL)
        put("error", error)
        put("suggestion", suggestion ?: JSONObject.NULL)
    }.toString()
}
```

Then replace in ALL tool files:
- `BrowserTool.kt` — replace inline JSON with `ToolResult.success(name, ...)` and `ToolResult.error(name, ...)`
- `PhoneListAppsTool.kt` — same
- `PhoneOpenAppTool.kt` — remove `errorResult()` helper, use `ToolResult.error()`
- `PhoneSystemInfoTool.kt` — same
- `PhoneScreenshotTool.kt` — same
- `PhoneNotificationsTool.kt` — replace inline JSON
- `PhoneClipboardTool.kt` — remove `errorResult()` helper
- `PhoneSettingsTool.kt` — remove `errorResult()` helper
- `PhoneSendSmsTool.kt` — remove `errorResult()` helper
- `PhoneCallTool.kt` — remove `errorResult()` helper
- `BrowserActionTool.kt` — replace inline JSON
- `AccessibilityReadTreeTool.kt` — replace `successResult()`/`errorResult()` helpers
- `AccessibilityTapTool.kt` — replace `successResult()`/`errorResult()` helpers
- `AccessibilitySwipeTool.kt` — replace `successResult()`/`errorResult()` helpers
- `AccessibilityTypeTool.kt` — replace `successResult()`/`errorResult()` helpers
- `AccessibilityNavTool.kt` — replace `successResult()`/`errorResult()` helpers
- `AccessibilityForegroundTool.kt` — replace inline JSON

**Example for PhoneCallTool.kt (before→after):**
```kotlin
// Before:
JSONObject().apply {
    put("type", "tool_result"); put("tool", name); put("success", true)
    put("content", "Calling $phoneNumber"); put("error", JSONObject.NULL)
}.toString()
// After:
ToolResult.success(name, "Calling $phoneNumber")

// Before:
private fun errorResult(error: String): String = JSONObject().apply {
    put("type", "tool_result"); put("tool", name); put("success", false)
    put("content", JSONObject.NULL); put("error", error)
}.toString()
// After: (inline the call, remove the helper)
ToolResult.error(name, error)
```

---

### Task 4: Extract gradient constant to theme

**Files:**
- Modify: `app/src/main/java/com/phoneagent/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/phoneagent/ui/ChatScreen.kt`
- Modify: `app/src/main/java/com/phoneagent/overlay/OverlayChatContent.kt`
- Modify: `app/src/main/java/com/phoneagent/ui/components/MessageBubble.kt`
- Modify: `app/src/main/java/com/phoneagent/ui/components/ConfirmationDialog.kt`
- Modify: `app/src/main/java/com/phoneagent/ui/MainScreen.kt`

**Problem:** `Brush.linearGradient(listOf(Primary, Secondary))` duplicated 5+ times.

**Fix:** Add to `Color.kt`:

```kotlin
import androidx.compose.ui.graphics.Brush

val AccentGradient = Brush.linearGradient(listOf(Primary, Secondary))
```

Then replace all `Brush.linearGradient(listOf(Primary, Secondary))` with `AccentGradient` in the 5 files.

---

### Task 5: Fix SpeechOutputManager pendingQueue synchronization

**Files:**
- Modify: `app/src/main/java/com/phoneagent/voice/SpeechOutputManager.kt:18`

**Problem:** `pendingQueue` is a plain `MutableList` accessed from multiple threads without synchronization.

**Fix:**
```kotlin
private val pendingQueue = mutableListOf<String>()
// Replace with:
private val pendingQueue = java.util.Collections.synchronizedList(mutableListOf<String>())
```

Also wrap the `forEach` + `clear` inside a synchronized block:
```kotlin
initialized = (status == TextToSpeech.SUCCESS)
if (initialized) {
    tts?.language = Locale.getDefault()
    synchronized(pendingQueue) {
        pendingQueue.forEach { tts?.speak(it, TextToSpeech.QUEUE_ADD, null, null) }
        pendingQueue.clear()
    }
}
```

---

### Task 6: Defer heavy initialization from PhoneAgentApplication.onCreate

**Files:**
- Modify: `app/src/main/java/com/phoneagent/PhoneAgentApplication.kt:18`

**Problem:** `AgentController(this)` runs on main thread during `onCreate()`, initializing Room, ML Kit, TTS, Keystore. ~500ms-2s blocking.

**Fix:** Defer `AgentController` creation to the first access via the `agentController()` companion method:

```kotlin
override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    // Defer heavy init to first access
}

// In companion object:
private val agentControllerLock = Any()

fun agentController(context: Context): AgentController {
    val app = context.applicationContext as PhoneAgentApplication
    if (!app::agentController.isInitialized) {
        synchronized(agentControllerLock) {
            if (!app::agentController.isInitialized) {
                // Launch on IO dispatcher via coroutine
                app.agentController = AgentController(app)
            }
        }
    }
    return app.agentController
}
```

Wait, this won't work because `AgentController` needs to be initialized synchronously. A better approach: keep the init at first access but use `kotlinx.coroutines.MainScope().launch { }` for the async parts (tool registration, DB init). Or just use `by lazy`:

```kotlin
private val agentControllerInstance: AgentController by lazy {
    AgentController(this)
}

fun agentController(context: Context): AgentController {
    return (context.applicationContext as PhoneAgentApplication).agentControllerInstance
}
```

`by lazy` will initialize on first access, which happens later than `onCreate()` (typically when the overlay service or main activity starts). Still on main thread, but deferred past the critical startup path.

---

### Task 7: Fix MainScreen overlay running state (optimistic → verified)

**Files:**
- Modify: `app/src/main/java/com/phoneagent/ui/MainScreen.kt:36, 67-76`

**Problem:** `overlayRunning` is set to `true`/`false` optimistically on button press, never verified against actual service state.

**Fix:** Check `OverlayService.isRunning()` if it exists, or use a more reliable pattern. Add a static flag to `OverlayService`:

In `OverlayService.kt`, add:
```kotlin
companion object {
    private const val NOTIFICATION_ID = 1
    @Volatile var isRunning = false
        private set

    fun start(context: Context) {
        val intent = Intent(context, OverlayService::class.java)
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, OverlayService::class.java)
        context.stopService(intent)
    }
}
```

In `OverlayService.onCreate()`:
```kotlin
override fun onCreate() {
    super.onCreate()
    isRunning = true
    bubbleController = FloatingBubbleController(this) { toggleChat() }
}

override fun onDestroy() {
    isRunning = false
    // ... existing
}
```

In `MainScreen.kt`:
```kotlin
var overlayRunning by remember { mutableStateOf(OverlayService.isRunning) }

// In onStart:
onStart = {
    OverlayService.start(context)
    overlayRunning = true
}
// After stopping:
onStop = {
    OverlayService.stop(context)
    overlayRunning = false
}
```

---

### Task 8: Fix MemoryRepository.insertMessage key collision

**Files:**
- Modify: `app/src/main/java/com/phoneagent/memory/MemoryRepository.kt:23`

**Problem:** `"chat_${System.currentTimeMillis()}"` can collide if called within 1ms.

**Fix:**
```kotlin
key = "chat_${System.nanoTime()}_${(Math.random() * 10000).toInt()}",
```

---

### Task 9: Clean up dead code

**Files:**
- Modify: `app/src/main/java/com/phoneagent/overlay/ChatOverlayController.kt:39`
- Modify: `app/src/main/java/com/phoneagent/ui/OnboardingScreen.kt:9`
- Modify: `app/src/main/java/com/phoneagent/perception/ScreenCaptureManager.kt:161-163`

**Problems:**
1. `ChatOverlayController.scope` — CoroutineScope allocated but never used
2. `OnboardingScreen.kt:9` — unused import `animation.core.tween`
3. `ScreenCaptureManager.releaseResources()` — redundant wrapper (but will be removed as part of Task 1)

**Fixes:**
1. Remove `scope` field and its import from `ChatOverlayController.kt`. Remove `destroy()`'s `scope.cancel()`.
2. Remove unused import from OnboardingScreen.kt.
3. Handled by Task 1.

---

### Task 10: Strip apiKey from ProviderConfig before serializing to DataStore

**Files:**
- Modify: `app/src/main/java/com/phoneagent/providers/ProviderRepository.kt:93-109`

**Problem:** API key stored in plaintext DataStore alongside encrypted keystore entry.

**Fix:** In `providersToJson`, never write the `apiKey` field:

```kotlin
put("apiKey", JSONObject.NULL) // Never persist API key in unencrypted DataStore
```

This ensures keys only exist in the encrypted Keystore.

---

### Task 11: Add AgentUiState message ID for stable LazyColumn keys

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentUiState.kt:3-6`
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt:152-153`

**Problem:** Messages have no stable ID. This forces fragile workarounds like `hashCode()` or index-based keys.

**Fix:** Add a simple sequential ID:
```kotlin
data class ChatMessage(
    val id: Int = counter++,
    val role: String,
    val content: String
) {
    companion object {
        private var counter = 0
    }
}
```

Then in `ChatScreen.kt`:
```kotlin
items(uiState.messages, key = { it.id }) { message ->
    MessageBubble(message = message)
}
```

---

### Task 12: Final verification

**Step 1:** Run all existing unit tests.
**Step 2:** Verify compilation passes.
**Step 3:** Commit and push.

---

## Summary

| Task | Priority | Files | Lines |
|------|----------|-------|-------|
| 1. ScreenCapture thread safety | Critical | 1 | ~40 change |
| 2. Stable LazyColumn keys | Critical | 2 | ~5 change |
| 3. ToolResult utility | Medium | 18 | ~400 removed, 25 added |
| 4. Gradient constant | Low | 6 | ~10 removed |
| 5. SpeechOutputManager sync | High | 1 | ~5 change |
| 6. Defer heavy init | Critical | 1 | ~5 change |
| 7. Overlay state verification | High | 2 | ~10 added |
| 8. MemoryRepository key collision | High | 1 | 1 line |
| 9. Dead code cleanup | Low | 2 | ~5 removed |
| 10. Strip apiKey from DataStore | High | 1 | 1 line |
| 11. Message ID for stable keys | Medium | 2 | ~5 added |
| 12. Verification | — | — | — |

**Total: 12 tasks, ~37 files modified, ~400 lines removed, ~100 lines added.**
