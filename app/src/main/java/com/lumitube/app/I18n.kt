package com.lumitube.app

import android.content.Context

/** Bahasa aplikasi: hanya Bahasa Indonesia (teks YouTube & subtitle bawaan juga Indonesia). */
object I18n {
    /** [code] = kode file terjemahan, [hl] = bahasa untuk YouTube, [sub] = kode subtitle YouTube. */
    data class Lang(val code: String, val name: String, val hl: String, val sub: String)

    /** Aplikasi hanya berbahasa Indonesia. */
    val langs = listOf(Lang("id", "Bahasa Indonesia", "id", "id"))

    val lang: String = "id"

    private lateinit var app: Context

    val current: Lang get() = langs.firstOrNull { it.code == lang } ?: langs[0]

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    /** Terjemahkan teks (kunci = teks Indonesia). Teks yang tidak dikenal dikembalikan apa adanya. */
    fun tr(s: String): String = s

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
