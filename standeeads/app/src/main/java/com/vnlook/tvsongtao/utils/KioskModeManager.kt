package com.vnlook.tvsongtao.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.vnlook.tvsongtao.receiver.DeviceAdminReceiver
import com.vnlook.tvsongtao.service.KioskAccessibilityService

/**
 * Manages kiosk mode (Lock Task Mode) for the application
 * This restricts users from leaving the app, making it suitable for dedicated devices
 */
class KioskModeManager(private val context: Context) {

    companion object {
        private const val TAG = "KioskModeManager"
        
        /**
         * Set this to false to disable Kiosk Mode throughout the app
         * When false, the app will not enter Lock Task Mode
         */
        const val KIOSK_MODE_ENABLED = true
    }

    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    /**
     * Check if the app is in Lock Task Mode
     */
    fun isInLockTaskMode(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
            } else {
                activityManager.isInLockTaskMode
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking lock task mode: ${e.message}")
            false
        }
    }

    /**
     * Start Lock Task Mode
     * @param activity The activity to start Lock Task Mode from
     */
    fun startLockTask(activity: Activity) {
        if (!KIOSK_MODE_ENABLED) {
            Log.d(TAG, "Kiosk mode is disabled by configuration")
            return
        }
        
        try {
            if (hasLockTaskPermission()) {
                if (!isInLockTaskMode()) {
                    Log.d(TAG, "Starting lock task mode")
                    activity.startLockTask()
                    
                    // Ensure Accessibility Service is enabled to dismiss "Hold to unpin" message
                    ensureAccessibilityServiceEnabled(activity)
                } else {
                    Log.d(TAG, "Already in lock task mode")
                }
            } else {
                Log.d(TAG, "No permission for lock task mode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting lock task mode: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Check if Accessibility Service is enabled and prompt user to enable it if not
     */
    fun ensureAccessibilityServiceEnabled(activity: Activity) {
        if (!isAccessibilityServiceEnabled()) {
            Log.d(TAG, "Accessibility Service is not enabled, prompting user")
            showAccessibilitySettings(activity)
        } else {
            Log.d(TAG, "Accessibility Service is already enabled")
        }
    }

    /**
     * Check if our Accessibility Service is enabled
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            Log.e(TAG, "Error finding accessibility setting: ${e.message}")
            return false
        }

        if (accessibilityEnabled == 1) {
            val serviceString = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val serviceName = "${context.packageName}/${KioskAccessibilityService::class.java.canonicalName}"
            return serviceString.split(':').any { it.equals(serviceName, ignoreCase = true) }
        }
        
        return false
    }

    /**
     * Show Accessibility Settings to enable our service
     */
    private fun showAccessibilitySettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            activity.startActivity(intent)
            
            // Show message to user
            Log.d(TAG, "Please enable the Kiosk Accessibility Service")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing accessibility settings: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Stop Lock Task Mode
     * @param activity The activity to stop Lock Task Mode from
     */
    fun stopLockTask(activity: Activity) {
        try {
            if (isInLockTaskMode()) {
                Log.d(TAG, "Stopping lock task mode")
                activity.stopLockTask()
            } else {
                Log.d(TAG, "Not in lock task mode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping lock task mode: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Check if the app has permission to use Lock Task Mode
     * For API level 23+ (Android 6.0+), we need to check if the app is a device owner
     * or if it's in the lock task packages list
     */
    fun hasLockTaskPermission(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                
                // Check if the app is a device owner app
                val isDeviceOwnerApp = dpm.isDeviceOwnerApp(context.packageName)
                Log.d(TAG, "Is device owner app: $isDeviceOwnerApp")
                
                // For Android M and above, we need to be a device owner
                // or be in the lock task whitelist (which requires device owner to set)
                isDeviceOwnerApp
            } else {
                // For older versions, we can just try to start lock task mode
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking lock task permission: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Open settings to allow the user to grant necessary permissions
     * This is needed for Lock Task Mode to work properly
     */
    fun openKioskModeSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening kiosk mode settings: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Enable full kiosk mode without requiring Device Owner permissions
     * This combines several techniques to lock down the device:
     * 1. Immersive mode to hide navigation buttons
     * 2. Keep screen on
     * 3. Show on lock screen
     * 4. Prevent status bar pull-down
     * 
     * Note: This is not as secure as Lock Task Mode with Device Owner,
     * but it works without ADB commands
     * 
     * @param activity The activity to enable kiosk mode on
     */
    fun enableFullKioskMode(activity: Activity) {
        if (!KIOSK_MODE_ENABLED) {
            Log.d(TAG, "Kiosk mode is disabled by configuration")
            return
        }
        
        try {
            // Gọi startLockTask để kích hoạt Lock Task Mode
            startLockTask(activity)
            
            // Keep screen on
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // Show when locked
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            
            // Enable immersive sticky mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11+
                val controller = activity.window.insetsController
                controller?.hide(android.view.WindowInsets.Type.statusBars() or 
                                android.view.WindowInsets.Type.navigationBars())
                controller?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                // For older Android versions
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            }
            
            Log.d(TAG, "Full kiosk mode enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling full kiosk mode: ${e.message}")
            e.printStackTrace()
        }
    }
}
