package io.pawsomepals.app.ui.components

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.pawsomepals.app.data.dao.ChatDao
import io.pawsomepals.app.data.model.Chat
import io.pawsomepals.app.data.repository.ChatRepository
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

@HiltWorker
class ChatSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val chatRepository: ChatRepository,
    private val chatDao: ChatDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val userId = auth.currentUser?.uid ?: return Result.failure()

            // Fetch chats from Firestore
            val firestoreChats = firestore.collection("chats")
                .whereArrayContains("participants", userId)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Chat::class.java) }

            // Update local database
            firestoreChats.forEach { chat ->
                chatDao.insertChat(chat)
            }

            return Result.success()
        } catch (e: Exception) {
            return if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        fun scheduleSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ChatSyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "chat_sync",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}