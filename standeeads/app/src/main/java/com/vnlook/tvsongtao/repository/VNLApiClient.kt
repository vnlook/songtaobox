package com.vnlook.tvsongtao.repository

import android.util.Log
import com.vnlook.tvsongtao.config.ApiConfig
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
    
    /**
     * Make a GET request to the VNL API
     * 
     * @return API response as JSON string or null if request failed
     */
    suspend fun getPlaylists(): String? = withContext(Dispatchers.IO) {
        try {
            val urlString = ApiConfig.getMediaPlaylistUrlWithFields()
            val url = URL(urlString)
            
            // Log the API request
            val headers = mapOf(
                "User-Agent" to ApiConfig.Headers.USER_AGENT,
                "Accept" to ApiConfig.Headers.ACCEPT,
                "Host" to ApiConfig.Headers.HOST,
                "Connection" to ApiConfig.Headers.CONNECTION
            )
            ApiLogger.logRequest(urlString, "GET", headers)
            
            // Open connection
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            // Set headers
            connection.setRequestProperty("User-Agent", ApiConfig.Headers.USER_AGENT)
            connection.setRequestProperty("Accept", ApiConfig.Headers.ACCEPT)
            connection.setRequestProperty("Host", ApiConfig.Headers.HOST)
            connection.setRequestProperty("Connection", ApiConfig.Headers.CONNECTION)
            
            // Set timeouts (increased for better reliability)
            connection.connectTimeout = ApiConfig.Timeouts.CONNECT_TIMEOUT
            connection.readTimeout = ApiConfig.Timeouts.READ_TIMEOUT
            
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
