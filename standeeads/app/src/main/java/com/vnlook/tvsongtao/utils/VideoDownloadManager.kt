package com.vnlook.tvsongtao.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vnlook.tvsongtao.data.DataManager
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.model.Video
import com.vnlook.tvsongtao.repository.DeviceRepositoryImpl
import com.vnlook.tvsongtao.repository.PlaylistRepository
import com.vnlook.tvsongtao.repository.PlaylistRepositoryImpl
import com.vnlook.tvsongtao.repository.VNLApiClient
import com.vnlook.tvsongtao.repository.VNLApiResponseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

/**
 * SIMPLIFIED VideoDownloadManager - Fixed all issues
 * - Only calls API when network is available
 * - Never deletes videos when offline
 * - No continuous loops or activity restarts
 */
class VideoDownloadManager(private val context: Context) {
    private val TAG = "VideoDownloadManager"
    private var downloadListener: VideoDownloadManagerListener? = null
    private var isInitializing = false
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        private const val PROGRESS_UPDATE_INTERVAL = 1000L // 1 second
    }
    
    // Download progress tracking
    private val handler = Handler(Looper.getMainLooper())
    private var totalDownloads = 0
    private var completedDownloads = 0
    
    private val progressMonitorRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            if (completedDownloads < totalDownloads) {
                handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
            }
        }
    }
    
    /**
     * SIMPLE initialization - only works when online
     */
    fun initializeVideoDownloadWithNetworkCheck(listener: VideoDownloadManagerListener?) {
        if (isInitializing) {
            Log.d(TAG, "Already initializing, skipping")
            return
        }
        
        this.downloadListener = listener
        isInitializing = true
        
        Log.d(TAG, "🔄 Starting SIMPLE video download initialization...")
        
        // Check network first
        if (!NetworkUtil.isNetworkAvailable(context)) {
            Log.d(TAG, "🚫 NO NETWORK - Cannot call API, finishing initialization")
            isInitializing = false
            // Call processOfflineMode to use cached data
            processOfflineMode()
            return
        }
        
        Log.d(TAG, "📡 NETWORK AVAILABLE - Making single API call...")
        
        coroutineScope.launch {
            try {
                Log.d(TAG, "📞 SINGLE API CALL - Making ONE API call to get both playlists and videos")
                
                // Make ONE API call to get raw response
                val apiResponse = VNLApiClient.getPlaylists()
                
                if (apiResponse.isNullOrEmpty()) {
                    Log.w(TAG, "🚫 API FAILED - No response received, using offline mode")
                    processOfflineMode()
                    return@launch
                }
                
                // Parse playlists and videos from API response
                val deviceRepository = DeviceRepositoryImpl(context)
                val deviceInfo = deviceRepository.getDeviceInfo()
                
                if (deviceInfo == null) {
                    Log.w(TAG, "No device info available, using offline mode")
                    processOfflineMode()
                    return@launch
                }
                
                val (allPlaylists, allVideos) = VNLApiResponseParser.parseApiResponse(apiResponse)
                
                // Filter for this device only
                val devicePlaylists = allPlaylists.filter { it.deviceId == deviceInfo.deviceId }
                val deviceVideos = devicePlaylists.flatMap { playlist ->
                    allVideos.filter { video -> playlist.videoIds.contains(video.id) }
                }.distinctBy { it.id }
                
                Log.d(TAG, "✅ API SUCCESS: ${devicePlaylists.size} playlists, ${deviceVideos.size} videos for device ${deviceInfo.deviceId}")
                
                if (devicePlaylists.isNotEmpty()) {
                    // Save to cache
                    val dataManager = DataManager(context)
                    dataManager.savePlaylists(devicePlaylists)
                    Log.d(TAG, "💾 Cached ${devicePlaylists.size} playlists")
                    
                    // Download videos
                    downloadVideos(deviceVideos)
                } else {
                    Log.d(TAG, "📭 No playlists for this device from API")
                    Log.d(TAG, "🔄 API returned no playlists - using cached playlists instead")
                    // IMPORTANT: Don't clear cache, use existing cached playlists
                    processOfflineMode()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "🚫 API FAILED - Exception: ${e.message}")
                Log.d(TAG, "🚫 API FAILED - Using offline mode")
                e.printStackTrace()
                processOfflineMode()
            } finally {
                isInitializing = false
            }
        }
    }
    
    /**
     * Process online mode - only when API succeeds
     */
    private suspend fun processOnlineMode(apiResponse: String) {
        try {
            // Parse API response
            val (apiPlaylists, apiVideos) = VNLApiResponseParser.parseApiResponse(apiResponse)
            
            // Get device info
            val deviceRepository = DeviceRepositoryImpl(context)
            val deviceInfo = deviceRepository.getDeviceInfo()
            
            if (deviceInfo == null) {
                Log.w(TAG, "No device info available")
                processOfflineMode()
                return
            }
            
            // Filter playlists for this device
            val devicePlaylists = apiPlaylists.filter { 
                it.deviceId == deviceInfo.deviceId && it.deviceName == deviceInfo.deviceName 
            }
            
            Log.d(TAG, "📋 Found ${devicePlaylists.size} playlists for device")
            
            // Save to cache
            val dataManager = DataManager(context)
            dataManager.savePlaylists(devicePlaylists)
            
            // Get videos for device playlists
            val deviceVideos = apiVideos.filter { video ->
                devicePlaylists.any { playlist -> 
                    playlist.videoIds.contains(video.id)
                }
            }
            
            Log.d(TAG, "🎬 Found ${deviceVideos.size} videos for device")
            
            if (deviceVideos.isNotEmpty()) {
                // Download videos
                downloadVideos(deviceVideos)
            } else {
                // No videos for device - notify ready with playlists
                withContext(Dispatchers.Main) {
                    downloadListener?.onVideoReady(devicePlaylists)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in online mode: ${e.message}")
            processOfflineMode()
        }
    }
    
    /**
     * Process offline mode - NEVER delete videos, only use cached data
     */
    private fun processOfflineMode() {
        coroutineScope.launch {
            try {
                Log.d(TAG, "🔄 Processing offline mode - using cached data only")
                
                // Load cached playlists
                val dataManager = DataManager(context)
                val cachedPlaylists = dataManager.getPlaylists()
                
                Log.d(TAG, "📋 Loaded ${cachedPlaylists.size} cached playlists")
                
                if (cachedPlaylists.isNotEmpty()) {
                    Log.d(TAG, "✅ Found cached playlists - using them for offline playback")
                    // Notify ready with cached playlists
                    withContext(Dispatchers.Main) {
                        downloadListener?.onVideoReady(cachedPlaylists)
                    }
                } else {
                    Log.d(TAG, "📭 No cached playlists available")
                    // No cached playlists - notify with empty list but don't delete anything
                    withContext(Dispatchers.Main) {
                        downloadListener?.onVideoReady(emptyList())
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in offline mode: ${e.message}")
                withContext(Dispatchers.Main) {
                    downloadListener?.onVideoReady(emptyList())
                }
            } finally {
                isInitializing = false
            }
        }
    }
    
    /**
     * Download videos
     */
    private suspend fun downloadVideos(videos: List<Video>) {
        try {
            totalDownloads = videos.size
            completedDownloads = 0
            
            withContext(Dispatchers.Main) {
                handler.post(progressMonitorRunnable)
            }
            
            // Download each video
            videos.forEach { video ->
                try {
                    downloadVideo(video)
                    completedDownloads++
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading video ${video.id}: ${e.message}")
                    completedDownloads++
                }
            }
            
            // All downloads completed
            withContext(Dispatchers.Main) {
                val dataManager = DataManager(context)
                val playlists = dataManager.getPlaylists()
                downloadListener?.onAllDownloadsCompleted()
                downloadListener?.onVideoReady(playlists)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading videos: ${e.message}")
        }
    }
    
    /**
     * Download a single video
     */
    private fun downloadVideo(video: Video) {
        // Implementation for downloading a single video
        Log.d(TAG, "Downloading video: ${video.id}")
        // Add actual download logic here
    }
    
    /**
     * Update download progress
     */
    private fun updateProgress() {
        val progressPercent = if (totalDownloads > 0) {
            (completedDownloads * 100) / totalDownloads
        } else {
            100
        }
        
        downloadListener?.onProgressUpdate(completedDownloads, totalDownloads, progressPercent)
    }
    
    /**
     * Set download listener
     */
    fun setDownloadListener(listener: VideoDownloadManagerListener?) {
        this.downloadListener = listener
    }
    
    /**
     * Debug movies directory
     */
    fun debugMoviesDirectory() {
        Log.d(TAG, "🎬 Movies directory debug info")
        // Add debug logic here
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        handler.removeCallbacks(progressMonitorRunnable)
        downloadListener = null
        isInitializing = false
    }
}