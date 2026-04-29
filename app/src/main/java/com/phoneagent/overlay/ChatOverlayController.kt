package com.phoneagent.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ChatOverlayController(
    private val context: Context,
    private val viewModel: com.phoneagent.agent.AgentController
) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var chatView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun show() {
        if (chatView != null) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            dimAmount = 0.4f
            y = 24
        }

        params = layoutParams

        val composeView = ComposeView(context).apply {
            setContent {
                com.phoneagent.ui.theme.PhoneAgentTheme {
                    OverlayChatContent(
                        agentController = viewModel,
                        onMinimize = { hide() },
                        onClose = { hide() }
                    )
                }
            }
        }

        chatView = composeView
        windowManager.addView(composeView, layoutParams)
    }

    fun hide() {
        chatView?.let {
            runCatching { windowManager.removeView(it) }
            chatView = null
        }
    }

    fun isShowing(): Boolean = chatView != null

    fun destroy() {
        hide()
        scope.cancel()
    }
}