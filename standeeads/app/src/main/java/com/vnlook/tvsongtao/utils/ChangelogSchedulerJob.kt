package com.vnlook.tvsongtao.utils

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.vnlook.tvsongtao.DigitalClockActivity
import com.vnlook.tvsongtao.repository.ChangelogRepository
import com.vnlook.tvsongtao.repository.ChangelogRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Job scheduler for checking changelog updates periodically
 */
class ChangelogSchedulerJob : JobService() {
    private val TAG = "ChangelogSchedulerJob"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        private const val JOB_ID = 1001
        private const val TAG = "ChangelogSchedulerJob"
//        private const val INTERVAL_MS = 60 * 60 * 1000L // 1 hour
        private const val INTERVAL_MS = 15 * 60 * 1000L // 1 hour

        /**
         * Schedule the changelog job to run every 15 minutes
         * DISABLED: ChangelogTimerManager singleton now handles changelog checking
         */
        fun schedule(context: Context) {
            Log.i(TAG, "📋 ChangelogSchedulerJob.schedule() called but DISABLED")
            Log.i(TAG, "📋 Using ChangelogTimerManager singleton instead for changelog checking")
            // No-op: Global timer in ChangelogTimerManager handles this now
        }
        
        /**
         * Cancel the scheduled job
         * @param context Application context
         */
        fun cancel(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            Log.d("ChangelogSchedulerJob", "Job cancelled")
        }
    }
    
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "🔄 Changelog scheduler job started (every 15 minutes)")
        
        // Check network connectivity first
        if (!NetworkUtil.isNetworkAvailable(applicationContext)) {
            Log.d(TAG, "🚫 No network available, skipping changelog job")
            jobFinished(params, false)
            return false
        }
        
        Log.d(TAG, "📡 Network available, checking for changelog updates in background thread...")
        
        // Run the job in background thread using coroutines (better than Thread)
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Running changelog job in background IO thread")
                
                val changelogRepository = ChangelogRepositoryImpl(applicationContext)
                val changelogUtil = ChangelogUtil(applicationContext, changelogRepository)
                
                // Check if there are changes with timeout protection
                val hasChanges = try {
                    changelogUtil.checkChange()
                } catch (e: java.net.SocketTimeoutException) {
                    Log.w(TAG, "⏱️ Changelog check timeout in scheduler - skipping")
                    jobFinished(params, false)
                    return@launch
                } catch (e: java.net.ConnectException) {
                    Log.w(TAG, "🔌 Changelog check connection failed in scheduler - skipping")
                    jobFinished(params, false)
                    return@launch
                } catch (e: java.io.IOException) {
                    Log.w(TAG, "🌐 Changelog check network error in scheduler - skipping: ${e.message}")
                    jobFinished(params, false)
                    return@launch
                } catch (e: Exception) {
                    Log.w(TAG, "❌ Changelog check error in scheduler - skipping: ${e.message}")
                    jobFinished(params, false)
                    return@launch
                }
                
                if (hasChanges) {
                    Log.d(TAG, "📝 Changelog updates detected, restarting DigitalClockActivity")
                    
                    // Restart the DigitalClockActivity to reload everything
                    reloadPlaylists()
                    
                } else {
                    Log.d(TAG, "📭 No changelog updates detected")
                }
                
                // Note: Device info update is now handled inside checkChange() method
                
            } catch (e: Exception) {
                Log.e(TAG, "💥 Unexpected error in changelog job: ${e.message}")
                e.printStackTrace()
            } finally {
                // Job finished
                Log.d(TAG, "✅ Changelog scheduler job completed")
                jobFinished(params, false)
            }
        }
        
        return true // Job is running asynchronously
    }
    
    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Job stopped")
        return false // Don't reschedule if stopped
    }
    
    /**
     * Reload playlists when changes are detected
     * This will restart the DigitalClockActivity to reload everything
     */
    private fun reloadPlaylists() {
        try {
            Log.d(TAG, "🔄 Restarting DigitalClockActivity due to changelog changes")
            
            // Create an intent to start the DigitalClockActivity
            val intent = Intent(applicationContext, DigitalClockActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            
            // Start the activity
            applicationContext.startActivity(intent)
            Log.d(TAG, "✅ DigitalClockActivity restart initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading playlists: ${e.message}")
            e.printStackTrace()
        }
    }
}
