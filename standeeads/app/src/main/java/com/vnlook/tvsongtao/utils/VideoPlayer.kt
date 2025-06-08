package com.vnlook.tvsongtao.utils

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.VideoView
import com.vnlook.tvsongtao.data.DataManager
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.model.Video
import java.io.File

class VideoPlayer(
    private val context: Context,
    private val videoView: VideoView,
    private val onPlaylistFinished: () -> Unit
) {
    private val dataManager = DataManager(context)
    private val downloadHelper = VideoDownloadHelper(context)
    private var currentPlaylist: Playlist? = null
    private var currentVideoIndex = 0
    private var videos: List<Video> = emptyList()
    private var isPlayingVideo = false
    
    fun playPlaylist(playlist: Playlist) {
        currentPlaylist = playlist
        videos = getVideosForPlaylist(playlist)
        
        if (videos.isEmpty()) {
            Log.d(TAG, "No videos available to play in playlist ${playlist.id}")
            onPlaylistFinished()
            return
        }
        
        currentVideoIndex = 0
        playCurrentVideo()
    }
    
    private fun getVideosForPlaylist(playlist: Playlist): List<Video> {
        val allVideos = dataManager.getVideos()
        return allVideos.filter { video ->
            video.isDownloaded && playlist.videoIds.contains(video.id)
        }
    }
    
    private fun playCurrentVideo() {
        // Check if we're already playing a video to prevent multiple calls
        if (isPlayingVideo) {
            Log.d(TAG, "Already playing a video, ignoring call to playCurrentVideo()")
            return
        }
        
        if (currentVideoIndex >= videos.size) {
            // Start from the beginning when reaching the end
            currentVideoIndex = 0
            onPlaylistFinished()
            return
        }
        
        val video = videos[currentVideoIndex]
        val videoPath = downloadHelper.getVideoDownloadPath(video.url)
        val file = File(videoPath)
        
        if (!file.exists()) {
            Log.e(TAG, "Video file not found: $videoPath")
            currentVideoIndex++
            playCurrentVideo()
            return
        }
        
        try {
            // Mark that we're starting to play a video
            isPlayingVideo = true
            
            // Use a proper file:// URI instead of a raw file path
            val uri = Uri.fromFile(file)
            Log.d(TAG, "Playing video: ${video.id}, URI: $uri")
            
            // Use setVideoURI instead of setVideoPath
            videoView.setVideoURI(uri)
            
            videoView.setOnCompletionListener {
                // Play next video when current one finishes
                Log.d(TAG, "Video completed: ${video.id}")
                isPlayingVideo = false  // Reset flag when video completes
                currentVideoIndex++
                playCurrentVideo()
            }
            
            videoView.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Video playback error: what=$what, extra=$extra, path=$videoPath")
                isPlayingVideo = false  // Reset flag on error
                currentVideoIndex++
                playCurrentVideo()
                true
            }
            
            videoView.setOnPreparedListener { mp ->
                Log.d(TAG, "Video prepared: ${video.id}")
                mp.isLooping = false
                videoView.start()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error playing video: ${e.message}")
            e.printStackTrace()
            isPlayingVideo = false  // Reset flag on exception
            currentVideoIndex++
            playCurrentVideo()
        }
    }
    
    fun stop() {
        if (videoView.isPlaying) {
            videoView.stopPlayback()
        }
        // Reset the playing flag when stopping
        isPlayingVideo = false
        Log.d(TAG, "Video playback stopped")
    }
    
    companion object {
        private const val TAG = "VideoPlayer"
    }
}
