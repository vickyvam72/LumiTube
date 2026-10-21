package com.lumitube.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumitube.app.data.Session
import org.json.JSONObject

/**
 * Bahasa aplikasi. Teks sumber ditulis dalam Bahasa Indonesia; terjemahannya ada di
 * assets/i18n/<kode>.json (kunci = teks Indonesia). Yang berubah: tampilan aplikasi,
 * teks dari YouTube (hl) dan subtitle bawaan. Judul video & audio tetap asli.
 */
object I18n {
    /** [code] = kode file terjemahan, [hl] = bahasa untuk YouTube, [sub] = kode subtitle YouTube. */
    data class Lang(val code: String, val name: String, val hl: String, val sub: String)

    val langs = listOf(
        Lang("en", "English", "en", "en"),
        Lang("id", "Bahasa Indonesia", "id", "id"),
        Lang("ms", "Bahasa Melayu", "ms", "ms"),
        Lang("es", "Español", "es", "es"),
        Lang("pt", "Português", "pt", "pt"),
        Lang("fr", "Français", "fr", "fr"),
        Lang("de", "Deutsch", "de", "de"),
        Lang("it", "Italiano", "it", "it"),
        Lang("ru", "Русский", "ru", "ru"),
        Lang("tr", "Türkçe", "tr", "tr"),
        Lang("vi", "Tiếng Việt", "vi", "vi"),
        Lang("th", "ไทย", "th", "th"),
        Lang("hi", "हिन्दी", "hi", "hi"),
        Lang("ja", "日本語", "ja", "ja"),
        Lang("ko", "한국어", "ko", "ko"),
        Lang("zh", "中文（简体）", "zh-CN", "zh-Hans"),
    )

    /** Bawaan: Inggris. */
    var lang by mutableStateOf("en")
        private set

    @Volatile private var table: Map<String, String> = emptyMap()
    private lateinit var app: Context

    val current: Lang get() = langs.firstOrNull { it.code == lang } ?: langs[0]

    fun init(ctx: Context) {
        app = ctx.applicationContext
        lang = Session.getString("app_lang")?.takeIf { c -> langs.any { it.code == c } } ?: "en"
        table = load(lang)
    }

    fun set(code: String) {
        if (code == lang) return
        table = load(code)
        lang = code
        Session.putString("app_lang", code)
        // Teks dari YouTube (judul rak, chip, "x ditonton") ikut bahasa baru.
        com.lumitube.app.ui.common.FeedLoader.clear()
    }

    private fun load(code: String): Map<String, String> {
        if (code == "id" || !::app.isInitialized) return emptyMap()
        return try {
            val json = JSONObject(app.assets.open("i18n/$code.json").bufferedReader().use { it.readText() })
            val m = HashMap<String, String>(json.length() * 2)
            json.keys().forEach { k -> json.optString(k).takeIf { it.isNotEmpty() }?.let { m[k] = it } }
            m
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Terjemahkan teks (kunci = teks Indonesia). Teks yang tidak dikenal dikembalikan apa adanya. */
    fun tr(s: String): String {
        if (lang == "id") return s
        return table[s] ?: s
    }

    /** Teks dengan isian: "Disimpan ke %s" → isian diganti berurutan. */
    fun tr(template: String, vararg args: Any?): String {
        val t = tr(template)
        val sb = StringBuilder()
        var i = 0
        var a = 0
        while (i < t.length) {
            if (t[i] == '%' && i + 1 < t.length && t[i + 1] == 's') {
                sb.append(args.getOrNull(a++)?.toString() ?: "")
                i += 2
            } else {
                sb.append(t[i]); i++
            }
        }
        return sb.toString()
    }
}

/** Singkatan [I18n.tr]. */
fun tr(s: String): String = I18n.tr(s)
fun tr(template: String, vararg args: Any?): String = I18n.tr(template, *args)
