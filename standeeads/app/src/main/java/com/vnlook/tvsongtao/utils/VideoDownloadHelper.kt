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
        val updatedVideos = mutableListOf<Video>()
        
        for (apiVideo in apiVideos) {
            // First check if there's a video with the same ID
            val savedVideoById = savedVideos.find { it.id == apiVideo.id }
            
            // Then check if there's a video with the same URL (even if ID is different)
            val savedVideoByUrl = if (savedVideoById == null) {
                savedVideos.find { it.url == apiVideo.url && it.isDownloaded && !it.localPath.isNullOrEmpty() }
            } else null
            
            when {
                // Case 1: Found video with same ID and it's downloaded with valid path
                savedVideoById != null && savedVideoById.isDownloaded && !savedVideoById.localPath.isNullOrEmpty() -> {
                    val file = File(savedVideoById.localPath!!)
                    if (file.exists() && file.length() > 0L) {
                        // File exists and is valid, no need to download
                        Log.d(TAG, "Video ${apiVideo.id} already downloaded at ${savedVideoById.localPath}")
                        // Add to updated videos list with download status preserved
                        updatedVideos.add(apiVideo.copy(isDownloaded = true, localPath = savedVideoById.localPath))
                    } else {
                        // File doesn't exist or is empty, needs downloading
                        Log.d(TAG, "Video ${apiVideo.id} marked as downloaded but file missing, re-downloading")
                        result.add(apiVideo)
                    }
                }
                
                // Case 2: Found video with same URL but different ID
                savedVideoByUrl != null -> {
                    val file = File(savedVideoByUrl.localPath!!)
                    if (file.exists() && file.length() > 0L) {
                        // File exists and is valid, reuse it
                        Log.d(TAG, "Found video with same URL (${apiVideo.url}), reusing downloaded file at ${savedVideoByUrl.localPath}")
                        // Add to updated videos list with download status and path from the matching URL video
                        updatedVideos.add(apiVideo.copy(isDownloaded = true, localPath = savedVideoByUrl.localPath))
                    } else {
                        // File doesn't exist or is empty, needs downloading
                        Log.d(TAG, "Found video with same URL but file missing, re-downloading")
                        result.add(apiVideo)
                    }
                }
                
                // Case 3: No matching video found or video not downloaded
                else -> {
                    Log.d(TAG, "Video ${apiVideo.id} needs downloading")
                    result.add(apiVideo)
                }
            }
        }
        
        // Return videos that need downloading
        if (updatedVideos.isNotEmpty()) {
            // Update the status of videos that are already downloaded
            updateVideoDownloadStatus(updatedVideos)
        }
        
        return result
    }
    
    /**
     * Updates the download status of videos in SharedPreferences
     * 
     * @param videos List of videos to update
     */
    private fun updateVideoDownloadStatus(videos: List<Video>) {
        try {
            val dataManager = com.vnlook.tvsongtao.data.DataManager(context)
            for (video in videos) {
                if (video.isDownloaded && !video.localPath.isNullOrEmpty()) {
                    dataManager.updateVideoDownloadStatus(video.id, true, video.localPath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating video download status: ${e.message}")
        }
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
