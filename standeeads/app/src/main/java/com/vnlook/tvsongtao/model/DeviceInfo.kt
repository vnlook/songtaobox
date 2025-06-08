package com.vnlook.tvsongtao.model

import com.google.gson.annotations.SerializedName

/**
 * Data class representing device information from the API
 */
data class DeviceInfo(
    @SerializedName("id")
    val id: Int = 0,
    
    @SerializedName("device_id")
    val deviceId: String = "",
    
    @SerializedName("device_name")
    val deviceName: String = "",
    
    @SerializedName("location")
    val location: String = "",
    
    @SerializedName("active")
    val active: Boolean = true,
    
    @SerializedName("mapLocation")
    val mapLocation: String = ""
)
