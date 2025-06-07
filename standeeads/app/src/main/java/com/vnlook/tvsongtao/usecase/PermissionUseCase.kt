package com.vnlook.tvsongtao.usecase

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * UseCase responsible for permission handling
 */
class PermissionUseCase(private val activity: AppCompatActivity) {
    
    companion object {
        const val PERMISSION_REQUEST_CODE = 123
    }
    
    /**
     * Check and request necessary permissions
     * @return true if all permissions are granted, false otherwise
     */
    fun checkAndRequestPermissions(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // For Android 13+, we need to request notification permission
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST_CODE)
                    return false
                }
            }
            
            // For all Android versions, check storage permissions
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
                return false
            }
            
            // If we reach here, we have all permissions
            return true
        } catch (e: Exception) {
            Log.e("PermissionUseCase", "Error checking permissions: ${e.message}")
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
            return grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        }
        return false
    }
}
