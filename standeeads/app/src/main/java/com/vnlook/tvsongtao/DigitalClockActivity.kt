package com.vnlook.tvsongtao

import android.app.ActivityOptions
import android.content.Intent
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
 * It checks for playlists and transitions to MainActivity if playlists are available.
 */
class DigitalClockActivity : AppCompatActivity(), VideoDownloadManagerListener {

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
    
    // Video Download Manager
    private lateinit var videoDownloadManager: VideoDownloadManager
    
    // Coroutine scope
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Handler for time updates
    private val handler = Handler(Looper.getMainLooper())
    
    // Date format
    private val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
    
    // Protection flags
    private var isLocationComponentsInitialized = false
    private var isVideoDownloadInitialized = false
    private var isDeviceRegistered = false
    private var isFirstLoad = true
    private var isInitializationStarted = false // NEW: Prevent multiple initialization calls
    
    // Location permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Permissions granted, proceed with location operations
            initializeLocationDependentComponents()
        } else {
            // Permissions denied, show a message and continue without location
            Toast.makeText(this, "Location permission is required for better functionality", 
                Toast.LENGTH_LONG).show()
            initializeLocationDependentComponents()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "Digital clock activity onCreate started")
        
        // PROTECTION: Prevent multiple onCreate calls from triggering multiple initializations
        if (isInitializationStarted) {
            Log.d(TAG, "🚫 Initialization already started, skipping duplicate onCreate")
            return
        }
        
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
        try {
            initializeViews()
            Log.d(TAG, "Views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing views: ${e.message}")
            e.printStackTrace()
        }
        
        // Initialize components
        try {
            initializeComponents()
            Log.d(TAG, "Components initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing components: ${e.message}")
            e.printStackTrace()
        }
        
        // Set up test button
        setupTestButton()
        
        // Mark initialization as started
        isInitializationStarted = true
        
        // Start the main initialization flow with a slight delay to ensure UI is rendered
        handler.postDelayed({
            startInitializationFlow()
        }, 500) // Reduced delay but still allow UI to render
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
        
        // Initialize permission use case
        permissionUseCase = PermissionUseCase(this)
        
        // Initialize kiosk mode manager
        kioskModeManager = KioskModeManager(this)
    }
    
    private fun setupTestButton() {
        try {
            val btnForceDelete = findViewById<Button>(R.id.btnForceDeleteVideos)
            btnForceDelete.setOnClickListener {
                Log.d(TAG, "=== TEST BUTTON CLICKED - Test ===")
                
                // Initialize video download manager if needed
                if (!::videoDownloadManager.isInitialized) {
                    videoDownloadManager = VideoDownloadManager(this)
                }
                
                // REMOVED: Force delete method no longer available
                // videoDownloadManager.forceDeleteAllVideosNow()
                
                Toast.makeText(this, "🗑️ Test completed! Check logs for details.", Toast.LENGTH_LONG).show()
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
        if (permissionUseCase.checkAndRequestPermissions()) {
            Log.d(TAG, "✅ All permissions granted in DigitalClockActivity")
        } else {
            Log.d(TAG, "⚠️ Some permissions not granted in DigitalClockActivity, requested")
        }
        
        // Enable full kiosk mode
        enableKioskMode()
        
        // Check and request location permissions
        checkLocationPermissions()
        
        // Check network status before proceeding with network-dependent operations
        val hasNetwork = NetworkUtil.isNetworkAvailable(this)
        Log.d(TAG, "📡 Network status: ${if (hasNetwork) "Available" else "Not available"}")
        
        if (hasNetwork) {
            Log.d(TAG, "🌐 Network available - proceeding with full initialization")
            // Register device info if needed
            registerDeviceInfo()
            
            // Start checking for playlists
            checkCompareAndDownloadPlaylists()
        } else {
            Log.d(TAG, "🚫 No network - skipping network-dependent operations")
            Log.d(TAG, "⏰ App will stay on clock screen until network is available")
            
            // Still try to check for cached playlists without API calls
            checkCompareAndDownloadPlaylists()
        }
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
    private fun checkLocationPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // Check which permissions we need to request
        DeviceInfoUtil.REQUIRED_PERMISSIONS.forEach { permission ->
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(permission)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            // Request missing permissions
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // All permissions already granted, proceed with location operations
            initializeLocationDependentComponents()
        }
    }
    
    private fun initializeLocationDependentComponents() {
        // PROTECTION: Prevent duplicate initialization
        if (isLocationComponentsInitialized) {
            Log.d(TAG, "🚫 Location components already initialized, skipping")
            return
        }
        
        Log.d(TAG, "🔄 Initializing location dependent components...")
        isLocationComponentsInitialized = true
        
        // REMOVED: checkCompareAndDownloadPlaylists() - already called in startInitializationFlow
        Log.d(TAG, "✅ Location dependent components initialized (checkCompareAndDownloadPlaylists already called)")
        
        // Also register device info which might use location
        registerDeviceInfo()
    }
    
    private fun registerDeviceInfo() {
        // PROTECTION: Prevent duplicate device registration
        if (isDeviceRegistered) {
            Log.d(TAG, "🚫 Device already registered, skipping")
            return
        }
        
        Log.d(TAG, "Checking if device info needs to be registered")
        
        // Check network connectivity first
        if (!NetworkUtil.isNetworkAvailable(this)) {
            Log.d(TAG, "🚫 No network available, skipping device registration")
            return
        }
        
        Log.d(TAG, "📡 Network available, proceeding with device registration")
        isDeviceRegistered = true
        
        // Run in a coroutine to avoid blocking the UI thread
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val result = deviceInfoUtil.registerOrUpdateDevice()
                withContext(Dispatchers.Main) {
                    if (result != null) {
                        Log.d(TAG, "✅ Device registration successful: $result")
                    } else {
                        Log.e(TAG, "❌ Device registration failed")
                        // Reset flag on failure so it can be retried later
                        isDeviceRegistered = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "💥 Error registering device: ${e.message}")
                e.printStackTrace()
                // Reset flag on error so it can be retried later
                withContext(Dispatchers.Main) {
                    isDeviceRegistered = false
                }
            }
        }
    }

    private fun checkCompareAndDownloadPlaylists() {
        // PROTECTION: Prevent duplicate video download initialization
        if (isVideoDownloadInitialized) {
            Log.d(TAG, "🚫 Video download already initialized, skipping")
            return
        }
        
        try {
            Log.d(TAG, "🔄 Starting simple playlist check...")
            isVideoDownloadInitialized = true
            
            // STEP 1: Check if we have local playlists first
            val dataManager = DataManager(this)
            val cachedPlaylists = dataManager.getPlaylists()
            
            if (cachedPlaylists.isNotEmpty()) {
                Log.d(TAG, "✅ Found ${cachedPlaylists.size} cached playlists, checking for videos...")
                
                // Check if we have videos for these playlists
                val hasVideos = checkIfVideosExist(cachedPlaylists)
                
                if (hasVideos) {
                    Log.d(TAG, "🎬 Found local videos, switching to video screen immediately")
                    switchToVideoScreen()
                    return
                } else {
                    Log.d(TAG, "📭 No local videos found for cached playlists")
                }
            } else {
                Log.d(TAG, "📭 No cached playlists found")
            }
            
            // STEP 2: Only call API on first load
            if (isFirstLoad) {
                Log.d(TAG, "🚀 First load - calling API to get playlists")
                isFirstLoad = false
                
                // Check network first
                if (!NetworkUtil.isNetworkAvailable(this)) {
                    Log.d(TAG, "🚫 No network on first load, staying on clock screen")
                    // No network and no cached videos - stay on clock screen
                    return
                }
                
                // Initialize video download manager for API call
                if (!::videoDownloadManager.isInitialized) {
                    videoDownloadManager = VideoDownloadManager(this)
                    videoDownloadManager.setDownloadListener(this)
                }
                
                // Call API once
                videoDownloadManager.initializeVideoDownloadWithNetworkCheck(this)
            } else {
                Log.d(TAG, "⏰ Not first load, staying on clock screen (API will be called by scheduler)")
                // Not first load and no cached videos - stay on clock screen
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in playlist check: ${e.message}")
            e.printStackTrace()
            // Reset flag on error so it can be retried
            isVideoDownloadInitialized = false
        }
    }
    
    private fun checkIfVideosExist(playlists: List<Playlist>): Boolean {
        return try {
            val moviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "standeeads")
            if (!moviesDir.exists()) {
                Log.d(TAG, "Movies directory does not exist")
                return false
            }
            
            val videoFiles = moviesDir.listFiles { file -> 
                file.isFile && file.name.endsWith(".mp4", ignoreCase = true)
            }
            
            val hasVideos = videoFiles != null && videoFiles.isNotEmpty()
            Log.d(TAG, "📁 Movies directory has ${videoFiles?.size ?: 0} video files")
            
            hasVideos
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for videos: ${e.message}")
            false
        }
    }
    
    private fun switchToVideoScreen() {
        Log.d(TAG, "🎬 Switching to video screen...")
        startMainActivity()
    }
    
    /**
     * Check for changelog updates in the background
     * If there are changes, reload playlists
     */
    private fun checkForChangelogUpdates() {
        // Check network connectivity first
        if (!NetworkUtil.isNetworkAvailable(this)) {
            Log.d(TAG, "No network available, skipping changelog check")
            return
        }
        
        coroutineScope.launch {
            try {
                Log.d(TAG, "Checking for changelog updates with network available")
                
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
                        videoDownloadManager.initializeVideoDownloadWithNetworkCheck(this@DigitalClockActivity)
                        
                        Log.d(TAG, "Playlists reloaded due to changelog updates with network check")
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
        Log.d(TAG, "onVideoReady called with ${playlists.size} playlists")
        runOnUiThread {
            if (playlists.isNotEmpty()) {
                Log.d(TAG, "Videos ready with playlists available, starting MainActivity immediately")
                // In offline mode or when videos are ready, always play them
                // Don't check for time scheduling in offline mode
                startMainActivity()
            } else {
                Log.d(TAG, "No playlists available in onVideoReady, staying on digital clock screen")
                // No playlists to play, stay on the clock screen
            }
        }
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
            // Permissions granted, continue with location dependent components only if not already initialized
            if (!isLocationComponentsInitialized) {
                initializeLocationDependentComponents()
            } else {
                Log.d(TAG, "🚫 Location components already initialized, skipping duplicate call")
            }
        } else {
            Log.d(TAG, "Permission denied in DigitalClockActivity")
        }
    }
    
    /**
     * Enable kiosk mode
     */
    private fun enableKioskMode() {
        try {
            // Use the enhanced kiosk mode that works without Device Owner permissions
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
     * Chặn các phím vật lý như Back, Home, Recent Apps
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Chặn các phím điều hướng khi chế độ kiosk được bật
        if (KioskModeManager.KIOSK_MODE_ENABLED) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_APP_SWITCH,
                KeyEvent.KEYCODE_POWER -> {
                    return true // Chặn phím
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    
    /**
     * Chặn nút Back
     */
    override fun onBackPressed() {
        // Không làm gì khi nút Back được nhấn trong chế độ kiosk
        if (KioskModeManager.KIOSK_MODE_ENABLED) {
            return
        }
        super.onBackPressed()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        Log.d(TAG, "🔄 DigitalClockActivity onDestroy - resetting flags")
        
        // Reset all protection flags to allow fresh initialization if activity is recreated
        isLocationComponentsInitialized = false
        isVideoDownloadInitialized = false
        isDeviceRegistered = false
        isInitializationStarted = false
        // Keep isFirstLoad as is - it should persist across activity recreations
        
        // Disable kiosk mode when activity is destroyed
        try {
            kioskModeManager.stopLockTask(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling kiosk mode: ${e.message}")
        }
        
        if (::videoDownloadManager.isInitialized) {
            videoDownloadManager.cleanup()
        }
    }
}
