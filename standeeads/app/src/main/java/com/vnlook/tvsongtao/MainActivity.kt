package com.vnlook.tvsongtao

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.vnlook.tvsongtao.StandeeAdsApplication
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.vnlook.tvsongtao.data.DataManager
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.utils.PlaylistScheduler
import com.vnlook.tvsongtao.utils.VideoDownloadManager
import com.vnlook.tvsongtao.utils.VideoDownloadManagerListener
import com.vnlook.tvsongtao.utils.VideoPlayer
import java.time.LocalTime
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity(), VideoDownloadManagerListener {

    private lateinit var videoView: VideoView
    private lateinit var tvStatus: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvPercentage: TextView
    private lateinit var loadingContainer: View
    private lateinit var progressBar: ProgressBar
    private lateinit var dataManager: DataManager
    private lateinit var videoDownloadManager: VideoDownloadManager
    private lateinit var videoPlayer: VideoPlayer
    private lateinit var playlistScheduler: PlaylistScheduler
    private var timer: Timer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isDownloadComplete = false
    private var appInitialized = false
    private var videosLoaded = false
    
    // Keep app alive with this runnable
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            try {
                Log.d("MainActivity", "Keep-alive check running")
                // Make sure loading UI is visible if videos are not loaded yet
                if (!videosLoaded) {
                    runOnUiThread {
                        try {
                            loadingContainer.visibility = View.VISIBLE
                            videoView.visibility = View.GONE
                            
                            // Update progress periodically if it's stuck at 0
                            if (progressBar.progress == 0) {
                                updateProgressUI(5) // Show some progress to indicate app is alive
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error updating UI in keepAlive: ${e.message}")
                        }
                    }
                }
                
                // Schedule next run
                handler.postDelayed(this, 3000) // Check every 3 seconds
            } catch (e: Exception) {
                Log.e("MainActivity", "Error in keepAliveRunnable: ${e.message}")
                e.printStackTrace()
                // Make sure we reschedule even if there's an error
                handler.postDelayed(this, 3000)
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set default uncaught exception handler to prevent app from crashing
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MainActivity", "UNCAUGHT EXCEPTION: ${throwable.message}")
            throwable.printStackTrace()
            
            try {
                // Try to show error message
                runOnUiThread {
                    try {
                        Toast.makeText(this@MainActivity, "Lỗi nghiêm trọng: ${throwable.message}", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        try {
            Log.d("MainActivity", "onCreate started")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            // Hide system UI for fullscreen experience
            setupFullScreen()

            // Initialize UI components first
            initializeUI()
            
            // Start keep-alive mechanism immediately
            handler.post(keepAliveRunnable)

            // Initialize managers in a separate thread to avoid ANR
            Thread {
                try {
                    initializeManagers()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error initializing managers: ${e.message}")
                    e.printStackTrace()
                    runOnUiThread {
                        showStatus("Lỗi khởi tạo: ${e.message}")
                    }
                }
            }.start()
            
            // Mark app as initialized
            appInitialized = true
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}")
            e.printStackTrace()
            
            // Even if there's an error, make sure we show something and don't close
            try {
                // Initialize UI components if not already done
                if (!::loadingContainer.isInitialized) {
                    initializeUI()
                }
                
                if (::loadingContainer.isInitialized && ::videoView.isInitialized) {
                    loadingContainer.visibility = View.VISIBLE
                    videoView.visibility = View.GONE
                    showStatus("Đã xảy ra lỗi: ${e.message}")
                } else {
                    Log.w("MainActivity", "Cannot update loading UI, views not initialized")
                }
                
                // Start keep-alive mechanism anyway
                handler.post(keepAliveRunnable)
                
                if (::tvStatus.isInitialized) {
                    Toast.makeText(this, "Lỗi khởi tạo ứng dụng: ${e.message}", Toast.LENGTH_LONG).show()
                } else {
                    Log.w("MainActivity", "Cannot show error toast, tvStatus not initialized")
                }
            } catch (e2: Exception) {
                Log.e("MainActivity", "Error in error handling: ${e2.message}")
            }
        }
    }
    
    private fun initializeUI() {
        try {
            Log.d("MainActivity", "Finding views")
            videoView = findViewById(R.id.videoView)
            tvStatus = findViewById(R.id.tvStatus)
            tvTitle = findViewById(R.id.tvTitle)
            tvPercentage = findViewById(R.id.tvPercentage)
            loadingContainer = findViewById(R.id.loadingContainer)
            progressBar = findViewById(R.id.progressBar)
            
            // Make sure loading UI is visible from the start
            if (::loadingContainer.isInitialized && ::videoView.isInitialized) {
                loadingContainer.visibility = View.VISIBLE
                videoView.visibility = View.GONE
                if (::progressBar.isInitialized) {
                    progressBar.progress = 0
                }
                if (::tvPercentage.isInitialized) {
                    tvPercentage.text = "0%"
                }
                showStatus("Đang khởi động ứng dụng...")
            } else {
                Log.w("MainActivity", "Cannot update loading UI, views not initialized")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing UI: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun initializeManagers() {
        try {
            Log.d("MainActivity", "Initializing managers")
            dataManager = DataManager(this)
            videoDownloadManager = VideoDownloadManager(this)
            
            // Set download listener on UI thread but don't start downloads
            // as they should be completed by ClockScreenActivity
            runOnUiThread {
                try {
                    if (::videoDownloadManager.isInitialized) {
                        videoDownloadManager.setDownloadListener(this)
                    } else {
                        Log.w("MainActivity", "Cannot set download listener, videoDownloadManager not initialized")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error setting download listener: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            playlistScheduler = PlaylistScheduler()
            
            // Initialize video player on UI thread
            runOnUiThread {
                try {
                    if (::videoView.isInitialized) {
                        videoPlayer = VideoPlayer(this, videoView) {
                            // When playlist finishes, check again after a short delay
                            handler.postDelayed({ 
                                try {
                                    checkAndPlayCurrentPlaylist() 
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Error in playlist callback: ${e.message}")
                                    e.printStackTrace()
                                }
                            }, 1000)
                        }
                    } else {
                        Log.w("MainActivity", "Cannot initialize video player, videoView not initialized")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error initializing video player: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Videos should already be downloaded by ClockScreenActivity
            // Skip permission check and directly start playing videos
            runOnUiThread {
                try {
                    // Mark videos as loaded since they should be downloaded by ClockScreenActivity
                    videosLoaded = true
                    isDownloadComplete = true
                    
                    // Hide loading UI and show video view
                    loadingContainer.visibility = View.GONE
                    videoView.visibility = View.VISIBLE
                    
                    // Start playing videos
                    checkAndPlayCurrentPlaylist()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error starting playback: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in initializeManagers: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun setupFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+, we need to request notification permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST_CODE)
                return
            }
        }
        
        // For all Android versions, check storage permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
            return
        }
        
        // If we reach here, we have all permissions
        checkAndLoadVideos()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, load videos
                checkAndLoadVideos()
            } else {
                // Permission denied
                if (::tvStatus.isInitialized) {
                    Toast.makeText(this, "Ứng dụng cần quyền truy cập để hoạt động", Toast.LENGTH_LONG).show()
                } else {
                    Log.w("MainActivity", "Cannot show error toast, tvStatus not initialized")
                }
            }
        }
    }
    
    private fun initializeApp() {
        try {
            Log.d("MainActivity", "Initializing app")
            // Start playlist checker
            startPlaylistChecker()
            
            // Videos should already be downloaded by ClockScreenActivity
            // Just check and play current playlist
            checkAndPlayCurrentPlaylist()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in initializeApp: ${e.message}")
            e.printStackTrace()
            showStatus("Lỗi khởi tạo: ${e.message}")
        }
    }
    
    private fun checkAndLoadVideos() {
        try {
            Log.d("MainActivity", "Loading videos and playlists")
            
            // Make sure loading UI is visible
            runOnUiThread {
                if (::loadingContainer.isInitialized && ::videoView.isInitialized) {
                    loadingContainer.visibility = View.VISIBLE
                    videoView.visibility = View.GONE
                    showStatus("Đang tải dữ liệu video...")
                } else {
                    Log.w("MainActivity", "Cannot update loading UI, views not initialized")
                }
            }
            
            // Check if dataManager is initialized
            if (!::dataManager.isInitialized) {
                Log.w("MainActivity", "DataManager not initialized, initializing now")
                try {
                    dataManager = DataManager(this)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to initialize dataManager: ${e.message}")
                    runOnUiThread {
                        showStatus("Lỗi khởi tạo dữ liệu: ${e.message}")
                    }
                    return
                }
            }
            
            // Check if videoDownloadManager is initialized
            if (!::videoDownloadManager.isInitialized) {
                Log.w("MainActivity", "VideoDownloadManager not initialized, initializing now")
                try {
                    videoDownloadManager = VideoDownloadManager(this)
                    videoDownloadManager.setDownloadListener(this)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to initialize videoDownloadManager: ${e.message}")
                    runOnUiThread {
                        showStatus("Lỗi khởi tạo trình quản lý tải xuống: ${e.message}")
                    }
                    return
                }
            }
            
            // Load data in a background thread to avoid ANR
            Thread {
                try {
                    // Load videos and playlists from data manager
                    val videos = dataManager.getVideos()
                    val playlists = dataManager.getPlaylists()
                    
                    Log.d("MainActivity", "Loaded ${videos.size} videos and ${playlists.size} playlists")
                    
                    // Start downloading videos
                    if (videos.isNotEmpty()) {
                        videoDownloadManager.downloadVideos(videos)
                    } else {
                        Log.w("MainActivity", "No videos found to download")
                        runOnUiThread {
                            showStatus("Không tìm thấy video nào")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error loading videos: ${e.message}")
                    e.printStackTrace()
                    
                    runOnUiThread {
                        showStatus("Lỗi tải dữ liệu: ${e.message}")
                    }
                }
            }.start()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in checkAndLoadVideos: ${e.message}")
            e.printStackTrace()
            showStatus("Lỗi tải dữ liệu: ${e.message}")
        }
    }

    private fun startPlaylistChecker() {
        try {
            Log.d("MainActivity", "Starting playlist checker")
            timer = Timer()
            timer?.schedule(object : TimerTask() {
                override fun run() {
                    checkAndPlayCurrentPlaylist()
                }
            }, 0, 60000) // Check every minute
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in startPlaylistChecker: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun checkAndPlayCurrentPlaylist() {
        try {
            Log.d("MainActivity", "Checking for current playlist")
            
            // Check if dataManager is initialized
            if (!::dataManager.isInitialized) {
                Log.w("MainActivity", "DataManager not initialized, initializing now")
                try {
                    dataManager = DataManager(this)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to initialize dataManager: ${e.message}")
                    showStatus("Lỗi khởi tạo dữ liệu: ${e.message}")
                    return
                }
            }
            
            // Check if playlistScheduler is initialized
            if (!::playlistScheduler.isInitialized) {
                Log.w("MainActivity", "PlaylistScheduler not initialized, initializing now")
                try {
                    playlistScheduler = PlaylistScheduler()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to initialize playlistScheduler: ${e.message}")
                    showStatus("Lỗi khởi tạo lịch phát: ${e.message}")
                    return
                }
            }
            
            Log.d("MainActivity", "checkAndPlayCurrentPlaylist called, isDownloadComplete=$isDownloadComplete")
            
            if (!isDownloadComplete) {
                // Don't try to play videos until downloads are complete
                Log.d("MainActivity", "Downloads not complete yet, not playing videos")
                showStatus("Đang chờ tải xong video...")
                return
            }
            
            val playlists = dataManager.getPlaylists()
            Log.d("MainActivity", "Got ${playlists.size} playlists from dataManager")
            
            val currentPlaylist = playlistScheduler.getCurrentPlaylist(playlists)
            Log.d("MainActivity", "Current playlist: ${currentPlaylist?.id ?: "none"}")

            if (currentPlaylist != null) {
                Log.d("MainActivity", "Found playlist to play: ${currentPlaylist.id}")
                
                // Check if videoPlayer is initialized
                if (!::videoPlayer.isInitialized) {
                    Log.w("MainActivity", "VideoPlayer not initialized, initializing now")
                    try {
                        if (::videoView.isInitialized) {
                            videoPlayer = VideoPlayer(this, videoView) {
                                // When playlist finishes, check again after a short delay
                                handler.postDelayed({ 
                                    try {
                                        checkAndPlayCurrentPlaylist() 
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Error in playlist callback: ${e.message}")
                                        e.printStackTrace()
                                    }
                                }, 1000)
                            }
                        } else {
                            Log.e("MainActivity", "Cannot initialize videoPlayer, videoView not initialized")
                            showStatus("Lỗi khởi tạo trình phát video")
                            return
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to initialize videoPlayer: ${e.message}")
                        showStatus("Lỗi khởi tạo trình phát video: ${e.message}")
                        return
                    }
                }
                
                runOnUiThread {
                    try {
                        // Hide loading UI if still visible
                        if (::loadingContainer.isInitialized && ::videoView.isInitialized) {
                            Log.d("MainActivity", "Hiding loading UI and showing video view")
                            loadingContainer.visibility = View.GONE
                            videoView.visibility = View.VISIBLE
                            
                            // Play the playlist
                            Log.d("MainActivity", "Starting to play playlist: ${currentPlaylist.id}")
                            videoPlayer.playPlaylist(currentPlaylist)
                        } else {
                            Log.w("MainActivity", "Cannot update UI, views not initialized")
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error updating UI in checkAndPlayCurrentPlaylist: ${e.message}")
                        e.printStackTrace()
                    }
                }
            } else {
                runOnUiThread {
                    try {
                        if (::videoPlayer.isInitialized) {
                            videoPlayer.stop()
                        } else {
                            Log.w("MainActivity", "Cannot stop video player, videoPlayer not initialized")
                        }
                        showStatus("Không có playlist nào cần phát vào lúc ${LocalTime.now()}")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error handling no playlist case: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in checkAndPlayCurrentPlaylist: ${e.message}")
            e.printStackTrace()
            runOnUiThread {
                if (::tvStatus.isInitialized) {
                    Toast.makeText(this, "Lỗi khi phát video: ${e.message}", Toast.LENGTH_LONG).show()
                } else {
                    Log.w("MainActivity", "Cannot show error toast, tvStatus not initialized")
                }
            }
        }
    }

    private fun showStatus(message: String) {
        try {
            if (::tvStatus.isInitialized) {
                tvStatus.text = message
                Log.d("MainActivity", "Status: $message")
            } else {
                Log.w("MainActivity", "Cannot show status, tvStatus not initialized: $message")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error showing status: ${e.message}")
        }
    }
    
    private var lastProgress = 0
    private val progressAnimator = ValueAnimator()
    
    private fun updateProgressUI(progress: Int) {
        try {
            if (::progressBar.isInitialized && ::tvPercentage.isInitialized) {
                // Don't update if progress hasn't changed or is less than previous (unless it's a reset to 0)
                if (progress == lastProgress || (progress < lastProgress && progress > 0)) {
                    return
                }
                
                // Animate progress changes for smoother UI
                progressAnimator.cancel()
                progressAnimator.setIntValues(lastProgress, progress)
                progressAnimator.addUpdateListener { animation ->
                    try {
                        val animatedValue = animation.animatedValue as Int
                        progressBar.progress = animatedValue
                        tvPercentage.text = "$animatedValue%"
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error in progress animation: ${e.message}")
                    }
                }
                
                // Faster animation for small changes, slower for large jumps
                val animDuration = when {
                    progress - lastProgress > 50 -> 800L  // Big jump
                    progress - lastProgress > 20 -> 500L  // Medium jump
                    else -> 300L  // Small change
                }
                
                progressAnimator.duration = animDuration
                progressAnimator.start()
                
                lastProgress = progress
                Log.d("MainActivity", "Progress updated to $progress%")
            } else {
                Log.w("MainActivity", "Cannot update progress UI, views not initialized: $progress%")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating progress UI: ${e.message}")
            // Fallback to direct update without animation
            try {
                if (::progressBar.isInitialized && ::tvPercentage.isInitialized) {
                    progressBar.progress = progress
                    tvPercentage.text = "$progress%"
                    lastProgress = progress
                }
            } catch (e2: Exception) {
                Log.e("MainActivity", "Error in fallback progress update: ${e2.message}")
            }
        }
    }

    override fun onResume() {
        try {
            Log.d("MainActivity", "onResume")
            super.onResume()
            setupFullScreen()
            
            // Update app foreground state
            StandeeAdsApplication.isAppInForeground = true
            
            // Check if we need to load videos
            if (appInitialized && !videosLoaded) {
                checkAndLoadVideos()
            }
            
            // Make sure keep-alive is running
            if (!handler.hasCallbacks(keepAliveRunnable)) {
                Log.d("MainActivity", "Restarting keep-alive runnable")
                handler.post(keepAliveRunnable)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onResume: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onPause() {
        try {
            Log.d("MainActivity", "onPause")
            super.onPause()
            
            // Update app foreground state
            StandeeAdsApplication.isAppInForeground = false
            
            // Safely stop video player if initialized
            if (::videoPlayer.isInitialized) {
                try {
                    videoPlayer.stop()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error stopping video player: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onPause: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        try {
            Log.d("MainActivity", "onDestroy")
            super.onDestroy()
            
            // Stop keep-alive mechanism
            handler.removeCallbacks(keepAliveRunnable)
            
            // Clean up other resources
            timer?.cancel()
            timer = null
            
            // Safely clean up videoDownloadManager if initialized
            if (::videoDownloadManager.isInitialized) {
                try {
                    videoDownloadManager.cleanup()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error cleaning up download manager: ${e.message}")
                }
            }
            
            // Safely stop videoPlayer if initialized
            if (::videoPlayer.isInitialized) {
                try {
                    videoPlayer.stop()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error stopping video player: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onDestroy: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // VideoDownloadManagerListener implementation
    override fun onProgressUpdate(completedDownloads: Int, totalDownloads: Int, progressPercent: Int) {
        try {
            Log.d("MainActivity", "Download progress: $completedDownloads/$totalDownloads - $progressPercent%")
            
            runOnUiThread {
                // Update progress bar with the actual progress percentage from download manager
                updateProgressUI(progressPercent)
                
                // Update status message
                if (totalDownloads > 0) {
                    if (completedDownloads == 0) {
                        showStatus("\u0110ang b\u1eaft \u0111\u1ea7u t\u1ea3i video...")
                    } else if (completedDownloads < totalDownloads) {
                        showStatus("\u0110\u00e3 t\u1ea3i $completedDownloads/$totalDownloads video")
                    } else {
                        showStatus("\u0110\u00e3 t\u1ea3i xong t\u1ea5t c\u1ea3 video")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onProgressUpdate: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun onAllDownloadsCompleted() {
        try {
            Log.d("MainActivity", "All downloads completed")
            isDownloadComplete = true
            
            runOnUiThread {
                // Update UI to show completion
                updateProgressUI(100)
                tvTitle.text = "\u0110\u00e3 s\u1eb5n s\u00e0ng ph\u00e1t video"
                showStatus("\u0110\u00e3 t\u1ea3i xong t\u1ea5t c\u1ea3 video")
                
                // Add a small delay before starting playback for better UX
                handler.postDelayed({
                    try {
                        checkAndPlayCurrentPlaylist()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error starting playback: ${e.message}")
                        e.printStackTrace()
                    }
                }, 1000) // 1 second delay for smooth transition
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onAllDownloadsCompleted: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun onVideoReady(playlists: List<Playlist>) {
        try {
            Log.d("MainActivity", "Videos ready for playback, received ${playlists.size} playlists")
            videosLoaded = true
            
            runOnUiThread {
                // Hide loading UI
                loadingContainer.visibility = View.GONE
                videoView.visibility = View.VISIBLE
                
                // Start playing videos
                checkAndPlayCurrentPlaylist()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onVideoReady: ${e.message}")
            e.printStackTrace()
        }
    }
}
