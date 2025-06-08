package com.vnlook.tvsongtao.model

/**
 * Data class representing a changelog entry from the API
 */
data class Changelog(
    val id: Int,
    val date_created: String,
    val date_updated: String?,
    val log: String
)
