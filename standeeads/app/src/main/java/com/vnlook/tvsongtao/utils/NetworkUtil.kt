package com.vnlook.tvsongtao.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log

/**
 * Utility class for checking network connectivity
 */
class NetworkUtil {
    
    companion object {
        private const val TAG = "NetworkUtil"
        
        /**
         * Check if device has internet connectivity
         * @param context Application context
         * @return true if connected to internet, false otherwise
         */
        fun isNetworkAvailable(context: Context): Boolean {
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val networkCapabilities = connectivityManager.activeNetwork ?: return false
                    val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
                    
                    return when {
                        actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                        actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                        actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                        else -> false
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val networkInfo = connectivityManager.activeNetworkInfo
                    @Suppress("DEPRECATION")
                    return networkInfo?.isConnected == true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking network availability: ${e.message}")
                return false
            }
        }
        
        /**
         * Check if device is connected to WiFi
         * @param context Application context
         * @return true if connected to WiFi, false otherwise
         */
        fun isWiFiConnected(context: Context): Boolean {
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val networkCapabilities = connectivityManager.activeNetwork ?: return false
                    val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
                    return actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                } else {
                    @Suppress("DEPRECATION")
                    val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                    @Suppress("DEPRECATION")
                    return networkInfo?.isConnected == true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking WiFi connectivity: ${e.message}")
                return false
            }
        }
        
        /**
         * Check if device is connected to mobile data
         * @param context Application context
         * @return true if connected to mobile data, false otherwise
         */
        fun isMobileDataConnected(context: Context): Boolean {
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val networkCapabilities = connectivityManager.activeNetwork ?: return false
                    val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
                    return actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                } else {
                    @Suppress("DEPRECATION")
                    val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
                    @Suppress("DEPRECATION")
                    return networkInfo?.isConnected == true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking mobile data connectivity: ${e.message}")
                return false
            }
        }
        
        /**
         * Get network type as string for logging
         * @param context Application context
         * @return Network type description
         */
        fun getNetworkTypeDescription(context: Context): String {
            return when {
                isWiFiConnected(context) -> "WiFi"
                isMobileDataConnected(context) -> "Mobile Data"
                isNetworkAvailable(context) -> "Other Network"
                else -> "No Network"
            }
        }
    }
} 