package com.hasanDroid.le5ascorer.util

import android.content.Context
import android.content.pm.PackageManager
import com.hasanDroid.le5ascorer.data.local.LeekhaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages app version tracking and handles data persistence/migration on app updates.
 *
 * This class ensures that:
 * 1. Version upgrades are detected
 * 2. Data integrity is validated after updates
 * 3. Necessary data cleanup/migration occurs between versions
 */
@Singleton
class VersionManager @Inject constructor(
    private val context: Context,
    private val database: LeekhaDatabase
) {
    companion object {
        private const val PREFS_NAME = "app_version_prefs"
        private const val KEY_LAST_VERSION_CODE = "last_version_code"
        private const val KEY_LAST_VERSION_NAME = "last_version_name"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_LAST_LAUNCH_TIME = "last_launch_time"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Initialize and check for app updates on first launch.
     * This is called from Le5aApplication.onCreate()
     */
    suspend fun initializeVersionTracking() = withContext(Dispatchers.IO) {
        val currentVersionCode = getCurrentVersionCode()
        val currentVersionName = getCurrentVersionName()
        val lastVersionCode = prefs.getInt(KEY_LAST_VERSION_CODE, -1)
        val lastVersionName = prefs.getString(KEY_LAST_VERSION_NAME, "") ?: ""
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

        when {
            // First launch of the app
            isFirstLaunch -> {
                onFirstLaunch(currentVersionCode, currentVersionName)
            }
            // App was updated from an older version
            currentVersionCode > lastVersionCode -> {
                onAppUpdate(lastVersionCode, currentVersionCode, lastVersionName, currentVersionName)
            }
            // Same version - normal launch
            else -> {
                onNormalLaunch(currentVersionCode)
            }
        }

        // Update stored version info
        prefs.edit().apply {
            putInt(KEY_LAST_VERSION_CODE, currentVersionCode)
            putString(KEY_LAST_VERSION_NAME, currentVersionName)
            putBoolean(KEY_FIRST_LAUNCH, false)
            putLong(KEY_LAST_LAUNCH_TIME, System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Called on the first launch of the app (fresh install).
     */
    private suspend fun onFirstLaunch(versionCode: Int, versionName: String) = withContext(Dispatchers.IO) {
        // Log first launch for analytics or debugging
        android.util.Log.i("VersionManager", "First launch detected. App v$versionName (code: $versionCode)")

        // Initialize any first-time-only data here
        // For example, you could set default settings, populate sample data, etc.
    }

    /**
     * Called when the app is updated from a previous version.
     * Handles version-specific migration and data preservation logic.
     */
    private suspend fun onAppUpdate(
        oldVersionCode: Int,
        newVersionCode: Int,
        oldVersionName: String,
        newVersionName: String
    ) = withContext(Dispatchers.IO) {
        android.util.Log.i(
            "VersionManager",
            "App update detected. v$oldVersionName -> v$newVersionName (code: $oldVersionCode -> $newVersionCode)"
        )

        try {
            // Validate database integrity after update
            validateDatabaseIntegrity()

            // Handle version-specific migrations
            when {
                oldVersionCode < 1 && newVersionCode >= 1 -> {
                    // Migration from pre-v1 to v1 (if needed in the future)
                    // Add migration logic here
                }
                // Add more version-specific migrations as the app evolves
                // oldVersionCode < 2 && newVersionCode >= 2 -> { /* migration logic */ }
                else -> {
                    // No migration needed for this version change
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VersionManager", "Error during app update handling", e)
            // Don't crash - allow app to continue but log the error
        }
    }

    /**
     * Called on normal app launch (same version).
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun onNormalLaunch(versionCode: Int) = withContext(Dispatchers.IO) {
        // Periodic data integrity check
        try {
            validateDatabaseIntegrity()
        } catch (e: Exception) {
            android.util.Log.e("VersionManager", "Error during normal launch integrity check", e)
        }
    }

    /**
     * Validates database integrity by checking if tables are accessible and data is readable.
     * This helps detect if the database was corrupted or inaccessible after an update.
     */
    private suspend fun validateDatabaseIntegrity() = withContext(Dispatchers.IO) {
        try {
            // Try to read from each table to ensure database is healthy
            val playerCount = database.playerDao().getAllPlayersSync().size
            val matchCount = database.matchDao().getAllMatchesSync().size

            android.util.Log.d(
                "VersionManager",
                "Database integrity check passed. Players: $playerCount, Matches: $matchCount"
            )
        } catch (e: Exception) {
            android.util.Log.e("VersionManager", "Database integrity check failed", e)
            throw e
        }
    }

    /**
     * Get the current app version code from PackageManager.
     */
    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (_: PackageManager.NameNotFoundException) {
            0
        }
    }

    /**
     * Get the current app version name from PackageManager.
     */
    private fun getCurrentVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (_: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }

    /**
     * Retrieve the last known app version code.
     */
    @Suppress("UNUSED")
    fun getLastVersionCode(): Int {
        return prefs.getInt(KEY_LAST_VERSION_CODE, -1)
    }

    /**
     * Check if this is the app's first launch.
     */
    @Suppress("UNUSED")
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    /**
     * Get the time of last app launch.
     */
    @Suppress("UNUSED")
    fun getLastLaunchTime(): Long {
        return prefs.getLong(KEY_LAST_LAUNCH_TIME, 0)
    }
}

