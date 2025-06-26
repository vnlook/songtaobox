package com.vnlook.tvsongtao.utils

import android.util.Log

/**
 * Utility class for logging API requests and responses
 */
object ApiLogger {
    private const val TAG = "ApiLogger"
    
    /**
     * Log API request details
     * 
     * @param url The API URL
     * @param method The HTTP method (GET, POST, etc.)
     * @param headers Map of request headers
     */
    fun logRequest(url: String, method: String, headers: Map<String, String>) {
        val headersString = headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        
        val logMessage = """
            |=== API REQUEST ===
            |URL: $url
            |Method: $method
            |Headers:
            |$headersString
            |==================
        """.trimMargin()
        
        Log.d(TAG, logMessage)
    }
    
    /**
     * Log VNL API request based on the curl command
     */
    fun logVNLApiRequest() {
        val url = "https://ledgiaodich.vienthongtayninh.vn:3030/items/media_playlist?fields=id,title,active,order,beginTime,endTime,assets.media_assets_id.title,assets.media_assets_id.fileUrl,assets.media_assets_id.file.filename_disk,assets.media_assets_id.file.id,media_assets_id.type,assets.media_assets_id.file.filename_download"
        val method = "GET"
        val headers = mapOf(
            "User-Agent" to "Apidog/1.0.0 (https://apidog.com)",
            "Accept" to "*/*",
            "Host" to "ledgiaodich.vienthongtayninh.vn:3030",
            "Connection" to "keep-alive"
        )
        
        logRequest(url, method, headers)
    }
    
    /**
     * Log file operations for API response files
     * 
     * @param operation The operation being performed (read, write, etc.)
     * @param filePath The path to the file
     * @param success Whether the operation was successful
     * @param message Additional message (optional)
     */
    fun logFileOperation(operation: String, filePath: String, success: Boolean, message: String = "") {
        val status = if (success) "SUCCESS" else "FAILED"
        Log.d(TAG, "[$status] $operation: $filePath $message")
    }
}
