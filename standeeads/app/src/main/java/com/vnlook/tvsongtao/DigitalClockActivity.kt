package com.vnlook.tvsongtao

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.AnalogClock
import android.widget.TextClock
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.repository.ChangelogRepository
import com.vnlook.tvsongtao.repository.ChangelogRepositoryImpl
import com.vnlook.tvsongtao.repository.DeviceRepository
import com.vnlook.tvsongtao.repository.DeviceRepositoryImpl
import com.vnlook.tvsongtao.usecase.DataUseCase
import com.vnlook.tvsongtao.usecase.PermissionUseCase
import com.vnlook.tvsongtao.utils.ChangelogUtil
import com.vnlook.tvsongtao.utils.DeviceInfoUtil
import com.vnlook.tvsongtao.utils.PlaylistScheduler
import com.vnlook.tvsongtao.utils.VideoDownloadManager
import com.vnlook.tvsongtao.utils.VideoDownloadManagerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DigitalClockActivity displays a full-screen clock with both digital and analog displays.
 * It checks for playlists and transitions to MainActivity if playlists are available.
 */
class DigitalClockActivity : AppCompatActivity(), VideoDownloadManagerListener {

    private lateinit var dateText: TextView
    private lateinit var analogClock: AnalogClock
    private lateinit var videoDownloadManager: VideoDownloadManager
    private lateinit var dataUseCase: DataUseCase
    private lateinit var playlistScheduler: PlaylistScheduler
    private lateinit var changelogRepository: ChangelogRepository
    private lateinit var changelogUtil: ChangelogUtil
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var deviceInfoUtil: DeviceInfoUtil
    private lateinit var permissionUseCase: PermissionUseCase
    private val handler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set full screen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        setContentView(R.layout.digital_clock_screen)
        
        // Initialize views
        dateText = findViewById(R.id.dateText)
        analogClock = findViewById(R.id.analogClock)
        
        // Configure TextClock explicitly
        val textClock = findViewById<TextClock>(R.id.textClock)
//        textClock.format12Hour = "hh:mm:ss a"
        textClock.format12Hour = null
        textClock.format24Hour = "HH:mm:ss"
        textClock.visibility = View.VISIBLE
        
        // Update date and start time updates
        updateDate()
        startTimeUpdates()
        
        // Initialize data use case and playlist scheduler
        dataUseCase = DataUseCase(this)
        dataUseCase.initialize()
        playlistScheduler = PlaylistScheduler()
        
        // Initialize changelog repository and util
        changelogRepository = ChangelogRepositoryImpl(this)
        changelogUtil = ChangelogUtil(this, changelogRepository)
        
        // Initialize device repository and util
        deviceRepository = DeviceRepositoryImpl(this)
        deviceInfoUtil = DeviceInfoUtil(this, deviceRepository)
        
        // Initialize permission use case
        permissionUseCase = PermissionUseCase(this)
        
        // Log that we're starting the activity
        Log.d(TAG, "Digital clock screen started")
        
        // Check permissions immediately
        if (permissionUseCase.checkAndRequestPermissions()) {
            Log.d(TAG, "All permissions granted in DigitalClockActivity")
        } else {
            Log.d(TAG, "Some permissions not granted in DigitalClockActivity, requested")
        }
        
        // Register device info if needed
        registerDeviceInfo()
        
