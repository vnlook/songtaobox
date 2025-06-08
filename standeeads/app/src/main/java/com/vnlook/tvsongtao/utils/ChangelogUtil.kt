package com.vnlook.tvsongtao.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.vnlook.tvsongtao.model.Changelog
import com.vnlook.tvsongtao.repository.ChangelogRepository

/**
 * Utility class for handling changelog operations and checking for changes
 */
class ChangelogUtil(private val context: Context, private val changelogRepository: ChangelogRepository) {
    private val TAG = "ChangelogUtil"
    
    companion object {
        private const val PREFS_NAME = "changelog_prefs"
        private const val KEY_LAST_TIME_CHANGE = "last_time_change"
    }
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    /**
     * Check if there's a change in the changelog
     * @return true if there's a change, false otherwise
     */
    suspend fun checkChange(): Boolean {
        try {
            // Get the latest changelog from the API
            val latestChangelog = changelogRepository.getLatestChangelog()
            
            if (latestChangelog == null) {
                Log.e(TAG, "Failed to get latest changelog")
                return false
            }
            
            // Get the last time change from SharedPreferences
            val savedLastTimeChange = sharedPreferences.getString(KEY_LAST_TIME_CHANGE, null)
            
            Log.d(TAG, "Latest changelog date: ${latestChangelog.date_created}")
            Log.d(TAG, "Saved last time change: $savedLastTimeChange")
            
            // If there's no saved last time change, save the current one and return false
            if (savedLastTimeChange == null) {
                updateLastTimeChange(latestChangelog)
                return false
            }
            
            // Compare the last time change with the one from the API
            val hasChanged = savedLastTimeChange != latestChangelog.date_created
            
            // If there's a change, update the last time change in SharedPreferences
            if (hasChanged) {
                updateLastTimeChange(latestChangelog)
                Log.d(TAG, "Changelog has changed")
            } else {
                Log.d(TAG, "No change in changelog")
            }
            
            return hasChanged
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for changelog changes: ${e.message}")
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
            sharedPreferences.edit().putString(KEY_LAST_TIME_CHANGE, changelog.date_created).apply()
            Log.d(TAG, "Updated last time change to: ${changelog.date_created}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last time change: ${e.message}")
            e.printStackTrace()
        }
    }
}
