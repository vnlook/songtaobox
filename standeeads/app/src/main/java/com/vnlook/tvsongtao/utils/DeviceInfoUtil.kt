package com.vnlook.tvsongtao.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
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
    
    // Location permission request code
    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        val REQUIRED_PERMISSIONS: Array<String> = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    
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
                    val gotLocation = getActualDeviceCoordinates(forceRefresh = true)
                    
                    // Update only necessary fields with new coordinates
                    val updatedDeviceInfo = if (gotLocation) {
                        localDevice.copy(
                            active = true,
                            location = getDeviceLocation(),
                            mapLocation = createMapLocationPoint(actualLongitude, actualLatitude)
                        )
                    } else {
                        // If we couldn't get location, keep the existing location
                        localDevice.copy(active = true)
                    }
                    
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
                        val gotLocation = getActualDeviceCoordinates(forceRefresh = true)
                        
                        // Update coordinates if we got them, otherwise keep existing
                        val updatedDeviceInfo = if (gotLocation) {
                            localDevice.copy(
                                active = true,
                                location = getDeviceLocation(),
                                mapLocation = createMapLocationPoint(actualLongitude, actualLatitude)
                            )
                        } else {
                            // If we couldn't get location, keep the existing location
                            localDevice.copy(active = true)
                        }
                        
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
        val gotLocation = getActualDeviceCoordinates()
        
        return if (gotLocation) {
            val location = getDeviceLocation()
            val mapLocation = createMapLocationPoint(actualLongitude, actualLatitude)
            
            DeviceInfo(
                deviceId = deviceId,
                deviceName = deviceName,
                location = location,
                active = true,
                mapLocation = mapLocation
            )
        } else {
            // If we couldn't get location, create with empty location
            DeviceInfo(
                deviceId = deviceId,
                deviceName = deviceName,
                location = "",
                active = true,
                mapLocation = createMapLocationPoint(defaultLongitude, defaultLatitude)
            )
        }
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
//        val manufacturer = Build.MANUFACTURER
//        val model = Build.MODEL
//        return if (model.startsWith(manufacturer)) {
//            model
//        } else {
//            "$manufacturer $model"
//        }
        val name = getCustomDeviceName(context)
        return  name
    }

    private fun getCustomDeviceName(context: Context): String {
        return Settings.Global.getString(context.contentResolver, "device_name")
            ?: Settings.Secure.getString(context.contentResolver, "bluetooth_name")
            ?: getFallbackDeviceName()
    }

    private fun getFallbackDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
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
    /**
     * Try to get actual device coordinates using FusedLocationProviderClient
     * @return Boolean indicating if location was successfully retrieved (or already had valid cached location)
     */
    @SuppressLint("MissingPermission")
    private fun getActualDeviceCoordinates(forceRefresh: Boolean = false): Boolean {
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
                return true
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
            Log.w(TAG, "Location permissions not granted")
            return false
        }
        
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // Check if location services are enabled
            val isLocationEnabled = LocationManagerCompat.isLocationEnabled(locationManager)
            Log.d(TAG, "Location services enabled: $isLocationEnabled")
            
            if (!isLocationEnabled) {
                Log.d(TAG, "Location services are disabled")
                actualLatitude = defaultLatitude
                actualLongitude = defaultLongitude
                return false
            }
            
            // Try to get last known location from FusedLocationProvider
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            
            // Create a CountDownLatch to wait for the location result
            val latch = java.util.concurrent.CountDownLatch(1)
            var success = false
            
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    Log.d(TAG, "Got location from FusedLocationProvider: ${it.latitude}, ${it.longitude}")
                    actualLatitude = it.latitude
                    actualLongitude = it.longitude
                    
                    // Save to preferences for future use
                    prefs.edit()
                        .putFloat("last_latitude", actualLatitude.toFloat())
                        .putFloat("last_longitude", actualLongitude.toFloat())
                        .apply()
                    success = true
                } ?: run {
                    Log.d(TAG, "No location available from FusedLocationProvider")
                    // Fall back to last known location from location manager
                    try {
                        val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                            ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                        
                        lastKnownLocation?.let {
                            actualLatitude = it.latitude
                            actualLongitude = it.longitude
                            Log.d(TAG, "Using last known location: $actualLongitude, $actualLatitude")
                            
                            prefs.edit()
                                .putFloat("last_latitude", actualLatitude.toFloat())
                                .putFloat("last_longitude", actualLongitude.toFloat())
                                .apply()
                            success = true
                        } ?: run {
                            Log.d(TAG, "No last known location available")
                            actualLatitude = defaultLatitude
                            actualLongitude = defaultLongitude
                            success = false
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting last known location", e)
                        actualLatitude = defaultLatitude
                        actualLongitude = defaultLongitude
                        success = false
                    }
                }
                latch.countDown()
            }.addOnFailureListener { exception ->
                Log.e(TAG, "Error getting location from FusedLocationProvider", exception)
                actualLatitude = defaultLatitude
                actualLongitude = defaultLongitude
                success = false
                latch.countDown()
            }
            
            // Wait for the location result with a timeout of 5 seconds
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            
            if (!success) {
                Log.d(TAG, "Using default coordinates: $defaultLongitude, $defaultLatitude")
            }
            
            return success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device coordinates: ${e.message}")
            e.printStackTrace()
            actualLatitude = defaultLatitude
            actualLongitude = defaultLongitude
            return false
        }
    }
    
    /**
     * Create a map location point string in the format "POINT (longitude latitude)"
     */
    private fun createMapLocationPoint(longitude: Double, latitude: Double): String {
        return "POINT ($longitude $latitude)"
    }
}
