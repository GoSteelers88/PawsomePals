package com.example.pawsomepals.data.repository

import com.example.pawsomepals.data.model.Notification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class NotificationRepository(private val firestore: FirebaseFirestore) {
    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: throw IllegalStateException("User not logged in")

    suspend fun getNotifications(): List<Notification> {
        return firestore.collection("notifications")
            .whereEqualTo("userId", currentUserId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .await()
            .toObjects(Notification::class.java)
    }

    suspend fun markAsRead(notificationId: String) {
        firestore.collection("notifications")
            .document(notificationId)
            .update("read", true)
            .await()
    }

    suspend fun addNotification(notification: Notification) {
        firestore.collection("notifications")
            .add(notification)
            .await()
    }

    suspend fun deleteNotification(notificationId: String) {
        firestore.collection("notifications")
            .document(notificationId)
            .delete()
            .await()
    }

    suspend fun getUnreadNotificationsCount(): Int {
        return firestore.collection("notifications")
            .whereEqualTo("userId", currentUserId)
            .whereEqualTo("read", false)
            .get()
            .await()
            .size()
    }
}