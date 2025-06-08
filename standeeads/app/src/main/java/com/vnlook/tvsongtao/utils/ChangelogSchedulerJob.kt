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

/**
 * JobService to periodically check for changes in the changelog API
 */
class ChangelogSchedulerJob : JobService() {
    private val TAG = "ChangelogSchedulerJob"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        private const val JOB_ID = 1001
//        private const val INTERVAL_MS = 60 * 60 * 1000L // 1 hour
        private const val INTERVAL_MS = 15 * 60 * 1000L // 1 hour

        /**
         * Schedule the job to run periodically
         * @param context Application context
         */
        fun schedule(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            
            // Check if the job is already scheduled
            val existingJob = jobScheduler.getPendingJob(JOB_ID)
            if (existingJob != null) {
                Log.d("ChangelogSchedulerJob", "Job already scheduled")
                return
            }
            
            val componentName = ComponentName(context, ChangelogSchedulerJob::class.java)
            
            // For API level 24 and above, there's a minimum period constraint
            val jobInfoBuilder = JobInfo.Builder(JOB_ID, componentName)
                .setPersisted(true) // Job persists across reboots
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // Requires network connection
            
            // For testing with short intervals, use minimum allowed interval
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // For Android 7.0 (API 24) and higher, minimum is 15 minutes
                jobInfoBuilder.setPeriodic(JobInfo.getMinPeriodMillis())
            } else {
                // For older versions, we can use our custom interval
                jobInfoBuilder.setPeriodic(INTERVAL_MS)
            }
            
            val jobInfo = jobInfoBuilder.build()
            
            val resultCode = jobScheduler.schedule(jobInfo)
            if (resultCode == JobScheduler.RESULT_SUCCESS) {
                Log.d("ChangelogSchedulerJob", "Job scheduled successfully")
            } else {
                Log.e("ChangelogSchedulerJob", "Failed to schedule job")
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
        Log.d(TAG, "Job started")
        
        // Run the job in a coroutine to avoid blocking the main thread
        coroutineScope.launch {
            try {
                // Create changelog repository and util
                val changelogRepository: ChangelogRepository = ChangelogRepositoryImpl(applicationContext)
                val changelogUtil = ChangelogUtil(applicationContext, changelogRepository)
                
                // Create device repository and util
                val deviceRepository: DeviceRepository = DeviceRepositoryImpl(applicationContext)
                val deviceInfoUtil = DeviceInfoUtil(applicationContext, deviceRepository)
                
                // Update device info in the background
                Log.d(TAG, "Updating device info")
                val deviceInfoResult = deviceInfoUtil.registerOrUpdateDevice()
                Log.d(TAG, "Device info update result: $deviceInfoResult")
                
                // Check for changelog changes
                val hasChanges = changelogUtil.checkChange()
                
                if (hasChanges) {
                    Log.d(TAG, "Changes detected, reloading playlists")
                    reloadPlaylists()
                } else {
                    Log.d(TAG, "No changes detected")
                }
                
                // Job finished
                jobFinished(params, false)
            } catch (e: Exception) {
                Log.e(TAG, "Error in job execution: ${e.message}")
                e.printStackTrace()
                jobFinished(params, true) // Reschedule on failure
            }
        }
        
        // Return true to indicate that our job is still running asynchronously
        return true
    }
    
    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Job stopped")
        // Return true to reschedule the job
        return true
    }
    
    /**
     * Reload playlists when changes are detected
     * This will restart the DigitalClockActivity to reload everything
     */
    private fun reloadPlaylists() {
        try {
            Log.d(TAG, "Reloading playlists")
            
            // Create an intent to start the DigitalClockActivity
            val intent = Intent(applicationContext, DigitalClockActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            
            // Start the activity
            applicationContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading playlists: ${e.message}")
            e.printStackTrace()
        }
    }
}
