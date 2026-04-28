package com.phoneagent.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.phoneagent.R

class FloatingBubbleController(
    private val context: Context,
    private val onBubbleTap: () -> Unit
) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var bubbleView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var touchX = 0f
    private var touchY = 0f

    fun show() {
        if (bubbleView != null) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        params = layoutParams

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_bubble, null)
        val bubble = view.findViewById<FrameLayout>(R.id.bubble_container)

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - touchX).toInt()
                    layoutParams.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(view, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (dx * dx + dy * dy < 100) {
                        onBubbleTap()
                    }
                    true
                }
                else -> false
            }
        }

        bubbleView = view
        windowManager.addView(view, layoutParams)
    }

    fun hide() {
        bubbleView?.let {
            runCatching { windowManager.removeView(it) }
            bubbleView = null
        }
    }

    fun isShowing(): Boolean = bubbleView != null
}
