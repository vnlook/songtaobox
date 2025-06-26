package com.vnlook.tvsongtao.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vnlook.tvsongtao.model.Changelog
import com.vnlook.tvsongtao.utils.ApiLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Implementation of ChangelogRepository that handles changelog API operations
 */
class ChangelogRepositoryImpl(private val context: Context) : ChangelogRepository {
    private val TAG = "ChangelogRepository"
    private val gson = Gson()
    
    private val API_URL = "https://ledgiaodich.vienthongtayninh.vn:3030/items/changelog"
    private val API_PARAMS = "limit=1&sort=-date_created"
    
    /**
     * Get latest changelog entry from VNL API
     * @return Latest changelog entry or null if API call failed
     */
    override suspend fun getLatestChangelog(): Changelog? = withContext(Dispatchers.IO) {
        try {
            val urlString = "$API_URL?$API_PARAMS"
            val url = URL(urlString)
            
            // Log the API request
            val headers = mapOf(
                "User-Agent" to "Apidog/1.0.0 (https://apidog.com)",
                "Accept" to "application/json",
                "Host" to "ledgiaodich.vienthongtayninh.vn:3030",
                "Connection" to "keep-alive"
            )
            ApiLogger.logRequest(urlString, "GET", headers)
            
            // Open connection
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            // Set headers
            connection.setRequestProperty("User-Agent", "Apidog/1.0.0 (https://apidog.com)")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Host", "ledgiaodich.vienthongtayninh.vn:3030")
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
                
                // Parse the response
                return@withContext parseChangelogResponse(responseString)
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
    
    /**
     * Parse the API response to extract the changelog entry
     * @param responseString API response as JSON string
     * @return Changelog object or null if parsing failed
     */
    private fun parseChangelogResponse(responseString: String): Changelog? {
        try {
            val jsonObject = JsonParser.parseString(responseString).asJsonObject
            val dataArray = jsonObject.getAsJsonArray("data")
            
            if (dataArray != null && dataArray.size() > 0) {
                val changelogJson = dataArray.get(0).asJsonObject
                return gson.fromJson(changelogJson, Changelog::class.java)
            }
            
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing changelog response: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
}
