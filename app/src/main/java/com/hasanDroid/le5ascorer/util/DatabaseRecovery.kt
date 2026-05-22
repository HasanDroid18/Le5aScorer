package com.hasanDroid.le5ascorer.util

import android.content.Context
import java.io.File

/**
 * Utility for recovering from database schema mismatches and corruption issues.
 * Handles comprehensive cleanup of all database files and related artifacts.
 */
object DatabaseRecovery {
    private const val TAG = "DatabaseRecovery"
    private const val DB_NAME = "leekha_database"

    /**
     * Comprehensively delete all database files and related artifacts.
     * This includes the main database file, WAL (Write-Ahead Log), and shared memory files.
     *
     * @return true if deletion was successful, false otherwise
     */
    fun deleteCorruptedDatabase(context: Context): Boolean {
        return try {
            android.util.Log.i(TAG, "Attempting comprehensive database cleanup")

            var allDeleted = true

            // Delete main database file
            val dbFile = context.getDatabasePath(DB_NAME)
            if (dbFile.exists()) {
                allDeleted = dbFile.delete() && allDeleted
                android.util.Log.d(TAG, "Main database file deleted: ${dbFile.delete()}")
            }

            // Delete WAL (Write-Ahead Log) file - used by newer SQLite versions
            val walFile = File(dbFile.absolutePath + "-wal")
            if (walFile.exists()) {
                allDeleted = walFile.delete() && allDeleted
                android.util.Log.d(TAG, "WAL file deleted: ${walFile.delete()}")
            }

            // Delete shared memory file - used by WAL
            val shmFile = File(dbFile.absolutePath + "-shm")
            if (shmFile.exists()) {
                allDeleted = shmFile.delete() && allDeleted
                android.util.Log.d(TAG, "Shared memory file deleted: ${shmFile.delete()}")
            }

            // Also try via Context.deleteDatabase as fallback
            val contextDelete = context.deleteDatabase(DB_NAME)
            android.util.Log.d(TAG, "Context.deleteDatabase result: $contextDelete")

            if (allDeleted) {
                android.util.Log.i(TAG, "Database cleanup completed successfully")
            } else {
                android.util.Log.w(TAG, "Some database files could not be deleted")
            }

            allDeleted
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error during database cleanup", e)
            false
        }
    }

    /**
     * Check if the database file exists and is accessible.
     */
    @Suppress("UNUSED")
    fun databaseExists(context: Context): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            dbFile.exists()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error checking database existence", e)
            false
        }
    }

    /**
     * Get diagnostic information about the database state.
     */
    fun getDatabaseDiagnostics(context: Context): String {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            val walFile = File(dbFile.absolutePath + "-wal")
            val shmFile = File(dbFile.absolutePath + "-shm")

            val sb = StringBuilder()
            sb.append("Database Diagnostics:\n")
            sb.append("Main DB exists: ${dbFile.exists()}\n")
            sb.append("WAL exists: ${walFile.exists()}\n")
            sb.append("SHM exists: ${shmFile.exists()}\n")
            if (dbFile.exists()) {
                sb.append("Main DB size: ${dbFile.length()} bytes\n")
            }
            sb.toString()
        } catch (e: Exception) {
            "Error getting diagnostics: ${e.message}"
        }
    }
}

