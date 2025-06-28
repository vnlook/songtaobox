package com.vnlook.tvsongtao.usecase

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
//import android.widget.VideoView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vnlook.tvsongtao.DigitalClockActivity
import com.vnlook.tvsongtao.MainActivity
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.utils.PlaylistScheduler
import com.vnlook.tvsongtao.utils.VideoDownloadManager
import com.vnlook.tvsongtao.utils.VideoDownloadManagerListener
import com.vnlook.tvsongtao.utils.VideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

/**
 * UseCase responsible for video playback and playlist management
 * NOTE: Changelog checking is now handled by ChangelogTimerManager singleton
 */
class VideoUseCase(
    private val activity: MainActivity,
    private val videoView: PlayerView,
    private val uiUseCase: UIUseCase,
    private val dataUseCase: DataUseCase
) : VideoDownloadManagerListener {

    private lateinit var videoDownloadManager: VideoDownloadManager
    private lateinit var videoPlayer: VideoPlayer
    private lateinit var playlistScheduler: PlaylistScheduler
    private val handler = Handler(Looper.getMainLooper())
    private var isDownloadComplete = false
    private var videosLoaded = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * Initialize video managers
     */
    fun initializeManagers() {
        try {
            Log.d("VideoUseCase", "Initializing video managers")
            
            // Check if activity is null
            if (activity == null) {
                Log.e("VideoUseCase", "Activity is null, cannot initialize managers")
                return
            }
            
            // Check if videoView is null
            if (videoView == null) {
                Log.e("VideoUseCase", "VideoView is null, cannot initialize managers")
                return
            }
            
            // Check if uiUseCase is null
            if (uiUseCase == null) {
                Log.e("VideoUseCase", "UIUseCase is null, cannot initialize managers")
                return
            }
            
            // Check if dataUseCase is null
            if (dataUseCase == null) {
                Log.e("VideoUseCase", "DataUseCase is null, cannot initialize managers")
                return
            }
            
            videoDownloadManager = VideoDownloadManager(activity)
            videoDownloadManager.setDownloadListener(this)
            
            playlistScheduler = PlaylistScheduler()
            
            // Initialize video player
            videoPlayer = VideoPlayer(activity, videoView) {
                // When playlist finishes, check again after a short delay
                handler.postDelayed({ 
                    try {
                        checkAndPlayCurrentPlaylist() 
                    } catch (e: Exception) {
                        Log.e("VideoUseCase", "Error in playlist callback: ${e.message}")
                        e.printStackTrace()
                    }
                }, 1000)
            }

            // Videos should already be downloaded by ClockScreenActivity
            // Mark videos as loaded since they should be downloaded by ClockScreenActivity
            videosLoaded = true
            isDownloadComplete = true
            
            // Hide loading UI and show video view
            uiUseCase.hideLoading(videoView)
            
            // Start playing videos
            checkAndPlayCurrentPlaylist()
            
            Log.d("VideoUseCase", "✅ Video managers initialized successfully")
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in initializeManagers: ${e.message}")
            e.printStackTrace()
            try {
                uiUseCase?.showStatus("Lỗi khởi tạo video manager: ${e.message}")
            } catch (e2: Exception) {
                Log.e("VideoUseCase", "Error showing error status: ${e2.message}")
            }
        }
    }
    
    /**
     * Check and load videos
     */
    fun checkAndLoadVideos() {
        try {
            Log.d("VideoUseCase", "Loading videos and playlists")
            
            // Make sure loading UI is visible
            uiUseCase.showLoading(videoView)
            uiUseCase.showStatus("Đang tải dữ liệu video...")
            
            // Initialize managers if not already done
            if (!::videoDownloadManager.isInitialized) {
                videoDownloadManager = VideoDownloadManager(activity)
                videoDownloadManager.setDownloadListener(this)
            }
            
            // Load data using coroutines
            coroutineScope.launch {
                try {
                    // Load videos and playlists from data manager using suspend functions
                    val videos = dataUseCase.getVideos()
                    // Use suspend function for playlists
                    val playlists = dataUseCase.getPlaylists()
                    
                    Log.d("VideoUseCase", "Loaded ${videos.size} videos and ${playlists.size} playlists")
                    
                    // Download videos if needed
                    Log.d("VideoUseCase", "Starting video download process...")
                    
                    // Use the public initialization method instead
                    videoDownloadManager.initializeVideoDownloadWithNetworkCheck(this@VideoUseCase)
                } catch (e: Exception) {
                    Log.e("VideoUseCase", "Error loading data: ${e.message}")
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        uiUseCase.showStatus("Lỗi tải dữ liệu: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in checkAndLoadVideos: ${e.message}")
            e.printStackTrace()
            uiUseCase.showStatus("Lỗi tải dữ liệu video")
        }
    }
    
    /**
     * Check and play current playlist
     */
    fun checkAndPlayCurrentPlaylist() {
        try {
            Log.d("VideoUseCase", "Checking current playlist")
            
            // Get current time
            val currentTime = LocalTime.now()
            
            // Use coroutines to get playlists
            coroutineScope.launch {
                try {
                    // Get playlists from data manager using suspend function
                    val playlists = dataUseCase.getPlaylists()

                    if (playlists.isEmpty()) {
                        Log.w("VideoUseCase", "No playlists found - returning to digital clock")
                        withContext(Dispatchers.Main) {
                            try {
                                // Return to DigitalClockActivity when no playlists available
                                Log.d("VideoUseCase", "No playlists found - returning to DigitalClockActivity")
                                
                                // Check activity state before restart
                                if (activity.isFinishing || activity.isDestroyed) {
                                    Log.w("VideoUseCase", "Activity is finishing/destroyed, cannot restart")
                                    return@withContext
                                }
                                
                                // Use Handler to post restart safely
                                Handler(Looper.getMainLooper()).postDelayed({
                                    try {
                                        if (!activity.isFinishing && !activity.isDestroyed) {
                                            val intent = Intent(activity, DigitalClockActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            activity.startActivity(intent)
                                            activity.finish()
                                            Log.d("VideoUseCase", "✅ Successfully returned to DigitalClockActivity (no playlists)")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("VideoUseCase", "Error in delayed restart (no playlists): ${e.message}")
                                        e.printStackTrace()
                                    }
                                }, 200)
                                
                            } catch (e: Exception) {
                                Log.e("VideoUseCase", "Error returning to DigitalClockActivity: ${e.message}")
                                // Fallback: just show status
                                try {
                                    uiUseCase.showStatus("Không có playlist nào để phát")
                                } catch (e2: Exception) {
                                    Log.e("VideoUseCase", "Error showing status: ${e2.message}")
                                }
                            }
                        }
                        return@launch
                    }
                    // Initialize managers if not already done
                    if (!::playlistScheduler.isInitialized) {
                        playlistScheduler = PlaylistScheduler()
                    }
                    
                    Log.d("VideoUseCase", "checkAndPlayCurrentPlaylist called, isDownloadComplete=$isDownloadComplete")
                    
                    if (!isDownloadComplete) {
                        // Check if we have any local videos available before showing "waiting" message
                        val videos = dataUseCase.getVideos()
                        val localVideos = videos.filter { video ->
                            video.isDownloaded && 
                            !video.localPath.isNullOrEmpty() &&
                            java.io.File(video.localPath!!).exists()
                        }
                        
                        if (localVideos.isNotEmpty()) {
                            Log.d("VideoUseCase", "✅ Found ${localVideos.size} local videos available on disk, proceeding with playback")
                            // We have local videos, mark as complete and continue
                            isDownloadComplete = true
                        } else {
                            // No local videos available, show waiting message
                            Log.d("VideoUseCase", "🚫 No local videos available on disk, waiting for downloads")
                        withContext(Dispatchers.Main) {
                                try {
                            uiUseCase.showStatus("Đang chờ tải xong video...")
                                } catch (e: Exception) {
                                    Log.e("VideoUseCase", "Error showing status: ${e.message}")
                                }
                            }
                            return@launch
                        }
                    }
                    
                    val currentPlaylist = playlistScheduler.getCurrentPlaylist(playlists)
                    Log.d("VideoUseCase", "Current playlist: ${currentPlaylist?.id ?: "none"}")
                    
                    if (currentPlaylist == null) {
                        Log.d("VideoUseCase", "No playlist scheduled for current time - returning to digital clock")
                        withContext(Dispatchers.Main) {
                            try {
                                // Stop video player safely
                                try {
                                    if (::videoPlayer.isInitialized) {
                                        videoPlayer.stop()
                                    }
                                } catch (e: Exception) {
                                    Log.w("VideoUseCase", "Error stopping video player: ${e.message}")
                                }
                                
                                // Return to DigitalClockActivity when no playlist is scheduled
                                Log.d("VideoUseCase", "No playlist scheduled - returning to DigitalClockActivity")
                                
                                // Check activity state before restart
                                if (activity.isFinishing || activity.isDestroyed) {
                                    Log.w("VideoUseCase", "Activity is finishing/destroyed, cannot restart")
                                    return@withContext
                                }
                                
                                // Use Handler to post restart safely
                                Handler(Looper.getMainLooper()).postDelayed({
                                    try {
                                        if (!activity.isFinishing && !activity.isDestroyed) {
                                            val intent = Intent(activity, DigitalClockActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            activity.startActivity(intent)
                                            activity.finish()
                                            Log.d("VideoUseCase", "✅ Successfully returned to DigitalClockActivity (no current playlist)")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("VideoUseCase", "Error in delayed restart (no current playlist): ${e.message}")
                                        e.printStackTrace()
                                    }
                                }, 200)
                                
                            } catch (e: Exception) {
                                Log.e("VideoUseCase", "Error returning to DigitalClockActivity: ${e.message}")
                                // Fallback: just show status
                                try {
                                    uiUseCase.showStatus("Không có playlist nào cần phát vào lúc ${LocalTime.now()}")
                                } catch (e2: Exception) {
                                    Log.e("VideoUseCase", "Error showing status: ${e2.message}")
                                }
                            }
                        }
                        return@launch
                    }
                    
                    Log.d("VideoUseCase", "Found playlist to play: ${currentPlaylist.id}")
                    
                    // Get videos for the current playlist
                    val videos = dataUseCase.getVideos()
                    Log.d("VideoUseCase", "💾 Total videos in cache: ${videos.size}")
                    
                    // Debug each video info with expected filename
                    videos.forEachIndexed { index, video ->
                        Log.d("VideoUseCase", "Video $index: ${video.name}, ID=${video.id}, URL=${video.url}")
                        if (!video.url.isNullOrEmpty()) {
                            val expectedFilename = VideoDownloadManager.extractFilenameFromUrl(video.url)
                            Log.d("VideoUseCase", "   Expected filename: $expectedFilename")
                        }
                    }
                    
                    // Debug: Show video IDs in cache
                    if (videos.isNotEmpty()) {
                        val videoIds = videos.take(5).map { it.id }
                        Log.d("VideoUseCase", "📹 First 5 video IDs in cache: $videoIds")
                    }
                    
                    // Debug: Show playlist video IDs
                    Log.d("VideoUseCase", "🎬 Playlist ${currentPlaylist.id} expects video IDs: ${currentPlaylist.videoIds}")
                    
                    val playlistVideos = videos.filter { video -> 
                        currentPlaylist.videoIds.contains(video.id) 
                    }
                    
                    Log.d("VideoUseCase", "🎯 Found ${playlistVideos.size} matching videos for playlist ${currentPlaylist.id}")
                    
                    if (playlistVideos.isEmpty()) {
                        Log.w("VideoUseCase", "No videos found for playlist ${currentPlaylist.id}")
                        withContext(Dispatchers.Main) {
                            try {
                                // Check if videos exist but are not downloaded
                                val allVideos = dataUseCase.getVideos()
                                val playlistVideoIds = currentPlaylist.videoIds
                                val hasVideosInPlaylist = allVideos.any { playlistVideoIds.contains(it.id) }
                                
                                if (hasVideosInPlaylist) {
                                    uiUseCase.showStatus("Video của playlist chưa được tải xuống")
                                } else {
                                    uiUseCase.showStatus("Playlist ${currentPlaylist.id} không có video nào")
                                }
                            } catch (e: Exception) {
                                Log.e("VideoUseCase", "Error showing playlist status: ${e.message}")
                            }
                        }
                        return@launch
                    }
                    
                    // Initialize video player if not already done
                    if (!::videoPlayer.isInitialized) {
                        withContext(Dispatchers.Main) {
                            videoPlayer = VideoPlayer(activity, videoView) {
                                // When playlist finishes, check again after a short delay
                                handler.postDelayed({ 
                                    try {
                                        checkAndPlayCurrentPlaylist() 
                                    } catch (e: Exception) {
                                        Log.e("VideoUseCase", "Error in playlist callback: ${e.message}")
                                        e.printStackTrace()
                                    }
                                }, 1000)
                            }
                        }
                    }
                    
                    // Start playing videos on the main thread
                    withContext(Dispatchers.Main) {
                        try {
                            // Hide loading UI
                            try {
                            uiUseCase.hideLoading(videoView)
                            } catch (e: Exception) {
                                Log.e("VideoUseCase", "Error hiding loading UI: ${e.message}")
                            }
                            
                            // Play the playlist
                            Log.d("VideoUseCase", "Starting to play playlist: ${currentPlaylist.id}")
                            videoPlayer.playPlaylist(currentPlaylist)
                        } catch (e: Exception) {
                            Log.e("VideoUseCase", "Error updating UI in checkAndPlayCurrentPlaylist: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VideoUseCase", "Error in checkAndPlayCurrentPlaylist coroutine: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in checkAndPlayCurrentPlaylist: ${e.message}")
            e.printStackTrace()
            activity.runOnUiThread {
                try {
                uiUseCase.showToast("Lỗi khi phát video: ${e.message}")
                } catch (e: Exception) {
                    Log.e("VideoUseCase", "Error showing toast: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Initialize app
     */
    fun initializeApp() {
        try {
            Log.i("VideoUseCase", "🚀 INITIALIZING APP...")
            
            // Videos should already be downloaded by ClockScreenActivity
            // Just check and play current playlist
            checkAndPlayCurrentPlaylist()
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in initializeApp: ${e.message}")
            e.printStackTrace()
            try {
            uiUseCase.showStatus("Lỗi khởi tạo: ${e.message}")
            } catch (e: Exception) {
                Log.e("VideoUseCase", "Error showing status: ${e.message}")
            }
        }
    }
    
    /**
     * Stop video playback temporarily (for app pause)
     */
    fun stopPlayback() {
        try {
            Log.d("VideoUseCase", "Stopping video playback temporarily")
            
            // Stop video player if it exists
            if (::videoPlayer.isInitialized) {
                try {
                    Log.d("VideoUseCase", "Stopping video player")
                    videoPlayer.stop()
                } catch (e: Exception) {
                    Log.e("VideoUseCase", "Error stopping video player: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in stopPlayback: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Stop and clean up resources
     */
    fun cleanup() {
        try {
            Log.d("VideoUseCase", "🧹 Starting VideoUseCase cleanup...")
            
            // Stop video player
            if (::videoPlayer.isInitialized) {
                try {
                    videoPlayer.stop()
                    Log.d("VideoUseCase", "✅ Video player stopped")
                } catch (e: Exception) {
                    Log.e("VideoUseCase", "Error stopping video player: ${e.message}")
                }
            }
            
            // Cancel coroutine scope
            try {
                coroutineScope.cancel()
                Log.d("VideoUseCase", "✅ Coroutine scope cancelled")
            } catch (e: Exception) {
                Log.e("VideoUseCase", "Error cancelling coroutine scope: ${e.message}")
            }
            
            Log.i("VideoUseCase", "✅ VideoUseCase cleanup completed")
            
        } catch (e: Exception) {
            Log.e("VideoUseCase", "💥 Error in cleanup: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Get download completion status
     */
    fun isDownloadComplete(): Boolean {
        return isDownloadComplete
    }
    
    /**
     * Get videos loaded status
     */
    fun isVideosLoaded(): Boolean {
        return videosLoaded
    }
    
    /**
     * Set videos loaded status
     */
    fun setVideosLoaded(loaded: Boolean) {
        videosLoaded = loaded
    }
    
    // VideoDownloadManagerListener implementation
    override fun onProgressUpdate(completedDownloads: Int, totalDownloads: Int, progressPercent: Int) {
        try {
            Log.d("VideoUseCase", "Download progress: $completedDownloads/$totalDownloads - $progressPercent%")
            
            activity.runOnUiThread {
                // Update progress bar with the actual progress percentage from download manager
                uiUseCase.updateProgressUI(progressPercent)
                
                // Update status message
                if (totalDownloads > 0) {
                    if (completedDownloads == 0) {
                        uiUseCase.showStatus("\u0110ang b\u1eaft \u0111\u1ea7u t\u1ea3i video...")
                    } else if (completedDownloads < totalDownloads) {
                        uiUseCase.showStatus("\u0110\u00e3 t\u1ea3i $completedDownloads/$totalDownloads video")
                    } else {
                        uiUseCase.showStatus("\u0110\u00e3 t\u1ea3i xong t\u1ea5t c\u1ea3 video")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in onProgressUpdate: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun onAllDownloadsCompleted() {
        try {
            Log.d("VideoUseCase", "All downloads completed")
            isDownloadComplete = true
            
            activity.runOnUiThread {
                // Update UI to show completion
                uiUseCase.updateProgressUI(100)
                uiUseCase.setTitle("\u0110\u00e3 s\u1eb5n s\u00e0ng ph\u00e1t video")
                uiUseCase.showStatus("\u0110\u00e3 t\u1ea3i xong t\u1ea5t c\u1ea3 video")
                
                // Add a small delay before starting playback for better UX
                handler.postDelayed({
                    try {
                        checkAndPlayCurrentPlaylist()
                    } catch (e: Exception) {
                        Log.e("VideoUseCase", "Error starting playback: ${e.message}")
                        e.printStackTrace()
                    }
                }, 1000) // 1 second delay for smooth transition
            }
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in onAllDownloadsCompleted: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun onVideoReady(playlists: List<Playlist>) {
        try {
            Log.d("VideoUseCase", "Videos ready for playback, received ${playlists.size} playlists")
            videosLoaded = true
            
            // ✅ IMPORTANT: Mark as download complete when videos are ready
            // This handles both online downloads and offline cached videos
            isDownloadComplete = true
            
            activity.runOnUiThread {
                // Hide loading UI
                uiUseCase.hideLoading(videoView)
                
                // Update status to indicate ready
                if (playlists.isNotEmpty()) {
                    uiUseCase.showStatus("Đã sẵn sàng phát video")
                } else {
                    uiUseCase.showStatus("Không có playlist nào để phát")
                }
                
                // Start playing videos immediately
                checkAndPlayCurrentPlaylist()
            }
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in onVideoReady: ${e.message}")
            e.printStackTrace()
        }
    }
}
