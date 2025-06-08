package com.vnlook.tvsongtao.repository

import com.vnlook.tvsongtao.model.Playlist

/**
 * Repository interface for playlist operations
 * All methods are suspend functions to support coroutines and asynchronous API calls
 */
interface PlaylistRepository {
    /**
     * Get all playlists directly from VNL API
     * @return List of playlists or empty list if API call failed
     */
    suspend fun getPlaylists(): List<Playlist>
    
    /**
     * Save playlists to local storage
     * @param playlists List of playlists to save
     */
    suspend fun savePlaylists(playlists: List<Playlist>)
}
