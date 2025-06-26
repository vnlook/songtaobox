package com.vnlook.tvsongtao.utils

import android.util.Log
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * SSL Configuration utility for development/testing purposes
 * WARNING: Do not use this in production!
 */
object SSLConfig {
    private const val TAG = "SSLConfig"
    
    /**
     * Configure SSL to trust all certificates during development
     * This should only be used during development/testing
     */
    fun configureDevSSL() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            
            // Also trust all hostnames during development
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
            
            Log.d(TAG, "Configured SSL for development (trust all certificates)")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring SSL: ${e.message}")
            e.printStackTrace()
        }
    }
} 