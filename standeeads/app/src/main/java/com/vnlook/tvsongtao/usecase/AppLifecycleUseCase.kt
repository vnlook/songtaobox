package com.vnlook.tvsongtao.usecase

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.VideoView
import androidx.media3.ui.PlayerView
import com.vnlook.tvsongtao.MainActivity
import com.vnlook.tvsongtao.StandeeAdsApplication

/**
 * UseCase responsible for app lifecycle management
 */
class AppLifecycleUseCase(
    private val activity: MainActivity,
    private val videoView: PlayerView,
    private val uiUseCase: UIUseCase,
    private val videoUseCase: VideoUseCase
) {
    private val handler = Handler(Looper.getMainLooper())
    private var appInitialized = false
    
    // Keep app alive with this runnable
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            try {
                Log.d("AppLifecycleUseCase", "Keep-alive check running")
                // Make sure loading UI is visible if videos are not loaded yet
                if (!videoUseCase.isVideosLoaded()) {
                    activity.runOnUiThread {
                        try {
                            uiUseCase.showLoading(videoView)
                            
                            // Update progress periodically if it's stuck at 0
                            uiUseCase.updateProgressUI(5) // Show some progress to indicate app is alive
                        } catch (e: Exception) {
                            Log.e("AppLifecycleUseCase", "Error updating UI in keepAlive: ${e.message}")
                        }
                    }
                }
                
                // Schedule next run
                handler.postDelayed(this, 3000) // Check every 3 seconds
            } catch (e: Exception) {
                Log.e("AppLifecycleUseCase", "Error in keepAliveRunnable: ${e.message}")
                e.printStackTrace()
                // Make sure we reschedule even if there's an error
                handler.postDelayed(this, 3000)
            }
        }
    }
    
    /**
     * Start the keep-alive mechanism
     */
    fun startKeepAlive() {
        handler.post(keepAliveRunnable)
    }
    
    /**
     * Stop the keep-alive mechanism
     */
    fun stopKeepAlive() {
        handler.removeCallbacks(keepAliveRunnable)
    }
    
    /**
     * Handle app resume
     */
    fun onResume() {
        try {
            Log.d("AppLifecycleUseCase", "onResume")
            
            // Update app foreground state
            StandeeAdsApplication.isAppInForeground = true
            
            // Check if we need to load videos
            if (appInitialized && !videoUseCase.isVideosLoaded()) {
                videoUseCase.checkAndLoadVideos()
            }
            
            // Make sure keep-alive is running
            if (!handler.hasCallbacks(keepAliveRunnable)) {
                Log.d("AppLifecycleUseCase", "Restarting keep-alive runnable")
                startKeepAlive()
            }
        } catch (e: Exception) {
            Log.e("AppLifecycleUseCase", "Error in onResume: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Handle app pause
     */
    fun onPause() {
        try {
            Log.d("AppLifecycleUseCase", "onPause")
            
            // Update app foreground state
            StandeeAdsApplication.isAppInForeground = false
            
            // Stop video playback
            videoUseCase.stopPlayback()
        } catch (e: Exception) {
            Log.e("AppLifecycleUseCase", "Error in onPause: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Handle app destroy
     */
    fun onDestroy() {
        try {
            Log.d("AppLifecycleUseCase", "onDestroy")
            
            // Stop keep-alive mechanism
            stopKeepAlive()
            
            // Clean up video resources
            videoUseCase.cleanup()
        } catch (e: Exception) {
            Log.e("AppLifecycleUseCase", "Error in onDestroy: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Set app initialized state
     */
    fun setAppInitialized(initialized: Boolean) {
        appInitialized = initialized
    }
    
    /**
     * Get app initialized state
     */
    fun isAppInitialized(): Boolean {
        return appInitialized
    }
}
