package com.phoneagent.perception

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PhoneAgentNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        private var recentNotifications: List<NotificationEntry> = emptyList()
        private const val MAX_STORED = 50
        private val lock = Any()

        fun getRecent(count: Int = 10): List<NotificationEntry> {
            return synchronized(lock) { recentNotifications.takeLast(count) }
        }

        fun clear() {
            synchronized(lock) { recentNotifications = emptyList() }
        }

        private fun addEntry(entry: NotificationEntry) {
            synchronized(lock) {
                recentNotifications = recentNotifications + entry
                if (recentNotifications.size > MAX_STORED) {
                    recentNotifications = recentNotifications.drop(1)
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val entry = NotificationEntry(
            packageName = sbn.packageName,
            title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: "",
            text = extras.getString(android.app.Notification.EXTRA_TEXT) ?: "",
            appName = extras.getString("android.appName") ?: sbn.packageName,
            timestamp = sbn.postTime
        )
        addEntry(entry)
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
