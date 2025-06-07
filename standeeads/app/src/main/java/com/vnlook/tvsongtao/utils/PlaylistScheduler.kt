package com.vnlook.tvsongtao.utils

import android.util.Log
import com.vnlook.tvsongtao.model.Playlist
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class PlaylistScheduler {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Checks if any playlist should be playing right now based on the current time
     * @return The first playlist that should be playing or null if none should play
     */
    fun getCurrentPlaylist(playlists: List<Playlist>): Playlist? {
        val now = LocalTime.now()
        
        for (playlist in playlists) {
            try {
                val startTime = LocalTime.parse(playlist.startTime, timeFormatter)
                val endTime = LocalTime.parse(playlist.endTime, timeFormatter)
                
                // Handle scenarios where playlist runs across midnight
                if (endTime.isBefore(startTime)) {
                    if ((now.isAfter(startTime) && now.isBefore(LocalTime.MAX)) ||
                        (now.isAfter(LocalTime.MIN) && now.isBefore(endTime))) {
                        return playlist
                    }
                } else {
                    if (now.isAfter(startTime) && now.isBefore(endTime)) {
                        return playlist
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing playlist times: ${e.message}")
            }
        }
        
        return null
    }
    
    companion object {
        private const val TAG = "PlaylistScheduler"
    }
}
