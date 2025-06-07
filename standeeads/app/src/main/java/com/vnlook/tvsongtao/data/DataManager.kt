package com.vnlook.tvsongtao.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.model.Video

class DataManager(private val context: Context) {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()
    
    companion object {
        private const val TAG = "DataManager"
        private const val PREFS_NAME = "video_ads_prefs"
        private const val KEY_VIDEOS = "videos"
        private const val KEY_PLAYLISTS = "playlists"
    }

    fun saveVideos(videos: List<Video>) {
        try {
            Log.d(TAG, "Saving ${videos.size} videos")
            val videosJson = gson.toJson(videos)
            sharedPreferences.edit().putString(KEY_VIDEOS, videosJson).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving videos: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getVideos(): List<Video> {
        try {
            val videosJson = sharedPreferences.getString(KEY_VIDEOS, null)
            if (videosJson != null) {
                try {
                    val type = object : TypeToken<List<Video>>() {}.type
                    val videos = gson.fromJson<List<Video>>(videosJson, type)
                    if (videos != null && videos.isNotEmpty()) {
                        Log.d(TAG, "Loaded ${videos.size} videos from preferences")
                        return videos
                    } else {
                        Log.w(TAG, "Loaded empty video list from preferences")
                    }
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Error parsing videos JSON: ${e.message}")
                    // Clear corrupted data
                    sharedPreferences.edit().remove(KEY_VIDEOS).apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading videos: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                Log.d(TAG, "No videos found in preferences, using mock data")
            }
            
            // Return mock data on first run or error
            val mockVideos = MockDataProvider.getMockVideos()
            saveVideos(mockVideos)
            return mockVideos
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in getVideos: ${e.message}")
            e.printStackTrace()
            // Return empty list as fallback
            return emptyList()
        }
    }

    fun savePlaylists(playlists: List<Playlist>) {
        try {
            Log.d(TAG, "Saving ${playlists.size} playlists")
            val playlistsJson = gson.toJson(playlists)
            sharedPreferences.edit().putString(KEY_PLAYLISTS, playlistsJson).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving playlists: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getPlaylists(): List<Playlist> {
        try {
            val playlistsJson = sharedPreferences.getString(KEY_PLAYLISTS, null)
            if (playlistsJson != null) {
                try {
                    val type = object : TypeToken<List<Playlist>>() {}.type
                    val playlists = gson.fromJson<List<Playlist>>(playlistsJson, type)
                    if (playlists != null && playlists.isNotEmpty()) {
                        Log.d(TAG, "Loaded ${playlists.size} playlists from preferences")
                        return playlists
                    } else {
                        Log.w(TAG, "Loaded empty playlist list from preferences")
                    }
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Error parsing playlists JSON: ${e.message}")
                    // Clear corrupted data
                    sharedPreferences.edit().remove(KEY_PLAYLISTS).apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading playlists: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                Log.d(TAG, "No playlists found in preferences, using mock data")
            }
            
            // Return mock data on first run or error
            val mockPlaylists = MockDataProvider.getMockPlaylists()
            savePlaylists(mockPlaylists)
            return mockPlaylists
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in getPlaylists: ${e.message}")
            e.printStackTrace()
            // Return empty list as fallback
            return emptyList()
        }
    }

    fun updateVideoDownloadStatus(videoId: String, isDownloaded: Boolean, localPath: String? = null) {
        try {
            Log.d(TAG, "Updating download status for video $videoId to $isDownloaded with path: $localPath")
            val videos = getVideos().toMutableList()
            val index = videos.indexOfFirst { it.id == videoId }
            if (index != -1) {
                videos[index] = videos[index].copy(
                    isDownloaded = isDownloaded,
                    localPath = localPath ?: videos[index].localPath
                )
                saveVideos(videos)
                Log.d(TAG, "Video $videoId status updated successfully")
            } else {
                Log.w(TAG, "Video $videoId not found in the list")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating video status: ${e.message}")
            e.printStackTrace()
        }
    }


}
