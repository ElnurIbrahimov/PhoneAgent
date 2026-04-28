package com.phoneagent.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.phoneagent.MainActivity
import com.phoneagent.PhoneAgentApplication
import com.phoneagent.R
import com.phoneagent.agent.AgentController

class OverlayService : Service() {

    private val binder = LocalBinder()
    private var bubbleController: FloatingBubbleController? = null
    private var chatController: ChatOverlayController? = null

    val agentController: AgentController by lazy {
        PhoneAgentApplication.agentController(this)
    }

    inner class LocalBinder : Binder() {
        fun getService(): OverlayService = this@OverlayService
    }

    override fun onCreate() {
        super.onCreate()
        bubbleController = FloatingBubbleController(this) {
            toggleChat()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        showBubble()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, PhoneAgentApplication.CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_agent)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showBubble() {
        if (OverlayPermissionManager.canDrawOverlays(this)) {
            bubbleController?.show()
        }
    }

    private fun toggleChat() {
        if (chatController?.isShowing() == true) {
            chatController?.hide()
        } else {
            chatController?.destroy()
            chatController = ChatOverlayController(this, agentController)
            chatController?.show()
        }
    }

    fun showChat() {
        if (chatController?.isShowing() != true) {
            chatController?.destroy()
            chatController = ChatOverlayController(this, agentController)
            chatController?.show()
        }
    }

    fun hideChat() {
        chatController?.hide()
    }

    override fun onDestroy() {
        chatController?.destroy()
        bubbleController?.hide()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
        }
    }
}
