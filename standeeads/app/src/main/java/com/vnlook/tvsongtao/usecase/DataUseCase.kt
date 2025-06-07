package com.vnlook.tvsongtao.usecase

import android.content.Context
import android.util.Log
import com.vnlook.tvsongtao.data.DataManager
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.model.Video

/**
 * UseCase responsible for data operations
 */
class DataUseCase(private val context: Context) {
    
    private lateinit var dataManager: DataManager
    
    /**
     * Initialize data manager
     */
    fun initialize() {
        try {
            if (!::dataManager.isInitialized) {
                dataManager = DataManager(context)
            }
        } catch (e: Exception) {
            Log.e("DataUseCase", "Error initializing data manager: ${e.message}")
            throw e
        }
    }
    
    /**
     * Get videos from data manager
     */
    fun getVideos(): List<Video> {
        try {
            if (!::dataManager.isInitialized) {
                initialize()
            }
            return dataManager.getVideos()
        } catch (e: Exception) {
            Log.e("DataUseCase", "Error getting videos: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }
    
    /**
     * Get playlists from data manager
     */
    fun getPlaylists(): List<Playlist> {
        try {
            if (!::dataManager.isInitialized) {
                initialize()
            }
            return dataManager.getPlaylists()
        } catch (e: Exception) {
            Log.e("DataUseCase", "Error getting playlists: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }
}
