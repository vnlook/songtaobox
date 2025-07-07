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
import com.vnlook.tvsongtao.config.ApiConfig
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
        
        /**
         * Extract filename from URL
         * Example: https://ledgiaodich.vienthongtayninh.vn:3030/assets/55d0667c-9141-47ed-8e9d-e3079b131e86.mp4
         * Returns: 55d0667c-9141-47ed-8e9d-e3079b131e86.mp4
         */
        fun extractFilenameFromUrl(url: String): String {
            return try {
                val urlObj = URL(url)
                val path = urlObj.path
                val filename = path.substringAfterLast('/')
                
                // Ensure it has .mp4 extension
                if (filename.isNotEmpty() && filename.contains('.')) {
                    filename
                } else {
                    // Fallback: use last part of path + .mp4
                    "${filename}.mp4"
                }
            } catch (e: Exception) {
                Log.e("VideoDownloadManager", "Error extracting filename from URL: ${e.message}")
                // Fallback: generate basic filename from URL path
                val lastSegment = url.substringAfterLast('/')
                if (lastSegment.contains('.')) {
                    lastSegment
                } else {
                    "video_${url.hashCode().toString().replace("-", "")}.mp4"
                }
            }
        }
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
     * Initialize video download with network check - properly handles API status codes
     */
    fun initializeVideoDownloadWithNetworkCheck(listener: VideoDownloadManagerListener?) {
        if (isInitializing) {
            Log.d(TAG, "Already initializing, skipping")
            return
        }
        
        this.downloadListener = listener
        isInitializing = true
        
        Log.d(TAG, "🔄 Starting video download initialization with API status check...")
        
        // Check network first
        if (!NetworkUtil.isNetworkAvailable(context)) {
            Log.d(TAG, "🚫 NO NETWORK - Using cached playlists")
            isInitializing = false
            processOfflineMode()
            return
        }
        
        Log.d(TAG, "📡 NETWORK AVAILABLE - Making API call...")
        
        coroutineScope.launch {
            try {
                Log.d(TAG, "📞 Making API call to get playlists and videos...")
                
                // Make API call - VNLApiClient already checks for HTTP 200 status
                val apiResponse = VNLApiClient.getPlaylists()
                
                // VNLApiClient returns null if status != 200 or on error
                if (apiResponse.isNullOrEmpty()) {
                    Log.w(TAG, "🚫 API FAILED (status != 200 or error) - Using cached playlists")
                    processOfflineMode()
                    return@launch
                }
                
                Log.d(TAG, "✅ API SUCCESS (status 200) - Processing response...")
                
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
                
                // IMPORTANT: Only update cache when API returns status 200 (success)
                if (devicePlaylists.isNotEmpty()) {
                    Log.d(TAG, "💾 API SUCCESS - Updating cache with new playlists")
                    val dataManager = DataManager(context)
                    dataManager.savePlaylists(devicePlaylists)
                    Log.d(TAG, "✅ Cache updated with ${devicePlaylists.size} playlists")
                    
                    // Download videos for new playlists
                    downloadVideos(deviceVideos)
                } else {
                    Log.d(TAG, "📭 API SUCCESS but no playlists for this device - using cached playlists")
                    // Don't clear cache, use existing cached playlists
                    processOfflineMode()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "🚫 API EXCEPTION: ${e.message} - Using cached playlists")
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
            
            // Download each video sequentially
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
     * Download a single video with actual implementation
     */
    private suspend fun downloadVideo(video: Video) {
        withContext(Dispatchers.IO) {
            try {
                if (video.url.isNullOrEmpty()) {
                    Log.w(TAG, "Video ${video.id} has no URL, skipping")
                    return@withContext
                }
                
                // Check network connectivity before download
                if (!NetworkUtil.isNetworkAvailable(context)) {
                    Log.w(TAG, "No network available for downloading video ${video.id}")
                    return@withContext
                }
                
                Log.d(TAG, "🎬 Downloading video: ${video.id} from ${video.url}")
                Log.d(TAG, "📱 Network type: ${if (NetworkUtil.isWiFiConnected(context)) "WiFi" else "Mobile"}")
                
                // Use proxy URL for assets from ASSETS_BASE_URL
                val downloadUrl = if (video.url.startsWith(ApiConfig.ASSETS_BASE_URL)) {
                    Log.d(TAG, "🎯 Video ${video.id} - startTime: ${video.startTime}, duration: ${video.duration}")
                    val proxyUrl = ApiConfig.getProxyUrl(
                        video.url,
                        start = video.startTime,
                        duration = video.duration
                    )
                    if (video.duration == null) {
                        Log.d(TAG, "🔄 Using proxy URL Check: $proxyUrl")
                    }
                    Log.d(TAG, "🔄 Using proxy URL: $proxyUrl")
                    proxyUrl
                } else {
                    video.url
                }
                
                // Debug URL details
                val url = URL(downloadUrl)
                Log.d(TAG, "🌐 URL Protocol: ${url.protocol}")
                Log.d(TAG, "🌐 URL Host: ${url.host}")
                Log.d(TAG, "🌐 URL Port: ${url.port}")
                Log.d(TAG, "🌐 URL Path: ${url.path}")
                
                // Extract filename from original URL but we'll adjust extension based on content-type
                val originalFileName = extractFilenameFromUrl(video.url)
                val fileName = if (downloadUrl != video.url) {
                    // If using proxy, assume it's video content and use .mp4 extension
                    val baseName = originalFileName.substringBeforeLast('.')
                    "${baseName}.mp4"
                } else {
                    originalFileName
                }
                
                // Get movies directory
                val moviesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "")
                if (!moviesDir.exists()) {
                    moviesDir.mkdirs()
                }
                Log.d(TAG, "📁 Movies directory: ${moviesDir.absolutePath}")
                
                val localFile = File(moviesDir, fileName)
                
                // Skip if file already exists but update download status
                if (localFile.exists()) {
                    Log.d(TAG, "Video ${video.id} already exists: ${localFile.absolutePath}")
                    
                    // Update download status for existing file
                    val dataManager = DataManager(context)
                    dataManager.updateVideoDownloadStatus(video.url, true, localFile.absolutePath)
                    Log.d(TAG, "📝 Updated status for existing video ${video.id}")
                    
                    return@withContext
                }
                
                // Download the video file with proper headers
                val connection = url.openConnection() as HttpURLConnection
                
                // Configure connection for real devices
                connection.connectTimeout = ApiConfig.Timeouts.DOWNLOAD_CONNECT_TIMEOUT // 5 minutes
                connection.readTimeout = ApiConfig.Timeouts.DOWNLOAD_READ_TIMEOUT // 5 minutes for large video files
                connection.requestMethod = "GET"
                
                // Add essential headers for real device compatibility
                connection.setRequestProperty("User-Agent", "StandeeAds/1.0 (Android)")
                connection.setRequestProperty("Accept", "*/*")
                connection.setRequestProperty("Connection", "keep-alive")
                
                Log.d(TAG, "🌐 Connecting to ${video.url} for video ${video.id}")
                
                val responseCode = connection.responseCode
                Log.d(TAG, "📡 HTTP Response: $responseCode for video ${video.id}")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val contentLength = connection.contentLength
                    Log.d(TAG, "📦 Content length: $contentLength bytes for video ${video.id}")
                    
                    connection.inputStream.use { input ->
                        FileOutputStream(localFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytes = 0
                            
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytes += bytesRead
                            }
                            
                            Log.d(TAG, "📥 Downloaded $totalBytes bytes for video ${video.id}")
                        }
                    }
                    
                    // Verify file was created successfully
                    if (localFile.exists() && localFile.length() > 0) {
                        Log.d(TAG, "✅ Successfully downloaded video ${video.id} to ${localFile.absolutePath} (${localFile.length()} bytes)")
                        
                        // Update download status in cache
                        val dataManager = DataManager(context)
                        dataManager.updateVideoDownloadStatus(video.url, true, localFile.absolutePath)
                        Log.d(TAG, "📝 Updated download status for video ${video.id}")
                    } else {
                        Log.e(TAG, "❌ Downloaded file is empty or doesn't exist for video ${video.id}")
                    }
                    
                } else {
                    Log.e(TAG, "❌ Failed to download video ${video.id}: HTTP $responseCode")
                    Log.e(TAG, "   Response message: ${connection.responseMessage}")
                    
                    // Try to read error response
                    try {
                        val errorResponse = connection.errorStream?.bufferedReader()?.readText()
                        if (!errorResponse.isNullOrEmpty()) {
                            Log.e(TAG, "   Error response: $errorResponse")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "   Could not read error response: ${e.message}")
                    }
                }
                
                connection.disconnect()
                
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "⏰ Timeout downloading video ${video.id}: ${e.message}")
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "🌐 Network error downloading video ${video.id}: ${e.message}")
            } catch (e: javax.net.ssl.SSLException) {
                Log.e(TAG, "🔒 SSL error downloading video ${video.id}: ${e.message}")
            } catch (e: java.io.IOException) {
                Log.e(TAG, "💾 I/O error downloading video ${video.id}: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Unexpected error downloading video ${video.id}: ${e.message}")
                e.printStackTrace()
            }
        }
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
     * Test connectivity to a specific domain
     */
    private suspend fun testConnectivity(videoUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(videoUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.requestMethod = "HEAD"
                connection.setRequestProperty("User-Agent", "StandeeAds/1.0 (Android)")
                
                val responseCode = connection.responseCode
                connection.disconnect()
                
                Log.d(TAG, "🔍 Connectivity test to ${url.host}: HTTP $responseCode")
                return@withContext responseCode in 200..299
            } catch (e: Exception) {
                Log.e(TAG, "🚫 Connectivity test failed: ${e.message}")
                return@withContext false
            }
        }
    }

    /**
     * Debug movies directory
     */
    fun debugMoviesDirectory() {
        Log.d(TAG, "🎬 Movies directory debug info")
        val moviesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "")
        Log.d(TAG, "   Directory exists: ${moviesDir.exists()}")
        Log.d(TAG, "   Directory path: ${moviesDir.absolutePath}")
        Log.d(TAG, "   Directory writable: ${moviesDir.canWrite()}")
        
        if (moviesDir.exists()) {
            val files = moviesDir.listFiles()
            Log.d(TAG, "   Files in directory: ${files?.size ?: 0}")
            files?.forEach { file ->
                Log.d(TAG, "     - ${file.name} (${file.length()} bytes)")
            }
        }
    }
    
    /**
     * Download a single video synchronously (for background changelog updates)
     * Returns true if download was successful, false otherwise
     */
    suspend fun downloadVideoSynchronously(video: Video): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (video.url.isNullOrEmpty()) {
                    Log.w(TAG, "Video ${video.id} has no URL, skipping")
                    return@withContext false
                }
                
                // Check network connectivity before download
                if (!NetworkUtil.isNetworkAvailable(context)) {
                    Log.w(TAG, "No network available for downloading video ${video.id}")
                    return@withContext false
                }
                
                Log.d(TAG, "🎬 Synchronously downloading video: ${video.id} from ${video.url}")
                
                // Use proxy URL for assets from ASSETS_BASE_URL
                val downloadUrl = if (video.url.startsWith(ApiConfig.ASSETS_BASE_URL)) {
                    Log.d(TAG, "🎯 Video ${video.id} - startTime: ${video.startTime}, duration: ${video.duration}")
                    val proxyUrl = ApiConfig.getProxyUrl(video.url, start = video.startTime, duration = video.duration)
                    if (video.duration == null) {
                        Log.d(TAG, "🔄 Using proxy URL check: $proxyUrl")
                    }
                    Log.d(TAG, "🔄 Using proxy URL: $proxyUrl")
                    proxyUrl
                } else {
                    video.url
                }
                
                // Extract filename from original URL but we'll adjust extension based on content-type
                val originalFileName = extractFilenameFromUrl(video.url)
                val fileName = if (downloadUrl != video.url) {
                    // If using proxy, assume it's video content and use .mp4 extension
                    val baseName = originalFileName.substringBeforeLast('.')
                    "${baseName}.mp4"
                } else {
                    originalFileName
                }
                
                // Get movies directory
                val moviesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "")
                if (!moviesDir.exists()) {
                    moviesDir.mkdirs()
                }
                
                val localFile = File(moviesDir, fileName)
                
                // Skip if file already exists but update download status
                if (localFile.exists()) {
                    Log.d(TAG, "Video ${video.id} already exists: ${localFile.absolutePath}")
                    
                    // Update download status for existing file
                    val dataManager = DataManager(context)
                    dataManager.updateVideoDownloadStatus(video.url, true, localFile.absolutePath)
                    
                    return@withContext true
                }
                
                // Download the video file with proper headers
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                
                // Configure connection
                connection.connectTimeout = ApiConfig.Timeouts.DOWNLOAD_CONNECT_TIMEOUT // 5 minutes
                connection.readTimeout = ApiConfig.Timeouts.DOWNLOAD_READ_TIMEOUT // 5 minutes
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "StandeeAds/1.0 (Android)")
                connection.setRequestProperty("Accept", "*/*")
                connection.setRequestProperty("Connection", "keep-alive")
                
                val responseCode = connection.responseCode
                Log.d(TAG, "📡 HTTP Response: $responseCode for video ${video.id}")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val contentLength = connection.contentLength
                    Log.d(TAG, "📦 Content length: $contentLength bytes for video ${video.id}")
                    
                    connection.inputStream.use { input ->
                        FileOutputStream(localFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytes = 0
                            
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytes += bytesRead
                            }
                            
                            Log.d(TAG, "📥 Downloaded $totalBytes bytes for video ${video.id}")
                        }
                    }
                    
                    // Verify file was created successfully
                    if (localFile.exists() && localFile.length() > 0) {
                        Log.d(TAG, "✅ Successfully downloaded video ${video.id} to ${localFile.absolutePath} (${localFile.length()} bytes)")
                        
                        // Update download status in cache
                        val dataManager = DataManager(context)
                        dataManager.updateVideoDownloadStatus(video.url, true, localFile.absolutePath)
                        
                        return@withContext true
                    } else {
                        Log.e(TAG, "❌ Downloaded file is empty or doesn't exist for video ${video.id}")
                        return@withContext false
                    }
                    
                } else {
                    Log.e(TAG, "❌ Failed to download video ${video.id}: HTTP $responseCode")
                    return@withContext false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error downloading video ${video.id}: ${e.message}")
                e.printStackTrace()
                return@withContext false
            }
        }
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