# PhoneAgent — Missing Features Implementation Plan

> **Goal:** Transform PhoneAgent from a browser-only bot into a real phone agent with system-wide interaction, visual understanding, and background execution.

**Architecture:** Add an Accessibility Service for native UI control, integrate vision screenshots into the agent loop, add 6 new tools, and make the agent survive backgrounding via the existing OverlayService.

**Tech Stack:** Android AccessibilityService, MediaProjection, ML Kit, ClipData, SmsManager, UsageStatsManager

---

## Phase A: Accessibility Service — Native App Control

### Task A1: Create AccessibilityService + config

**Files:**
- Create: `app/src/main/java/com/phoneagent/accessibility/AgentAccessibilityService.kt`
- Create: `app/src/main/res/xml/accessibility_service_config.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

AccessibilityService captures the UI tree for any app and performs gestures system-wide.

```kotlin
// AgentAccessibilityService.kt
package com.phoneagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AgentAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: AgentAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // --- Public API ---

    fun readUITree(maxDepth: Int = 10, maxNodes: Int = 100): String {
        val root = rootInActiveWindow ?: return "No active window."
        val sb = StringBuilder()
        var count = 0
        dumpNode(root, sb, 0, maxDepth, count, maxNodes)
        root.recycle()
        return sb.toString()
    }

    private fun dumpNode(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int, maxDepth: Int, count: Int, maxNodes: Int): Int {
        if (depth > maxDepth || count >= maxNodes) return count
        var c = count + 1
        val indent = "  ".repeat(depth)
        val cls = node.className?.toString()?.substringAfterLast(".") ?: "?"
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val id = node.viewIdResourceName ?: ""
        val clickable = if (node.isClickable) " [TAP]" else ""
        val rect = Rect()
        node.getBoundsInScreen(rect)

        sb.append("$indent$cls")
        if (text.isNotBlank()) sb.append(" text='$text'")
        if (desc.isNotBlank()) sb.append(" desc='$desc'")
        if (id.isNotBlank()) sb.append(" id='$id'")
        if (clickable.isNotBlank()) sb.append(clickable)
        sb.append(" bounds=[${rect.left},${rect.top},${rect.right},${rect.bottom}]")
        sb.append("\n")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            c = dumpNode(child, sb, depth + 1, maxDepth, c, maxNodes)
            child.recycle()
            if (c >= maxNodes) break
        }
        return c
    }

    fun findAndTap(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByText(root, text)
        root.recycle()
        return if (node != null) {
            performTapOnNode(node)
            node.recycle()
            true
        } else false
    }

    fun findAndTapById(id: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeById(root, id)
        root.recycle()
        return if (node != null) {
            performTapOnNode(node)
            node.recycle()
            true
        } else false
    }

    fun tapAt(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipeUp(): Boolean {
        val root = rootInActiveWindow ?: return false
        val rect = Rect()
        root.getBoundsInScreen(rect)
        root.recycle()
        val startX = rect.centerX()
        val startY = (rect.bottom * 0.7).toInt()
        val endY = (rect.bottom * 0.3).toInt()
        val path = Path().apply { moveTo(startX.toFloat(), startY.toFloat()); lineTo(startX.toFloat(), endY.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipeDown(): Boolean {
        val root = rootInActiveWindow ?: return false
        val rect = Rect()
        root.getBoundsInScreen(rect)
        root.recycle()
        val startX = rect.centerX()
        val startY = (rect.bottom * 0.3).toInt()
        val endY = (rect.bottom * 0.7).toInt()
        val path = Path().apply { moveTo(startX.toFloat(), startY.toFloat()); lineTo(startX.toFloat(), endY.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun typeText(text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false.also { root.recycle() }
        val result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle()
        root.recycle()
        return result
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun getForegroundPackage(): String? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString()
        root.recycle()
        return pkg
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) {
            if (node.isClickable) return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun findNodeById(node: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        if (node.viewIdResourceName?.contains(id) == true && node.isClickable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeById(child, id)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun performTapOnNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val parent = node.parent ?: return false
        val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        parent.recycle()
        return result
    }
}
```

Accessibility service config XML:
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_service_description" />
```

Manifest additions (inside `<application>`):
```xml
        <service
            android:name=".accessibility.AgentAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>
```

Add string: `<string name="accessibility_service_description">Allows PhoneAgent to read screen content and perform gestures for AI-driven assistance</string>`

### Task A2: Create accessibility tools

**Files:**
- Create: `app/src/main/java/com/phoneagent/agent/tools/AccessibilityReadTreeTool.kt`
- Create: `app/src/main/java/com/phoneagent/agent/tools/AccessibilityTapTool.kt`
- Create: `app/src/main/java/com/phoneagent/agent/tools/AccessibilitySwipeTool.kt`
- Create: `app/src/main/java/com/phoneagent/agent/tools/AccessibilityTypeTool.kt`
- Create: `app/src/main/java/com/phoneagent/agent/tools/AccessibilityNavTool.kt`
- Create: `app/src/main/java/com/phoneagent/agent/tools/AccessibilityForegroundTool.kt`

Each tool delegates to `AgentAccessibilityService.instance`. If instance is null, return error "Accessibility service not running."

**AccessibilityReadTreeTool** — `accessibility.read_tree`: returns UI tree as text
**AccessibilityTapTool** — `accessibility.tap_text` and `accessibility.tap_at`: tap by text match or coordinates
**AccessibilitySwipeTool** — `accessibility.swipe`: swipe up/down
**AccessibilityTypeTool** — `accessibility.type`: type text into focused field
**AccessibilityNavTool** — `accessibility.back`, `accessibility.home`: global navigation
**AccessibilityForegroundTool** — `accessibility.foreground_app`: get currently open app package name

Create these as concise Tool implementations following existing patterns (Tool + DescribableTool, JSON result envelopes).

---

## Phase B: Vision Feedback Loop

### Task B1: Integrate screenshots into agent messages

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt`
- Modify: `app/src/main/java/com/phoneagent/providers/BaseOpenAiProvider.kt`

The agent loop should send a screenshot with every request to the model (vision-capable models). Modify `executeSteps` to:
1. Before building `loopMessage`, attempt a screenshot via `ScreenCaptureManager`
2. If screenshot succeeds, encode to base64 and append as vision content block
3. Update `buildRequestBody` to support vision content (array of content blocks instead of plain string)

In `BaseOpenAiProvider.buildRequestBody`, change the message content construction:
```kotlin
// When vision payload is present, use content array format
if (request.visionPayload != null) {
    messages.put(JSONObject().apply {
        put("role", "user")
        put("content", JSONArray(request.visionPayload))
    })
} else {
    messages.put(JSONObject().apply {
        put("role", "user")
        put("content", request.message)
    })
}
```

Update `AgentRequest` to accept an optional `visionPayload: String? = null` (the JSONArray string from VisionPayloadBuilder).

### Task B2: Fix VisionPayloadBuilder to build proper content array

**Files:**
- Modify: `app/src/main/java/com/phoneagent/perception/VisionPayloadBuilder.kt`

The `buildVisionPayload` method should return the FULL content array for the model, including both the text instruction and the image. This is what the LLM API expects.

Update the method to construct:
```json
[
  {"type": "text", "text": "<user instruction>"},
  {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}}
]
```

---

## Phase C: New Phone Tools

### Task C1: Clipboard tool

**Files:**
- Create: `app/src/main/java/com/phoneagent/agent/tools/PhoneClipboardTool.kt`

Read/write system clipboard via `ClipboardManager`. Two actions: `read` and `write` (write returns the text, write takes `text` arg).

### Task C2: Phone action tools (SMS + Call)

**Files:**
- Create: `app/src/main/java/com/phoneagent/agent/tools/PhoneSendSmsTool.kt`
- Create: `app/src/main/java/com/phoneagent/agent/tools/PhoneCallTool.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add SEND_SMS, CALL_PHONE permissions - marked as dangerous, only granted on Android < 6 or with runtime permission)

SMS tool: `phone.send_sms` — args: `phone_number`, `message`. Uses `SmsManager.getDefault().sendTextMessage()`.
Call tool: `phone.call` — args: `phone_number`. Uses `Intent.ACTION_CALL` with `tel:` URI. Marked as HIGH risk in SafetyGate.

### Task C3: Settings toggle tools

**Files:**
- Create: `app/src/main/java/com/phoneagent/agent/tools/PhoneSettingsTool.kt`

Toggle WiFi, Bluetooth, flashlight. Uses `WifiManager`, `BluetoothAdapter`, `CameraManager` (flashlight). These need no additional permissions (WiFi needs `CHANGE_WIFI_STATE` which is normal permission).

---

## Phase D: Background Execution

### Task D1: Move agent loops to OverlayService

**Files:**
- Modify: `app/src/main/java/com/phoneagent/overlay/OverlayService.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt`
- Modify: `app/src/main/java/com/phoneagent/ui/ChatScreen.kt`
- Modify: `app/src/main/java/com/phoneagent/MainActivity.kt`
- Modify: `app/src/main/java/com/phoneagent/PhoneAgentApplication.kt`

**Key changes:**
1. `AgentController` no longer owns a `CoroutineScope` — it gets scoped to the Service lifecycle
2. `OverlayService` creates and owns the `AgentController` (not `PhoneAgentApplication`)
3. `MainActivity`/`ChatScreen` obtain `AgentController` via `OverlayService` binder instead of `PhoneAgentApplication`
4. `PhoneAgentApplication` drops `agentController` property — it's now service-owned
5. When the overlay service is running, agent tasks continue even when activity is backgrounded
6. ChatScreen binds to `OverlayService` to get the shared `AgentController`

This is the riskiest change — need to handle binder lifecycle and ensure the agent controller is created once and survives activity recreation.

**Simpler approach (lower risk):** Keep AgentController in Application but make the coroutine scope tied to the Application lifecycle (which it already is, via `PhoneAgentApplication`). The OverlayService already has access to it. The agent loop already runs in a coroutine that outlives the activity. The key missing piece is that when the activity is destroyed, the overlay chat window is destroyed too. Fix: OverlayService's ChatOverlayController already handles this — it shows a chat overlay independently.

Actually, the current architecture already partially supports backgrounding: OverlayService creates ChatOverlayController instances that observe `agentController.uiState`. The agent loop runs in Application-scoped coroutines. The only gap is: when the floating bubble is tapped and the chat overlay is showing, entering text and sending works. But if the activity (MainActivity/ChatScreen) is used instead of the overlay, the agent dies with the activity.

To fix this minimally: ensure `AgentController`'s scope is properly scoped (already Application-scoped), and ensure the overlay ChatOverlayController is the primary chat interface (the Compose ChatScreen is secondary).

Add a `START_STICKY` behavior that reconnects the overlay on service restart.

---

## Phase E: Integration & SafetyGate Updates

### Task E1: Register all new tools in AgentController

Wire everything: all accessibility tools, clipboard, SMS, call, settings. Add new tools to `toolRegistry.registerAll`.

### Task E2: Update SafetyGate for new tools

Add accessibility tools to `lowRiskTools` (read_tree, foreground_app). Add tap/swipe to `mediumRiskTools`. Add SMS and call to `highRiskTools` (always require confirmation).

### Task E3: Update AndroidManifest with all new permissions

Add: `BIND_ACCESSIBILITY_SERVICE`, `SEND_SMS`, `CALL_PHONE`, `READ_CLIPBOARD` (API 29+).

---

## Phase F: Build & Test

Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` after each phase.
