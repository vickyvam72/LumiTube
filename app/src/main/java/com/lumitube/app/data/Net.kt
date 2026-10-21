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
        post()
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

    /** Periksa ulang (mis. saat aplikasi dibuka lagi). */
    fun refresh() = post()

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()

    /**
     * Periksa ulang status (beberapa kali, karena Android kadang masih melaporkan jaringan
     * lama sesaat setelah putus).
     */
    private fun post() {
        for (delay in longArrayOf(0, 1500, 4000)) {
            main.postDelayed({ worker.execute { val now = check(); main.post { online = now } } }, delay)
        }
    }

    /** Ada jaringan dan internetnya benar-benar tersambung (bukan sekadar data aktif tanpa kuota). */
    private fun check(): Boolean {
        val c = cm ?: return true
        return try {
            val n = c.activeNetwork ?: return false
            val caps = c.getNetworkCapabilities(n) ?: return false
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return true
            // Belum tervalidasi oleh Android: coba langsung ke server YouTube.
            probe()
        } catch (_: Exception) {
            true
        }
    }

    private fun probe(): Boolean = try {
        val req = okhttp3.Request.Builder().url("https://www.youtube.com/generate_204").head().build()
        InnerTube.http.newBuilder().callTimeout(4, java.util.concurrent.TimeUnit.SECONDS).build()
            .newCall(req).execute().use { it.code in 200..399 }
    } catch (_: Exception) {
        false
    }
}
