package com.hasanDroid.le5ascorer

import android.app.Application
import com.hasanDroid.le5ascorer.util.VersionManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class Le5aApplication : Application() {

    @Inject
    lateinit var versionManager: VersionManager

    override fun onCreate() {
        super.onCreate()

        // Initialize version tracking and handle app updates
        // Run in background to avoid blocking app startup
        CoroutineScope(Dispatchers.Default).launch {
            versionManager.initializeVersionTracking()
        }
    }
}
