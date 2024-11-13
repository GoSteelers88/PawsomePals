package io.pawsomepals.app.data

import android.content.Context
import androidx.room.Room
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DatabaseBackupHelper {
    private const val DATABASE_NAME = "pawsome_pals_database"

    fun backupDatabase(context: Context): Boolean {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        val backupDir = File(context.getExternalFilesDir(null), "database_backups")

        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(backupDir, "${DATABASE_NAME}_backup_$timestamp.db")

        return try {
            copyDatabase(dbFile, backupFile)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun copyDatabase(sourceFile: File, destFile: File) {
        if (!sourceFile.exists()) {
            throw IllegalStateException("Source database does not exist")
        }

        sourceFile.inputStream().use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun restoreDatabase(context: Context, backupFile: File): Boolean {
        val dbFile = context.getDatabasePath(DATABASE_NAME)

        return try {
            // Close the database before restoration
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .build()
                .close()

            copyDatabase(backupFile, dbFile)

            // Reopen the database
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .build()
                .openHelper
                .writableDatabase
                .close()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getBackupFiles(context: Context): List<File> {
        val backupDir = File(context.getExternalFilesDir(null), "database_backups")
        return backupDir.listFiles { file ->
            file.name.startsWith("${DATABASE_NAME}_backup_") && file.name.endsWith(".db")
        }?.sortedDescending() ?: emptyList()
    }
}