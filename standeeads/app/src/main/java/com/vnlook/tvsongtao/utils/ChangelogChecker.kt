package com.vnlook.tvsongtao.utils

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vnlook.tvsongtao.DigitalClockActivity
import com.vnlook.tvsongtao.repository.ChangelogRepository
import com.vnlook.tvsongtao.repository.ChangelogRepositoryImpl
import com.vnlook.tvsongtao.repository.DeviceRepository
import com.vnlook.tvsongtao.repository.DeviceRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A utility class for checking changelog updates using a Handler
 * This is useful for testing with shorter intervals than JobScheduler allows
 */
class ChangelogChecker(private val context: Context) {
    private val TAG = "ChangelogChecker"
    private val handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val changelogRepository: ChangelogRepository = ChangelogRepositoryImpl(context)
    private val changelogUtil = ChangelogUtil(context, changelogRepository)
    private val deviceRepository: DeviceRepository = DeviceRepositoryImpl(context)
    private val deviceInfoUtil = DeviceInfoUtil(context, deviceRepository)
    
    companion object {
        // For testing, check every 15 seconds
        private const val CHECK_INTERVAL_MS = 30 * 60 * 1000L
        
        // Singleton instance
        @Volatile
        private var INSTANCE: ChangelogChecker? = null
        
        fun getInstance(context: Context): ChangelogChecker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChangelogChecker(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val checkRunnable = object : Runnable {
        override fun run() {
            checkForChanges()
            // Schedule next check
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }
    
    /**
     * Start periodic changelog checks
     */
    fun startChecking() {
        Log.d(TAG, "🚫 ChangelogChecker DISABLED to prevent continuous loops")
        // DISABLED: stopChecking() // Stop any existing checker first
        // DISABLED: handler.post(checkRunnable)
    }
    
    /**
     * Stop periodic changelog checks
     */
    fun stopChecking() {
        Log.d(TAG, "Stopping changelog checker")
        handler.removeCallbacks(checkRunnable)
    }
    
    /**
     * Check for changes in the changelog and update device info
     */
    private fun checkForChanges() {
        Log.d(TAG, "Starting changelog check with network verification...")
        
        // Check network connectivity first
        if (!NetworkUtil.isNetworkAvailable(context)) {
            Log.d(TAG, "🚫 No network available, skipping changelog check")
            return
        }
        
        Log.d(TAG, "📡 Network available, proceeding with changelog check in background thread")
        
        // Run everything in background thread (IO dispatcher)
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Update device info with timeout handling
                try {
                    Log.d(TAG, "📱 Updating device info with timeout protection...")
                    val deviceInfoResult = deviceInfoUtil.registerOrUpdateDevice()
                    Log.d(TAG, "✅ Device info update result: $deviceInfoResult")
                } catch (e: java.net.SocketTimeoutException) {
                    Log.w(TAG, "⏱️ Device info update timeout - skipping this cycle")
                    return@launch
                } catch (e: java.net.ConnectException) {
                    Log.w(TAG, "🔌 Device info update connection failed - skipping this cycle")
                    return@launch
                } catch (e: java.io.IOException) {
                    Log.w(TAG, "🌐 Device info update network error - skipping this cycle: ${e.message}")
                    return@launch
                } catch (e: Exception) {
                    Log.w(TAG, "❌ Device info update error - skipping this cycle: ${e.message}")
                    return@launch
                }
                
                // Check for changelog changes with timeout handling
                try {
                    Log.d(TAG, "📝 Checking changelog with timeout protection...")
                val hasChanges = changelogUtil.checkChange()
                
                if (hasChanges) {
                        Log.d(TAG, "🔄 Changes detected in changelog, refreshing data in background")
                        // DON'T reload activity, just refresh data in background
                        refreshDataInBackground()
                } else {
                        Log.d(TAG, "📭 No changes detected in changelog")
                }
                } catch (e: java.net.SocketTimeoutException) {
                    Log.w(TAG, "⏱️ Changelog check timeout - skipping this cycle")
                    return@launch
                } catch (e: java.net.ConnectException) {
                    Log.w(TAG, "🔌 Changelog check connection failed - skipping this cycle")
                    return@launch
                } catch (e: java.io.IOException) {
                    Log.w(TAG, "🌐 Changelog check network error - skipping this cycle: ${e.message}")
                    return@launch
                } catch (e: Exception) {
                    Log.w(TAG, "❌ Changelog check error - skipping this cycle: ${e.message}")
                    return@launch
                }
                
            } catch (e: Exception) {
                Log.w(TAG, "💥 Unexpected error in changelog check - skipping this cycle: ${e.message}")
            }
        }
    }
    
    /**
     * Refresh data in background without reloading activity
     * This prevents screen flashing and UI interruption
     */
    private fun refreshDataInBackground() {
        try {
            Log.d(TAG, "🔄 Refreshing playlists data in background (no activity reload)")
            
            // Just refresh the VideoDownloadManager data in background
            val videoDownloadManager = VideoDownloadManager(context)
            videoDownloadManager.initializeVideoDownloadWithNetworkCheck(null)
            
            Log.d(TAG, "✅ Background data refresh initiated")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error refreshing data in background: ${e.message}")
            e.printStackTrace()
        }
    }
}
