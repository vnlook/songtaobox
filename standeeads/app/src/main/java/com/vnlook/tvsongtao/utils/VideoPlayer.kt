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
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    init {
        playerView.player = player
        
        // Configure audio settings
        configureAudio()
        
        // Set volume to maximum by default
        setMaxVolume()

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

    /**
     * Configure audio settings for ExoPlayer to ensure proper audio playback
     * especially in emulator environments
     */
    private fun configureAudio() {
        try {
            // Set ExoPlayer audio attributes
            val audioAttributes = ExoPlayerAudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            
            player.setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            
            // Request audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
                
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                player.volume = 1.0f
                                if (!player.isPlaying) {
                                    player.play()
                                }
                            }
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                player.pause()
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, 
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                player.volume = 0.5f
                            }
                        }
                    }.build()
                
                audioManager.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { focusChange -> 
                        // Handle focus change if needed
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
            
            Log.d(TAG, "Audio configuration completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring audio: ${e.message}")
            e.printStackTrace()
        }
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
            
            // Ensure audio is properly configured before playing
            configureAudio()
            setMaxVolume()
            
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
        
        // Release audio focus when stopping
        releaseAudioFocus()
        
        Log.d(TAG, "Video playback stopped")
    }

    fun release() {
        player.release()
        releaseAudioFocus()
        Log.d(TAG, "ExoPlayer released")
    }
    
    /**
     * Release audio focus when no longer needed
     */
    private fun releaseAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let {
                    audioManager.abandonAudioFocusRequest(it)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio focus: ${e.message}")
        }
    }
    
    /**
     * Sets the ExoPlayer volume to maximum level (1.0)
     * Also attempts to set the device volume to maximum using AudioManager
     */
    fun setMaxVolume() {
        try {
            // Set ExoPlayer's internal volume to maximum (1.0)
            player.volume = 1.0f
            
            // Also set system volume to maximum
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
            
            // Ensure audio is not muted (especially important for emulators)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE,
                        0
                    )
                }
            }
            
            Log.d(TAG, "Volume set to maximum (${maxVolume})")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "VideoPlayer"
    }
}