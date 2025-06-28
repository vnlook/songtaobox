package com.vnlook.tvsongtao

import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.AnalogClock
import android.widget.Button
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.repository.ChangelogRepository
import com.vnlook.tvsongtao.repository.ChangelogRepositoryImpl
import com.vnlook.tvsongtao.repository.DeviceRepository
import com.vnlook.tvsongtao.repository.DeviceRepositoryImpl
import com.vnlook.tvsongtao.usecase.DataUseCase
import com.vnlook.tvsongtao.usecase.PermissionUseCase
import com.vnlook.tvsongtao.utils.ChangelogUtil
import com.vnlook.tvsongtao.utils.DeviceInfoUtil
import com.vnlook.tvsongtao.utils.KioskModeManager
import com.vnlook.tvsongtao.utils.NetworkUtil
import com.vnlook.tvsongtao.utils.PlaylistScheduler
import com.vnlook.tvsongtao.utils.VideoDownloadManager
import com.vnlook.tvsongtao.utils.VideoDownloadManagerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import android.os.Environment
import com.vnlook.tvsongtao.data.DataManager

/**
 * DigitalClockActivity displays a full-screen clock with both digital and analog displays.
 * It checks for cached playlists in SharedPreferences and transitions to MainActivity 
 * if valid videos are available.
 */
class DigitalClockActivity : AppCompatActivity() {

    private val TAG = "DigitalClockActivity"
    
    // UI Components
    private lateinit var dateText: TextView
    private lateinit var analogClock: AnalogClock
    
    // Use Cases
    private lateinit var dataUseCase: DataUseCase
    private lateinit var playlistScheduler: PlaylistScheduler
    private lateinit var changelogRepository: ChangelogRepository
    private lateinit var changelogUtil: ChangelogUtil
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var deviceInfoUtil: DeviceInfoUtil
    private lateinit var permissionUseCase: PermissionUseCase
    private lateinit var kioskModeManager: KioskModeManager
    private lateinit var dataManager: DataManager
    
    // Video Download Manager
    private lateinit var videoDownloadManager: VideoDownloadManager
    
    // Coroutine scope with crash protection
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Handler for time updates and playlist checks
    private val handler = Handler(Looper.getMainLooper())
    
    // Date format
    private val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
    
    // Protection flags
    private var isLocationComponentsInitialized = false
    private var isVideoDownloadInitialized = false
    private var isDeviceRegistered = false
    private var isFirstLoad = true
    private var isInitializationStarted = false
    
