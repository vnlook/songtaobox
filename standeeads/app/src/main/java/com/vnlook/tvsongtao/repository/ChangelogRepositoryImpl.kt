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
        var connection: HttpURLConnection? = null
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
            connection = url.openConnection() as HttpURLConnection
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
            Log.d(TAG, "Changelog API response code: $responseCode")
            
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
                Log.d(TAG, "✅ Changelog API response received: ${responseString.length} bytes")
                
                // Parse the response
                return@withContext parseChangelogResponse(responseString)
            } else {
                Log.e(TAG, "❌ Changelog API failed with HTTP $responseCode")
                // Try to read error response
                try {
                    val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                    val errorResponse = StringBuilder()
                    var errorLine: String?
                    while (errorReader.readLine().also { errorLine = it } != null) {
                        errorResponse.append(errorLine)
                    }
                    errorReader.close()
                    Log.e(TAG, "Error response: ${errorResponse.toString().take(200)}")
                } catch (e: Exception) {
                    Log.e(TAG, "Could not read error response: ${e.message}")
                }
                return@withContext null
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "🕐 Changelog API timeout: ${e.message}")
            return@withContext null
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "🌐 Changelog API network error - unknown host: ${e.message}")
            return@withContext null
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "🔌 Changelog API connection error: ${e.message}")
            return@withContext null
        } catch (e: javax.net.ssl.SSLException) {
            Log.e(TAG, "🔒 Changelog API SSL error: ${e.message}")
            return@withContext null
        } catch (e: java.io.IOException) {
            Log.e(TAG, "📡 Changelog API IO error: ${e.message}")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "💥 Changelog API unexpected error: ${e.message}")
            e.printStackTrace()
            return@withContext null
        } finally {
            // Always close connection
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing connection: ${e.message}")
            }
        }
    }
    
    /**
     * Parse the API response to extract the changelog entry
     * @param responseString API response as JSON string
     * @return Changelog object or null if parsing failed
     */
    private fun parseChangelogResponse(responseString: String): Changelog? {
        try {
            // Check if response is empty or blank
            if (responseString.isBlank()) {
                Log.e(TAG, "❌ Empty response string from changelog API")
                return null
            }
            
            Log.d(TAG, "🔍 Parsing changelog response...")
            val jsonObject = JsonParser.parseString(responseString).asJsonObject
            
            // Check if data field exists
            if (!jsonObject.has("data")) {
                Log.e(TAG, "❌ Response missing 'data' field")
                return null
            }
            
            val dataArray = jsonObject.getAsJsonArray("data")
            
            if (dataArray == null) {
                Log.e(TAG, "❌ 'data' field is not an array")
                return null
            }
            
            if (dataArray.size() == 0) {
                Log.w(TAG, "⚠️ Empty data array - no changelog entries found")
                return null
            }
            
            val changelogJson = dataArray.get(0).asJsonObject
            val changelog = gson.fromJson(changelogJson, Changelog::class.java)
            
            if (changelog?.date_created.isNullOrBlank()) {
                Log.e(TAG, "❌ Parsed changelog has invalid date_created field")
                return null
            }
            
            Log.d(TAG, "✅ Successfully parsed changelog: ${changelog.date_created}")
            return changelog
            
        } catch (e: com.google.gson.JsonSyntaxException) {
            Log.e(TAG, "📝 JSON syntax error parsing changelog: ${e.message}")
            Log.e(TAG, "Response preview: ${responseString.take(200)}")
            return null
        } catch (e: com.google.gson.JsonParseException) {
            Log.e(TAG, "📝 JSON parse error: ${e.message}")
            return null
        } catch (e: IllegalStateException) {
            Log.e(TAG, "📝 JSON structure error: ${e.message}")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "💥 Unexpected error parsing changelog response: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
}
