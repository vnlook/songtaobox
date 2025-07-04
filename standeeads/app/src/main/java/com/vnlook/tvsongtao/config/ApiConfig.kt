package com.vnlook.tvsongtao.config

/**
 * Centralized API configuration for VNL API endpoints
 * Contains all URLs and API-related constants
 */
object ApiConfig {
    
    /**
     * Base URL for all VNL API endpoints
     */
//    private const val BASE_URL = "https://ledgiaodich.vienthongtayninh.vn:3030"
    private const val BASE_URL = "https://songtao.vnlook.com"

    /**
     * API endpoint for media playlist
     */
    const val MEDIA_PLAYLIST_URL = "$BASE_URL/items/media_playlist"
    
    /**
     * API endpoint for changelog
     */
    const val CHANGELOG_URL = "$BASE_URL/items/changelog"
    
    /**
     * API endpoint for device management
     */
    const val DEVICE_URL = "$BASE_URL/items/play_device"
    
    /**
     * Base URL for media assets (video files)
     */
    const val ASSETS_BASE_URL = "$BASE_URL/assets"
    
    /**
     * Proxy endpoint for downloading assets
     */
    const val PROXY_URL = "$BASE_URL/convert/proxy"
    
    /**
     * Common API fields for media playlist endpoint
     */
//    const val MEDIA_PLAYLIST_FIELDS = "id,title,active,order,beginTime,endTime,portrait,device.device_id,device.device_name,assets.media_assets_id.title,assets.media_assets_id.id,assets.media_assets_id.fileUrl,assets.media_assets_id.file.filename_disk,assets.media_assets_id.file.id,media_assets_id.type,assets.media_assets_id.file.filename_download,assets.order"
    const val MEDIA_PLAYLIST_FIELDS = "id,title,active,order,beginTime,endTime,portrait,device.device_id,device.device_name,assets.media_assets_id.title,assets.media_assets_id.id,assets.media_assets_id.fileUrl,assets.media_assets_id.file.filename_disk,assets.media_assets_id.file.id,media_assets_id.type,assets.media_assets_id.file.filename_download,assets.order,assets.media_assets_id.startTime,assets.media_assets_id.duration"

    /**
     * Common API fields for changelog endpoint
     */
    const val CHANGELOG_FIELDS = "id,title,content,status,created,updated"
    
    /**
     * Common headers for API requests
     */
//    object Headers {
//        const val USER_AGENT = "Apidog/1.0.0 (https://apidog.com)"
//        const val ACCEPT = "*/*"
//        const val CONNECTION = "keep-alive"
//        const val HOST = "ledgiaodich.vienthongtayninh.vn:3030"
//    }
    object Headers {
        const val USER_AGENT = "Apidog/1.0.0 (https://apidog.com)"
        const val ACCEPT = "*/*"
        const val CONNECTION = "keep-alive"
        const val HOST = "songtao.vnlook.com"
    }
    
    /**
     * Timeout configurations for API requests
     */
    object Timeouts {
        const val CONNECT_TIMEOUT = 30000 // 30 seconds
        const val READ_TIMEOUT = 60000    // 60 seconds
        const val DOWNLOAD_CONNECT_TIMEOUT = 300000 // 5 minutes for downloads
        const val DOWNLOAD_READ_TIMEOUT = 300000    // 5 minutes for downloads
    }
    
    /**
     * Generate device-specific URL for updates
     */
    fun getDeviceUpdateUrl(deviceId: String): String {
        return "$DEVICE_URL/$deviceId"
    }
    
    /**
     * Generate full media playlist URL with fields
     */
    fun getMediaPlaylistUrlWithFields(): String {
        return "$MEDIA_PLAYLIST_URL?fields=$MEDIA_PLAYLIST_FIELDS"
    }
    
    /**
     * Generate full changelog URL with fields
     */
    fun getChangelogUrlWithFields(): String {
        return "$CHANGELOG_URL?$CHANGELOG_FIELDS"
    }
    
    /**
     * Generate proxy URL for downloading assets
     * @param originalUrl The original asset URL to proxy
     * @param start Start position in seconds (optional)
     * @param duration Duration in seconds (optional)
     * @return Proxy URL with query parameters
     */
    fun getProxyUrl(originalUrl: String, start: Int? = null, duration: Int? = null): String {
        val params = mutableListOf("url=${java.net.URLEncoder.encode(originalUrl, "UTF-8")}")
        if (start != null) params.add("start=$start")
        if (duration != null) params.add("duration=$duration")
        return "$PROXY_URL?${params.joinToString("&")}"
    }
} 