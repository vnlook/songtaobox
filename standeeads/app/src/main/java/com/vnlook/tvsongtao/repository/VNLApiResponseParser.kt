package com.vnlook.tvsongtao.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.vnlook.tvsongtao.config.ApiConfig
import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.model.Video
import com.vnlook.tvsongtao.utils.ApiLogger
import com.vnlook.tvsongtao.model.DeviceInfo
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
    /**
     * Parse the raw API response JSON string into VNLApiResponse object
     * 
     * @param jsonString JSON string from API response
     * @return VNLApiResponse object
     */
    fun getRawApiResponse(jsonString: String): VNLApiResponse {
        try {
            val type = object : TypeToken<VNLApiResponse>() {}.type
            return gson.fromJson(jsonString, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing raw API response: ${e.message}")
            e.printStackTrace()
            return VNLApiResponse(emptyList())
        }
    }
    
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
            Log.d(TAG, "API Response format matches curl command: curl --location --request GET '${ApiConfig.getMediaPlaylistUrlWithFields()}'")
            
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
                        val assetId = mediaAsset.id
                        if (videoId == null) {
                            Log.w(TAG, "Skipping video with null ID in playlist ${playlistData.id}")
                            return@let null
                        }
                        
                        val videoName = mediaAsset.title ?: "Untitled Video"
                        val baseUrl = mediaAsset.fileUrl ?: ApiConfig.ASSETS_BASE_URL
                        val fileName = mediaAsset.file.filenameDisk
                        if (fileName == null) {
                            Log.w(TAG, "Skipping video ${videoId} with null filename in playlist ${playlistData.id}")
                            return@let null
                        }
                        
                        // Create video URL by combining baseUrl and fileName
                        val videoUrl = "$baseUrl/$fileName"
                        var order = asset.order ?: 1
                        
                        Log.d(TAG, "Found video: ID=${videoId}, name=${videoName}, url=${videoUrl}, startTime=${mediaAsset.startTime}, duration=${mediaAsset.duration}")
                        
                        Video(
                            id = "$assetId",
                            name = videoName,
                            order = order,
                            url = videoUrl,
                            startTime = mediaAsset.startTime,
                            duration = mediaAsset.duration,
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
                    portrait = playlistData.portrait,
                    deviceId = playlistData.device?.deviceId,
                    deviceName = playlistData.device?.deviceName,
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
     * OPTIMIZED: Parse API response JSON string into playlists and videos with device filtering
     * This method filters playlists during parsing to avoid processing unnecessary data
     * 
     * @param jsonString JSON string from API response
     * @param deviceInfo Device info to filter playlists by
     * @return Pair of filtered playlists and videos lists
     */
    fun parseApiResponseWithDeviceFilter(jsonString: String, deviceInfo: DeviceInfo?): Pair<List<Playlist>, List<Video>> {
        Log.d(TAG, "Parsing VNL API response JSON with device filter")
        
        if (deviceInfo == null) {
            Log.w(TAG, "DeviceInfo is null, falling back to regular parsing")
            return parseApiResponse(jsonString)
        }
        
        Log.d(TAG, "Filtering for device: ID=${deviceInfo.deviceId}, Name=${deviceInfo.deviceName}")
        
        try {
            // Validate JSON structure first
            try {
                JSONObject(jsonString)
                Log.d(TAG, "JSON structure validation passed")
            } catch (e: Exception) {
                Log.e(TAG, "Invalid JSON structure: ${e.message}")
                return Pair(emptyList(), emptyList())
            }
            
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
            
            // Filter playlists during parsing - OPTIMIZATION
            val filteredPlaylistData = apiResponse.data.filter { playlistData ->
                val matchesDevice = playlistData.device?.deviceId == deviceInfo.deviceId && 
                                   playlistData.device?.deviceName == deviceInfo.deviceName &&
                                   playlistData.device?.deviceName != null
                
                if (matchesDevice) {
                    Log.d(TAG, "✅ Processing playlist: ID=${playlistData.id}, matches device filter")
                } else {
                    Log.d(TAG, "⏭️ Skipping playlist: ID=${playlistData.id}, doesn't match device filter")
                }
                
                matchesDevice
            }
            
            Log.d(TAG, "Filtered ${filteredPlaylistData.size} of ${apiResponse.data.size} playlists for device")
            
            filteredPlaylistData.forEach { playlistData ->
                // Extract videos from playlist assets
                val playlistVideos = playlistData.assets.mapNotNull { asset ->
                    asset.mediaAssetsId?.let { mediaAsset ->
                        val videoId = mediaAsset.file?.id
                        val assetId = mediaAsset.id
                        if (videoId == null) {
                            Log.w(TAG, "Skipping video with null ID in playlist ${playlistData.id}")
                            return@let null
                        }
                        
                        val videoName = mediaAsset.title ?: "Untitled Video"
                        val baseUrl = mediaAsset.fileUrl ?: ApiConfig.ASSETS_BASE_URL
                        val fileName = mediaAsset.file.filenameDisk
                        if (fileName == null) {
                            Log.w(TAG, "Skipping video ${videoId} with null filename in playlist ${playlistData.id}")
                            return@let null
                        }
                        
                        // Create video URL by combining baseUrl and fileName
                        val videoUrl = "$baseUrl/$fileName"
                        var order = asset.order ?: 1
                        
                        Log.d(TAG, "Found video: ID=${videoId}, name=${videoName}, url=${videoUrl}, startTime=${mediaAsset.startTime}, duration=${mediaAsset.duration}")
                        
                        Video(
                            id = "$assetId",
                            name = videoName,
                            order = order,
                            url = videoUrl,
                            startTime = mediaAsset.startTime,
                            duration = mediaAsset.duration,
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
                    portrait = playlistData.portrait,
                    deviceId = playlistData.device?.deviceId,
                    deviceName = playlistData.device?.deviceName,
                    videoIds = playlistVideos.map { it.id }
                )
                
                Log.d(TAG, "Created playlist: ID=${playlist.id}, startTime=${playlist.startTime}, endTime=${playlist.endTime}, videoCount=${playlist.videoIds.size}")
                playlists.add(playlist)
            }
            
            Log.d(TAG, "✅ OPTIMIZED: Parsed ${playlists.size} playlists and ${videos.size} videos (filtered during parsing)")
            
            // Log detailed summary of parsed data
            val playlistSummary = playlists.joinToString("\n") { 
                "Playlist ${it.id}: ${it.startTime}-${it.endTime}, ${it.videoIds.size} videos" 
            }
            Log.d(TAG, "Filtered playlists summary:\n$playlistSummary")
            
            return Pair(playlists, videos)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing API response with device filter: ${e.message}")
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
        @SerializedName("portrait") val portrait: Boolean = true,
        @SerializedName("device") val device: VNLDevice? = null,
        @SerializedName("assets") val assets: List<VNLAsset> = emptyList()
    )
    
    data class VNLAsset(
        @SerializedName("media_assets_id") val mediaAssetsId: VNLMediaAsset? = null,
        @SerializedName("order") val order: Int? = null
    )

    data class VNLDevice(
        @SerializedName("device_id") val deviceId: String? = null,
        @SerializedName("device_name") val deviceName: String? = null
    )
    
    data class VNLMediaAsset(
        @SerializedName("id") val id: Int = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("fileUrl") val fileUrl: String? = null,
        @SerializedName("file") val file: VNLFile? = null,
        @SerializedName("type") val type: String? = null,
        @SerializedName("startTime") val startTime: Int? = null,
        @SerializedName("duration") val duration: Int? = null
    )
    
    data class VNLFile(
        @SerializedName("filename_disk") val filenameDisk: String? = null,
        @SerializedName("id") val id: String? = null,
        @SerializedName("filename_download") val filenameDownload: String? = null
    )
}
