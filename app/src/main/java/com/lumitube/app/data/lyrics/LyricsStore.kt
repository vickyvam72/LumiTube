package com.lumitube.app.data.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lirik yang ikut tersimpan saat lagu di-download, supaya tetap tampil
 * (termasuk efek per kata) ketika diputar tanpa internet.
 */
object LyricsStore {
    var dir: File? = null

    private fun file(videoId: String) = dir?.let { File(it, "lyrics").apply { mkdirs() } }?.let { File(it, "$videoId.json") }

    fun has(videoId: String) = file(videoId)?.exists() == true

    fun save(videoId: String, l: Lyrics) {
        val f = file(videoId) ?: return
        val lines = JSONArray()
        for (line in l.lines) {
            val words = JSONArray()
            for (w in line.words) words.put(JSONArray().put(w.text).put(w.start).put(w.end))
            lines.put(
                JSONObject().put("s", line.start).put("e", line.end).put("t", line.text)
                    .put("b", line.background).put("w", words)
            )
        }
        f.writeText(
            JSONObject().put("source", l.source).put("synced", l.synced).put("word", l.wordSynced)
                .put("lines", lines).toString()
        )
    }

    fun load(videoId: String): Lyrics? {
        val f = file(videoId) ?: return null
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            val arr = o.getJSONArray("lines")
            val lines = (0 until arr.length()).map { i ->
                val l = arr.getJSONObject(i)
                val w = l.optJSONArray("w") ?: JSONArray()
                LyricLine(
                    start = l.optLong("s"),
                    end = l.optLong("e"),
                    text = l.optString("t"),
                    words = (0 until w.length()).map { k ->
                        val a = w.getJSONArray(k)
                        LyricWord(a.optString(0), a.optLong(1), a.optLong(2))
                    },
                    background = l.optBoolean("b"),
                )
            }
            Lyrics(o.optString("source") + " (offline)", lines, o.optBoolean("synced"), o.optBoolean("word"))
        } catch (_: Exception) {
            null
        }
    }
}
