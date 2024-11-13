// NotificationBuilder.kt
package io.pawsomepals.app.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.pawsomepals.app.MainActivity
import io.pawsomepals.app.R

class NotificationBuilder(private val context: Context) {

    fun buildPlaydateNotification(
        title: String,
        content: String,
        playdateId: Any  // Change to Any to handle both Int and String
    ): NotificationCompat.Builder {
        val notificationId = when (playdateId) {
            is Int -> playdateId
            is String -> playdateId.hashCode()
            else -> throw IllegalArgumentException("playdateId must be Int or String")
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("destination", "playdate_details")
            putExtra("playdateId", playdateId.toString())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
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

    fun buildMatchNotification(
        title: String,
        content: String,
        userId: String,
        matchId: String
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID_MATCHES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
        // Add any additional notification configuration
    }
    companion object {
        const val CHANNEL_ID_MATCHES = "matches_channel"
        // ... other channel IDs
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
            chatId.hashCode(),
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