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

class VideoDownloadManager(private val context: Context) {
    private val playlistRepository: PlaylistRepository = PlaylistRepositoryImpl(context)

    companion object {
        private const val TAG = "VideoDownloadManager"
        private const val PROGRESS_UPDATE_INTERVAL = 500L // 0.5 seconds
    }

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val dataManager = DataManager(context)
    private val downloadHelper = VideoDownloadHelper(context)
    private val downloadIds = mutableMapOf<Long, String>()
    private var totalDownloads = 0
    private var completedDownloads = 0
    private var downloadListener: VideoDownloadManagerListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressMonitorRunning = false
    private var isInitializing = false
    private val pendingVideos = mutableListOf<String>()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val progressMonitorRunnable = object : Runnable {
        override fun run() {
            try {
                updateDownloadProgress()
            } finally {
                if (progressMonitorRunning) {
                    handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
                }
            }
        }
    }

    fun setDownloadListener(listener: VideoDownloadManagerListener) {
        this.downloadListener = listener
    }

    /**
     * Initialize video download with network check
     * If network is available, download new playlists and clean up old videos
     * If no network, use cached playlists
     */
    fun initializeVideoDownloadWithNetworkCheck(listener: VideoDownloadManagerListener?) {
        if (isInitializing) {
            Log.d(TAG, "Video download initialization already in progress, skipping")
            return
        }
        
        this.downloadListener = listener
        isInitializing = true
        
        coroutineScope.launch {
            try {
                val hasNetwork = NetworkUtil.isNetworkAvailable(context)
                val networkType = NetworkUtil.getNetworkTypeDescription(context)
                Log.d(TAG, "Network status: $hasNetwork ($networkType)")
                
                if (hasNetwork) {
                    // Online: Download new playlists and clean up old videos
                    initializeOnlineMode()
                } else {
                    // Offline: Use cached playlists
                    initializeOfflineMode()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in network-aware initialization: ${e.message}")
                e.printStackTrace()
                // Fallback to offline mode
                try {
                    initializeOfflineMode()
                } catch (offlineError: Exception) {
                    Log.e(TAG, "Error in offline fallback: ${offlineError.message}")
                    withContext(Dispatchers.Main) {
                        downloadListener?.onAllDownloadsCompleted()
                    }
                }
            } finally {
                isInitializing = false
            }
        }
    }
    
    /**
     * Initialize in online mode - download new playlists and clean up
     */
    private suspend fun initializeOnlineMode() {
        Log.d(TAG, "Initializing in online mode - downloading new playlists")
        
        // Get new playlists from API (already filtered by device)
        val newPlaylists = playlistRepository.getPlaylists()
        Log.d(TAG, "Retrieved ${newPlaylists.size} playlists for this device from API")
        
        // Get videos ONLY for this device's playlists
        val newVideos: List<Video>
        if (newPlaylists.isNotEmpty()) {
            // Extract video IDs from device's playlists
            val deviceVideoIds = newPlaylists.flatMap { it.videoIds }.toSet()
            Log.d(TAG, "Device has ${deviceVideoIds.size} unique video IDs in playlists")
            
            // Get all videos from API and filter by device playlist video IDs
            val apiResponse = VNLApiClient.getPlaylists()
            if (!apiResponse.isNullOrEmpty()) {
                val (_, allVideos) = VNLApiResponseParser.parseApiResponse(apiResponse)
                newVideos = allVideos.filter { video -> deviceVideoIds.contains(video.id) }
                Log.d(TAG, "Filtered to ${newVideos.size} videos for this device (from ${allVideos.size} total)")
            } else {
                Log.w(TAG, "API call failed, falling back to offline mode")
                initializeOfflineMode()
                return
            }
        } else {
            Log.w(TAG, "No playlists found for this device, using empty video list")
            newVideos = emptyList()
        }
        
        // Get old playlists and videos for comparison
        val oldPlaylists = dataManager.getPlaylists()
        val oldVideos = dataManager.getVideos()
        
        // Clean up ALL videos not in new device playlist before merging
        cleanupAllInvalidVideos(oldVideos, newVideos)
        
        // Merge videos (this will preserve download status for valid videos)
        val mergedVideos = dataManager.mergeVideos(newVideos)
        dataManager.saveVideos(mergedVideos)
        
        // Update playlists if changed
        if (downloadHelper.checkIfPlaylistsNeedUpdate(newPlaylists, oldPlaylists)) {
            Log.d(TAG, "Playlists have changed, updating cache")
            dataManager.savePlaylists(newPlaylists)
        } else {
            Log.d(TAG, "Playlists unchanged, keeping existing data")
        }
        
        // Start downloading new videos
        val videosToDownload = downloadHelper.getVideosToDownload(mergedVideos)
        Log.d(TAG, "Found ${videosToDownload.size} videos to download")
        
        if (videosToDownload.isEmpty()) {
            Log.d(TAG, "All videos already downloaded, notifying completion")
            withContext(Dispatchers.Main) {
                downloadListener?.onAllDownloadsCompleted()
            }
        } else {
            totalDownloads = videosToDownload.map { it.url }.distinct().size
            completedDownloads = 0
            startProgressMonitoring()
            downloadVideos(videosToDownload)
        }
    }
    
    /**
     * Initialize in offline mode - use cached playlists and videos
     */
    private suspend fun initializeOfflineMode() {
        Log.d(TAG, "Initializing in offline mode - using cached data")
        
        // Get cached playlists and videos
        val cachedPlaylists = dataManager.getPlaylists()
        val cachedVideos = dataManager.getVideos()
        
        Log.d(TAG, "Found ${cachedPlaylists.size} cached playlists and ${cachedVideos.size} cached videos")
        
        if (cachedPlaylists.isEmpty()) {
            Log.w(TAG, "No cached playlists available in offline mode")
            withContext(Dispatchers.Main) {
                downloadListener?.onAllDownloadsCompleted()
            }
            return
        }
        
        // Check which cached videos are actually available locally
        val availableVideos = cachedVideos.filter { video ->
            if (video.isDownloaded && !video.localPath.isNullOrEmpty()) {
                val file = File(video.localPath!!)
                val exists = file.exists() && file.length() > 0
                if (!exists) {
                    Log.d(TAG, "Cached video ${video.id} file not found: ${video.localPath}")
                }
                exists
            } else {
                false
            }
        }
        
        Log.d(TAG, "Found ${availableVideos.size} available videos in offline mode")
        
        // Update videos list to reflect only available videos
        dataManager.saveVideos(availableVideos)
        
        // Notify completion immediately since we're using cached data
        withContext(Dispatchers.Main) {
            downloadListener?.onVideoReady(cachedPlaylists)
            downloadListener?.onAllDownloadsCompleted()
        }
    }
    
    /**
     * Clean up ALL videos not belonging to this device's playlists
     */
    private fun cleanupAllInvalidVideos(oldVideos: List<Video>, newDeviceVideos: List<Video>) {
        try {
            val newDeviceVideoUrls = newDeviceVideos.map { it.url }.toSet()
            Log.d(TAG, "Cleaning up videos not in device playlists. Valid URLs: ${newDeviceVideoUrls.size}")
            
            // Delete video files from storage that are not in new device video list
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            var deletedCount = 0
            
            if (moviesDir != null && moviesDir.exists()) {
                val files = moviesDir.listFiles()
                files?.forEach { file ->
                    if (file.isFile) {
                        // Check if this file corresponds to any valid device video
                        val isValidForDevice = newDeviceVideoUrls.any { url ->
                            downloadHelper.extractFilenameFromUrl(url) == file.name
                        }
                        
                        if (!isValidForDevice) {
                            val deleted = file.delete()
                            if (deleted) {
                                deletedCount++
                                Log.d(TAG, "Deleted video file not for this device: ${file.absolutePath}")
                            } else {
                                Log.w(TAG, "Failed to delete invalid video file: ${file.absolutePath}")
                            }
                        }
                    }
                }
            }
            
            // Also clean up old video references that are not for this device
            val invalidOldVideos = oldVideos.filter { oldVideo ->
                !newDeviceVideoUrls.contains(oldVideo.url) && 
                oldVideo.isDownloaded && 
                !oldVideo.localPath.isNullOrEmpty()
            }
            
            invalidOldVideos.forEach { invalidVideo ->
                val file = File(invalidVideo.localPath!!)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        deletedCount++
                        Log.d(TAG, "Deleted invalid cached video file: ${invalidVideo.localPath}")
                    }
                }
            }
            
            Log.d(TAG, "Cleanup completed. Deleted $deletedCount invalid video files")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up invalid video files: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Clean up video files that are no longer valid according to current playlists
     */
    private fun cleanupInvalidVideoFiles(validVideos: List<Video>) {
        try {
            val validVideoUrls = validVideos.map { it.url }.toSet()
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            
            if (moviesDir != null && moviesDir.exists()) {
                val files = moviesDir.listFiles()
                files?.forEach { file ->
                    if (file.isFile) {
                        // Check if this file corresponds to any valid video
                        val isValid = validVideoUrls.any { url ->
                            downloadHelper.extractFilenameFromUrl(url) == file.name
                        }
                        
                        if (!isValid) {
                            val deleted = file.delete()
                            if (deleted) {
                                Log.d(TAG, "Deleted invalid video file: ${file.absolutePath}")
                            } else {
                                Log.w(TAG, "Failed to delete invalid video file: ${file.absolutePath}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up invalid video files: ${e.message}")
            e.printStackTrace()
        }
    }

    fun initializeVideoDownload(listener: VideoDownloadManagerListener?) {
        if (isInitializing) {
            Log.d(TAG, "Video download initialization already in progress, skipping")
            return
        }
        this.downloadListener = listener
        Log.d(TAG, "Initializing video download process")
        isInitializing = true

        coroutineScope.launch {
            try {
                Log.d(TAG, "Making direct API call to VNL API endpoint")
                // Get playlists directly from API
                val playlists = playlistRepository.getPlaylists()
                Log.d(TAG, "Successfully retrieved ${playlists.size} playlists from API")
                
                // Get videos directly from API
                val apiVideos: List<Video>
                val apiResponse = VNLApiClient.getPlaylists()
                
                if (!apiResponse.isNullOrEmpty()) {
                    // Parse videos from API response and filter by device playlists
                    val (_, allVideos) = VNLApiResponseParser.parseApiResponse(apiResponse)
                    
                    // Filter videos by device's playlist video IDs
                    if (playlists.isNotEmpty()) {
                        val deviceVideoIds = playlists.flatMap { it.videoIds }.toSet()
                        apiVideos = allVideos.filter { video -> deviceVideoIds.contains(video.id) }
                        Log.d(TAG, "Filtered to ${apiVideos.size} videos for this device (from ${allVideos.size} total)")
                    } else {
                        Log.w(TAG, "No playlists found for this device, using empty video list")
                        apiVideos = emptyList()
                    }
                } else {
                    Log.w(TAG, "API call failed or returned empty response, using empty video list")
                    apiVideos = emptyList()
                }
                
                // Get saved data from DataManager
                val savedPlaylists = dataManager.getPlaylists()
                val savedVideos = dataManager.getVideos()
                
                // Clean up ALL videos not in new device playlist before merging
                cleanupAllInvalidVideos(savedVideos, apiVideos)
                
                // Merge and save videos
                val mergedVideos = dataManager.mergeVideos(apiVideos)
                dataManager.saveVideos(mergedVideos)
                
                // Check if playlists need update and save them if needed
                if (downloadHelper.checkIfPlaylistsNeedUpdate(playlists, savedPlaylists)) {
                    Log.d(TAG, "Playlists have changed, updating SharedPreferences")
                    dataManager.savePlaylists(playlists)
                } else {
                    Log.d(TAG, "Playlists haven't changed, keeping existing data")
                }

                val videosToDownload = downloadHelper.getVideosToDownload(mergedVideos)
                val videoUrlToDownload = videosToDownload.map { it.url }.distinct()
                Log.d(TAG, "Found ${videosToDownload.size} videos that need downloading")
                videosToDownload.forEach { video ->
                    Log.d(TAG, "Video to download: ${video.id} - ${video.name}")
                }

                if (videosToDownload.isEmpty()) {
                    Log.d(TAG, "All videos are already downloaded, notifying UI")
                    withContext(Dispatchers.Main) {
                        downloadListener?.onAllDownloadsCompleted()
                    }
                } else {
                    Log.d(TAG, "Starting video downloads")
                    totalDownloads = videoUrlToDownload.size
                    completedDownloads = 0

                    startProgressMonitoring()
                    downloadVideos(videosToDownload)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing video download: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    downloadListener?.onAllDownloadsCompleted()
                }
            } finally {
                isInitializing = false
            }
        }
    }

    fun downloadVideos(videos: List<Video>) {
        val videoUrlToDownload = videos.filter { !it.isDownloaded }.map { it.url }.distinct()
        pendingVideos.clear()
        pendingVideos.addAll(videoUrlToDownload)

        totalDownloads = videoUrlToDownload.size
        completedDownloads = 0
        notifyProgressUpdate()
        startProgressMonitoring()

        downloadNextVideo()
    }

    private fun downloadNextVideo() {
        if (pendingVideos.isEmpty()) return

        val videoUrl = pendingVideos.removeAt(0)
        val destinationPath = downloadHelper.getVideoDownloadPath(videoUrl)
        val destinationFile = File(destinationPath)

        if (destinationFile.exists() && destinationFile.length() > 0) {
            Log.d(TAG, "Video ${videoUrl} already exists. Skipping download.")
            dataManager.updateVideoDownloadStatus(videoUrl, true, destinationPath)
            completedDownloads++
            notifyProgressUpdate()
            downloadNextVideo()
            return
        }

        destinationFile.parentFile?.mkdirs()

        try {
            // Use direct download with SSL configuration instead of DownloadManager
            coroutineScope.launch {
                try {
                    val url = URL(videoUrl)
                    val connection = url.openConnection() as HttpsURLConnection
                    
                    // Configure SSL
                    connection.setRequestProperty("User-Agent", "Apidog/1.0.0 (https://apidog.com)")
                    connection.setRequestProperty("Accept", "*/*")
                    connection.setRequestProperty("Host", "ledgiaodich.vienthongtayninh.vn:3030")
                    connection.setRequestProperty("Connection", "keep-alive")
                    
                    // Set timeouts
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val contentLength = connection.contentLength
                        var bytesDownloaded = 0L

                        FileOutputStream(destinationFile).use { output ->
                            connection.inputStream.use { input ->
                                val buffer = ByteArray(8192)
                                var bytes = input.read(buffer)
                                while (bytes >= 0) {
                                    output.write(buffer, 0, bytes)
                                    bytesDownloaded += bytes
                                    bytes = input.read(buffer)

                                    // Update progress
                                    if (contentLength > 0) {
                                        val progress = (bytesDownloaded * 100 / contentLength).toInt()
                                        withContext(Dispatchers.Main) {
                                            downloadListener?.onProgressUpdate(completedDownloads, totalDownloads, progress)
                                        }
                                    }
                                }
                            }
                        }

                        // Download completed successfully
                        Log.d(TAG, "Video downloaded successfully: $videoUrl")
                        dataManager.updateVideoDownloadStatus(videoUrl, true, destinationPath)
                        completedDownloads++
                        notifyProgressUpdate()
                        downloadNextVideo()
                    } else {
                        Log.e(TAG, "Failed to download video. Response code: $responseCode")
                        // Skip this video and try the next one
                        downloadNextVideo()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading video $videoUrl: ${e.message}")
                    e.printStackTrace()
                    // Skip this video and try the next one
                    downloadNextVideo()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up video download for $videoUrl: ${e.message}")
            e.printStackTrace()
            downloadNextVideo()
        }
    }

    private fun startProgressMonitoring() {
        if (!progressMonitorRunning) {
            progressMonitorRunning = true
            handler.post(progressMonitorRunnable)
        }
    }

    private fun stopProgressMonitoring() {
        progressMonitorRunning = false
        handler.removeCallbacks(progressMonitorRunnable)
    }

    private fun updateDownloadProgress() {
        var progressPercent = 0

        // Kiểm tra có video nào đang tải
        var anyRunning = false
        val completedDownloadIds = mutableListOf<Long>()

        for ((downloadId, _) in downloadIds) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = if (statusIndex != -1) cursor.getInt(statusIndex) else DownloadManager.STATUS_FAILED

                if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED) {
                    anyRunning = true
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    val bytesDownloaded = if (bytesDownloadedIndex != -1) cursor.getInt(bytesDownloadedIndex) else -1
                    val bytesTotal = if (bytesTotalIndex != -1) cursor.getInt(bytesTotalIndex) else -1

                    Log.d(TAG, "DownloadId=$downloadId, bytesDownloaded=$bytesDownloaded, bytesTotal=$bytesTotal")

                    if (bytesDownloaded >= 0 && bytesTotal > 0) {
                        progressPercent = (bytesDownloaded * 100.0 / bytesTotal).toInt().coerceIn(0, 100)
                        if (progressPercent < 0) {
                            Log.e(TAG, "Download progress error: $completedDownloads/$totalDownloads - $progressPercent%")
                        }
                    } else {
                        progressPercent = 0
                    }
                    cursor.close()
                    break // Chỉ tính tiến độ của video đang tải đầu tiên
                } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    // Video này đã tải xong, coi như 100%
                    progressPercent = 100
                    completedDownloadIds.add(downloadId)
                    cursor.close()
                    // Không break để kiểm tra các video khác có đang chạy không
                } else {
                    cursor.close()
                }
            } else {
                cursor?.close()
            }
        }

        // Handle completed downloads outside the loop to avoid concurrent modification
        for (downloadId in completedDownloadIds) {
            val videoUrl = downloadIds[downloadId]
            if (videoUrl != null) {
                val localPath = downloadHelper.getVideoDownloadPath(videoUrl)
                dataManager.updateVideoDownloadStatus(videoUrl, true, localPath)
                Log.d(TAG, "Processing completed download for video $videoUrl. Path: $localPath")

                downloadIds.remove(downloadId)
                completedDownloads++

                // Start next download
                downloadNextVideo()
            }
        }

        if (!anyRunning) {
            // Nếu không có video nào đang chạy, chỉ báo 100% khi completedDownloads = totalDownloads
            progressPercent = if (completedDownloads == totalDownloads && totalDownloads > 0) 100 else 0
        }

        Log.d(TAG, "Download progress: $completedDownloads/$totalDownloads - $progressPercent%")
        if (completedDownloads == totalDownloads) {
            onAllDownloadsComplete()
        } else {
            downloadListener?.onProgressUpdate(completedDownloads, totalDownloads, progressPercent)
        }
    }

    private fun notifyProgressUpdate() {
        updateDownloadProgress()
    }

    private fun onAllDownloadsComplete() {
        stopProgressMonitoring()
        // Lấy playlists đã tải xong (giả sử từ dataManager)
        val playlists = dataManager.getPlaylists()
        downloadListener?.onVideoReady(playlists)
        downloadListener?.onAllDownloadsCompleted()
    }

    fun cleanup() {
        stopProgressMonitoring()
    }
}