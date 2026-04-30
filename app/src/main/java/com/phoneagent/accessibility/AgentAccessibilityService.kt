package com.phoneagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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

    fun readUITree(maxDepth: Int = 10, maxNodes: Int = 100): String {
        val root = rootInActiveWindow ?: return "No active window."
        val sb = StringBuilder()
        var count = 0
        count = dumpNode(root, sb, 0, maxDepth, count, maxNodes)
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
        if (node != null) {
            performTapOnNode(node)
            if (node !== root) {
                root.recycle()
            }
            return true
        }
        root.recycle()
        return false
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
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        root.recycle()
        if (focused == null) return false
        val result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle()
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
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun performTapOnNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null && !current.isClickable) {
            val parent = current.parent
            if (parent !== current) {
                current.recycle()
                current = parent
            } else {
                current.recycle()
                return false
            }
        }
        return if (current != null) {
            val result = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            current.recycle()
            result
        } else {
            false
        }
    }
}
