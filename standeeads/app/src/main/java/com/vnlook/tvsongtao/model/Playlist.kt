package com.vnlook.tvsongtao.model

import com.google.gson.annotations.SerializedName

data class Playlist(
    @SerializedName("id") val id: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String,
    @SerializedName("portrait") val portrait: Boolean,
    @SerializedName("device_id") val deviceId: String?,
    @SerializedName("device_name") val deviceName: String?,
    @SerializedName("videoIds") val videoIds: List<String>
)
