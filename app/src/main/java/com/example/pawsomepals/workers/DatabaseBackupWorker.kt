package com.example.pawsomepals.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pawsomepals.data.DatabaseBackupHelper

class DatabaseBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            DatabaseBackupHelper.backupDatabase(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}