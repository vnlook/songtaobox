package com.vnlook.tvsongtao

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.ui.PlayerView
import com.vnlook.tvsongtao.usecase.*
import com.vnlook.tvsongtao.utils.KioskModeManager
import com.vnlook.tvsongtao.utils.VideoDownloadManagerListener

/**
 * Main activity for the Standee Ads application
 * Refactored to use usecase pattern for better code organization
 */
class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var videoView: PlayerView
    private lateinit var tvStatus: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvPercentage: TextView
    private lateinit var loadingContainer: View
    private lateinit var progressBar: ProgressBar
    
    // UseCases
    private lateinit var uiUseCase: UIUseCase
    private lateinit var dataUseCase: DataUseCase
    private lateinit var videoUseCase: VideoUseCase
    private lateinit var permissionUseCase: PermissionUseCase
    private lateinit var appLifecycleUseCase: AppLifecycleUseCase
    private lateinit var kioskModeManager: KioskModeManager
    
    private val handler = Handler(Looper.getMainLooper())

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

            // Find UI components
            videoView = findViewById(R.id.playerView)
            tvStatus = findViewById(R.id.tvStatus)
            tvTitle = findViewById(R.id.tvTitle)
            tvPercentage = findViewById(R.id.tvPercentage)
            loadingContainer = findViewById(R.id.loadingContainer)
            progressBar = findViewById(R.id.progressBar)
            
            // Initialize usecases
            initializeUseCases()
            
            // Setup fullscreen
            uiUseCase.setupFullScreen()
            
            // Initialize UI
            uiUseCase.initializeUI(videoView, tvStatus, tvTitle, tvPercentage, loadingContainer, progressBar)
            
            // Check permissions immediately in the main thread
            // This ensures permissions are requested as soon as the activity is created
            permissionUseCase.checkAndRequestPermissions()
            
            // Initialize and enable kiosk mode
            kioskModeManager = KioskModeManager(this)
            enableKioskMode()
            
            // Start keep-alive mechanism
            appLifecycleUseCase.startKeepAlive()

            // Initialize managers in a separate thread to avoid ANR
            Thread {
                try {
                    initializeApplication()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error initializing application: ${e.message}")
                    e.printStackTrace()
                    runOnUiThread {
                        uiUseCase.showStatus("Lỗi khởi tạo: ${e.message}")
                    }
                }
            }.start()
            
            // Mark app as initialized
            appLifecycleUseCase.setAppInitialized(true)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}")
            e.printStackTrace()
            
            // Even if there's an error, make sure we show something and don't close
            try {
                if (::uiUseCase.isInitialized) {
                    uiUseCase.showStatus("Đã xảy ra lỗi: ${e.message}")
                    uiUseCase.showToast("Lỗi khởi tạo ứng dụng: ${e.message}")
                }
                
                // Start keep-alive mechanism anyway
                if (::appLifecycleUseCase.isInitialized) {
                    appLifecycleUseCase.startKeepAlive()
                }
            } catch (e2: Exception) {
                Log.e("MainActivity", "Error in error handling: ${e2.message}")
            }
        }
    }
    
    /**
     * Initialize all usecases
     */
    private fun initializeUseCases() {
        uiUseCase = UIUseCase(this)
        dataUseCase = DataUseCase(this)
        permissionUseCase = PermissionUseCase(this)
        
        // Initialize data manager
        dataUseCase.initialize()
        
        // Initialize video usecase after data usecase
        videoUseCase = VideoUseCase(this, videoView, uiUseCase, dataUseCase)
        
        // NOTE: Changelog timer is now managed globally by ChangelogTimerManager singleton
        // Started automatically in StandeeAdsApplication.onCreate()
        Log.i("MainActivity", "📋 Using global changelog timer from ChangelogTimerManager")
        
        // Initialize app lifecycle usecase last
        appLifecycleUseCase = AppLifecycleUseCase(this, videoView, uiUseCase, videoUseCase)
    }
    
    /**
     * Initialize the application
     */
    private fun initializeApplication() {
        // Initialize video managers
        videoUseCase.initializeManagers()
        
        // Load videos if permissions are granted
        // Note: We've already checked permissions in onCreate, but we check again here
        // in case permissions were granted between onCreate and now
        if (permissionUseCase.checkAndRequestPermissions()) {
            Log.d("MainActivity", "Permissions granted, loading videos")
            videoUseCase.checkAndLoadVideos()
        } else {
            Log.d("MainActivity", "Permissions not granted yet, videos will be loaded after permission grant")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (permissionUseCase.handlePermissionResult(requestCode, permissions, grantResults)) {
            // Permission granted, load videos
            videoUseCase.checkAndLoadVideos()
        } else {
            // Permission denied
            uiUseCase.showToast("Ứng dụng cần quyền truy cập để hoạt động")
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
            Log.e("MainActivity", "Error enabling kiosk mode: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun onResume() {
        super.onResume()
        uiUseCase.setupFullScreen()
        appLifecycleUseCase.onResume()
        
        // Always re-enable kiosk mode on resume
        if (::kioskModeManager.isInitialized) {
            enableKioskMode()
        }
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

    override fun onPause() {
        super.onPause()
        appLifecycleUseCase.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Disable kiosk mode when activity is destroyed
        try {
            if (::kioskModeManager.isInitialized) {
                kioskModeManager.stopLockTask(this)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error disabling kiosk mode: ${e.message}")
        }
        
        appLifecycleUseCase.onDestroy()
    }
}
