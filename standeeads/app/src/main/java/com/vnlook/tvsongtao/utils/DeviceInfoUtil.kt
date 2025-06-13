package com.vnlook.tvsongtao.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.vnlook.tvsongtao.model.DeviceInfo
import com.vnlook.tvsongtao.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * Utility class for handling device information operations
 */
class DeviceInfoUtil(
    private val context: Context,
    private val deviceRepository: DeviceRepository
) {
    private val TAG = "DeviceInfoUtil"
    
    // Default location coordinates (used as fallback if actual location is not available)
    private val defaultLongitude = 106.1256638
    private val defaultLatitude = 11.2791636
    
    // Coordinates for actual device location
    private var actualLongitude = defaultLongitude
    private var actualLatitude = defaultLatitude
    
    /**
     * Register or update device information
     * First checks if device exists on server by device_id
     * If exists on server but not in local storage, saves to local storage
     * If doesn't exist anywhere, creates new device
     * Otherwise, updates the existing device
     */
    suspend fun registerOrUpdateDevice(): DeviceInfo? {
        return withContext(Dispatchers.IO) {
            try {
                // Get current device ID
                val currentDeviceId = getDeviceId()
                Log.d(TAG, "Current device ID: $currentDeviceId")
                
                // Check if device exists on the server
                val serverDevice = deviceRepository.findDeviceByDeviceId(currentDeviceId)
                Log.d(TAG, "Device exists on server: ${serverDevice != null}")
                
                // Check if device info exists locally
                val hasLocalDeviceInfo = deviceRepository.hasDeviceInfo()
                Log.d(TAG, "Device info exists in SharedPreferences: $hasLocalDeviceInfo")
                
                if (serverDevice != null) {
                    // Device exists on server
                    if (!hasLocalDeviceInfo) {
                        // Device exists on server but not locally - save it locally first
                        Log.d(TAG, "Device exists on server but not locally. Saving to local storage.")
                        deviceRepository.saveDeviceInfo(serverDevice)
                    }
                    
                    // Update the device with new information
                    Log.d(TAG, "Updating existing device on server")
                    val localDevice = deviceRepository.getDeviceInfo() ?: serverDevice
                    
                    // Force get new device coordinates (don't use cached values)
                    getActualDeviceCoordinates(forceRefresh = true)
                    
                    // Update only necessary fields with new coordinates
                    val updatedDeviceInfo = localDevice.copy(
                        active = true,
                        location = getDeviceLocation(),
                        mapLocation = createMapLocationPoint(actualLongitude, actualLatitude)
                    )
                    
                    // Log the updated coordinates
                    Log.d(TAG, "Updating device with coordinates: $actualLongitude, $actualLatitude")
                    val result = deviceRepository.updateDevice(updatedDeviceInfo)
                    Log.d(TAG, "Device updated: $result")
                    
                    // Make sure the updated info is saved locally
                    if (result != null) {
                        deviceRepository.saveDeviceInfo(result)
                    }
                    
                    return@withContext result
                } else if (!hasLocalDeviceInfo) {
                    // Device doesn't exist anywhere - create new
                    Log.d(TAG, "Device doesn't exist anywhere, creating new device")
                    val deviceInfo = createDeviceInfo()
                    val result = deviceRepository.createDevice(deviceInfo)
                    Log.d(TAG, "Device created: $result")
                    
                    // Double check that device info was saved properly
                    val savedAfterCreate = deviceRepository.hasDeviceInfo()
                    Log.d(TAG, "Device info exists after create: $savedAfterCreate")
                    
                    return@withContext result
                } else {
                    // Device exists locally but not on server - create on server using local info
                    Log.d(TAG, "Device exists locally but not on server")
                    val localDevice = deviceRepository.getDeviceInfo()
                    
                    if (localDevice != null) {
                        // Force get new device coordinates (don't use cached values)
                        getActualDeviceCoordinates(forceRefresh = true)
                        
                        // Update coordinates
                        val updatedDeviceInfo = localDevice.copy(
                            active = true,
                            location = getDeviceLocation(),
                            mapLocation = createMapLocationPoint(actualLongitude, actualLatitude)
                        )
                        
                        // Create on server
                        Log.d(TAG, "Creating device on server with local info")
                        val result = deviceRepository.createDevice(updatedDeviceInfo)
                        Log.d(TAG, "Device created on server: $result")
                        
                        // Make sure the updated info is saved locally
                        if (result != null) {
                            deviceRepository.saveDeviceInfo(result)
                        }
                        
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
        
        // Get actual device coordinates
        getActualDeviceCoordinates()
        
        val location = getDeviceLocation()
        val mapLocation = createMapLocationPoint(actualLongitude, actualLatitude)
        
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
    @SuppressLint("HardwareIds")
    private fun getDeviceId(): String {
//        val uuid = UUID.randomUUID().toString()
//        return  uuid + getDeviceName()
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
                    latitude = actualLatitude
                    longitude = actualLongitude
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
                val addresses = geocoder.getFromLocation(actualLatitude, actualLongitude, 1)
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
     * Get actual device coordinates using LocationManager
     * Updates actualLongitude and actualLatitude with real values if available
     * Otherwise keeps the default values
     */
    /**
     * Attempts to get actual device coordinates if possible,
     * otherwise uses saved or default coordinates
     * 
     * @param forceRefresh If true, always tries to get new coordinates and doesn't use cached values
     * 
     * Note: This method will not work without location permissions in the manifest:
     * - android.permission.ACCESS_FINE_LOCATION or
     * - android.permission.ACCESS_COARSE_LOCATION
     */
    private fun getActualDeviceCoordinates(forceRefresh: Boolean = false) {
        Log.d(TAG, "Attempting to get device coordinates, forceRefresh=$forceRefresh")
        
        val prefs = context.getSharedPreferences("device_location", Context.MODE_PRIVATE)
        
        // If not forcing refresh, try to get from preferences first
        if (!forceRefresh) {
            val savedLat = prefs.getFloat("last_latitude", 0f)
            val savedLong = prefs.getFloat("last_longitude", 0f)
            
            // If we have saved valid coordinates, use them
            if (savedLat != 0f && savedLong != 0f) {
                actualLatitude = savedLat.toDouble()
                actualLongitude = savedLong.toDouble()
                Log.d(TAG, "Using saved coordinates: $actualLongitude, $actualLatitude")
                return
            }
        } else {
            Log.d(TAG, "Force refreshing coordinates - not using cached values")
        }
        
        // Check if we have location permissions
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            context, 
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            context, 
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (!hasFineLocationPermission && !hasCoarseLocationPermission) {
            Log.w(TAG, "No location permissions granted. Add ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION to AndroidManifest.xml")
            // Use default coordinates since we don't have permission
            actualLatitude = defaultLatitude
            actualLongitude = defaultLongitude
            Log.d(TAG, "Using default coordinates: $defaultLongitude, $defaultLatitude")
            return
        }
        
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // Check if location services are enabled
            if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
                Log.d(TAG, "Location services are disabled")
                actualLatitude = defaultLatitude
                actualLongitude = defaultLongitude
                return
            }
            
            // Try to get location from available providers
            val providers = locationManager.getProviders(true)
            if (providers.isEmpty()) {
                Log.d(TAG, "No location providers available")
                actualLatitude = defaultLatitude
                actualLongitude = defaultLongitude
                return
            }
            
            var bestLocation: Location? = null
            
            for (provider in providers) {
                try {
                    @SuppressLint("MissingPermission") // We already checked permissions above
                    val location = locationManager.getLastKnownLocation(provider)
                    if (location != null) {
                        Log.d(TAG, "Got location from provider: $provider")
                        if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                            bestLocation = location
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception for provider $provider: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting location from provider $provider: ${e.message}")
                }
            }
            
            // Update coordinates if we found a location
            if (bestLocation != null) {
                actualLatitude = bestLocation.latitude
                actualLongitude = bestLocation.longitude
                Log.d(TAG, "Using actual device coordinates: $actualLongitude, $actualLatitude")
                
                // Save to preferences for future use
                prefs.edit()
                    .putFloat("last_latitude", actualLatitude.toFloat())
                    .putFloat("last_longitude", actualLongitude.toFloat())
                    .apply()
            } else {
                Log.d(TAG, "No location found from any provider, using default coordinates")
                actualLatitude = defaultLatitude
                actualLongitude = defaultLongitude
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device coordinates: ${e.message}")
            e.printStackTrace()
            actualLatitude = defaultLatitude
            actualLongitude = defaultLongitude
        }
    }
    
    /**
     * Create a map location point string in the format "POINT (longitude latitude)"
     */
    private fun createMapLocationPoint(longitude: Double, latitude: Double): String {
        return "POINT ($longitude $latitude)"
    }
}
