package com.lumitube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException

/** Unduh, rapikan, dan (bila perlu) terjemahkan subtitle YouTube. */
object Subtitles {
    private data class Cue(val start: String, val end: String, val text: String)

    private fun download(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", InnerTube.DESKTOP_UA).build()
        InnerTube.http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
            ?.takeIf { it.contains("-->") }
    } catch (_: Exception) {
        null
    }

    private fun ms(t: String): Long {
        val p = t.trim().split(":", ".")
        return try {
            when (p.size) {
                4 -> ((p[0].toLong() * 60 + p[1].toLong()) * 60 + p[2].toLong()) * 1000 + p[3].toLong()
                3 -> (p[0].toLong() * 60 + p[1].toLong()) * 1000 + p[2].toLong()
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Subtitle otomatis YouTube berbentuk "bergulir": tiap cue mengulang baris sebelumnya
     * dan ada cue 10 ms. Di sini dibuat jadi satu cue per kalimat tanpa tag & posisi,
     * sehingga tampil rapi di tengah bawah.
     */
    private fun parse(vtt: String): List<Cue> {
        val out = mutableListOf<Cue>()
        var prevLines = emptySet<String>()
        // Per baris: setiap baris "-->" memulai cue baru (lebih tahan terhadap baris kosong di teks).
        val all = vtt.replace("\r", "").split("\n")
        var k = 0
        while (k < all.size) {
            if (!all[k].contains("-->")) { k++; continue }
            val timing = all[k].split("-->")
            var e = k + 1
            while (e < all.size && !all[e].contains("-->")) e++
            // Baris terakhir sebelum cue berikutnya bisa berupa nomor cue; buang angka polos.
            val body = all.subList(k + 1, e)
            k = e
            val start = timing[0].trim()
            val end = timing.getOrNull(1)?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: continue
            val textLines = body
                .map { it.replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").replace("&amp;", "&").trim() }
                .filter { it.isNotEmpty() && !Regex("^\\d+$").matches(it) }
            val fresh = textLines.filter { it !in prevLines }
            if (textLines.isNotEmpty()) prevLines = textLines.toSet()
            if (ms(end) - ms(start) < 60) continue
            val text = (fresh.ifEmpty { textLines }).joinToString("\n")
            if (text.isBlank()) continue
            out.add(Cue(start, end, text))
        }
        return out
    }

    private fun build(cues: List<Cue>): String = buildString {
        append("WEBVTT\n\n")
        for (c in cues) append(c.start).append(" --> ").append(c.end).append("\n").append(c.text).append("\n\n")
    }

    /** Terjemahan cadangan lewat Google Terjemahan bila YouTube menolak &tlang=. */
    private fun translate(cues: List<Cue>, lang: String): List<Cue> {
        val result = ArrayList<Cue>(cues.size)
        var i = 0
        while (i < cues.size) {
            val batch = mutableListOf<Cue>()
            var size = 0
            while (i < cues.size && size < 3500) {
                batch.add(cues[i]); size += cues[i].text.length + 1; i++
            }
            val q = batch.joinToString("\n") { it.text.replace("\n", " ") }
            val req = Request.Builder()
                .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$lang&dt=t")
                .header("User-Agent", InnerTube.DESKTOP_UA)
                .post(FormBody.Builder().add("q", q).build())
                .build()
            val body = InnerTube.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Terjemahan gagal (${r.code})")
                r.body?.string() ?: throw IOException("Terjemahan kosong")
            }
            val parts = JSONArray(body).optJSONArray(0) ?: throw IOException("Terjemahan kosong")
            val text = (0 until parts.length()).joinToString("") { parts.optJSONArray(it)?.optString(0) ?: "" }
            val lines = text.split("\n").map { it.trim() }
            batch.forEachIndexed { k, c -> result.add(c.copy(text = lines.getOrNull(k)?.ifBlank { null } ?: c.text)) }
        }
        return result
    }

    /** Hasil berupa teks VTT yang sudah rapi, atau exception bila semua cara gagal. */
    suspend fun load(videoId: String, cap: Caption, translateTo: String?): String = withContext(Dispatchers.IO) {
        var altLoaded = false
        var altCap: Caption? = null
        suspend fun alt(): Caption? {
            if (!altLoaded) {
                altLoaded = true
                val list = StreamResolver.altCaptions(videoId)
                altCap = list.firstOrNull { it.lang == cap.lang && it.auto == cap.auto } ?: list.firstOrNull { it.lang == cap.lang }
            }
            return altCap
        }
        val tl = translateTo?.let { "&tlang=$it" } ?: ""
        val direct = download(cap.url + tl) ?: alt()?.let { download(it.url + tl) }
        if (direct != null) {
            val cues = parse(direct)
            if (cues.isNotEmpty()) return@withContext build(cues)
        }
        if (translateTo != null) {
            val original = download(cap.url) ?: alt()?.let { download(it.url) } ?: throw IOException("Subtitle kosong")
            val cues = parse(original)
            if (cues.isEmpty()) throw IOException("Subtitle kosong")
            return@withContext build(translate(cues, translateTo))
        }
        throw IOException("Subtitle kosong")
    }
}
