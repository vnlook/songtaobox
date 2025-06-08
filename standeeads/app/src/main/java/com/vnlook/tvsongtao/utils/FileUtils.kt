package com.vnlook.tvsongtao.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vnlook.tvsongtao.model.Playlist
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader

/**
 * Utility class for file operations
 */
object FileUtils {
    private const val TAG = "FileUtils"
    private val gson = Gson()

    /**
     * Read playlists from a JSON file
     * 
     * @param context Application context
     * @param filePath Path to the JSON file (can be absolute path or relative to app's files directory)
     * @return List of playlists or empty list if file not found or invalid
     */
    fun readPlaylistsFromFile(context: Context, filePath: String): List<Playlist> {
        try {
            val file = getFile(context, filePath)
            if (!file.exists()) {
                Log.e(TAG, "Playlist file not found: $filePath")
                return emptyList()
            }

            val json = readFileContent(file)
            if (json.isNullOrEmpty()) {
                Log.e(TAG, "Playlist file is empty: $filePath")
                return emptyList()
            }

            val type = object : TypeToken<List<Playlist>>() {}.type
            val playlists = gson.fromJson<List<Playlist>>(json, type)
            Log.d(TAG, "Successfully loaded ${playlists.size} playlists from file: $filePath")
            return playlists
        } catch (e: Exception) {
            Log.e(TAG, "Error reading playlists from file: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Read playlists from a content URI (for files selected from file picker)
     * 
     * @param context Application context
     * @param uri Content URI of the JSON file
     * @return List of playlists or empty list if file not found or invalid
     */
    fun readPlaylistsFromUri(context: Context, uri: Uri): List<Playlist> {
        try {
            val json = readContentUri(context, uri)
            if (json.isNullOrEmpty()) {
                Log.e(TAG, "Playlist file is empty or cannot be read: $uri")
                return emptyList()
            }

            val type = object : TypeToken<List<Playlist>>() {}.type
            val playlists = gson.fromJson<List<Playlist>>(json, type)
            Log.d(TAG, "Successfully loaded ${playlists.size} playlists from URI: $uri")
            return playlists
        } catch (e: Exception) {
            Log.e(TAG, "Error reading playlists from URI: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Get a File object from a path, handling both absolute and relative paths
     */
    private fun getFile(context: Context, filePath: String): File {
        return if (filePath.startsWith("/")) {
            // Absolute path
            File(filePath)
        } else {
            // Relative path to app's files directory
            File(context.filesDir, filePath)
        }
    }

    /**
     * Read content from a file
     */
    private fun readFileContent(file: File): String? {
        return try {
            FileInputStream(file).use { fis ->
                BufferedReader(InputStreamReader(fis)).use { reader ->
                    val stringBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line)
                    }
                    stringBuilder.toString()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading file: ${e.message}")
            null
        }
    }

    /**
     * Read content from a content URI
     */
    private fun readContentUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val stringBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line)
                    }
                    stringBuilder.toString()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading URI: ${e.message}")
            null
        }
    }
}
