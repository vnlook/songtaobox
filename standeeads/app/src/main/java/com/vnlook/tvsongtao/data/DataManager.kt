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

    /**
     * Retrieves videos from SharedPreferences
     * @return List of videos or empty list if none found
     */
    fun getVideos(): List<Video> {
        val videosJson = sharedPreferences.getString(KEY_VIDEOS, null)
        if (videosJson.isNullOrEmpty()) {
            Log.d(TAG, "No videos found in preferences")
            return emptyList()
        }
        
        try {
            val type = object : TypeToken<List<Video>>() {}.type
            val videos = gson.fromJson<List<Video>>(videosJson, type)
            Log.d(TAG, "Loaded ${videos.size} videos from preferences")
            return videos
        } catch (e: Exception) {
            Log.e(TAG, "Error loading videos from preferences: ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * Merges new videos with existing videos, preserving download status
     * 
     * @param newVideos New videos from API/mock data
     * @return Merged list of videos with preserved download status
     */
    fun mergeVideos(newVideos: List<Video>): List<Video> {
        val existingVideos = getVideos()
        val result = mutableListOf<Video>()
        
        // Create map for efficient lookups - prioritize URL matching
        val existingByUrl = existingVideos.associateBy { it.url }
        val existingById = existingVideos.associateBy { it.id }
        
        // Track URLs in new videos for later cleanup
        val newVideoUrls = newVideos.map { it.url }.toSet()
        
        // Process each new video
        for (newVideo in newVideos) {
            // Find existing video by URL (primary matching criteria)
            val existingVideo = existingByUrl[newVideo.url]
            
            if (existingVideo != null && existingVideo.isDownloaded && !existingVideo.localPath.isNullOrEmpty()) {
                // Check if the file actually exists
                val file = java.io.File(existingVideo.localPath!!)
                if (file.exists() && file.length() > 0) {
                    // Keep download status and path
                    result.add(newVideo.copy(isDownloaded = true, localPath = existingVideo.localPath))
                    Log.d(TAG, "Preserved download status for video with URL ${newVideo.url}, path: ${existingVideo.localPath}")
                } else {
                    // File doesn't exist, reset download status
                    result.add(newVideo)
                    Log.d(TAG, "Reset download status for video with URL ${newVideo.url} because file doesn't exist")
                }
            } else {
                // Not found or not downloaded, add as is
                result.add(newVideo)
            }
        }
        
        // Delete files for videos that are no longer in the new data
        deleteUnusedVideoFiles(existingVideos, newVideoUrls)
        
        return result
    }
    
    /**
     * Deletes video files that are no longer needed
     * 
     * @param existingVideos List of videos from SharedPreferences
     * @param newVideoUrls Set of URLs in the new video list
     */
    private fun deleteUnusedVideoFiles(existingVideos: List<Video>, newVideoUrls: Set<String>) {
        for (existingVideo in existingVideos) {
            if (!newVideoUrls.contains(existingVideo.url) && 
                existingVideo.isDownloaded && 
                !existingVideo.localPath.isNullOrEmpty()) {
                
                // This video is no longer needed, delete the file
                val file = java.io.File(existingVideo.localPath!!)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        Log.d(TAG, "Deleted unused video file: ${existingVideo.localPath}")
                    } else {
                        Log.e(TAG, "Failed to delete unused video file: ${existingVideo.localPath}")
                    }
                }
            }
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

    /**
     * Retrieves playlists from SharedPreferences
     * @return List of playlists or empty list if none found
     */
    fun getPlaylists(): List<Playlist> {
        val playlistsJson = sharedPreferences.getString(KEY_PLAYLISTS, null)
        if (playlistsJson.isNullOrEmpty()) {
            Log.d(TAG, "No playlists found in preferences")
            return emptyList()
        }
        
        try {
            val type = object : TypeToken<List<Playlist>>() {}.type
            val playlists = gson.fromJson<List<Playlist>>(playlistsJson, type)
            Log.d(TAG, "Loaded ${playlists.size} playlists from preferences")
            return playlists
        } catch (e: Exception) {
            Log.e(TAG, "Error loading playlists from preferences: ${e.message}")
            return emptyList()
        }
    }

    fun updateVideoDownloadStatus(videoUrl: String, isDownloaded: Boolean, localPath: String? = null) {
        try {
            Log.d(TAG, "Updating download status for video $videoUrl to $isDownloaded with path: $localPath")
            val videos = getVideos().toMutableList()
            for (video in videos) {
                if (video.url == videoUrl) {
                    video.isDownloaded = isDownloaded
                    video.localPath = localPath
                }
            }
            saveVideos(videos)
//            val index = videos.indexOfFirst { it.id == videoId }
//            if (index != -1) {
//                videos[index] = videos[index].copy(
//                    isDownloaded = isDownloaded,
//                    localPath = localPath ?: videos[index].localPath
//                )
//                saveVideos(videos)
//                Log.d(TAG, "Video $videoUrl status updated successfully")
//            } else {
//                Log.w(TAG, "Video $videoUrl not found in the list")
//            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating video status: ${e.message}")
            e.printStackTrace()
        }
    }


}