    // Location permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d(TAG, "✅ All permissions granted")
        } else {
            Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "🕐 DigitalClockActivity onCreate started")
        
        // PROTECTION: Prevent multiple onCreate calls from triggering multiple initializations
        if (isInitializationStarted) {
            Log.d(TAG, "🚫 Initialization already started, skipping duplicate onCreate")
            return
        }
        
        try {
            // Set full screen
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            
            // Keep screen on
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // Set content view first
            setContentView(R.layout.digital_clock_screen)
            Log.d(TAG, "Layout set successfully")
            
            // Initialize views immediately and ensure they're visible
            initializeViews()
            Log.d(TAG, "Views initialized successfully")
            
            // Initialize components
            initializeComponents()
            Log.d(TAG, "Components initialized successfully")
            
            // Set up test button
            setupTestButton()
            
            // Mark initialization as started
            isInitializationStarted = true
            
            // Start the main initialization flow
            startInitializationFlow()
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 CRITICAL: Error in onCreate: ${e.message}")
            e.printStackTrace()
            // Try to at least show basic UI
            try {
                setContentView(R.layout.digital_clock_screen)
                initializeViews()
            } catch (e2: Exception) {
                Log.e(TAG, "💥 CRITICAL: Failed to initialize basic UI: ${e2.message}")
            }
        }
    }
    
    private fun initializeViews() {
        // Initialize views
        dateText = findViewById(R.id.dateText)
        analogClock = findViewById(R.id.analogClock)
        
        // Configure TextClock explicitly
        val textClock = findViewById<TextClock>(R.id.textClock)
        textClock.format12Hour = null
        textClock.format24Hour = "HH:mm:ss"
        textClock.visibility = View.VISIBLE
        
        // Ensure all views are visible immediately
        dateText.visibility = View.VISIBLE
        analogClock.visibility = View.VISIBLE
        
        // Update date and start time updates
        updateDate()
        startTimeUpdates()
        
        Log.d(TAG, "UI components are now visible")
    }
    
    private fun initializeComponents() {
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
        
        // Initialize data manager
        dataManager = DataManager(this)
        
        // Initialize permission use case
        permissionUseCase = PermissionUseCase(this)
        
        // Initialize kiosk mode manager
        kioskModeManager = KioskModeManager(this)
    }
    
    private fun setupTestButton() {
        try {
            val btnForceDelete = findViewById<Button>(R.id.btnForceDeleteVideos)
            btnForceDelete.setOnClickListener {
                Log.d(TAG, "=== TEST BUTTON CLICKED ===")
                Toast.makeText(this, "🕐 Clock test completed! Check logs for details.", Toast.LENGTH_LONG).show()
            }
            Log.d(TAG, "Test button setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up test button: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun startInitializationFlow() {
        Log.d(TAG, "🚀 Starting initialization flow...")
        
        // Check permissions immediately
        Log.d(TAG, "🔒 Checking permissions status...")
        permissionUseCase.areAllPermissionsGranted()
        
        if (permissionUseCase.checkAndRequestPermissions()) {
            Log.d(TAG, "✅ All permissions granted in DigitalClockActivity")
        } else {
            Log.d(TAG, "⚠️ Some permissions not granted in DigitalClockActivity, requested")
        }
        
        // Enable full kiosk mode
        enableKioskMode()
        
        // Start checking for cached playlists periodically
        startPlaylistCheck()
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
     * Start periodic playlist checking every 30 seconds
     */
    private fun startPlaylistCheck() {
        Log.d(TAG, "🔄 Starting periodic playlist check...")
        
        // Check immediately
        checkCachedPlaylists()
        
        // Then check every 30 seconds
        handler.post(object : Runnable {
            override fun run() {
                checkCachedPlaylists()
                handler.postDelayed(this, 30000) // Check every 30 seconds
            }
        })
    }
    
    /**
     * Check for cached playlists in SharedPreferences and verify if videos exist
     */
    private fun checkCachedPlaylists() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 Checking cached playlists...")
                
                // Get cached playlists from SharedPreferences
                val cachedPlaylists = dataManager.getPlaylists()
                
                if (cachedPlaylists.isNotEmpty()) {
                    Log.d(TAG, "✅ Found ${cachedPlaylists.size} cached playlists")
                    
                    // Check if we have valid videos for these playlists
                    val hasValidVideos = checkIfValidVideosExist(cachedPlaylists)
                    
                    if (hasValidVideos) {

                        val  currentPlaylist = playlistScheduler.getCurrentPlaylist(cachedPlaylists)
                        if (currentPlaylist != null) {
                            Log.d(TAG, "🎬 Found valid videos, switching to MainActivity")
                            withContext(Dispatchers.Main) {
                                switchToMainActivity()
                            }
                        } else {
                            Log.d(TAG, "📭 No valid playlist found for cached playlists")
                        }

                    } else {
                        Log.d(TAG, "📭 No valid videos found for cached playlists")
                    }
                } else {
                    Log.d(TAG, "📭 No cached playlists found")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "💥 Error checking cached playlists: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Check if a file is a video file based on MIME type and extension
     */
    private fun isVideoFile(file: File): Boolean {
        try {
            // First, try to get MIME type from the system
            val uri = Uri.fromFile(file)
            val mimeType = contentResolver.getType(uri)
            
            if (mimeType != null && mimeType.startsWith("video/")) {
                return true
            }
            
            // Fallback to extension-based check for common video formats
            val fileName = file.name.lowercase()
            val videoExtensions = listOf(
                ".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", 
                ".webm", ".m4v", ".3gp", ".ts", ".mpg", ".mpeg"
            )
            
            return videoExtensions.any { fileName.endsWith(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking if file is video: ${e.message}")
            // Fallback to basic extension check
            return file.name.lowercase().endsWith(".mp4")
        }
    }

    /**
     * Check if valid videos exist for the cached playlists
     */
    private fun checkIfValidVideosExist(playlists: List<Playlist>): Boolean {
        return try {
            val moviesDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "")
            if (!moviesDir.exists()) {
                Log.d(TAG, "📁 Movies directory does not exist")
                return false
            }
            
            val videoFiles = moviesDir.listFiles { file -> 
                file.isFile && isVideoFile(file)
            }
            
            if (videoFiles == null || videoFiles.isEmpty()) {
                Log.d(TAG, "📁 No video files found in movies directory")
                return false
            }
            
            Log.d(TAG, "📁 Found ${videoFiles.size} video files in movies directory")
            
            // Check if we have videos for at least one playlist
            val allVideos = dataManager.getVideos()
            var hasValidVideos = false
            
            for (playlist in playlists) {
                val playlistVideos = allVideos.filter { video ->
                    playlist.videoIds.contains(video.id)
                }
                
//                val downloadedVideos = playlistVideos.filter { video ->
//                    video.isDownloaded && !video.localPath.isNullOrEmpty() && File(video.localPath!!).exists()
//                }
                
                if (playlistVideos.isNotEmpty()) {
                    Log.d(TAG, "✅ Playlist ${playlist.id} has ${playlistVideos.size} valid videos")
                    hasValidVideos = true
                    break
                }
            }
            
            return hasValidVideos
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error checking for valid videos: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Switch to MainActivity to play videos
     */
    private fun switchToMainActivity() {
        Log.d(TAG, "🎬 Switching to MainActivity...")
        
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        
        // Use ActivityOptions for smooth transition
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            val options = ActivityOptions.makeCustomAnimation(
                this,
                R.anim.fade_in,
                R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
        } else {
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        
        finish()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (::permissionUseCase.isInitialized && permissionUseCase.handlePermissionResult(requestCode, permissions, grantResults)) {
            Log.d(TAG, "✅ Permission granted in DigitalClockActivity")
        } else {
            Log.d(TAG, "❌ Permission denied in DigitalClockActivity")
        }
    }
    
    /**
     * Enable kiosk mode
     */
    private fun enableKioskMode() {
        try {
            kioskModeManager.enableFullKioskMode(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling kiosk mode: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Always re-enable kiosk mode on resume
        enableKioskMode()
    }
    
    /**
     * Block physical keys like Back, Home, Recent Apps
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (KioskModeManager.KIOSK_MODE_ENABLED) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_APP_SWITCH,
                KeyEvent.KEYCODE_POWER -> {
                    return true // Block key
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    
    /**
     * Block Back button
     */
    override fun onBackPressed() {
        if (KioskModeManager.KIOSK_MODE_ENABLED) {
            return
        }
        super.onBackPressed()
    }
    
    override fun onDestroy() {
        try {
            Log.d(TAG, "🔄 DigitalClockActivity onDestroy started")
            
            // Cancel coroutine scope first to stop any ongoing operations
            coroutineScope.cancel()
            Log.d(TAG, "✅ Coroutine scope cancelled")
            
            // Remove any pending handlers
            handler.removeCallbacksAndMessages(null)
            Log.d(TAG, "✅ Handler callbacks removed")
            
            // Reset initialization flag
            isInitializationStarted = false
            
            // Disable kiosk mode when activity is destroyed
            if (::kioskModeManager.isInitialized) {
                kioskModeManager.stopLockTask(this)
                Log.d(TAG, "✅ Kiosk mode disabled")
            }
            
            Log.d(TAG, "✅ DigitalClockActivity onDestroy completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 CRITICAL: Error in onDestroy: ${e.message}")
            e.printStackTrace()
        } finally {
            super.onDestroy()
        }
    }
}
