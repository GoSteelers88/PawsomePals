package com.example.pawsomepals.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

class NotificationChannelManager(private val context: Context) {
    companion object {
        const val CHANNEL_PLAYDATES = "playdates"
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_UPDATES = "updates"
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(CHANNEL_PLAYDATES, "Playdates", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_UPDATES, "App Updates", NotificationManager.IMPORTANCE_LOW)
            )
            val notificationManager = NotificationManagerCompat.from(context)
            channels.forEach { channel ->
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}