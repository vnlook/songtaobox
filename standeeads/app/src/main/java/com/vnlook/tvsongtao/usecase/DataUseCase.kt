package com.vnlook.tvsongtao.usecase

import android.content.Context
import android.util.Log
import com.vnlook.tvsongtao.data.DataManager
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.model.Video
import com.vnlook.tvsongtao.repository.PlaylistRepository
import com.vnlook.tvsongtao.repository.PlaylistRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UseCase responsible for data operations
 */
class DataUseCase(private val context: Context) {
    
    private lateinit var dataManager: DataManager
    private lateinit var playlistRepository: PlaylistRepository
    private val TAG = "DataUseCase"
    
    /**
     * Initialize data manager and repositories
     */
    fun initialize() {
        try {
            if (!::dataManager.isInitialized) {
                dataManager = DataManager(context)
            }
            
            if (!::playlistRepository.isInitialized) {
                playlistRepository = PlaylistRepositoryImpl(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing data manager or repositories: ${e.message}")
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
     * Get playlists from repository
     * This is a suspend function that must be called from a coroutine
     */
    suspend fun getPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        try {
            if (!::playlistRepository.isInitialized) {
                initialize()
            }
            playlistRepository.getPlaylists()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting playlists: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    

}
