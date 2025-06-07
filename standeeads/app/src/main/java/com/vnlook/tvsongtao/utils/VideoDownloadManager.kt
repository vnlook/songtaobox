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
import com.vnlook.tvsongtao.data.MockDataProvider
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * VideoDownloadManager handles the download and management of video content.
 * Flow:
 * 1. When app opens, get playlist videos from API (mock data)
 * 2. Check if playlist JSON has changed or downloads are incomplete
 * 3. Download videos and store paths in playlist JSON
 * 4. Show progress during downloads
 * 5. Play videos from local paths when all downloads complete
 */
class VideoDownloadManager(private val context: Context) {

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
    private var receiverRegistered = false
    private var progressMonitorRunning = false
    private val pendingVideos = mutableListOf<Video>()
    
    // Coroutine scope for background operations
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
    
    /**
     * Initializes the video download process according to the flow:
     * 1. Fetch mock data (simulating API)
     * 2. Check if playlists need updating or videos need downloading
     * 3. Download any missing videos
     * 4. Play videos when ready
     */
    fun initializeVideoDownload(listener: VideoDownloadManagerListener?) {
        Log.d(TAG, "Initializing video download process")
        this.downloadListener = listener
        
        coroutineScope.launch {
            try {
                // Fetch mock data (simulating API call)
                val mockData = MockDataProvider.getMockPlaylists()
                val apiVideos = MockDataProvider.getMockVideos()
                
                // Get saved playlists from SharedPreferences
                val savedPlaylists = dataManager.getPlaylists()
                
                // Instead of overwriting videos, merge them to preserve download status
                val mergedVideos = dataManager.mergeVideos(apiVideos)
                
                // Save the merged videos back to SharedPreferences
                dataManager.saveVideos(mergedVideos)
                
                // Save playlists from API/mock to SharedPreferences only if they've changed
                if (downloadHelper.checkIfPlaylistsNeedUpdate(mockData, savedPlaylists)) {
                    Log.d(TAG, "Playlists have changed, updating SharedPreferences")
                    dataManager.savePlaylists(mockData)
                } else {
                    Log.d(TAG, "Playlists haven't changed, keeping existing data")
                }
                
                // Log the number of videos we need to check
                Log.d(TAG, "Checking ${apiVideos.size} videos for download status")
                
                // Get list of videos that need downloading
                val videosToDownload = downloadHelper.getVideosToDownload(apiVideos, mergedVideos)
                
                // Log the videos that need downloading
                Log.d(TAG, "Found ${videosToDownload.size} videos that need downloading")
                videosToDownload.forEach { video ->
                    Log.d(TAG, "Video to download: ${video.id} - ${video.name}")
                }
                
                // Check if all videos are already downloaded
                if (videosToDownload.isEmpty()) {
                    Log.d(TAG, "All videos are already downloaded, notifying UI")
                    withContext(Dispatchers.Main) {
                        downloadListener?.onAllDownloadsCompleted()
                    }
                } else {
                    // Start downloading videos
                    Log.d(TAG, "Starting video downloads")
                    totalDownloads = videosToDownload.size
                    completedDownloads = 0
                    
                    // Register broadcast receiver for download completion
                    if (!receiverRegistered) {
                        ContextCompat.registerReceiver(
                            context,
                            downloadCompleteReceiver,
                            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                            ContextCompat.RECEIVER_NOT_EXPORTED
                        )
                        receiverRegistered = true
                    }
                    
                    // Start progress monitoring
                    startProgressMonitoring()
                    
                    // Start downloading videos
                    downloadVideos(videosToDownload)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing video download: ${e.message}")
                e.printStackTrace()
                // Just log the error since there's no onError method in the interface
                // We could add a Toast message here if needed
            }
        }
    }
    
    /**
     * Downloads a list of videos one at a time
     */
    fun downloadVideos(videos: List<Video>) {
        if (!receiverRegistered) {
            // Register broadcast receiver for download completion
            ContextCompat.registerReceiver(
                context,
                downloadCompleteReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
        
        totalDownloads = videos.size
        completedDownloads = 0
        
        // Update UI with initial progress
        notifyProgressUpdate()
        
        // Start progress monitoring
        startProgressMonitoring()
        
        // Store videos to download
        pendingVideos.clear()
        pendingVideos.addAll(videos)
        
        // Start downloading the first video
        downloadNextVideo()
    }
    
    /**
     * Downloads a single video
     */
    private fun downloadVideo(video: Video) {
        val destinationPath = downloadHelper.getVideoDownloadPath(video.id)
        val destinationFile = File(destinationPath)
        
        // Check if file already exists and is valid
        if (destinationFile.exists() && destinationFile.length() > 0) {
            Log.d(TAG, "Video ${video.id} already exists at path: $destinationPath. Skipping download.")
            
            // Mark as downloaded in database
            dataManager.updateVideoDownloadStatus(video.id, true, destinationPath)
            
            // Increment completed downloads counter
            completedDownloads++
            
            // Update progress
            notifyProgressUpdate()
            
            // Download next video
            downloadNextVideo()
            
            // Check if all downloads are complete
            if (completedDownloads >= totalDownloads) {
                onAllDownloadsComplete()
            }
            
            return
        }
        
        // Ensure parent directory exists
        destinationFile.parentFile?.mkdirs()
        
        // Create download request
        val request = DownloadManager.Request(Uri.parse(video.url))
            .setTitle("Downloading video ${video.id}")
            .setDescription("Downloading ${video.name}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(destinationFile))
        
        try {
            val downloadId = downloadManager.enqueue(request)
            downloadIds[downloadId] = video.id
            Log.d(TAG, "Started download for video ${video.id}, downloadId: $downloadId, path: $destinationPath")
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading video ${video.id}: ${e.message}")
            
            // Try next video on error
            downloadNextVideo()
        }
    }
    
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                
                if (downloadId != -1L) {
                    val videoId = downloadIds[downloadId]
                    
                    if (videoId != null) {
                        // Get download status
                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = downloadManager.query(query)
                        
                        if (cursor.moveToFirst()) {
                            val statusColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            if (statusColumnIndex < 0) {
                                Log.e(TAG, "Status column not found in cursor")
                                cursor.close()
                                return
                            }
                            val status = cursor.getInt(statusColumnIndex)
                            
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                // Update video download status and local path
                                val localPath = downloadHelper.getVideoDownloadPath(videoId)
                                dataManager.updateVideoDownloadStatus(videoId, true, localPath)
                                
                                // Log the download path
                                Log.d(TAG, "Download completed for video $videoId. Path: $localPath")
                                
                                // Remove download ID from tracking map
                                downloadIds.remove(downloadId)
                                
                                // Increment completed downloads counter
                                completedDownloads++
                                
                                // Update progress
                                notifyProgressUpdate()
                                
                                // Download next video if there are more videos to download
                                downloadNextVideo()
                                
                                // Check if all downloads are complete
                                if (completedDownloads >= totalDownloads) {
                                    onAllDownloadsComplete()
                                }
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                // Log error
                                val reasonColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val reason = if (reasonColumnIndex >= 0) {
                                    cursor.getInt(reasonColumnIndex)
                                } else {
                                    -1 // Unknown reason
                                }
                                Log.e(TAG, "Download failed for video $videoId. Reason code: $reason")
                                
                                // Remove download ID from tracking map
                                downloadIds.remove(downloadId)
                                
                                // Try next video
                                downloadNextVideo()
                            }
                        }
                        cursor.close()
                    }
                }
            }
        }
    }
    
