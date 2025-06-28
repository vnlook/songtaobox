package com.vnlook.tvsongtao.usecase

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * UseCase responsible for permission handling in activities
 * This class handles the actual permission requests and results
 */
class PermissionUseCase(private val activity: AppCompatActivity) {
    
    companion object {
        const val PERMISSION_REQUEST_CODE = 123
        private const val TAG = "PermissionUseCase"
    }
    
    /**
     * Check and request necessary permissions
     * @return true if all permissions are granted, false otherwise
     */
    fun checkAndRequestPermissions(): Boolean {
        try {
            Log.d(TAG, "Checking and requesting permissions in activity: ${activity.javaClass.simpleName}")
            var allPermissionsGranted = true
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // For Android 13+, we need to request notification permission
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Requesting POST_NOTIFICATIONS permission")
                    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST_CODE)
                    allPermissionsGranted = false
                } else {
                    Log.d(TAG, "POST_NOTIFICATIONS permission already granted")
                }
            }
            
            // For all Android versions, check storage permissions
            val storagePermissionsNeeded = mutableListOf<String>()
            
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "READ_EXTERNAL_STORAGE permission needed")
                storagePermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                Log.d(TAG, "READ_EXTERNAL_STORAGE permission already granted")
            }
            
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                // For Android 12 and below, check WRITE_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "WRITE_EXTERNAL_STORAGE permission needed")
                    storagePermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    Log.d(TAG, "WRITE_EXTERNAL_STORAGE permission already granted")
                }
            }
            
            if (storagePermissionsNeeded.isNotEmpty()) {
                Log.d(TAG, "Requesting storage permissions: ${storagePermissionsNeeded.joinToString()}")
                ActivityCompat.requestPermissions(activity, storagePermissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
                allPermissionsGranted = false
            }
            
            // For Android 11+, check MANAGE_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    Log.d(TAG, "MANAGE_EXTERNAL_STORAGE permission not granted - opening settings")
                    requestManageExternalStoragePermission()
                    allPermissionsGranted = false
                } else {
                    Log.d(TAG, "MANAGE_EXTERNAL_STORAGE permission already granted")
                }
            }
            
            // If we reach here and allPermissionsGranted is still true, we have all permissions
            if (allPermissionsGranted) {
                Log.d(TAG, "All permissions are already granted")
            }
            
            return allPermissionsGranted
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Request MANAGE_EXTERNAL_STORAGE permission for Android 11+
     */
    private fun requestManageExternalStoragePermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
                Log.d(TAG, "Opened MANAGE_EXTERNAL_STORAGE settings")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening MANAGE_EXTERNAL_STORAGE settings: ${e.message}")
            // Fallback to general settings
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Error opening general storage settings: ${e2.message}")
            }
        }
    }
    
    /**
     * Handle permission result
     * @return true if permission was granted, false otherwise
     */
    fun handlePermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission result for ${permissions.joinToString()}: ${if (granted) "GRANTED" else "DENIED"}")
            return granted
        }
        Log.d(TAG, "Unknown request code: $requestCode")
        return false
    }
    
    /**
     * Check if all required permissions are granted
     * @return true if all permissions are granted, false otherwise
     */
    fun areAllPermissionsGranted(): Boolean {
        try {
            var allGranted = true
            
            // Check basic storage permissions
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "❌ READ_EXTERNAL_STORAGE not granted")
                allGranted = false
            } else {
                Log.d(TAG, "✅ READ_EXTERNAL_STORAGE granted")
            }
            
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "❌ WRITE_EXTERNAL_STORAGE not granted")
                    allGranted = false
                } else {
                    Log.d(TAG, "✅ WRITE_EXTERNAL_STORAGE granted")
                }
            }
            
            // Check MANAGE_EXTERNAL_STORAGE for Android 11+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    Log.d(TAG, "❌ MANAGE_EXTERNAL_STORAGE not granted")
                    allGranted = false
                } else {
                    Log.d(TAG, "✅ MANAGE_EXTERNAL_STORAGE granted")
                }
            }
            
            Log.d(TAG, "Overall permission status: ${if (allGranted) "ALL GRANTED ✅" else "SOME MISSING ❌"}")
            return allGranted
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions: ${e.message}")
            return false
        }
    }
}
