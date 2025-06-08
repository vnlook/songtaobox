package com.vnlook.tvsongtao.usecase

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting READ_EXTERNAL_STORAGE permission")
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
                allPermissionsGranted = false
            } else {
                Log.d(TAG, "READ_EXTERNAL_STORAGE permission already granted")
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
}
