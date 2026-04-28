package com.phoneagent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.phoneagent.agent.AgentController

class PhoneAgentApplication : Application() {

    lateinit var agentController: AgentController
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        agentController = AgentController(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "phoneagent_overlay_channel"

        fun agentController(context: Context): AgentController {
            return (context.applicationContext as PhoneAgentApplication).agentController
        }
    }
}
