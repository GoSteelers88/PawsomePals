package io.pawsomepals.app.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat

class NotificationManager(private val context: Context) {

    private val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(context)
    private val channelManager: NotificationChannelManager = NotificationChannelManager(context)
    private val notificationBuilder: NotificationBuilder = NotificationBuilder(context)

    init {
        channelManager.createNotificationChannels()
    }

    fun showPlaydateNotification(title: String, content: String, playdateId: Int) {
        val notification = notificationBuilder.buildPlaydateNotification(title, content, playdateId)
        notificationManager.notify(playdateId, notification.build())
    }

    fun showMessageNotification(title: String, content: String, chatId: String) {
        val notification = notificationBuilder.buildMessageNotification(title, content, chatId)
        notificationManager.notify(chatId.hashCode(), notification.build())
    }

    fun showUpdateNotification(title: String, content: String) {
        val notification = notificationBuilder.buildUpdateNotification(title, content)
        notificationManager.notify(NOTIFICATION_ID_UPDATE, notification.build())
    }

    fun sendMatchNotification(
        userId: String,
        title: String,
        message: String,
        data: Map<String, String>
    ) {
        val notification = notificationBuilder.buildMatchNotification(
            title = title,
            content = message,
            userId = userId,
            matchId = data["matchId"] ?: return
        )
        notificationManager.notify(userId.hashCode(), notification.build())
    }

    fun showPlaydateRequestNotification(requestId: String, requesterName: String) {
        val title = "New Playdate Request"
        val content = "$requesterName has sent you a playdate request!"
        val notification = notificationBuilder.buildPlaydateNotification(
            title = title,
            content = content,
            playdateId = requestId.hashCode()  // Convert String to Int using hashCode
        )
        notificationManager.notify(requestId.hashCode(), notification.build())
    }
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    companion object {
        private const val NOTIFICATION_ID_UPDATE = 1001
    }
}