    /**
     * Downloads the next video in the queue
     */
    private fun downloadNextVideo() {
        if (pendingVideos.isNotEmpty()) {
            // Get the next video to download
            val video = pendingVideos.removeAt(0)
            Log.d(TAG, "Starting download for video ${video.id}")
            
            // Download the video
            downloadVideo(video)
        } else {
            Log.d(TAG, "No more videos to download")
        }
    }
    
    /**
     * Called when all downloads are complete
     */
    private fun onAllDownloadsComplete() {
        Log.d(TAG, "All downloads complete")
        
        // Stop progress monitoring
        stopProgressMonitoring()
        
        // Unregister broadcast receiver
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(downloadCompleteReceiver)
                receiverRegistered = false
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver: ${e.message}")
            }
        }
        
        // Notify listener
        handler.post {
            downloadListener?.onAllDownloadsCompleted()
        }
        
        // Notify that videos are ready
        notifyVideosReady()
    }
    
    /**
     * Notifies that videos are ready for playback
     */
    private fun notifyVideosReady() {
        coroutineScope.launch {
            val playlists = withContext(Dispatchers.IO) {
                dataManager.getPlaylists()
            }
            
            handler.post {
                downloadListener?.onVideoReady(playlists)
            }
        }
    }
    
    /**
     * Starts progress monitoring
     */
    private fun startProgressMonitoring() {
        progressMonitorRunning = true
        handler.post(progressMonitorRunnable)
    }
    
    /**
     * Stops progress monitoring
     */
    private fun stopProgressMonitoring() {
        progressMonitorRunning = false
        handler.removeCallbacks(progressMonitorRunnable)
    }
    
    /**
     * Updates download progress
     */
    private fun updateDownloadProgress() {
        if (totalDownloads <= 0) return
        
        // File completion progress (how many files are done)
        val fileProgress = completedDownloads * 100 / totalDownloads
        
        // Only calculate bytes progress if we have active downloads and no files completed yet
        var overallProgress = fileProgress
        
        // If no files are completed yet but downloads are in progress, calculate bytes progress
        if (fileProgress == 0 && downloadIds.isNotEmpty()) {
            var totalBytes = 0L
            var downloadedBytes = 0L
            var hasValidDownloads = false
            
            val query = DownloadManager.Query().setFilterByStatus(
                DownloadManager.STATUS_RUNNING or
                DownloadManager.STATUS_PAUSED or
                DownloadManager.STATUS_PENDING
            )
            
            val cursor = downloadManager.query(query)
            while (cursor.moveToNext()) {
                val downloadIdColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                if (downloadIdColumnIndex >= 0) {
                    val id = cursor.getLong(downloadIdColumnIndex)
                    if (downloadIds.containsKey(id)) {
                        val bytesDownloadedColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalBytesColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        
                        // Only proceed if both column indices are valid
                        if (bytesDownloadedColumnIndex >= 0 && totalBytesColumnIndex >= 0) {
                            val bytesDownloaded = cursor.getLong(bytesDownloadedColumnIndex)
                            val totalSize = cursor.getLong(totalBytesColumnIndex)
                            
                            if (totalSize > 0) {
                                downloadedBytes += bytesDownloaded
                                totalBytes += totalSize
                                hasValidDownloads = true
                            }
                        }
                    }
                }
            }
            cursor.close()
            
            // Only update progress if we have valid download information
            if (hasValidDownloads && totalBytes > 0) {
                overallProgress = (downloadedBytes * 100 / totalBytes).toInt()
                // Ensure we show at least 1% progress when download has started
                if (overallProgress == 0 && downloadedBytes > 0) {
                    overallProgress = 1
                }
            }
        }
        
        // Notify UI of progress
        notifyProgressUpdate(overallProgress)
    }
    
    /**
     * Notifies the UI of download progress
     */
    private fun notifyProgressUpdate(progressPercent: Int = calculateProgress()) {
        handler.post {
            downloadListener?.onProgressUpdate(completedDownloads, totalDownloads, progressPercent)
        }
    }
    
    /**
     * Calculates the current progress as a percentage
     */
    private fun calculateProgress(): Int {
        if (totalDownloads == 0) return 100
        return (completedDownloads * 100 / totalDownloads)
    }
    
    /**
     * Sets the download listener
     */
    fun setDownloadListener(listener: VideoDownloadManagerListener?) {
        this.downloadListener = listener
    }
    
    /**
     * Cleans up resources when the manager is no longer needed
     */
    fun cleanup() {
        stopProgressMonitoring()
        
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(downloadCompleteReceiver)
                receiverRegistered = false
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver: ${e.message}")
            }
        }
    }
}
