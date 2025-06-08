package com.vnlook.tvsongtao.usecase

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.VideoView
import com.vnlook.tvsongtao.MainActivity
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.utils.PlaylistScheduler
import com.vnlook.tvsongtao.utils.VideoDownloadManager
import com.vnlook.tvsongtao.utils.VideoDownloadManagerListener
import com.vnlook.tvsongtao.utils.VideoPlayer
import java.time.LocalTime
import java.util.Timer
import java.util.TimerTask

/**
 * UseCase responsible for video playback and playlist management
 */
class VideoUseCase(
    private val activity: MainActivity,
    private val videoView: VideoView,
    private val uiUseCase: UIUseCase,
    private val dataUseCase: DataUseCase
) : VideoDownloadManagerListener {

    private lateinit var videoDownloadManager: VideoDownloadManager
    private lateinit var videoPlayer: VideoPlayer
    private lateinit var playlistScheduler: PlaylistScheduler
    private var timer: Timer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isDownloadComplete = false
    private var videosLoaded = false
    
    /**
     * Initialize video managers
     */
    fun initializeManagers() {
        try {
            Log.d("VideoUseCase", "Initializing video managers")
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
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in initializeManagers: ${e.message}")
            e.printStackTrace()
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
            
            // Load data in a background thread
            Thread {
                try {
                    // Load videos and playlists from data manager
                    val videos = dataUseCase.getVideos()
                    val playlists = dataUseCase.getPlaylists()
                    
                    Log.d("VideoUseCase", "Loaded ${videos.size} videos and ${playlists.size} playlists")
                    
                    // Start downloading videos
                    if (videos.isNotEmpty()) {
                        videoDownloadManager.downloadVideos(videos)
                    } else {
                        Log.w("VideoUseCase", "No videos found to download")
                        activity.runOnUiThread {
                            uiUseCase.showStatus("Không tìm thấy video nào")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VideoUseCase", "Error loading videos: ${e.message}")
                    e.printStackTrace()
                    
                    activity.runOnUiThread {
                        uiUseCase.showStatus("Lỗi tải dữ liệu: ${e.message}")
                    }
                }
            }.start()
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in checkAndLoadVideos: ${e.message}")
            e.printStackTrace()
            uiUseCase.showStatus("Lỗi tải dữ liệu: ${e.message}")
        }
    }
    
    /**
     * Start playlist checker
     */
    fun startPlaylistChecker() {
        try {
            Log.d("VideoUseCase", "Starting playlist checker")
            timer = Timer()
            timer?.schedule(object : TimerTask() {
                override fun run() {
                    checkAndPlayCurrentPlaylist()
                }
            }, 0, 60000) // Check every minute
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in startPlaylistChecker: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Check and play current playlist
     */
    fun checkAndPlayCurrentPlaylist() {
        try {
            Log.d("VideoUseCase", "Checking for current playlist")
            
            // Initialize managers if not already done
            if (!::playlistScheduler.isInitialized) {
                playlistScheduler = PlaylistScheduler()
            }
            
            Log.d("VideoUseCase", "checkAndPlayCurrentPlaylist called, isDownloadComplete=$isDownloadComplete")
            
            if (!isDownloadComplete) {
                // Don't try to play videos until downloads are complete
                Log.d("VideoUseCase", "Downloads not complete yet, not playing videos")
                uiUseCase.showStatus("Đang chờ tải xong video...")
                return
            }
            
            val playlists = dataUseCase.getPlaylists()
            Log.d("VideoUseCase", "Got ${playlists.size} playlists from dataManager")
            
            val currentPlaylist = playlistScheduler.getCurrentPlaylist(playlists)
            Log.d("VideoUseCase", "Current playlist: ${currentPlaylist?.id ?: "none"}")

            if (currentPlaylist != null) {
                Log.d("VideoUseCase", "Found playlist to play: ${currentPlaylist.id}")
                
                // Initialize video player if not already done
                if (!::videoPlayer.isInitialized) {
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
                
                activity.runOnUiThread {
                    try {
                        // Hide loading UI
                        uiUseCase.hideLoading(videoView)
                        
                        // Play the playlist
                        Log.d("VideoUseCase", "Starting to play playlist: ${currentPlaylist.id}")
                        videoPlayer.playPlaylist(currentPlaylist)
                    } catch (e: Exception) {
                        Log.e("VideoUseCase", "Error updating UI in checkAndPlayCurrentPlaylist: ${e.message}")
                        e.printStackTrace()
                    }
                }
            } else {
                activity.runOnUiThread {
                    try {
                        if (::videoPlayer.isInitialized) {
                            videoPlayer.stop()
                        }
                        uiUseCase.showStatus("Không có playlist nào cần phát vào lúc ${LocalTime.now()}")
                    } catch (e: Exception) {
                        Log.e("VideoUseCase", "Error handling no playlist case: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in checkAndPlayCurrentPlaylist: ${e.message}")
            e.printStackTrace()
            activity.runOnUiThread {
                uiUseCase.showToast("Lỗi khi phát video: ${e.message}")
            }
        }
    }
    
    /**
     * Initialize app
     */
    fun initializeApp() {
        try {
            Log.d("VideoUseCase", "Initializing app")
            // Start playlist checker
            startPlaylistChecker()
            
            // Videos should already be downloaded by ClockScreenActivity
            // Just check and play current playlist
            checkAndPlayCurrentPlaylist()
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in initializeApp: ${e.message}")
            e.printStackTrace()
            uiUseCase.showStatus("Lỗi khởi tạo: ${e.message}")
        }
    }
    
    /**
     * Stop and clean up resources
     */
    fun cleanup() {
        try {
            // Clean up timer
            timer?.cancel()
            timer = null
            
            // Clean up video download manager
//            if (::videoDownloadManager.isInitialized) {
//                try {
//                    videoDownloadManager.cleanup()
//                } catch (e: Exception) {
//                    Log.e("VideoUseCase", "Error cleaning up download manager: ${e.message}")
//                }
//            }
            
            // Stop video player
            if (::videoPlayer.isInitialized) {
                try {
                    videoPlayer.stop()
                } catch (e: Exception) {
                    Log.e("VideoUseCase", "Error stopping video player: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in cleanup: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Stop video playback
     */
    fun stopPlayback() {
        if (::videoPlayer.isInitialized) {
            try {
                videoPlayer.stop()
            } catch (e: Exception) {
                Log.e("VideoUseCase", "Error stopping video player: ${e.message}")
            }
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
            
            activity.runOnUiThread {
                // Hide loading UI
                uiUseCase.hideLoading(videoView)
                
                // Start playing videos
                checkAndPlayCurrentPlaylist()
            }
        } catch (e: Exception) {
            Log.e("VideoUseCase", "Error in onVideoReady: ${e.message}")
            e.printStackTrace()
        }
    }
}
