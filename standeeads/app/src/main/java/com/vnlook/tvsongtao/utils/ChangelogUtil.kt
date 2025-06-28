package com.vnlook.tvsongtao.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.vnlook.tvsongtao.model.Changelog
import com.vnlook.tvsongtao.repository.ChangelogRepository
import com.vnlook.tvsongtao.repository.DeviceRepository
import com.vnlook.tvsongtao.repository.DeviceRepositoryImpl

/**
 * Utility class for handling changelog operations and checking for changes
 */
class ChangelogUtil(private val context: Context, private val changelogRepository: ChangelogRepository) {
    private val TAG = "ChangelogUtil"
    
    // Device-related repositories
    private val deviceRepository: DeviceRepository = DeviceRepositoryImpl(context)
    private val deviceInfoUtil = DeviceInfoUtil(context, deviceRepository)
    
    companion object {
        private const val PREFS_NAME = "changelog_prefs"
        private const val KEY_LAST_TIME_CHANGE = "last_time_change"
        
        /**
         * Static method to check for changelog changes
         * Checks network availability first - returns false if no network or any error
         * @param context The application context
         * @return true if there's a change, false otherwise or if no network or API failed
         */
        suspend fun checkChange(context: Context): Boolean {
            return try {
                Log.d("ChangelogUtil", "🔍 Starting static changelog check...")
                
                // Check network first - if no network, return false
                if (!NetworkUtil.isNetworkAvailable(context)) {
                    Log.d("ChangelogUtil", "🚫 No network available, returning false")
                    return false
                }
                
                Log.d("ChangelogUtil", "📡 Network available, proceeding with changelog check")
                
                // Create instance and check for changes
                val changelogRepository = com.vnlook.tvsongtao.repository.ChangelogRepositoryImpl(context)
                val changelogUtil = ChangelogUtil(context, changelogRepository)
                
                val result = changelogUtil.checkChange()
                Log.d("ChangelogUtil", "📊 Static changelog check result: $result")
                
                result
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("ChangelogUtil", "🕐 Static changelog check timeout: ${e.message}")
                false
            } catch (e: java.net.UnknownHostException) {
                Log.e("ChangelogUtil", "🌐 Static changelog check network error: ${e.message}")
                false
            } catch (e: java.net.ConnectException) {
                Log.e("ChangelogUtil", "🔌 Static changelog check connection error: ${e.message}")
                false
            } catch (e: Exception) {
                Log.e("ChangelogUtil", "💥 Error in static checkChange: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    /**
     * Check if there's a change in the changelog
     * Also updates device info before checking changelog
     * @return true if there's a change, false otherwise or if API failed
     */
    suspend fun checkChange(): Boolean {
        try {
            // First, update device info
            try {
                Log.d(TAG, "📱 Updating device info before changelog check...")
                val deviceUpdateResult = deviceInfoUtil.registerOrUpdateDevice()
                if (deviceUpdateResult != null) {
                    Log.d(TAG, "✅ Device info updated successfully: $deviceUpdateResult")
                } else {
                    Log.d(TAG, "📭 Device info update returned null (server may be unreachable)")
                }
            } catch (e: java.net.SocketTimeoutException) {
                Log.w(TAG, "🕐 Device update timeout (continuing with changelog check): ${e.message}")
            } catch (e: java.net.UnknownHostException) {
                Log.w(TAG, "🌐 Device update network error (continuing with changelog check): ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Device update failed (continuing with changelog check): ${e.message}")
                // Continue with changelog check even if device update fails
            }
            
            // Get the latest changelog from the API
            Log.d(TAG, "🔍 Fetching latest changelog from API...")
            val latestChangelog = changelogRepository.getLatestChangelog()
            
            if (latestChangelog == null) {
                Log.e(TAG, "❌ Failed to get latest changelog from API - returning false")
                return false
            }
            
            Log.d(TAG, "📄 Latest changelog retrieved successfully")
            
            // Get the last time change from SharedPreferences
            val savedLastTimeChange = sharedPreferences.getString(KEY_LAST_TIME_CHANGE, null)
            
            Log.d(TAG, "🆕 Latest changelog date: ${latestChangelog.date_created}")
            Log.d(TAG, "💾 Saved last time change: $savedLastTimeChange")
            
            // If there's no saved last time change, save the current one and return false
            if (savedLastTimeChange == null) {
                Log.d(TAG, "🔄 First time checking - saving current changelog date")
                updateLastTimeChange(latestChangelog)
                return false
            }
            
            // Compare the last time change with the one from the API
            val hasChanged = savedLastTimeChange != latestChangelog.date_created
            
            // If there's a change, update the last time change in SharedPreferences
            if (hasChanged) {
                updateLastTimeChange(latestChangelog)
                Log.i(TAG, "🔄 Changelog has CHANGED! (${savedLastTimeChange} → ${latestChangelog.date_created})")
            } else {
                Log.d(TAG, "✅ No change in changelog")
            }
            
            return hasChanged
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "🕐 Changelog check timeout: ${e.message}")
            return false
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "🌐 Changelog check network error: ${e.message}")
            return false
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "🔌 Changelog check connection error: ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error checking for changelog changes: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Update the last time change in SharedPreferences
     * @param changelog The latest changelog entry
     */
    private fun updateLastTimeChange(changelog: Changelog) {
        try {
            if (changelog.date_created.isNullOrBlank()) {
                Log.e(TAG, "❌ Cannot update last time change - invalid date_created")
                return
            }
            
            sharedPreferences.edit()
                .putString(KEY_LAST_TIME_CHANGE, changelog.date_created)
                .apply()
                
            Log.d(TAG, "💾 Updated last time change to: ${changelog.date_created}")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error updating last time change: ${e.message}")
            e.printStackTrace()
        }
    }
}
