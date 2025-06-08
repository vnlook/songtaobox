package com.vnlook.tvsongtao.repository

import android.util.Log
import com.vnlook.tvsongtao.utils.ApiLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for making API calls to VNL API
 */
object VNLApiClient {
    private const val TAG = "VNLApiClient"
    
    private const val API_URL = "https://songtao.vnlook.com/items/media_playlist"
    private const val API_FIELDS = "id,title,active,order,beginTime,endTime,assets.media_assets_id.title,assets.media_assets_id.fileUrl,assets.media_assets_id.file.filename_disk,assets.media_assets_id.file.id,media_assets_id.type,assets.media_assets_id.file.filename_download"
    
    /**
     * Make a GET request to the VNL API
     * 
     * @return API response as JSON string or null if request failed
     */
    suspend fun getPlaylists(): String? = withContext(Dispatchers.IO) {
        try {
            val urlString = "$API_URL?fields=$API_FIELDS"
            val url = URL(urlString)
            
            // Log the API request
            val headers = mapOf(
                "User-Agent" to "Apidog/1.0.0 (https://apidog.com)",
                "Accept" to "*/*",
                "Host" to "songtao.vnlook.com",
                "Connection" to "keep-alive"
            )
            ApiLogger.logRequest(urlString, "GET", headers)
            
            // Open connection
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            // Set headers
            connection.setRequestProperty("User-Agent", "Apidog/1.0.0 (https://apidog.com)")
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Host", "songtao.vnlook.com")
            connection.setRequestProperty("Connection", "keep-alive")
            
            // Set timeouts
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            // Get response
            val responseCode = connection.responseCode
            Log.d(TAG, "API response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read response
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val responseString = response.toString()
                Log.d(TAG, "API response received: ${responseString.length} bytes")
                Log.d(TAG, "API response preview: ${responseString.take(100)}...")
                
                return@withContext responseString
            } else {
                Log.e(TAG, "API request failed with response code: $responseCode")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error making API request: ${e.message}")
            e.printStackTrace()
            return@withContext null
        }
    }
}
