package com.lumitube.app.data

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Menyimpan cookie login, visitorData dan preferensi kecil aplikasi. */
object Session {
    private lateinit var prefs: SharedPreferences

    var loggedIn by mutableStateOf(false)
        private set
    var account by mutableStateOf<Account?>(null)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("lumitube", Context.MODE_PRIVATE)
        loggedIn = cookie?.contains("SAPISID=") == true
        val name = prefs.getString("acc_name", null)
        if (loggedIn && name != null) {
            account = Account(name, prefs.getString("acc_handle", null), prefs.getString("acc_photo", null))
        }
    }

    val cookie: String?
        get() = prefs.getString("cookie", null)

    fun saveCookie(value: String) {
        prefs.edit().putString("cookie", value).apply()
        loggedIn = value.contains("SAPISID=")
    }

    fun saveAccount(acc: Account) {
        account = acc
        prefs.edit()
            .putString("acc_name", acc.name)
            .putString("acc_handle", acc.handle)
            .putString("acc_photo", acc.photo)
            .apply()
    }

    fun logout() {
        prefs.edit().remove("cookie").remove("acc_name").remove("acc_handle").remove("acc_photo").apply()
        loggedIn = false
        account = null
        visitorData = null
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (_: Exception) {
        }
    }

    var visitorData: String?
        get() = prefs.getString("visitor", null)
        set(value) {
            prefs.edit().putString("visitor", value).apply()
        }

    /** "video" atau "music": mode terakhir yang dibuka. */
    var mode: String
        get() = prefs.getString("mode", "video") ?: "video"
        set(value) {
            prefs.edit().putString("mode", value).apply()
        }

    fun isLyricsProviderEnabled(name: String): Boolean = prefs.getBoolean("lyr_$name", true)

    fun setLyricsProviderEnabled(name: String, enabled: Boolean) {
        prefs.edit().putBoolean("lyr_$name", enabled).apply()
    }

    /** Tampilkan PiP lirik saat aplikasi diminimalkan di mode Music. */
    var lyricsPip: Boolean
        get() = prefs.getBoolean("lyrics_pip", true)
        set(value) {
            prefs.edit().putBoolean("lyrics_pip", value).apply()
        }

    /** Putar otomatis video terkait berikutnya. */
    var autoplay: Boolean
        get() = prefs.getBoolean("autoplay", true)
        set(value) {
            prefs.edit().putBoolean("autoplay", value).apply()
        }

    /** Tanyakan YouTube / YouTube Music setiap aplikasi dibuka. */
    var askMode: Boolean
        get() = prefs.getBoolean("ask_mode", true)
        set(value) {
            prefs.edit().putBoolean("ask_mode", value).apply()
        }

    /** Ulangi video (mode YouTube). */
    var loopVideo: Boolean
        get() = prefs.getBoolean("loop_video", false)
        set(value) {
            prefs.edit().putBoolean("loop_video", value).apply()
        }

    /** Kualitas maksimum video (tinggi piksel). */
    var maxQuality: Int
        get() = prefs.getInt("max_quality", 720)
        set(value) {
            prefs.edit().putInt("max_quality", value).apply()
        }
}
