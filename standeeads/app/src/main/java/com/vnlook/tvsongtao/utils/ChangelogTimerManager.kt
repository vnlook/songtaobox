package com.vnlook.tvsongtao.utils

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vnlook.tvsongtao.DigitalClockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.*

/**
 * Singleton Timer Manager for changelog checking
 * Runs every 60 seconds globally across the app
 */
object ChangelogTimerManager {
    
    private const val TAG = "ChangelogTimerManager"
    private const val CHECK_INTERVAL = 60000L // 60 seconds
    
    private var timer: Timer? = null
    private var appContext: Context? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Initialize timer with application context
     * Should be called from Application.onCreate()
     */
    fun initialize(context: Application) {
        appContext = context.applicationContext
        startGlobalTimer()
    }
    
    /**
     * Start the global changelog timer
     */
    private fun startGlobalTimer() {
        // Stop existing timer first
        stopTimer()
        
        Log.i(TAG, "🚀🚀🚀 STARTING GLOBAL CHANGELOG TIMER 🚀🚀🚀")
        
        try {
            // Create timer with daemon thread
            timer = Timer("GlobalChangelogTimer", true)
            
            timer?.schedule(object : TimerTask() {
                override fun run() {
                    val timestamp = System.currentTimeMillis()
                    Log.i(TAG, "⏰ GLOBAL TIMER TICK #$timestamp - executing changelog check...")
                    
                    // Run changelog check in background
                    coroutineScope.launch {
                        try {
                            val context = appContext
                            if (context != null) {
                                val hasChanges = ChangelogUtil.checkChange(context)
                                
                                if (hasChanges) {
                                    Log.i(TAG, "🔥 CHANGES DETECTED! Restarting app...")
                                    
                                    // Restart app on main thread
                                    Handler(Looper.getMainLooper()).post {
                                        restartApp()
                                    }
                                } else {
                                    Log.d(TAG, "📋 No changes detected, continuing...")
                                }
                            } else {
                                Log.w(TAG, "⚠️ App context is null, skipping check")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "💥 Error in timer task: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }
            }, 1000, CHECK_INTERVAL) // Start after 1 second, then every 60 seconds
            
            Log.i(TAG, "✅ Global changelog timer started successfully")
            Log.i(TAG, "🎯 Timer scheduled: 1 second delay, 60 second interval")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error starting global timer: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Restart the entire app by launching DigitalClockActivity
     */
    private fun restartApp() {
        try {
            val context = appContext
            if (context == null) {
                Log.e(TAG, "❌ Cannot restart app: context is null")
                return
            }
            
            Log.i(TAG, "🔄 FORCE RESTARTING APP...")
            
            val intent = Intent(context, DigitalClockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                       Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            context.startActivity(intent)
            Log.i(TAG, "✅ App restart initiated successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error restarting app: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Stop the global timer
     */
    fun stopTimer() {
        timer?.let {
            Log.d(TAG, "🛑 Stopping global changelog timer...")
            it.cancel()
            it.purge()
            timer = null
            Log.d(TAG, "✅ Global timer stopped")
        }
    }
    
    /**
     * Check if timer is currently running
     */
    fun isTimerRunning(): Boolean {
        return timer != null
    }
    
    /**
     * Get debug information about timer status
     */
    fun getDebugInfo(): String {
        return """
            Timer Running: ${isTimerRunning()}
            Timer Object: ${timer}
            App Context: ${appContext != null}
            Check Interval: ${CHECK_INTERVAL}ms
        """.trimIndent()
    }
} 