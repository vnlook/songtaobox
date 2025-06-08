package com.vnlook.tvsongtao.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.model.Video
import com.vnlook.tvsongtao.utils.ApiLogger
import org.json.JSONObject

/**
 * Parser for VNL API responses
 */
object VNLApiResponseParser {
    private const val TAG = "VNLApiResponseParser"
    private val gson = Gson()

    /**
     * Parse API response JSON string into playlists and videos
     * 
     * @param jsonString JSON string from API response
     * @return Pair of playlists and videos lists
     */
    fun parseApiResponse(jsonString: String): Pair<List<Playlist>, List<Video>> {
        Log.d(TAG, "Parsing VNL API response JSON")
        
        try {
            // Validate JSON structure first
            try {
                JSONObject(jsonString)
                Log.d(TAG, "JSON structure validation passed")
            } catch (e: Exception) {
                Log.e(TAG, "Invalid JSON structure: ${e.message}")
                return Pair(emptyList(), emptyList())
            }
            
            // Log the API response format
            Log.d(TAG, "API Response format matches curl command: curl --location --request GET 'https://songtao.vnlook.com/items/media_playlist?fields=id,title,active,order,beginTime,endTime,assets.media_assets_id.title,assets.media_assets_id.fileUrl,assets.media_assets_id.file.filename_disk,assets.media_assets_id.file.id,media_assets_id.type,assets.media_assets_id.file.filename_download'")
            
            val type = object : TypeToken<VNLApiResponse>() {}.type
            val apiResponse: VNLApiResponse
            
            try {
                apiResponse = gson.fromJson<VNLApiResponse>(jsonString, type)
                Log.d(TAG, "Successfully parsed API response with ${apiResponse.data.size} playlists")
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "Failed to parse API response: ${e.message}")
                return Pair(emptyList(), emptyList())
            }
            
            val videos = mutableListOf<Video>()
            val playlists = mutableListOf<Playlist>()
            
            apiResponse.data.forEach { playlistData ->
                Log.d(TAG, "Processing playlist: ID=${playlistData.id}, title=${playlistData.title}, beginTime=${playlistData.beginTime}, endTime=${playlistData.endTime}")
                
                // Extract videos from playlist assets
                val playlistVideos = playlistData.assets.mapNotNull { asset ->
                    asset.mediaAssetsId?.let { mediaAsset ->
                        val videoId = mediaAsset.file?.id
                        if (videoId == null) {
                            Log.w(TAG, "Skipping video with null ID in playlist ${playlistData.id}")
                            return@let null
                        }
                        
                        val videoName = mediaAsset.title ?: "Untitled Video"
                        val baseUrl = mediaAsset.fileUrl ?: "https://songtao.vnlook.com/assets"
                        val fileName = mediaAsset.file.filenameDisk
                        if (fileName == null) {
                            Log.w(TAG, "Skipping video ${videoId} with null filename in playlist ${playlistData.id}")
                            return@let null
                        }
                        
                        // Create video URL by combining baseUrl and fileName
                        val videoUrl = "$baseUrl/$fileName"
                        
                        Log.d(TAG, "Found video: ID=${videoId}, name=${videoName}, url=${videoUrl}")
                        
                        Video(
                            id = videoId,
                            name = videoName,
                            url = videoUrl,
                            isDownloaded = false
                        )
                    }
                }
                
                // Add videos to the main list
                videos.addAll(playlistVideos)
                
                // Create playlist with video IDs
                val playlist = Playlist(
                    id = playlistData.id.toString(),
                    startTime = playlistData.beginTime?.substring(0, 5) ?: "00:00", // Extract HH:MM from HH:MM:SS
                    endTime = playlistData.endTime?.substring(0, 5) ?: "23:59",
                    videoIds = playlistVideos.map { it.id }
                )
                
                Log.d(TAG, "Created playlist: ID=${playlist.id}, startTime=${playlist.startTime}, endTime=${playlist.endTime}, videoCount=${playlist.videoIds.size}")
                playlists.add(playlist)
            }
            
            Log.d(TAG, "Parsed ${playlists.size} playlists and ${videos.size} videos from API response")
            
            // Log detailed summary of parsed data
            val playlistSummary = playlists.joinToString("\n") { 
                "Playlist ${it.id}: ${it.startTime}-${it.endTime}, ${it.videoIds.size} videos" 
            }
            Log.d(TAG, "Playlists summary:\n$playlistSummary")
            
            val videoSummary = videos.take(5).joinToString("\n") { 
                "Video ${it.id}: ${it.name}, URL=${it.url}" 
            }
            Log.d(TAG, "First 5 videos summary:\n$videoSummary${if (videos.size > 5) "\n...and ${videos.size - 5} more" else ""}")
            
            return Pair(playlists, videos)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing API response: ${e.message}")
            e.printStackTrace()
            return Pair(emptyList(), emptyList())
        }
    }
    
    /**
     * Data classes for parsing API response
     */
    data class VNLApiResponse(
        @SerializedName("data") val data: List<VNLPlaylistData> = emptyList()
    )
    
    data class VNLPlaylistData(
        @SerializedName("id") val id: Int = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("active") val active: Boolean = false,
        @SerializedName("order") val order: Int = 0,
        @SerializedName("beginTime") val beginTime: String? = null,
        @SerializedName("endTime") val endTime: String? = null,
        @SerializedName("assets") val assets: List<VNLAsset> = emptyList()
    )
    
    data class VNLAsset(
        @SerializedName("media_assets_id") val mediaAssetsId: VNLMediaAsset? = null
    )
    
    data class VNLMediaAsset(
        @SerializedName("title") val title: String? = null,
        @SerializedName("fileUrl") val fileUrl: String? = null,
        @SerializedName("file") val file: VNLFile? = null,
        @SerializedName("type") val type: String? = null
    )
    
    data class VNLFile(
        @SerializedName("filename_disk") val filenameDisk: String? = null,
        @SerializedName("id") val id: String? = null,
        @SerializedName("filename_download") val filenameDownload: String? = null
    )
}
