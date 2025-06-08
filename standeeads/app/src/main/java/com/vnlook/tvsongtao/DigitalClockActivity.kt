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
import com.vnlook.tvsongtao.usecase.DataUseCase
import com.vnlook.tvsongtao.utils.PlaylistScheduler
import com.vnlook.tvsongtao.utils.VideoDownloadManager
import com.vnlook.tvsongtao.utils.VideoDownloadManagerListener
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
    private val handler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    
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
        textClock.format12Hour = "hh:mm:ss a"
        textClock.format24Hour = "HH:mm:ss"
        textClock.visibility = View.VISIBLE
        
        // Update date and start time updates
        updateDate()
        startTimeUpdates()
        
        // Initialize data use case and playlist scheduler
        dataUseCase = DataUseCase(this)
        dataUseCase.initialize()
        playlistScheduler = PlaylistScheduler()
        
        // Log that we're starting the activity
        Log.d(TAG, "Digital clock screen started")
        
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


        } catch (e: Exception) {
            Log.e(TAG, "Error checking for playlists: ${e.message}")
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
        runOnUiThread {
            Log.d(TAG, "All downloads completed")
            
            // Check if there are any playlists to play
            val playlists = dataUseCase.getPlaylists()
            
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
                    
                    // Schedule a check in 1 minute to see if a new playlist should start
//                    handler.postDelayed({
//                        checkForPlaylists()
//                    }, 60000) // Check again in 1 minute
                }
            } else {
                Log.d(TAG, "No playlists found, staying on digital clock screen")
                // No playlists to play, stay on the clock screen
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
