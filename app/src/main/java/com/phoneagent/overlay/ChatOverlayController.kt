package com.phoneagent.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.phoneagent.MainActivity
import com.phoneagent.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ChatOverlayController(
    private val context: Context,
    private val viewModel: com.phoneagent.agent.AgentController
) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var chatView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var modelSpinner: Spinner? = null
    private var messageInput: EditText? = null
    private var sendButton: Button? = null
    private var chatContent: TextView? = null
    private var loadingIndicator: ProgressBar? = null
    private var errorText: TextView? = null
    private var closeButton: Button? = null
    private var minimizeButton: Button? = null
    private var hasLaunchedMainActivity = false

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
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 0
        }

        params = layoutParams

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_chat, null)
        chatView = view
        windowManager.addView(view, layoutParams)

        bindViews(view)
        observeState()

        viewModel.onVoiceResult = { text ->
            messageInput?.setText(text)
        }
        viewModel.onVoiceError = { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        }

        scope.launch {
            val models = viewModel.getAllAvailableModels()
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, models)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modelSpinner?.adapter = adapter
        }
    }

    private fun bindViews(view: View) {
        modelSpinner = view.findViewById(R.id.model_spinner)
        messageInput = view.findViewById(R.id.message_input)
        sendButton = view.findViewById(R.id.send_button)
        chatContent = view.findViewById(R.id.chat_content)
        loadingIndicator = view.findViewById(R.id.loading_indicator)
        errorText = view.findViewById(R.id.error_text)
        closeButton = view.findViewById(R.id.close_button)
        minimizeButton = view.findViewById(R.id.minimize_button)

        sendButton?.setOnClickListener {
            val message = messageInput?.text?.toString()?.trim()
            if (!message.isNullOrEmpty()) {
                val selectedModel = modelSpinner?.selectedItem?.toString() ?: ""
                if (selectedModel.isNotBlank()) {
                    viewModel.sendMessage(message, selectedModel)
                    messageInput?.text?.clear()
                }
            }
        }

        closeButton?.setOnClickListener {
            hide()
        }

        minimizeButton?.setOnClickListener {
            hide()
        }

        val micButton = view.findViewById<Button>(R.id.mic_button)
        micButton?.setOnClickListener {
            viewModel.startVoiceInput()
        }
    }

    private fun observeState() {
        scope.launch {
            viewModel.uiState.collect { state ->
                updateUI(state)

                if (state.pendingConfirmation != null && !hasLaunchedMainActivity) {
                    hasLaunchedMainActivity = true
                    Toast.makeText(
                        context,
                        "Sensitive action requires approval. Opening PhoneAgent...",
                        Toast.LENGTH_LONG
                    ).show()
                    hide()
                    val intent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } else if (state.pendingConfirmation == null) {
                    hasLaunchedMainActivity = false
                }
            }
        }
    }

    private fun updateUI(state: com.phoneagent.agent.AgentUiState) {
        val messages = buildString {
            state.messages.forEach { msg ->
                appendLine("${msg.role}: ${msg.content}")
                appendLine()
            }
        }
        chatContent?.text = messages

        loadingIndicator?.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        if (state.error != null) {
            errorText?.visibility = View.VISIBLE
            errorText?.text = state.error
        } else if (state.agentStepStatus != null) {
            errorText?.visibility = View.VISIBLE
            errorText?.text = state.agentStepStatus
        } else {
            errorText?.visibility = View.GONE
            errorText?.text = null
        }

        val inputEnabled = !state.isLoading && state.pendingConfirmation == null
        messageInput?.isEnabled = inputEnabled
        sendButton?.isEnabled = inputEnabled

        val scrollView = chatView?.findViewById<ScrollView>(R.id.chat_scroll)
        scrollView?.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
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