        // Start checking for playlists with a slight delay to ensure UI is visible
        handler.postDelayed({
            checkCompareAndDownloadPlaylists()
        }, 1000)
    }
    
    private fun startTimeUpdates() {
        // Update time every second
        handler.post(object : Runnable {
            override fun run() {
                updateDate()
                handler.postDelayed(this, 1000)
            }
        })
    }
    
    private fun updateDate() {
        val currentDate = Date()
        dateText.text = dateFormat.format(currentDate)
    }
    
    /**
     * Register device information with the API
     * Creates a new device if it doesn't exist, or updates an existing one
     */
    private fun registerDeviceInfo() {
        Log.d(TAG, "Checking if device info needs to be registered")
        
        // Run in a coroutine to avoid blocking the UI thread
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val result = deviceInfoUtil.registerOrUpdateDevice()
                withContext(Dispatchers.Main) {
                    if (result != null) {
                        Log.d(TAG, "Device registration successful: $result")
                    } else {
                        Log.e(TAG, "Device registration failed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering device: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun checkCompareAndDownloadPlaylists() {
        try {
            // Initialize video download manager if needed
            if (!::videoDownloadManager.isInitialized) {
                videoDownloadManager = VideoDownloadManager(this)
                videoDownloadManager.setDownloadListener(this)
                videoDownloadManager.initializeVideoDownload(this)
            }
            Log.d(TAG, "Start download Playlist")
            // Start download process
            
            // Don't check for changelog updates when app is first opened
            // The scheduler job will handle periodic checks


        } catch (e: Exception) {
            Log.e(TAG, "Error checking for playlists: ${e.message}")
        }
    }
    
    /**
     * Check for changelog updates in the background
     * If there are changes, reload playlists
     */
    private fun checkForChangelogUpdates() {
        coroutineScope.launch {
            try {
                Log.d(TAG, "Checking for changelog updates")
                
                // Check if there are changes in the changelog
                val hasChanges = changelogUtil.checkChange()
                
                if (hasChanges) {
                    Log.d(TAG, "Changelog updates detected, reloading playlists")
                    
                    // Reload playlists from API
                    withContext(Dispatchers.Main) {
                        // Re-initialize video download manager to reload playlists
                        if (::videoDownloadManager.isInitialized) {
                            videoDownloadManager.cleanup()
                        }
                        videoDownloadManager = VideoDownloadManager(this@DigitalClockActivity)
                        videoDownloadManager.setDownloadListener(this@DigitalClockActivity)
                        videoDownloadManager.initializeVideoDownload(this@DigitalClockActivity)
                        
                        Log.d(TAG, "Playlists reloaded due to changelog updates")
                    }
                } else {
                    Log.d(TAG, "No changelog updates detected")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for changelog updates: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
//    private fun checkForPlaylists() {
//        try {
//            // Initialize video download manager if needed
//            if (!::videoDownloadManager.isInitialized) {
//                videoDownloadManager = VideoDownloadManager(this)
//                videoDownloadManager.setDownloadListener(this)
//                videoDownloadManager.initializeVideoDownload(this)
//            }
//
//            // Check if there are any playlists
//            val playlists = dataUseCase.getPlaylists()
//
//            if (playlists.isNotEmpty()) {
//                // Use PlaylistScheduler to check if there's a valid playlist for the current time
//                val currentPlaylist = playlistScheduler.getCurrentPlaylist(playlists)
//
//                if (currentPlaylist != null) {
//                    Log.d(TAG, "Found valid playlist for current time: ${currentPlaylist.id}, starting video download")
//                    // Start download process
//                    videoDownloadManager.initializeVideoDownload(this)
//                } else {
//                    Log.d(TAG, "No playlist scheduled for current time, staying on clock screen")
//                    // No playlist scheduled for current time, stay on the clock screen
//
//                    // Schedule another check in 1 minute
//                    handler.postDelayed({
//                        checkForPlaylists()
//                    }, 60000) // Check again in 1 minute
//                }
//            } else {
//                Log.d(TAG, "No playlists found, staying on clock screen")
//                // No playlists, just stay on the clock screen
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Error checking for playlists: ${e.message}")
//        }
//    }

//    override fun onProgressUpdate(completedDownloads: Int, totalDownloads: Int, progressPercent: Int) {
//        // We don't show download progress in this screen
//        Log.d(TAG, "Download progress: $completedDownloads/$totalDownloads - $progressPercent%")
//    }
    override fun onProgressUpdate(completed: Int, total: Int, progressPercent: Int) {
        Log.d(TAG, "Download progress: $completed/$total - $progressPercent%")
    }
    
    override fun onAllDownloadsCompleted() {
        Log.d(TAG, "All downloads completed")
        
        // Use coroutines to get playlists
        coroutineScope.launch {
            try {
                // Check if there are any playlists to play
                val playlists = dataUseCase.getPlaylists()
                
                withContext(Dispatchers.Main) {
                    if (playlists.isNotEmpty()) {
                        // Use PlaylistScheduler to check if there's a valid playlist for the current time
                        val currentPlaylist = playlistScheduler.getCurrentPlaylist(playlists)
                        
                        if (currentPlaylist != null) {
                            Log.d(TAG, "Found valid playlist for current time: ${currentPlaylist.id}, transitioning to video playback")
                            // Wait a moment before starting MainActivity
                            handler.postDelayed({
                                startMainActivity()
                            }, 1000)
                        } else {
                            Log.d(TAG, "No playlist scheduled for current time, staying on digital clock screen")
                            // No playlist scheduled for current time, stay on the clock screen
                        }
                    } else {
                        Log.d(TAG, "No playlists found, staying on digital clock screen")
                        // No playlists to play, stay on the clock screen
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting playlists: ${e.message}")
            }
        }
    }
    
    override fun onVideoReady(playlists: List<Playlist>) {
//        runOnUiThread {
//            if (playlists.isNotEmpty()) {
//                // Use PlaylistScheduler to check if there's a valid playlist for the current time
//                val currentPlaylist = playlistScheduler.getCurrentPlaylist(playlists)
//
//                if (currentPlaylist != null) {
//                    Log.d(TAG, "Videos ready with valid playlist for current time: ${currentPlaylist.id}, starting MainActivity")
//                    startMainActivity()
//                } else {
//                    Log.d(TAG, "No playlist scheduled for current time in onVideoReady, staying on digital clock screen")
//                    // No playlist scheduled for current time, stay on the clock screen
//
//                    // Schedule a check in 1 minute to see if a new playlist should start
//                    handler.postDelayed({
//                        checkForPlaylists()
//                    }, 60000) // Check again in 1 minute
//                }
//            } else {
//                Log.d(TAG, "No playlists available in onVideoReady, staying on digital clock screen")
//                // No playlists to play, stay on the clock screen
//            }
//        }
    }
    
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        
        // Use ActivityOptions for a more modern approach to transitions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // Create ActivityOptions with custom animations
            val options = ActivityOptions.makeCustomAnimation(
                this,
                R.anim.fade_in,
                R.anim.fade_out
            )
            
            // Start activity with animation options
            startActivity(intent, options.toBundle())
        } else {
            // Fallback for older devices
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        
        finish()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (::permissionUseCase.isInitialized && permissionUseCase.handlePermissionResult(requestCode, permissions, grantResults)) {
            Log.d(TAG, "Permission granted in DigitalClockActivity")
            // Permissions granted, continue with normal flow
            checkCompareAndDownloadPlaylists()
        } else {
            Log.d(TAG, "Permission denied in DigitalClockActivity")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::videoDownloadManager.isInitialized) {
            videoDownloadManager.cleanup()
        }
    }
    
    companion object {
        private const val TAG = "DigitalClockActivity"
    }
}
