package com.example.pawsomepals.data.repository

import com.example.pawsomepals.data.model.Notification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class NotificationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    suspend fun getNotifications(): List<Notification> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()

        return@withContext firestore.collection("notifications")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .await()
            .toObjects(Notification::class.java)
    }

    suspend fun markAsRead(notificationId: String) {
        val userId = getCurrentUserId() ?: return
        firestore.collection("notifications")
            .document(notificationId)
            .get()
            .await()
            .let { doc ->
                if (doc.getString("userId") == userId) {
                    doc.reference.update("read", true).await()
                }
            }
    }

    suspend fun addNotification(notification: Notification) {
        val userId = getCurrentUserId() ?: return
        firestore.collection("notifications")
            .add(notification.copy(userId = userId))
            .await()
    }

    suspend fun deleteNotification(notificationId: String) {
        val userId = getCurrentUserId() ?: return
        firestore.collection("notifications")
            .document(notificationId)
            .get()
            .await()
            .let { doc ->
                if (doc.getString("userId") == userId) {
                    doc.reference.delete().await()
                }
            }
    }

    suspend fun getUnreadNotificationsCount(): Int {
        val userId = getCurrentUserId() ?: return 0
        return firestore.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("read", false)
            .get()
            .await()
            .size()
    }
}