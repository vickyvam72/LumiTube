package com.lumitube.app

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File

/**
 * Pencatat crash: bila aplikasi tertutup paksa, penyebabnya disimpan ke file supaya
 * bisa dilihat & disalin saat aplikasi dibuka lagi. Galat di coroutine latar belakang
 * dicatat tanpa menutup aplikasi.
 */
object CrashLog {
    private var file: File? = null
    private var installed = false

    fun install(context: Context) {
        if (file == null) file = File(context.applicationContext.filesDir, "last_crash.txt")
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            write("Crash di thread ${t.name}", e)
            previous?.uncaughtException(t, e)
        }
    }

    fun write(title: String, e: Throwable) {
        try {
            val f = file ?: return
            val text = buildString {
                append("LumiTube ").append(BuildConfig.APP_MODE).append(' ').append(BuildConfig.VERSION_NAME)
                    .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                append("Android ").append(android.os.Build.VERSION.RELEASE).append(" • ")
                    .append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL).append('\n')
                append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())).append('\n')
                append(title).append('\n')
                append(e.stackTraceToString().take(6000))
            }
            f.writeText(text)
        } catch (_: Throwable) {
        }
    }

    /** Laporan crash terakhir (lalu dihapus), atau null. */
    fun take(): String? = try {
        file?.takeIf { it.exists() }?.let { f -> f.readText().also { f.delete() } }
    } catch (_: Throwable) {
        null
    }

    /** Untuk scope coroutine yang hidup lama: galat dicatat, aplikasi tidak tertutup. */
    val handler = CoroutineExceptionHandler { _, e ->
        if (e !is kotlinx.coroutines.CancellationException) {
            android.util.Log.e("LumiTube", "Galat latar belakang", e)
        }
    }
}
