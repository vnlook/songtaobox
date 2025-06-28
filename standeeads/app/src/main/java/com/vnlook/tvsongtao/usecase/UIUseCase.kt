package com.vnlook.tvsongtao.usecase

import android.animation.ValueAnimator
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vnlook.tvsongtao.MainActivity

/**
 * UseCase responsible for UI-related operations in the app
 */
class UIUseCase(private val activity: MainActivity) {
    
    // UI Components
    private var tvStatus: TextView? = null
    private var tvTitle: TextView? = null
    private var tvPercentage: TextView? = null
    private var loadingContainer: View? = null
    private var progressBar: ProgressBar? = null
    private var lastProgress = 0
    private val progressAnimator = ValueAnimator()
    
    /**
     * Initialize UI components
     */
    fun initializeUI(
        videoView: View,
        tvStatus: TextView,
        tvTitle: TextView,
        tvPercentage: TextView,
        loadingContainer: View,
        progressBar: ProgressBar
    ) {
        try {
            Log.d("UIUseCase", "Initializing UI components")
            this.tvStatus = tvStatus
            this.tvTitle = tvTitle
            this.tvPercentage = tvPercentage
            this.loadingContainer = loadingContainer
            this.progressBar = progressBar
            
            // Make sure loading UI is visible from the start
            loadingContainer.visibility = View.VISIBLE
            videoView.visibility = View.GONE
            progressBar.progress = 0
            tvPercentage.text = "0%"
            showStatus("Đang khởi động ứng dụng...")
        } catch (e: Exception) {
            Log.e("UIUseCase", "Error initializing UI: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Setup fullscreen mode
     */
    fun setupFullScreen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.insetsController?.let {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            }
            
            // Keep screen on
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            Log.e("UIUseCase", "Error setting up fullscreen: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Show status message - always runs on UI thread
     */
    fun showStatus(message: String) {
        try {
            activity.runOnUiThread {
                try {
                    tvStatus?.let {
                        it.text = message
                        Log.d("UIUseCase", "Status: $message")
                    } ?: Log.w("UIUseCase", "Cannot show status, tvStatus not initialized: $message")
                } catch (e: Exception) {
                    Log.e("UIUseCase", "Error showing status on UI thread: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("UIUseCase", "Error showing status: ${e.message}")
        }
    }
    
    /**
     * Update progress UI with animation - always runs on UI thread
     */
    fun updateProgressUI(progress: Int) {
        try {
            activity.runOnUiThread {
                try {
                    val progressBarLocal = progressBar
                    val tvPercentageLocal = tvPercentage
                    
                    if (progressBarLocal != null && tvPercentageLocal != null) {
                        // Don't update if progress hasn't changed or is less than previous (unless it's a reset to 0)
                        if (progress == lastProgress || (progress < lastProgress && progress > 0)) {
                            return@runOnUiThread
                        }
                        
                        // Animate progress changes for smoother UI
                        progressAnimator.cancel()
                        progressAnimator.setIntValues(lastProgress, progress)
                        progressAnimator.addUpdateListener { animation ->
                            try {
                                val animatedValue = animation.animatedValue as Int
                                progressBarLocal.progress = animatedValue
                                tvPercentageLocal.text = "$animatedValue%"
                            } catch (e: Exception) {
                                Log.e("UIUseCase", "Error in progress animation: ${e.message}")
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
                        Log.d("UIUseCase", "Progress updated to $progress%")
                    } else {
                        Log.w("UIUseCase", "Cannot update progress UI, views not initialized: $progress%")
                    }
                } catch (e: Exception) {
                    Log.e("UIUseCase", "Error updating progress UI on UI thread: ${e.message}")
                    // Fallback to direct update without animation
                    try {
                        progressBar?.progress = progress
                        tvPercentage?.text = "$progress%"
                        lastProgress = progress
                    } catch (e2: Exception) {
                        Log.e("UIUseCase", "Error in fallback progress update: ${e2.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UIUseCase", "Error updating progress UI: ${e.message}")
        }
    }
    
        /**
     * Show loading UI - always runs on UI thread
     */
    fun showLoading(videoView: View) {
        try {
            activity.runOnUiThread {
                try {
                    loadingContainer?.visibility = View.VISIBLE
                    videoView.visibility = View.GONE
                } catch (e: Exception) {
                    Log.e("UIUseCase", "Error showing loading on UI thread: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("UIUseCase", "Error showing loading: ${e.message}")
        }
    }

    /**
     * Hide loading UI - always runs on UI thread
     */
    fun hideLoading(videoView: View) {
        try {
            activity.runOnUiThread {
                try {
                    loadingContainer?.visibility = View.GONE
                    videoView.visibility = View.VISIBLE
                } catch (e: Exception) {
                    Log.e("UIUseCase", "Error hiding loading on UI thread: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("UIUseCase", "Error hiding loading: ${e.message}")
        }
    }

    /**
     * Set title text - always runs on UI thread
     */
    fun setTitle(title: String) {
        try {
            activity.runOnUiThread {
                try {
                    tvTitle?.text = title
                } catch (e: Exception) {
                    Log.e("UIUseCase", "Error setting title on UI thread: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("UIUseCase", "Error setting title: ${e.message}")
        }
    }
    
    /**
     * Show toast message
     */
    fun showToast(message: String, duration: Int = Toast.LENGTH_LONG) {
        try {
            Toast.makeText(activity, message, duration).show()
        } catch (e: Exception) {
            Log.e("UIUseCase", "Error showing toast: ${e.message}")
        }
    }
}
