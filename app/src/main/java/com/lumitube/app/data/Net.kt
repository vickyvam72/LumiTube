package com.lumitube.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Status koneksi internet (untuk pop up "Kamu sedang offline"). */
object Net {
    var online by mutableStateOf(true)
        private set

    private var cm: ConnectivityManager? = null

    fun init(ctx: Context) {
        val c = ctx.getSystemService(ConnectivityManager::class.java) ?: return
        cm = c
        online = check()
        try {
            c.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = post()
                override fun onLost(network: Network) = post()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = post()
                override fun onUnavailable() = post()
            })
        } catch (_: Exception) {
        }
    }

    private fun post() {
        val now = check()
        android.os.Handler(android.os.Looper.getMainLooper()).post { online = now }
    }

    private fun check(): Boolean {
        val c = cm ?: return true
        return try {
            val n = c.activeNetwork ?: return false
            val caps = c.getNetworkCapabilities(n) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }
}
