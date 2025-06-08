package com.vnlook.tvsongtao

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextClock
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.utils.VideoDownloadManager
import com.vnlook.tvsongtao.utils.VideoDownloadManagerListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ClockScreenActivity displays a full-screen clock while handling video downloads in the background.
 * Once all downloads are complete, it transitions to the MainActivity for video playback.
 */
//class ClockScreenActivity : AppCompatActivity(), VideoDownloadManagerListener {
//
//    private lateinit var dateText: TextView
//    private lateinit var downloadStatusText: TextView
//    private lateinit var downloadProgressBar: ProgressBar
//    private lateinit var videoDownloadManager: VideoDownloadManager
//    private val handler = Handler(Looper.getMainLooper())
//    private val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // Set full screen
//        window.setFlags(
//            WindowManager.LayoutParams.FLAG_FULLSCREEN,
//            WindowManager.LayoutParams.FLAG_FULLSCREEN
//        )
//
//        // Keep screen on
//        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//
//        setContentView(R.layout.clock_screen)
//
//        // Initialize views
//        dateText = findViewById(R.id.dateText)
//        downloadStatusText = findViewById(R.id.downloadStatusText)
//        downloadProgressBar = findViewById(R.id.downloadProgressBar)
//
//        // Configure TextClock explicitly
//        val textClock = findViewById<TextClock>(R.id.textClock)
//        textClock.format12Hour = "hh:mm:ss a"
//        textClock.format24Hour = "HH:mm:ss"
//        textClock.visibility = View.VISIBLE
//
//        // Update date and start time updates
//        updateDate()
//        startTimeUpdates()
//
//        // Show download UI with initial status
//        downloadStatusText.visibility = View.VISIBLE
//        downloadProgressBar.visibility = View.VISIBLE
//        downloadProgressBar.max = 100
//        downloadProgressBar.progress = 0
//        downloadStatusText.text = "Đang chuẩn bị tải video..."
//
//        // Log that we're starting the activity
//        Log.d(TAG, "Clock screen started, preparing to download videos")
//
//        // Start download process with a slight delay to ensure UI is visible
//        handler.postDelayed({
//            initializeDownloadManager()
//        }, 1000)
//    }
//
//    private fun startTimeUpdates() {
//        // Update time every second
//        handler.post(object : Runnable {
//            override fun run() {
//                updateDate()
//                handler.postDelayed(this, 1000)
//            }
//        })
//    }
//
//    private fun updateDate() {
//        val currentDate = Date()
//        dateText.text = dateFormat.format(currentDate)
//
//        // Find and update the TextClock if it's not automatically updating
//        val textClock = findViewById<TextClock>(R.id.textClock)
//        textClock?.let {
//            // Force refresh the TextClock
//            it.format12Hour = "hh:mm:ss a"
//            it.format24Hour = "HH:mm:ss"
//        }
//    }
//
//    private fun initializeDownloadManager() {
//        try {
//            videoDownloadManager = VideoDownloadManager(this)
//            videoDownloadManager.setDownloadListener(this)
//
//            // Show download UI
//            downloadStatusText.visibility = View.VISIBLE
//            downloadProgressBar.visibility = View.VISIBLE
//            downloadStatusText.text = "Đang chuẩn bị tải video..."
//
//            // Start download process
//            videoDownloadManager.initializeVideoDownload(this)
//        } catch (e: Exception) {
//            Log.e(TAG, "Error initializing download manager: ${e.message}")
//            downloadStatusText.text = "Lỗi: ${e.message}"
//        }
//    }
//
//    override fun onProgressUpdate(completedDownloads: Int, totalDownloads: Int, progressPercent: Int) {
//        runOnUiThread {
//            downloadProgressBar.progress = progressPercent
//            downloadStatusText.text = "Đang tải video: $completedDownloads/$totalDownloads ($progressPercent%)"
//            Log.d(TAG, "Download progress: $completedDownloads/$totalDownloads - $progressPercent%")
//        }
//    }
//
//    override fun onAllDownloadsCompleted() {
//        runOnUiThread {
//            downloadStatusText.text = "Tải xuống hoàn tất. Đang chuẩn bị phát video..."
//            Log.d(TAG, "All downloads completed")
//
//            // Wait a moment before starting MainActivity
//            handler.postDelayed({
//                startMainActivity()
//            }, 2000)
//        }
//    }
//
//    override fun onVideoReady(playlists: List<Playlist>) {
//        // This will be handled in MainActivity
//    }
//
//    private fun startMainActivity() {
//        val intent = Intent(this, MainActivity::class.java)
//        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//        startActivity(intent)
//        finish()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        if (::videoDownloadManager.isInitialized) {
//            videoDownloadManager.cleanup()
//        }
//    }
//
//    companion object {
//        private const val TAG = "ClockScreenActivity"
//    }
//}
