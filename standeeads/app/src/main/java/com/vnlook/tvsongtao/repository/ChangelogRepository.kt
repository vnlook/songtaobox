package com.vnlook.tvsongtao.repository

import com.vnlook.tvsongtao.model.Changelog

/**
 * Repository interface for changelog operations
 * All methods are suspend functions to support coroutines and asynchronous API calls
 */
interface ChangelogRepository {
    /**
     * Get latest changelog entry from VNL API
     * @return Latest changelog entry or null if API call failed
     */
    suspend fun getLatestChangelog(): Changelog?
}
