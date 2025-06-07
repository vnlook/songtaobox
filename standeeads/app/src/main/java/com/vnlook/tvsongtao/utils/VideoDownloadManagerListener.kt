package com.vnlook.tvsongtao.utils

import com.vnlook.tvsongtao.model.Playlist

/**
 * Interface for video download progress and completion events
 */
interface VideoDownloadManagerListener {
    /**
     * Called to update the download progress
     * 
     * @param completed Number of completed downloads
     * @param total Total number of downloads
     * @param progressPercent Overall download progress as percentage
     */
    fun onProgressUpdate(completed: Int, total: Int, progressPercent: Int = 0)
    
    /**
     * Called when all downloads have completed
     */
    fun onAllDownloadsCompleted()
    
    /**
     * Called when videos are ready for playback
     * 
     * @param playlists List of playlists with downloaded videos
     */
    fun onVideoReady(playlists: List<Playlist>)
}
