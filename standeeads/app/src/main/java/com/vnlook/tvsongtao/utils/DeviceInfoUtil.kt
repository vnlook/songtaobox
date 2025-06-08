package com.vnlook.tvsongtao.utils

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import com.vnlook.tvsongtao.model.DeviceInfo
import com.vnlook.tvsongtao.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Utility class for handling device information operations
 */
class DeviceInfoUtil(
    private val context: Context,
    private val deviceRepository: DeviceRepository
) {
    private val TAG = "DeviceInfoUtil"
    
    // Default location coordinates (can be updated with actual location if available)
    private val defaultLongitude = 106.7906368997206
    private val defaultLatitude = 10.804248069799328
    
    /**
     * Register or update device information
     * If device info doesn't exist in SharedPreferences, create a new device
     * Otherwise, update the existing device
     */
    suspend fun registerOrUpdateDevice(): DeviceInfo? {
        return withContext(Dispatchers.IO) {
            try {
                if (!deviceRepository.hasDeviceInfo()) {
                    // First time - create new device
                    Log.d(TAG, "No device info found, creating new device")
                    val deviceInfo = createDeviceInfo()
                    val result = deviceRepository.createDevice(deviceInfo)
                    Log.d(TAG, "Device created: $result")
                    return@withContext result
                } else {
                    // Device exists - update it
                    Log.d(TAG, "Existing device info found, updating device")
                    val existingDeviceInfo = deviceRepository.getDeviceInfo()
                    if (existingDeviceInfo != null) {
                        // Update only necessary fields
                        val updatedDeviceInfo = existingDeviceInfo.copy(
                            active = true,
                            location = getDeviceLocation(),
                            mapLocation = createMapLocationPoint(defaultLongitude, defaultLatitude)
                        )
                        val result = deviceRepository.updateDevice(updatedDeviceInfo)
                        Log.d(TAG, "Device updated: $result")
                        return@withContext result
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering/updating device: ${e.message}")
                e.printStackTrace()
            }
            return@withContext null
        }
    }
    
    /**
     * Create a new DeviceInfo object with device details
     */
    private fun createDeviceInfo(): DeviceInfo {
        val deviceId = getDeviceId()
        val deviceName = getDeviceName()
        val location = getDeviceLocation()
        val mapLocation = createMapLocationPoint(defaultLongitude, defaultLatitude)
        
        return DeviceInfo(
            deviceId = deviceId,
            deviceName = deviceName,
            location = location,
            active = true,
            mapLocation = mapLocation
        )
    }
    
    /**
     * Get the device ID (Android ID)
     */
    private fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }
    
    /**
     * Get the device name (manufacturer and model)
     */
    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) {
            model
        } else {
            "$manufacturer $model"
        }
    }
    
    /**
     * Get the device location (country and city/region if available)
     */
    private fun getDeviceLocation(): String {
        try {
            // For Vietnamese devices, always use Vietnam as the country
            val country = "Vietnam"
            
            // Try to get more specific location using Geocoder if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val location = Location("default").apply {
                    latitude = defaultLatitude
                    longitude = defaultLongitude
                }
                
                // Using a callback approach requires a different pattern
                // We can't return directly from the lambda, so we'll use a variable
                var result = "Ho Chi Minh City, $country" // Default to Ho Chi Minh City if geocoding fails
                
                // Since we can't block for the result in the new API, we'll just use the default
                // The callback will update the location for next time
                geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val city = address.locality ?: address.subAdminArea ?: "Ho Chi Minh City"
                        val province = address.adminArea ?: ""
                        result = if (province.isNotEmpty()) {
                            "$city, $province, $country"
                        } else {
                            "$city, $country"
                        }
                        // Save this for next time in SharedPreferences
                        val prefs = context.getSharedPreferences("device_location", Context.MODE_PRIVATE)
                        prefs.edit().putString("cached_location", result).apply()
                    }
                }
                
                // Try to get cached location from previous runs
                val prefs = context.getSharedPreferences("device_location", Context.MODE_PRIVATE)
                val cachedLocation = prefs.getString("cached_location", null)
                if (cachedLocation != null) {
                    return cachedLocation
                }
                
                return result
            } else {
                @Suppress("DEPRECATION")
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(defaultLatitude, defaultLongitude, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    val address = addresses[0]
                    val city = address.locality ?: address.subAdminArea ?: "Ho Chi Minh City"
                    val province = address.adminArea ?: ""
                    return if (province.isNotEmpty()) {
                        "$city, $province, $country"
                    } else {
                        "$city, $country"
                    }
                }
            }
            
            // Default to Ho Chi Minh City, Vietnam if geocoding fails
            return "Ho Chi Minh City, $country"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location: ${e.message}")
            return "Ho Chi Minh City, Vietnam" // Default location
        }
    }
    
    /**
     * Create a map location point string in the format "POINT (longitude latitude)"
     */
    private fun createMapLocationPoint(longitude: Double, latitude: Double): String {
        return "POINT ($longitude $latitude)"
    }
}
