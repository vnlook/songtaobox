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
                    // Parse videos from API response
                    val (_, videos) = VNLApiResponseParser.parseApiResponse(apiResponse)
                    apiVideos = videos
                    Log.d(TAG, "Retrieved ${apiVideos.size} videos directly from API")
                } else {
                    Log.w(TAG, "API call failed or returned empty response, using empty video list")
                    apiVideos = emptyList()
                }
                
                // Get saved data from DataManager
                val savedPlaylists = dataManager.getPlaylists()
                
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