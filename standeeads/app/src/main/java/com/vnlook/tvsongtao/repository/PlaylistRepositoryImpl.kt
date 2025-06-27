package com.vnlook.tvsongtao.repository

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.utils.ApiLogger
import com.vnlook.tvsongtao.utils.DeviceInfoUtil
import com.vnlook.tvsongtao.data.DataManager
import com.vnlook.tvsongtao.repository.DeviceRepositoryImpl
import com.vnlook.tvsongtao.repository.VNLApiClient
import com.vnlook.tvsongtao.repository.VNLApiResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Implementation of PlaylistRepository that handles playlist operations
 * This repository handles both API data and offline cached data from SharedPreferences
 * It makes direct API calls to the VNL API endpoint when online, falls back to cache when offline
 */
class PlaylistRepositoryImpl(private val context: Context) : PlaylistRepository {
    private val TAG = "PlaylistRepository"
    private val dataManager = DataManager(context)
    
    /**
     * Get playlists from VNL API when online, or from SharedPreferences cache when offline
     * @return List of playlists from API or cache, empty list only if both sources failed
     */
    override suspend fun getPlaylists(): List<Playlist> {
        Log.d(TAG, "Checking network before making API call...")
        
        // Check network connectivity first
        if (!com.vnlook.tvsongtao.utils.NetworkUtil.isNetworkAvailable(context)) {
            Log.d(TAG, "🚫 No network available, loading playlists from SharedPreferences cache")
            
            // Load from SharedPreferences cache when offline
            val cachedPlaylists = dataManager.getPlaylists()
            if (cachedPlaylists.isNotEmpty()) {
                Log.d(TAG, "✅ Successfully loaded ${cachedPlaylists.size} playlists from cache (offline mode)")
                return cachedPlaylists
            } else {
                Log.w(TAG, "⚠️ No cached playlists found in SharedPreferences")
                return emptyList()
            }
        }
        
        Log.d(TAG, "📡 Network available, making direct API call to VNL API endpoint")
        
        // Make the API call using VNLApiClient
        val apiResponse = VNLApiClient.getPlaylists()
        
        if (apiResponse.isNullOrEmpty()) {
            Log.e(TAG, "API call failed or returned empty response, trying SharedPreferences fallback")
            
            // Fallback to SharedPreferences if API fails
            val cachedPlaylists = dataManager.getPlaylists()
            if (cachedPlaylists.isNotEmpty()) {
                Log.d(TAG, "✅ Using cached playlists as fallback: ${cachedPlaylists.size} playlists")
                return cachedPlaylists
            } else {
                Log.e(TAG, "❌ Both API and cache failed - no playlists available")
                return emptyList()
            }
        }
        
        // Parse the API response
        val (playlists, _) = VNLApiResponseParser.parseApiResponse(apiResponse)
        Log.d(TAG, "Successfully retrieved ${playlists.size} playlists from API")
        
        // Save the API response to file for offline use
        saveApiResponseToFile(apiResponse)
        
        // Check if device is in portrait mode
        val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        Log.d(TAG, "Device orientation is ${if (isPortrait) "portrait" else "landscape"}")
        val deviceRepository = DeviceRepositoryImpl(context)
        val deviceInfo = deviceRepository.getDeviceInfo()
        
        // Filter playlists based on device orientation and portrait field
        val filteredPlaylists = playlists.filter { 
            it.deviceId == deviceInfo?.deviceId && 
            it.deviceName == deviceInfo?.deviceName && 
            it.deviceName != null 
        }
        
        Log.d(TAG, "Filtered playlists based on device info: ${filteredPlaylists.size} of ${playlists.size}")
        
        // Save filtered playlists to SharedPreferences for offline use
        dataManager.savePlaylists(filteredPlaylists)
        Log.d(TAG, "✅ Saved ${filteredPlaylists.size} playlists to SharedPreferences cache")
        
        return filteredPlaylists
    }
    
    /**
     * Save playlists - this is a no-op in this implementation as repository doesn't save data
     * @param playlists List of playlists to save
     */
    override suspend fun savePlaylists(playlists: List<Playlist>) {
        // No-op: Repository doesn't save data to SharedPreferences
        Log.d(TAG, "savePlaylists called but not implemented - repository doesn't save data")
    }
    
    /**
     * Save API response to file for offline use
     * @param apiResponse API response string to save
     */
    private fun saveApiResponseToFile(apiResponse: String) {
        try {
            val externalFilesDir = context.getExternalFilesDir(null) ?: return
            val file = File(externalFilesDir, "vnl_api_response.json")
            
            file.writeText(apiResponse)
            Log.d(TAG, "Saved API response to file: ${file.absolutePath}")
            ApiLogger.logFileOperation("WRITE", file.absolutePath, true, "${apiResponse.length} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving API response to file: ${e.message}")
            e.printStackTrace()
        }
    }
}
