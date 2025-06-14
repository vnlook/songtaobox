package com.vnlook.tvsongtao.repository

import com.vnlook.tvsongtao.model.DeviceInfo

/**
 * Repository interface for device-related operations
 */
interface DeviceRepository {
    /**
     * Create a new device in the backend
     * @param deviceInfo The device information to create
     * @return The created device information from the API
     */
    suspend fun createDevice(deviceInfo: DeviceInfo): DeviceInfo?
    
    /**
     * Update an existing device in the backend
     * @param deviceInfo The device information to update
     * @return The updated device information from the API
     */
    suspend fun updateDevice(deviceInfo: DeviceInfo): DeviceInfo?
    
    /**
     * Get list of all devices from the backend
     * @return List of all devices, or empty list if failed
     */
    suspend fun getListDevices(): List<DeviceInfo>
    
    /**
     * Find device in the backend by device ID
     * @param deviceId The device ID to search for
     * @return The device information if found, null otherwise
     */
    suspend fun findDeviceByDeviceId(deviceId: String): DeviceInfo?
    
    /**
     * Save device information to local storage
     * @param deviceInfo The device information to save
     */
    fun saveDeviceInfo(deviceInfo: DeviceInfo)
    
    /**
     * Get device information from local storage
     * @return The saved device information, or null if not available
     */
    fun getDeviceInfo(): DeviceInfo?
    
    /**
     * Check if device information exists in local storage
     * @return True if device information exists, false otherwise
     */
    fun hasDeviceInfo(): Boolean
}
