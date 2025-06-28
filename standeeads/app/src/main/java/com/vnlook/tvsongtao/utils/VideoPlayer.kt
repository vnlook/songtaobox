package com.vnlook.tvsongtao.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vnlook.tvsongtao.data.DataManager
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.model.Video
import java.io.File

class VideoPlayer(
    private val context: Context,
    private val playerView: PlayerView,
    private val onPlaylistFinished: () -> Unit
) {
    private val dataManager = DataManager(context)
    private val downloadHelper = VideoDownloadHelper(context)
    private var currentPlaylist: Playlist? = null
    private var videos: List<Video> = emptyList()

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    init {
        playerView.player = player
        
        // Enable repeat mode for continuous playback
        player.repeatMode = Player.REPEAT_MODE_ALL

        // Gắn listener cho trạng thái phát
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        Log.d(TAG, "Player ready")
                    }
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "Playlist ended - but should repeat due to REPEAT_MODE_ALL")
                        // With REPEAT_MODE_ALL, this shouldn't happen unless there are no valid videos
                        onPlaylistFinished()
                    }
                    Player.STATE_BUFFERING -> {
                        Log.d(TAG, "Player buffering")
                    }
                    Player.STATE_IDLE -> {
                        Log.d(TAG, "Player idle")
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error: ${error.message}")
                // Skip to next video in case of error
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                } else {
                    // If no more videos, try to restart playlist
                    if (videos.isNotEmpty()) {
                        setupPlaylist()
                    } else {
                        onPlaylistFinished()
                    }
                }
            }
        })
    }

    fun playPlaylist(playlist: Playlist) {
        currentPlaylist = playlist
        videos = getVideosForPlaylist(playlist)

        if (videos.isEmpty()) {
            Log.d(TAG, "No videos available to play in playlist ${playlist.id}")
            onPlaylistFinished()
            return
        }

        setupPlaylist()
    }

    private fun setupPlaylist() {
        val mediaItems = videos.mapNotNull { video ->
            val videoPath = if (!video.localPath.isNullOrEmpty()) {
                video.localPath!!
            } else {
                downloadHelper.getVideoDownloadPath(video.url ?: "")
            }
            
            val file = File(videoPath)
            if (!file.exists()) {
                Log.e(TAG, "Video file not found: $videoPath")
                return@mapNotNull null
            }
            
            val uri = Uri.fromFile(file)
            Log.d(TAG, "Adding video to playlist: ${video.id}, URI: $uri")
            MediaItem.fromUri(uri)
        }
        
        if (mediaItems.isEmpty()) {
            Log.d(TAG, "No valid media items found")
            onPlaylistFinished()
            return
        }
        
        Log.d(TAG, "🎬 Setting up playlist with ${mediaItems.size} videos")
        
        // Set all media items at once
        player.setMediaItems(mediaItems)
        player.prepare()
        player.play()
    }

    private fun getVideosForPlaylist(playlist: Playlist): List<Video> {
        val allVideos = dataManager.getVideos()
        Log.d(TAG, "🎬 Total videos in cache: ${allVideos.size}")
        
        val playlistVideos = allVideos.filter { video ->
            playlist.videoIds.contains(video.id)
        }
        Log.d(TAG, "📋 Videos for playlist ${playlist.id}: ${playlistVideos.size}")
        
        val downloadedVideos = playlistVideos.filter { video ->
            val isDownloaded = video.isDownloaded
            Log.d(TAG, "Video ${video.id}: isDownloaded=$isDownloaded, localPath=${video.localPath}")
            
            // Also check if file actually exists
            if (isDownloaded && !video.localPath.isNullOrEmpty()) {
                val file = File(video.localPath!!)
                val fileExists = file.exists()
                Log.d(TAG, "Video ${video.id}: file exists=$fileExists, path=${video.localPath}")
                return@filter fileExists
            }
            return@filter isDownloaded
        }
        
        Log.d(TAG, "✅ Downloaded videos for playlist: ${downloadedVideos.size}")
        return downloadedVideos
    }

    fun stop() {
        player.stop()
        Log.d(TAG, "Video playback stopped")
    }

    fun release() {
        player.release()
        Log.d(TAG, "ExoPlayer released")
    }

    companion object {
        private const val TAG = "VideoPlayer"
    }
}