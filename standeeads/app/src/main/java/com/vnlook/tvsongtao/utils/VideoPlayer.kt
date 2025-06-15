package com.vnlook.tvsongtao.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes as ExoPlayerAudioAttributes
import androidx.media3.common.C
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
    private var currentVideoIndex = 0
    private var videos: List<Video> = emptyList()
    private var isPlayingVideo = false

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    init {
        playerView.player = player

        // Gắn listener cho trạng thái phát
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    Log.d(TAG, "Video completed")
                    isPlayingVideo = false
                    currentVideoIndex++
                    playCurrentVideo()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error: ${error.message}")
                isPlayingVideo = false
                currentVideoIndex++
                playCurrentVideo()
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
        if (isPlayingVideo) {
            Log.d(TAG, "Already playing a video, ignoring call to playCurrentVideo()")
            return
        }

        if (currentVideoIndex >= videos.size) {
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
            isPlayingVideo = true
            val uri = Uri.fromFile(file)
            Log.d(TAG, "Playing video: ${video.id}, URI: $uri")

            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            
            player.play()

        } catch (e: Exception) {
            Log.e(TAG, "Error playing video: ${e.message}")
            e.printStackTrace()
            isPlayingVideo = false
            currentVideoIndex++
            playCurrentVideo()
        }
    }

    fun stop() {
        player.stop()
        isPlayingVideo = false
        

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