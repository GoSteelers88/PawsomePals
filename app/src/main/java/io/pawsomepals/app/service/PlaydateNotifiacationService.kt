package io.pawsomepals.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.pawsomepals.app.MainActivity
import io.pawsomepals.app.R

class PlaydateNotificationService(private val context: Context) {

    private val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playdate Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for playdate requests and updates"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showPlaydateRequestNotification(requestId: Int, requesterName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("destination", "playdate_requests")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("New Playdate Request")
            .setContentText("$requesterName wants to schedule a playdate!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(requestId, notification)
    }

    fun showPlaydateConfirmedNotification(playdateId: Int, partnerName: String, dateTime: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("destination", "playdate_details")
            putExtra("playdateId", playdateId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Playdate Confirmed")
            .setContentText("Your playdate with $partnerName on $dateTime is confirmed!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(playdateId, notification)
    }

    fun showPlaydateCancelledNotification(playdateId: Int, partnerName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Playdate Cancelled")
            .setContentText("Your playdate with $partnerName has been cancelled.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(playdateId, notification)
    }

    fun showPlaydateReminderNotification(playdateId: Int, partnerName: String, dateTime: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("destination", "playdate_details")
            putExtra("playdateId", playdateId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Playdate Reminder")
            .setContentText("Don't forget your playdate with $partnerName at $dateTime!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(playdateId, notification)
    }

    companion object {
        private const val CHANNEL_ID = "playdate_notifications"
    }
}