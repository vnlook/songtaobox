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
import com.vnlook.tvsongtao.repository.DeviceRepository
import com.vnlook.tvsongtao.repository.DeviceRepositoryImpl
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
         */
        fun schedule(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            
            val jobInfo = JobInfo.Builder(JOB_ID, ComponentName(context, ChangelogSchedulerJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(15 * 60 * 1000L) // 15 minutes
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build()
            
            val result = jobScheduler.schedule(jobInfo)
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d(TAG, "✅ Changelog job scheduled successfully (every 15 minutes)")
            } else {
                Log.e(TAG, "❌ Failed to schedule changelog job")
            }
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
                    Log.d(TAG, "📝 Changelog updates detected, refreshing data in background (no activity reload)")
                    
                    // Only refresh VideoDownloadManager in background, don't restart activities
                    try {
                        val videoDownloadManager = VideoDownloadManager(applicationContext)
                        videoDownloadManager.initializeVideoDownloadWithNetworkCheck(null)
                        Log.d(TAG, "✅ Background data refresh initiated from scheduler")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error refreshing data in scheduler: ${e.message}")
                    }
                    
                } else {
                    Log.d(TAG, "📭 No changelog updates detected")
                }
                
                // Also register device info if network is available
                try {
                    Log.d(TAG, "📱 Updating device info in background...")
                    val deviceRepository = DeviceRepositoryImpl(applicationContext)
                    val deviceInfoUtil = DeviceInfoUtil(applicationContext, deviceRepository)
                    val result = deviceInfoUtil.registerOrUpdateDevice()
                    
                    if (result != null) {
                        Log.d(TAG, "✅ Device registration successful in scheduler")
                    } else {
                        Log.d(TAG, "📭 Device registration returned null in scheduler")
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    Log.w(TAG, "⏱️ Device registration timeout in scheduler")
                } catch (e: java.net.ConnectException) {
                    Log.w(TAG, "🔌 Device registration connection failed in scheduler")
                } catch (e: java.io.IOException) {
                    Log.w(TAG, "🌐 Device registration network error in scheduler: ${e.message}")
                } catch (e: Exception) {
                    Log.w(TAG, "❌ Device registration failed in scheduler: ${e.message}")
                }
                
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
            Log.d(TAG, "🚫 DISABLED playlist reload to prevent continuous activity restarts")
            
            // DISABLED: Create an intent to start the DigitalClockActivity
            // DISABLED: val intent = Intent(applicationContext, DigitalClockActivity::class.java)
            // DISABLED: intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            
            // DISABLED: Start the activity
            // DISABLED: applicationContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading playlists: ${e.message}")
            e.printStackTrace()
        }
    }
}
