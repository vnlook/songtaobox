package com.vnlook.tvsongtao

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vnlook.tvsongtao.repository.DeviceRepository
import com.vnlook.tvsongtao.repository.DeviceRepositoryImpl
import com.vnlook.tvsongtao.usecase.PermissionUseCase
import com.vnlook.tvsongtao.utils.ChangelogChecker
import com.vnlook.tvsongtao.utils.ChangelogSchedulerJob
import com.vnlook.tvsongtao.utils.DeviceInfoUtil
import com.vnlook.tvsongtao.utils.SSLConfig
import com.vnlook.tvsongtao.utils.NetworkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StandeeAdsApplication : Application() {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        private const val TAG = "StandeeAdsApplication"
        
        // Keep a reference to the application context
        lateinit var appContext: Context
            private set
            
        // Flag to track if the app is in the foreground
        var isAppInForeground = false
            set(value) {
                field = value
                Log.d(TAG, "App foreground state changed to: $value")
            }
    }
    
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        
        // Configure SSL for development
        SSLConfig.configureDevSSL()
        
        // Set up global error handler
        setupUncaughtExceptionHandler()
        
        // Check permissions at application level
        checkAppPermissions()
        
        // Register or update device info
        registerDeviceInfo()
        
        // Schedule the changelog checker job
        scheduleChangelogJob()
        
        Log.d(TAG, "StandeeAdsApplication initialized")
    }
    
    /**
     * Register or update device information with the API
     * Creates a new device if it doesn't exist, or updates an existing one
     */
    private fun registerDeviceInfo() {
        try {
            Log.d(TAG, "Checking device registration requirements")
            
            // Check network connectivity first
            if (!NetworkUtil.isNetworkAvailable(this)) {
                Log.d(TAG, "🚫 No network available, skipping device registration")
                return
            }
            
            Log.d(TAG, "📡 Network available, proceeding with device registration")
            val deviceRepository: DeviceRepository = DeviceRepositoryImpl(this)
            val deviceInfoUtil = DeviceInfoUtil(this, deviceRepository)
            
            coroutineScope.launch {
                try {
                    val result = deviceInfoUtil.registerOrUpdateDevice()
                    Log.d(TAG, "✅ Device registration result: $result")
                } catch (e: Exception) {
                    Log.e(TAG, "💥 Error registering device: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error setting up device registration: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Schedule the job to check for changelog updates
     * For testing, we use both the JobScheduler and a Handler-based checker
     */
    private fun scheduleChangelogJob() {
        try {
            // Schedule the JobScheduler for every 15 minutes
            try {
                ChangelogSchedulerJob.schedule(this)
                Log.d(TAG, "✅ Changelog scheduler job scheduled (every 15 minutes)")
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling changelog job: ${e.message}")
                e.printStackTrace()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in scheduleChangelogJob: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "UNCAUGHT EXCEPTION: ${throwable.message}")
                throwable.printStackTrace()
                
                // Save crash log to file
                saveCrashLog(throwable)
                
                // Show toast if app is in foreground
                if (isAppInForeground) {
                    try {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                appContext,
                                "Đã xảy ra lỗi: ${throwable.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to show toast: ${e.message}")
                    }
                }
                
                // Give some time for logging before passing to default handler
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    // Ignore
                }
                
                // Let the default handler deal with the crash
                defaultHandler?.uncaughtException(thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Error in custom exception handler: ${e.message}")
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    private fun saveCrashLog(throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val filename = "crash_$timestamp.txt"
            val crashLogDir = File(filesDir, "crash_logs")
            
            if (!crashLogDir.exists()) {
                crashLogDir.mkdirs()
            }
            
            val crashLogFile = File(crashLogDir, filename)
            FileOutputStream(crashLogFile).use { fos ->
                PrintWriter(fos).use { pw ->
                    pw.println("Time: $timestamp")
                    pw.println("Exception: ${throwable.message}")
                    throwable.printStackTrace(pw)
                }
            }
            
            Log.d(TAG, "Crash log saved to ${crashLogFile.absolutePath}")
            
            // Clean up old crash logs (keep only last 10)
            cleanupOldCrashLogs(crashLogDir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log: ${e.message}")
        }
    }
    
    private fun cleanupOldCrashLogs(crashLogDir: File) {
        try {
            val files = crashLogDir.listFiles()
            if (files != null && files.size > 10) {
                // Sort by last modified time
                val sortedFiles = files.sortedBy { it.lastModified() }
                // Delete oldest files, keeping only the 10 most recent
                for (i in 0 until files.size - 10) {
                    sortedFiles[i].delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old crash logs: ${e.message}")
        }
    }
    
    /**
     * Check app permissions at application level
     * This ensures permissions are prepared as soon as the app starts
     * Note: Actual permission requests will happen in activities
     */
    private fun checkAppPermissions() {
        try {
            Log.d(TAG, "Preparing app permissions at application level")
            // We can't directly request permissions at the Application level
            // as that requires an Activity context, but we can log the current status
            // The actual permission requests will happen in MainActivity
            Log.d(TAG, "Permission status will be checked in MainActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions: ${e.message}")
            e.printStackTrace()
        }
    }
}
