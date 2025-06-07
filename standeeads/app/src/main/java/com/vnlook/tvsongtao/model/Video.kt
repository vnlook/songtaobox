package com.vnlook.tvsongtao.model

import com.google.gson.annotations.SerializedName

data class Video(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String,
    @SerializedName("isDownloaded") var isDownloaded: Boolean = false,
    @SerializedName("localPath") var localPath: String? = null
)
