package com.example.pawsomepals.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.pawsomepals.MainActivity
import com.example.pawsomepals.R
import com.example.pawsomepals.notification.NotificationChannelManager

class NotificationBuilder(private val context: Context) {

    fun buildPlaydateNotification(
        title: String,
        content: String,
        playdateId: Int
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("destination", "playdate_details")
            putExtra("playdateId", playdateId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_PLAYDATES)
            .setSmallIcon(R.drawable.ic_notification_playdate)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
    }

    fun buildMessageNotification(
        title: String,
        content: String,
        chatId: String
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("destination", "chat")
            putExtra("chatId", chatId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification_message)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
    }

    fun buildUpdateNotification(
        title: String,
        content: String
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("destination", "app_updates")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification_update)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
    }
}