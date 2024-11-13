package io.pawsomepals.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkUtils @Inject constructor(
    private val context: Context
) {
    fun isNetworkAvailable(): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    Log.d("NetworkUtils", "WiFi connection available")
                    true
                }
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    Log.d("NetworkUtils", "Cellular connection available")
                    true
                }
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    Log.d("NetworkUtils", "Ethernet connection available")
                    true
                }
                else -> {
                    Log.d("NetworkUtils", "No network connection available")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("NetworkUtils", "Error checking network state", e)
            return false
        }
    }

    fun getConnectionType(): String {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return "None"
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return "None"

            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            Log.e("NetworkUtils", "Error getting connection type", e)
            return "Error"
        }
    }
}