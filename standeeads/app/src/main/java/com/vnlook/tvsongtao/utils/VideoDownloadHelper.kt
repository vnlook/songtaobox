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
     * Determines which videos need to be downloaded
     * 
     * @param videos List of videos to check for download
     * @return List of videos that need to be downloaded
     */
    fun getVideosToDownload(videos: List<Video>): List<Video> {
        val result = mutableListOf<Video>()
        val updatedVideos = mutableListOf<Video>()
        
        for (video in videos) {
            // Check if the video has a local path and if the file exists
            if (video.isDownloaded && !video.localPath.isNullOrEmpty()) {
                val file = File(video.localPath!!)
                if (file.exists() && file.length() > 0L) {
                    // File exists and is valid, no need to download
                    Log.d(TAG, "Video ${video.id} already downloaded at ${video.localPath}")
                    // Keep the video with its download status preserved
                    updatedVideos.add(video)
                } else {
                    // File doesn't exist or is empty, needs downloading
                    Log.d(TAG, "Video ${video.id} marked as downloaded but file missing, re-downloading")
                    result.add(video)
                }
            } else {
                // No local path or not marked as downloaded, needs downloading
                Log.d(TAG, "Video ${video.id} needs downloading")
                result.add(video)
            }
        }
        
        // Update the status of videos that are already downloaded if needed
        if (updatedVideos.isNotEmpty()) {
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
                    dataManager.updateVideoDownloadStatus(video.url, true, video.localPath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating video download status: ${e.message}")
        }
    }
    
    /**
     * Checks if a video file exists locally using URL
     * 
     * @param videoUrl URL of the video to check
     * @return True if the video file exists, false otherwise
     */
    fun isVideoFileExists(videoUrl: String): Boolean {
        try {
            val file = File(getVideoDownloadPath(videoUrl))
            return file.exists() && file.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if video file exists: ${e.message}")
            return false
        }
    }
    
    /**
     * Checks if a video file exists locally using video ID (deprecated)
     * 
     * @param videoId ID of the video to check
     * @return True if the video file exists, false otherwise
     */
    @Deprecated("Use isVideoFileExists(videoUrl) instead for consistency")
    fun isVideoFileExistsById(videoId: String): Boolean {
        try {
            val file = File(getVideoDownloadPathById(videoId))
            return file.exists() && file.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if video file exists: ${e.message}")
            return false
        }
    }
    
    /**
     * Gets the download path for a video using filename from URL
     * 
     * @param videoUrl URL of the video to extract filename
     * @return Absolute path to the video file
     */
    fun getVideoDownloadPath(videoUrl: String): String {
        try {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val videoFileName = VideoDownloadManager.extractFilenameFromUrl(videoUrl)
            // Ensure directory exists
            if (directory != null && !directory.exists()) {
                directory.mkdirs()
            }
            
            return File(directory, videoFileName).absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video download path: ${e.message}")
            // Return a fallback path in case of error
            val fallbackFilename = VideoDownloadManager.extractFilenameFromUrl(videoUrl)
            return context.filesDir.absolutePath + "/$fallbackFilename"
        }
    }
    
    /**
     * Gets the download path for a video using video ID (deprecated - use URL version)
     * 
     * @param videoId ID of the video
     * @return Absolute path to the video file
     */
    @Deprecated("Use getVideoDownloadPath(videoUrl) instead for consistency")
    fun getVideoDownloadPathById(videoId: String): String {
        try {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val videoFileName = "video_${videoId}.mp4" // Fallback to old pattern for compatibility
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
