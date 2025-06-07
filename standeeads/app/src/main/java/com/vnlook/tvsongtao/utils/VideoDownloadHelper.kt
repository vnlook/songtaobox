package com.vnlook.tvsongtao.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.model.Video
import java.io.File

/**
 * Helper class for VideoDownloadManager providing utility functions
 * for video download management
 */
class VideoDownloadHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoDownloadHelper"
    }
    
    /**
     * Checks if playlists have changed compared to saved ones
     * 
     * @param newPlaylists Playlists from API/mock data
     * @param savedPlaylists Playlists saved in SharedPreferences
     * @return True if playlists have changed, false otherwise
     */
    fun checkIfPlaylistsNeedUpdate(newPlaylists: List<Playlist>, savedPlaylists: List<Playlist>): Boolean {
        if (newPlaylists.size != savedPlaylists.size) return true
        
        // Compare each playlist
        for (i in newPlaylists.indices) {
            val newPlaylist = newPlaylists[i]
            val savedPlaylist = savedPlaylists.find { it.id == newPlaylist.id } ?: return true
            
            // Check if video IDs have changed
            if (newPlaylist.videoIds.size != savedPlaylist.videoIds.size) return true
            
            for (videoId in newPlaylist.videoIds) {
                if (!savedPlaylist.videoIds.contains(videoId)) return true
            }
            
            // Check if times have changed
            if (newPlaylist.startTime != savedPlaylist.startTime || 
                newPlaylist.endTime != savedPlaylist.endTime) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Checks if all videos are downloaded
     * 
     * @param videos List of videos to check
     * @return True if all videos are downloaded, false otherwise
     */
    fun areAllVideosDownloaded(videos: List<Video>): Boolean {
        return videos.isNotEmpty() && videos.all { it.isDownloaded && !it.localPath.isNullOrEmpty() }
    }
    
    /**
     * Determines which videos need to be downloaded based on comparing API videos with saved videos
     * 
     * @param apiVideos Videos from API/mock data
     * @param savedVideos Videos saved in SharedPreferences
     * @return List of videos that need to be downloaded
     */
    fun getVideosToDownload(apiVideos: List<Video>, savedVideos: List<Video>): List<Video> {
        val result = mutableListOf<Video>()
        
        for (apiVideo in apiVideos) {
            val savedVideo = savedVideos.find { it.id == apiVideo.id }
            
            if (savedVideo == null) {
                // New video, needs downloading
                result.add(apiVideo)
            } else if (!savedVideo.isDownloaded || savedVideo.localPath.isNullOrEmpty()) {
                // Existing video but not downloaded or missing local path
                result.add(apiVideo)
            } else {
                // Check if the file exists at the specified path
                val file = File(savedVideo.localPath!!)
                if (!file.exists() || file.length() == 0L) {
                    // File doesn't exist or is empty, needs downloading
                    result.add(apiVideo)
                }
            }
        }
        
        return result
    }
    
    /**
     * Checks if a video file exists locally
     * 
     * @param videoId ID of the video to check
     * @return True if the video file exists, false otherwise
     */
    fun isVideoFileExists(videoId: String): Boolean {
        try {
            val file = File(getVideoDownloadPath(videoId))
            return file.exists() && file.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if video file exists: ${e.message}")
            return false
        }
    }
    
    /**
     * Gets the download path for a video
     * 
     * @param videoId ID of the video
     * @return Absolute path to the video file
     */
    fun getVideoDownloadPath(videoId: String): String {
        try {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val videoFileName = "video_${videoId}.mp4"
            
            // Ensure directory exists
            if (directory != null && !directory.exists()) {
                directory.mkdirs()
            }
            
            return File(directory, videoFileName).absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video download path: ${e.message}")
            // Return a fallback path in case of error
            return context.filesDir.absolutePath + "/video_${videoId}.mp4"
        }
    }
}
