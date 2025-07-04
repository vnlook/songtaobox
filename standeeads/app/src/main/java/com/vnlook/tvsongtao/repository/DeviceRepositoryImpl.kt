package com.vnlook.tvsongtao.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.vnlook.tvsongtao.config.ApiConfig
import com.vnlook.tvsongtao.model.DeviceInfo
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Implementation of DeviceRepository interface
 */
class DeviceRepositoryImpl(private val context: Context) : DeviceRepository {
    
    private val TAG = "DeviceRepositoryImpl"
    private val PREF_NAME = "device_info_prefs"
    private val KEY_DEVICE_INFO = "device_info"
    private val gson = Gson()
    
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Create a new device in the backend
     * @param deviceInfo The device information to create
     * @return The created device information from the API
     */
    override suspend fun createDevice(deviceInfo: DeviceInfo): DeviceInfo? {
        try {
            val url = URL(ApiConfig.DEVICE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            
            // Create JSON array with device info
            val deviceInfoArray = JsonArray()
            val deviceInfoJson = JsonObject()
            deviceInfoJson.addProperty("device_id", deviceInfo.deviceId)
            deviceInfoJson.addProperty("device_name", deviceInfo.deviceName)
            deviceInfoJson.addProperty("location", deviceInfo.location)
            deviceInfoJson.addProperty("active", deviceInfo.active)
            deviceInfoJson.addProperty("mapLocation", deviceInfo.mapLocation)
            deviceInfoArray.add(deviceInfoJson)
            
            // Write request body
            val outputStreamWriter = OutputStreamWriter(connection.outputStream)
            outputStreamWriter.write(deviceInfoArray.toString())
            outputStreamWriter.flush()
            
            // Get response
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Create device response: $response")
                
                // Parse response
                val jsonResponse = JsonParser.parseString(response).asJsonObject
                val dataArray = jsonResponse.getAsJsonArray("data")
                if (dataArray != null && dataArray.size() > 0) {
                    val deviceInfoResponse = gson.fromJson(dataArray.get(0), DeviceInfo::class.java)
                    // Save to SharedPreferences
                    saveDeviceInfo(deviceInfoResponse)
                    return deviceInfoResponse
                }
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Error creating device: $responseCode, $errorResponse")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating device: ${e.message}")
            e.printStackTrace()
        }
        return null
    }
    
    /**
     * Update an existing device in the backend
     * @param deviceInfo The device information to update
     * @return The updated device information from the API
     */
    override suspend fun updateDevice(deviceInfo: DeviceInfo): DeviceInfo? {
        try {
            val url = URL(ApiConfig.getDeviceUpdateUrl(deviceInfo.id.toString()))
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "PATCH"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            
            // Create JSON object with device info
            val deviceInfoJson = JsonObject()
            deviceInfoJson.addProperty("id", deviceInfo.id)
            deviceInfoJson.addProperty("device_name", deviceInfo.deviceName)
            deviceInfoJson.addProperty("location", deviceInfo.location)
            deviceInfoJson.addProperty("active", deviceInfo.active)
            deviceInfoJson.addProperty("mapLocation", deviceInfo.mapLocation)
            
            // Write request body
            val outputStreamWriter = OutputStreamWriter(connection.outputStream)
            outputStreamWriter.write(deviceInfoJson.toString())
            outputStreamWriter.flush()
            
            // Get response
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Update device response: $response")
                
                // Parse response
                val jsonResponse = JsonParser.parseString(response).asJsonObject
                val dataObject = jsonResponse.getAsJsonObject("data")
                if (dataObject != null) {
                    val deviceInfoResponse = gson.fromJson(dataObject, DeviceInfo::class.java)
                    // Save to SharedPreferences
                    saveDeviceInfo(deviceInfoResponse)
                    return deviceInfoResponse
                }
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Error updating device: $responseCode, $errorResponse")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception updating device: ${e.message}")
            e.printStackTrace()
        }
        return null
    }
    
    /**
     * Save device information to local storage
     * @param deviceInfo The device information to save
     */
    override fun saveDeviceInfo(deviceInfo: DeviceInfo) {
        try {
            val deviceInfoJson = gson.toJson(deviceInfo)
            sharedPreferences.edit().putString(KEY_DEVICE_INFO, deviceInfoJson).apply()
            Log.d(TAG, "Device info saved to SharedPreferences: $deviceInfoJson")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving device info: ${e.message}")
        }
    }
    
    /**
     * Get device information from local storage
     * @return The saved device information, or null if not available
     */
    override fun getDeviceInfo(): DeviceInfo? {
        try {
            val deviceInfoJson = sharedPreferences.getString(KEY_DEVICE_INFO, null)
            if (deviceInfoJson != null) {
                return gson.fromJson(deviceInfoJson, DeviceInfo::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device info: ${e.message}")
        }
        return null
    }
    
    /**
     * Check if device information exists in local storage
     * @return True if device information exists, false otherwise
     */
    override fun hasDeviceInfo(): Boolean {
        return sharedPreferences.contains(KEY_DEVICE_INFO)
    }
    
    /**
     * Get list of all devices from the backend
     * @return List of all devices, or empty list if failed
     */
    override suspend fun getListDevices(): List<DeviceInfo> {
        try {
            val url = URL(ApiConfig.DEVICE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Get devices list response: $response")
                
                // Parse response
                val jsonResponse = JsonParser.parseString(response).asJsonObject
                val dataArray = jsonResponse.getAsJsonArray("data")
                
                if (dataArray != null) {
                    val devicesList = mutableListOf<DeviceInfo>()
                    for (i in 0 until dataArray.size()) {
                        val deviceJson = dataArray.get(i).asJsonObject
                        val device = gson.fromJson(deviceJson, DeviceInfo::class.java)
                        devicesList.add(device)
                    }
                    Log.d(TAG, "Found ${devicesList.size} devices")
                    return devicesList
                }
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Error getting devices list: $responseCode, $errorResponse")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting devices list: ${e.message}")
            e.printStackTrace()
        }
        return emptyList()
    }
    
    /**
     * Find device in the backend by device ID
     * @param deviceId The device ID to search for
     * @return The device information if found, null otherwise
     */
    override suspend fun findDeviceByDeviceId(deviceId: String): DeviceInfo? {
        try {
            val devices = getListDevices()
            return devices.find { it.deviceId == deviceId }
        } catch (e: Exception) {
            Log.e(TAG, "Exception finding device by ID: ${e.message}")
            e.printStackTrace()
        }
        return null
    }
    
    companion object {
        /**
         * Get the device ID (Android ID)
         * @param context Application context
         * @return Device ID string
         */
        fun getDeviceId(context: Context): String {
            return android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
        }
        
        /**
         * Get the device name (manufacturer and model)
         * @return Device name string
         */
//        fun getDeviceName(): String {
//            val manufacturer = Build.MANUFACTURER
//            val model = Build.MODEL
//            return if (model.startsWith(manufacturer)) {
//                model
//            } else {
//                "$manufacturer $model"
//            }
//        }
        
        /**
         * Create a map location point string in the format "POINT (longitude latitude)"
         * @param longitude The longitude value
         * @param latitude The latitude value
         * @return Formatted map location string
         */
        fun createMapLocationPoint(longitude: Double, latitude: Double): String {
            return "POINT ($longitude $latitude)"
        }
        
        /**
         * Get the current locale's country as the location
         * @return Country name
         */
        fun getCurrentLocation(): String {
            val locale = Locale.getDefault()
            return locale.displayCountry
        }
    }
}
