package com.vnlook.tvsongtao.utils

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vnlook.tvsongtao.DigitalClockActivity
import com.vnlook.tvsongtao.data.DataManager
import com.vnlook.tvsongtao.repository.DeviceRepositoryImpl
import com.vnlook.tvsongtao.repository.VNLApiClient
import com.vnlook.tvsongtao.repository.VNLApiResponseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Singleton Timer Manager for changelog checking and device info updates
 * Runs every 60 seconds globally across the app
 */
object ChangelogTimerManager {
    
    private const val TAG = "ChangelogTimerManager"
    private const val CHECK_INTERVAL = 60000L // 60 seconds
    
    private var timer: Timer? = null
    private var appContext: Context? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Track last device info update to ensure it happens every 60 seconds
    private var lastDeviceInfoUpdate = 0L
    
    /**
     * Initialize timer with application context
     * Should be called from Application.onCreate()
     */
    fun initialize(context: Application) {
        appContext = context.applicationContext
//        startGlobalTimer()

        Handler(Looper.getMainLooper()).postDelayed({
            startGlobalTimer()
        }, 25_000L) // 15 giây
    }
    
    /**
     * Start the global changelog timer
     */
    private fun startGlobalTimer() {
        // Stop existing timer first
        stopTimer()
        
        Log.i(TAG, "🚀🚀🚀 STARTING GLOBAL CHANGELOG TIMER 🚀🚀🚀")
        
        try {
            // Create timer with daemon thread
            timer = Timer("GlobalChangelogTimer", true)
            
            timer?.schedule(object : TimerTask() {
                override fun run() {
                    val timestamp = System.currentTimeMillis()
                    Log.i(TAG, "⏰ GLOBAL TIMER TICK #$timestamp - executing checks...")
                    
                    // Run checks in background
                    coroutineScope.launch {
                        try {
                            val context = appContext
                            if (context != null) {
                                // STEP 1: Update device info every 60 seconds
                                updateDeviceInfo(context)
                                
                                // STEP 2: Check for changelog changes
                                val hasChanges = ChangelogUtil.checkChange(context)
                                
                                if (hasChanges) {
                                    Log.i(TAG, "🔥 CHANGES DETECTED! Processing updates...")
                                    
                                    // STEP 3: Process changelog changes in background
                                    val updateSuccess = processChangelogUpdates(context)
                                    
                                    if (updateSuccess) {
                                        Log.i(TAG, "✅ Updates completed successfully, restarting app...")
                                        
                                        // STEP 4: Restart app on main thread
//                                        Handler(Looper.getMainLooper()).post {
//                                            restartApp()
//                                        }
                                        withContext(Dispatchers.Main) {
                                            restartApp()
                                        }
                                    } else {
                                        Log.e(TAG, "❌ Updates failed, not restarting app")
                                    }
                                } else {
                                    Log.d(TAG, "📋 No changes detected, continuing...")
                                }
                            } else {
                                Log.w(TAG, "⚠️ App context is null, skipping checks")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "💥 Error in timer task: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }
            }, 1000, CHECK_INTERVAL) // Start after 1 second, then every 60 seconds
            
            Log.i(TAG, "✅ Global changelog timer started successfully")
            Log.i(TAG, "🎯 Timer scheduled: 1 second delay, 60 second interval")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error starting global timer: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Update device info every 60 seconds
     */
    private suspend fun updateDeviceInfo(context: Context) {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Ensure device info is updated every 60 seconds
            if (currentTime - lastDeviceInfoUpdate >= CHECK_INTERVAL) {
                Log.d(TAG, "📱 Updating device info...")
                
                // Check network connectivity first
                if (!NetworkUtil.isNetworkAvailable(context)) {
                    Log.d(TAG, "🚫 No network available, skipping device info update")
                    return
                }
                
                val deviceRepository = DeviceRepositoryImpl(context)
                val deviceInfoUtil = DeviceInfoUtil(context, deviceRepository)
                
                val result = deviceInfoUtil.registerOrUpdateDevice()
                
                if (result != null) {
                    Log.d(TAG, "✅ Device info updated successfully: $result")
                    lastDeviceInfoUpdate = currentTime
                } else {
                    Log.e(TAG, "❌ Device info update failed")
                }
            } else {
                Log.d(TAG, "⏭️ Device info update not needed yet")
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error updating device info: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Process changelog updates in background:
     * 1. Get latest playlists from API
     * 2. Save over old cached playlists
     * 3. Compare with old videos to determine what to download/remove
     * 4. Download new videos
     * 5. Remove invalid videos
     */
    private suspend fun processChangelogUpdates(context: Context): Boolean {
        return try {
            Log.i(TAG, "🔄 Processing changelog updates...")
            
            // Check network connectivity
            if (!NetworkUtil.isNetworkAvailable(context)) {
                Log.e(TAG, "🚫 No network available for updates")
                return false
            }
            
            val dataManager = DataManager(context)
            
            // STEP 1: Get old playlists and videos for comparison
            val oldPlaylists = dataManager.getPlaylists()
            val oldVideos = dataManager.getVideos()
            Log.d(TAG, "📋 Old data: ${oldPlaylists.size} playlists, ${oldVideos.size} videos")
            
            // STEP 2: Get latest playlists from API
            Log.d(TAG, "🌐 Fetching latest playlists from API...")
            val apiResponse = VNLApiClient.getPlaylists()
            
            if (apiResponse == null) {
                Log.e(TAG, "❌ Failed to get playlists from API")
                return false
            }
            
            // STEP 3: Parse and filter playlists for this device
            val deviceRepository = DeviceRepositoryImpl(context)
            val deviceInfo = deviceRepository.getDeviceInfo()
            
            if (deviceInfo == null) {
                Log.e(TAG, "❌ No device info available")
                return false
            }
            
            val (newPlaylists, newVideos) = VNLApiResponseParser.parseApiResponse(apiResponse)
            
            // Filter for this device only
            val devicePlaylists = newPlaylists.filter { 
                it.deviceId == deviceInfo.deviceId && it.deviceName == deviceInfo.deviceName 
            }
            val deviceVideos = devicePlaylists.flatMap { playlist ->
                newVideos.filter { video -> playlist.videoIds.contains(video.id) }
            }.distinctBy { it.id }
            
            Log.d(TAG, "📋 New data: ${devicePlaylists.size} device playlists, ${deviceVideos.size} device videos")
            
            // STEP 4: Save new playlists and videos (overwrite cache)
            Log.d(TAG, "💾 Saving new playlists and videos to cache...")
            dataManager.savePlaylists(devicePlaylists)
            dataManager.saveVideos(deviceVideos)
            
            // STEP 5: Compare and determine what needs to be downloaded/removed
            val videosToDownload = mutableListOf<com.vnlook.tvsongtao.model.Video>()
            val videosToRemove = mutableListOf<com.vnlook.tvsongtao.model.Video>()
            
            // Find videos that need to be downloaded (new or not yet downloaded)
            for (newVideo in deviceVideos) {
                val oldVideo = oldVideos.find { it.id == newVideo.id }
                if (oldVideo == null || !oldVideo.isDownloaded || oldVideo.localPath.isNullOrEmpty()) {
                    videosToDownload.add(newVideo)
                }
            }
            
            // Find videos that need to be removed (no longer in new playlists)
            for (oldVideo in oldVideos) {
                val stillNeeded = deviceVideos.any { it.id == oldVideo.id }
                if (!stillNeeded && oldVideo.isDownloaded && !oldVideo.localPath.isNullOrEmpty()) {
                    videosToRemove.add(oldVideo)
                }
            }
            
            Log.d(TAG, "📊 Analysis: ${videosToDownload.size} to download, ${videosToRemove.size} to remove")
            
            // STEP 6: Remove invalid videos first
            if (videosToRemove.isNotEmpty()) {
                Log.d(TAG, "🗑️ Removing ${videosToRemove.size} invalid videos...")
                val videoDownloadManager = VideoDownloadManager(context)
                
                for (video in videosToRemove) {
                    try {
                        if (!video.localPath.isNullOrEmpty()) {
                            val file = java.io.File(video.localPath!!)
                            if (file.exists() && file.delete()) {
                                Log.d(TAG, "✅ Deleted video file: ${file.name}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting video ${video.id}: ${e.message}")
                    }
                }
            }
            
            // STEP 7: Download new videos
            if (videosToDownload.isNotEmpty()) {
                Log.d(TAG, "⬇️ Downloading ${videosToDownload.size} new videos...")
                
                // Use VideoDownloadManager to download videos synchronously
                val videoDownloadManager = VideoDownloadManager(context)
                
                // Download videos one by one and wait for completion
                var downloadedCount = 0
                for (video in videosToDownload) {
                    try {
                        val success = videoDownloadManager.downloadVideoSynchronously(video)
                        if (success) {
                            downloadedCount++
                            Log.d(TAG, "✅ Downloaded video ${video.id} ($downloadedCount/${videosToDownload.size})")
                        } else {
                            Log.e(TAG, "❌ Failed to download video ${video.id}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "💥 Error downloading video ${video.id}: ${e.message}")
                    }
                }
                
                Log.d(TAG, "📊 Download complete: $downloadedCount/${videosToDownload.size} successful")
            }
            
            Log.i(TAG, "✅ Changelog updates processed successfully")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error processing changelog updates: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Restart the entire app by launching DigitalClockActivity
     */
    private fun restartApp() {
        try {
            val context = appContext
            if (context == null) {
                Log.e(TAG, "❌ Cannot restart app: context is null")
                return
            }
            
            Log.i(TAG, "🔄 FORCE RESTARTING APP...")
            
            val intent = Intent(context, DigitalClockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                       Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            context.startActivity(intent)
            Log.i(TAG, "✅ App restart initiated successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error restarting app: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Stop the global timer
     */
    fun stopTimer() {
        timer?.let {
            Log.d(TAG, "🛑 Stopping global changelog timer...")
            it.cancel()
            it.purge()
            timer = null
            Log.d(TAG, "✅ Global timer stopped")
        }
    }
    
    /**
     * Check if timer is currently running
     */
    fun isTimerRunning(): Boolean {
        return timer != null
    }
    
    /**
     * Get debug information about timer status
     */
    fun getDebugInfo(): String {
        return """
            Timer Running: ${isTimerRunning()}
            Timer Object: ${timer}
            App Context: ${appContext != null}
            Check Interval: ${CHECK_INTERVAL}ms
            Last Device Info Update: ${if (lastDeviceInfoUpdate > 0) Date(lastDeviceInfoUpdate) else "Never"}
        """.trimIndent()
    }
} 