package com.phoneagent.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.phoneagent.ui.theme.*

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
            160,
            160,
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

        val composeView = ComposeView(context).apply {
            setContent {
                PhoneAgentTheme {
                    BubbleContent()
                }
            }
        }

        composeView.setOnTouchListener { _, event ->
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
                    windowManager.updateViewLayout(composeView, layoutParams)
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

        bubbleView = composeView
        windowManager.addView(composeView, layoutParams)
    }

    fun hide() {
        bubbleView?.let {
            runCatching { windowManager.removeView(it) }
            bubbleView = null
        }
    }

    fun isShowing(): Boolean = bubbleView != null
}

@Composable
private fun BubbleContent() {
    val gradient = Brush.radialGradient(
        listOf(Primary.copy(alpha = 0.9f), Secondary.copy(alpha = 0.6f))
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "PA",
            style = MaterialTheme.typography.titleMedium,
            color = OnBackground,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}