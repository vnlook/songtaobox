package com.vnlook.tvsongtao.utils

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vnlook.tvsongtao.DigitalClockActivity
import com.vnlook.tvsongtao.repository.ChangelogRepository
import com.vnlook.tvsongtao.repository.ChangelogRepositoryImpl
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
        Log.d(TAG, "Starting changelog checker with interval: $CHECK_INTERVAL_MS ms")
        stopChecking() // Stop any existing checker first
        handler.post(checkRunnable)
    }
    
    /**
     * Stop periodic changelog checks
     */
    fun stopChecking() {
        Log.d(TAG, "Stopping changelog checker")
        handler.removeCallbacks(checkRunnable)
    }
    
    /**
     * Check for changes in the changelog
     */
    private fun checkForChanges() {
        Log.d(TAG, "Checking for changelog changes...")
        
        coroutineScope.launch {
            try {
                val hasChanges = changelogUtil.checkChange()
                
                if (hasChanges) {
                    Log.d(TAG, "Changes detected in changelog, reloading playlists")
                    reloadPlaylists()
                } else {
                    Log.d(TAG, "No changes detected in changelog")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for changelog changes: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Reload playlists when changes are detected
     */
    private fun reloadPlaylists() {
        try {
            Log.d(TAG, "Reloading playlists")
            
            // Create an intent to start the DigitalClockActivity
            val intent = Intent(context, DigitalClockActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            
            // Start the activity
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading playlists: ${e.message}")
            e.printStackTrace()
        }
    }
}
