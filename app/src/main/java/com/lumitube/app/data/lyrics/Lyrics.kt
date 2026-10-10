package com.lumitube.app.data.lyrics

import android.util.Base64
import com.lumitube.app.data.InnerTube
import com.lumitube.app.data.Repo
import com.lumitube.app.data.Session
import com.lumitube.app.data.arr
import com.lumitube.app.data.objects
import com.lumitube.app.data.obj
import com.lumitube.app.data.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

// ------------------------------------------------------------------ Model

/** Satu kata (atau suku kata). [text] sudah termasuk spasi di belakangnya bila ada. */
data class LyricWord(val text: String, val start: Long, val end: Long)

data class LyricLine(
    val start: Long,
    val end: Long,
    val text: String,
    val words: List<LyricWord>,
    val background: Boolean = false,
)

data class Lyrics(
    val source: String,
    val lines: List<LyricLine>,
    val synced: Boolean,
    /** true bila waktu per kata berasal langsung dari penyedia (bukan perkiraan). */
    val wordSynced: Boolean,
) {
    val quality: Int get() = when {
        wordSynced -> 3
        synced -> 2
        lines.isNotEmpty() -> 1
        else -> 0
    }
}

data class SongQuery(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSec: Int,
)

// ------------------------------------------------------------------ Utilitas

object LyricsUtils {

    private val timeTag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val wordTag = Regex("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>")

    private fun toMs(m: String, s: String, frac: String?): Long {
        val f = when {
            frac == null -> 0L
            frac.length == 1 -> frac.toLong() * 100
            frac.length == 2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return m.toLong() * 60_000 + s.toLong() * 1000 + f
    }

    /** Parser LRC biasa dan LRC "enhanced" (<mm:ss.xx> per kata). */
    fun parseLrc(lrc: String, source: String): Lyrics {
        data class Raw(val start: Long, val body: String)
        val raws = mutableListOf<Raw>()
        for (rawLine in lrc.lines()) {
            val line = rawLine.trim()
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) continue
            val body = line.substring(tags.last().range.last + 1)
            for (t in tags) {
                raws.add(Raw(toMs(t.groupValues[1], t.groupValues[2], t.groupValues[3].ifEmpty { null }), body))
            }
        }
        raws.sortBy { it.start }
        var anyWords = false
        val lines = raws.mapIndexedNotNull { i, r ->
            val end = raws.getOrNull(i + 1)?.start ?: (r.start + 6000)
            val wordMatches = wordTag.findAll(r.body).toList()
            if (wordMatches.isNotEmpty()) {
                anyWords = true
                val words = mutableListOf<LyricWord>()
                wordMatches.forEachIndexed { wi, m ->
                    val start = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3].ifEmpty { null })
                    val textEnd = wordMatches.getOrNull(wi + 1)?.range?.first ?: r.body.length
                    val text = r.body.substring(m.range.last + 1, textEnd)
                    val wEnd = wordMatches.getOrNull(wi + 1)?.let {
                        toMs(it.groupValues[1], it.groupValues[2], it.groupValues[3].ifEmpty { null })
                    } ?: end
                    if (text.isNotEmpty()) words.add(LyricWord(text, start, wEnd))
                }
                val text = words.joinToString("") { it.text }.trim()
                if (text.isEmpty()) null else LyricLine(r.start, end, text, words)
            } else {
                val text = r.body.trim()
                if (text.isEmpty()) null else LyricLine(r.start, end, text, estimateWords(text, r.start, end))
            }
        }
        return Lyrics(source, lines, synced = lines.isNotEmpty(), wordSynced = anyWords)
    }

    fun plain(text: String, source: String): Lyrics {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .map { LyricLine(-1, -1, it, emptyList()) }
        return Lyrics(source, lines, synced = false, wordSynced = false)
    }

    /**
     * Perkiraan waktu per kata untuk lirik yang hanya sinkron per baris,
     * supaya efek per kata tetap berjalan. Durasi dibagi sesuai panjang kata.
     */
    fun estimateWords(text: String, start: Long, end: Long): List<LyricWord> {
        val parts = Regex("\\S+\\s*").findAll(text).map { it.value }.toList()
        if (parts.isEmpty()) return emptyList()
        // Batasi durasi aktif: penyanyi biasanya menyelesaikan baris sebelum jeda panjang.
        val total = (end - start).coerceIn(300, (parts.size * 650L).coerceAtLeast(1200))
        val weights = parts.map { it.trim().length.coerceAtLeast(1) + 1 }
        val sum = weights.sum().toDouble()
        var t = start.toDouble()
        return parts.mapIndexed { i, w ->
            val d = total * weights[i] / sum
            val word = LyricWord(w, t.toLong(), (t + d).toLong())
            t += d
            word
        }
    }

    /**
     * Mencocokkan potongan kata ke teks baris asli supaya spasi antar-kata benar
     * (sebagian penyedia memberi suku kata tanpa spasi).
     */
    fun alignWords(lineText: String, words: List<LyricWord>): List<LyricWord> {
        if (words.isEmpty()) return words
        var cursor = 0
        val out = mutableListOf<LyricWord>()
        for ((i, w) in words.withIndex()) {
            val core = w.text.trim()
            val idx = if (core.isEmpty()) -1 else lineText.indexOf(core, cursor)
            if (idx < 0) {
                out.add(w)
                continue
            }
            var endIdx = idx + core.length
            val nextCore = words.getOrNull(i + 1)?.text?.trim()
            val nextIdx = if (nextCore.isNullOrEmpty()) lineText.length else lineText.indexOf(nextCore, endIdx).let { if (it < 0) endIdx else it }
            // Sertakan spasi di antara kata ini dan kata berikutnya.
            while (endIdx < nextIdx && endIdx < lineText.length && lineText[endIdx].isWhitespace()) endIdx++
            out.add(w.copy(text = lineText.substring(idx, endIdx)))
            cursor = endIdx
        }
        return out
    }

    private val titleNoise = listOf(
        Regex("\\s*[(\\[][^)\\]]*(official|video|audio|lyric|visualizer|hd|hq|4k|mv|m/v)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE),
        Regex("\\s*[(\\[]\\s*(feat|ft)\\.?[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE),
        Regex("\\s+(feat|ft)\\.?\\s.*$", RegexOption.IGNORE_CASE),
        Regex("\\s*\\|.*$"),
        Regex("\\s*-\\s*(official|lyric|audio|video).*$", RegexOption.IGNORE_CASE),
    )

    fun cleanTitle(t: String): String {
        var s = t
        for (r in titleNoise) s = s.replace(r, "")
        return s.trim()
    }

    fun cleanArtist(a: String): String {
        var s = a.substringBefore(" • ").trim()
        for (sep in listOf(" & ", ", ", " x ", " feat. ", " ft. ", " featuring ", " dan ", " and ")) {
            if (s.contains(sep, ignoreCase = true)) s = s.split(sep, ignoreCase = true, limit = 2)[0]
        }
        return s.trim()
    }
}

// ------------------------------------------------------------------ TTML (Apple Music)

object TtmlParser {

    private fun parseTime(time: String): Long {
        val t = time.trim()
        val parts = t.split(":")
        val secs = when (parts.size) {
            3 -> parts[0].toDouble() * 3600 + parts[1].toDouble() * 60 + parts[2].toDouble()
            2 -> parts[0].toDouble() * 60 + parts[1].toDouble()
            else -> when {
                t.endsWith("ms") -> t.dropLast(2).toDouble() / 1000.0
                t.endsWith("s") -> t.dropLast(1).toDouble()
                t.endsWith("m") -> t.dropLast(1).toDouble() * 60
                else -> t.toDoubleOrNull() ?: 0.0
            }
        }
        return (secs * 1000).toLong()
    }

    private fun attr(el: Element, name: String): String {
        el.getAttribute(name).takeIf { it.isNotEmpty() }?.let { return it }
        el.getAttribute("ttm:$name").takeIf { it.isNotEmpty() }?.let { return it }
        el.getAttributeNS("http://www.w3.org/ns/ttml#metadata", name).takeIf { it.isNotEmpty() }?.let { return it }
        return el.getAttributeNS("http://www.w3.org/ns/ttml#parameter", name) ?: ""
    }

    private fun localName(n: Node): String = n.localName ?: n.nodeName.substringAfterLast(':')

    fun parse(ttml: String, source: String): Lyrics? {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        } catch (_: Exception) {
        }
        val doc = try {
            factory.newDocumentBuilder().parse(ttml.byteInputStream())
        } catch (_: Exception) {
            return null
        }
        val ps = doc.getElementsByTagNameNS("*", "p")
        val lines = mutableListOf<LyricLine>()
        var anyWords = false
        for (i in 0 until ps.length) {
            val p = ps.item(i) as? Element ?: continue
            val words = mutableListOf<LyricWord>()
            var child = p.firstChild
            while (child != null) {
                if (child is Element && localName(child) == "span") {
                    val role = attr(child, "role")
                    if (role != "x-translation" && role != "x-roman" && role != "x-bg") {
                        val b = attr(child, "begin")
                        val e = attr(child, "end")
                        val txt = child.textContent ?: ""
                        if (b.isNotEmpty() && e.isNotEmpty() && txt.isNotEmpty()) {
                            val next = child.nextSibling
                            val space = txt.last().isWhitespace() ||
                                (next != null && next.nodeType == Node.TEXT_NODE && next.textContent?.firstOrNull()?.isWhitespace() == true)
                            words.add(LyricWord(if (space) txt.trimEnd() + " " else txt, parseTime(b), parseTime(e)))
                        }
                    }
                }
                child = child.nextSibling
            }
            val pBegin = attr(p, "begin")
            val pEnd = attr(p, "end")
            val text = if (words.isNotEmpty()) words.joinToString("") { it.text }.trim() else (p.textContent ?: "").trim()
            if (text.isEmpty()) continue
            val start = if (pBegin.isNotEmpty()) parseTime(pBegin) else words.firstOrNull()?.start ?: continue
            val end = if (pEnd.isNotEmpty()) parseTime(pEnd) else words.lastOrNull()?.end ?: (start + 5000)
            if (words.isNotEmpty()) anyWords = true
            lines.add(LyricLine(start, end, text, words.ifEmpty { LyricsUtils.estimateWords(text, start, end) }))
        }
        if (lines.isEmpty()) return null
        lines.sortBy { it.start }
        return Lyrics(source, lines, synced = true, wordSynced = anyWords)
    }
}

// ------------------------------------------------------------------ Penyedia

interface LyricsProvider {
    val name: String
    suspend fun fetch(q: SongQuery): Lyrics?
}

private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String? {
    val req = Request.Builder().url(url)
        .header("User-Agent", "LumiTube/1.1 (https://github.com/vickyvam72/lumitube)")
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .build()
    return InnerTube.http.newCall(req).execute().use { r ->
        if (r.isSuccessful) r.body?.string() else null
    }
}

private fun url(base: String, vararg params: Pair<String, Any?>): String {
    val b = base.toHttpUrl().newBuilder()
    for ((k, v) in params) if (v != null && v.toString().isNotEmpty()) b.addQueryParameter(k, v.toString())
    return b.build().toString()
}

/** Kenali format isi lirik (TTML, LRC per kata, LRC biasa, teks) — diadaptasi dari BitChord. */
fun parseAnyLyrics(raw: String, source: String): Lyrics? {
    var content = raw.trim()
    if (content.startsWith("{")) {
        val j = try {
            JSONObject(content)
        } catch (_: Exception) {
            null
        }
        content = j?.str("ttml") ?: j?.str("lyrics") ?: j?.str("syncedLyrics") ?: j?.str("plainLyrics") ?: return null
    }
    val lyrics = when {
        content.contains("<tt", ignoreCase = true) || content.contains("w3.org/ns/ttml") -> TtmlParser.parse(content, source)
        Regex("\\[\\d{1,3}:\\d{2}").containsMatchIn(content) -> LyricsUtils.parseLrc(content, source)
        content.trimStart().startsWith("<") -> null
        else -> LyricsUtils.plain(content, source)
    }
    return lyrics?.takeIf { l -> l.lines.any { it.text.isNotBlank() } }
}

/** Normalisasi judul/artis untuk pencocokan. */
private fun norm(v: String): String =
    java.text.Normalizer.normalize(v, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ")
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()

/** Apple Music TTML lewat BetterLyrics (sinkron per kata). */
object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"
    override suspend fun fetch(q: SongQuery): Lyrics? {
        val body = httpGet(
            url(
                "https://lyrics-api.boidu.dev/getLyrics",
                "s" to q.title, "a" to q.artist, "d" to q.durationSec.takeIf { it > 0 }, "al" to q.album,
            ),
            mapOf("Accept" to "application/json"),
        ) ?: return null
        return parseAnyLyrics(body, name)
    }
}

/** Waktu karaoke QQ Music lewat BetterLyrics ("Portato"). */
object BetterLyricsQQProvider : LyricsProvider {
    override val name = "BetterLyrics QQ"
    override suspend fun fetch(q: SongQuery): Lyrics? {
        val body = httpGet(
            url(
                "https://lyrics-api.boidu.dev/qq/getLyrics",
                "s" to q.title, "a" to q.artist, "d" to q.durationSec.takeIf { it > 0 }, "al" to q.album,
            ),
            mapOf("Accept" to "application/json"),
        ) ?: return null
        return parseAnyLyrics(body, name)
    }
}

/** lrc.red: TTML Apple Music per suku kata, dicari lewat ISRC. */
object LrcRedProvider : LyricsProvider {
    override val name = "lrc.red"
    private val isrcRegex = Regex("[A-Z]{2}[A-Z0-9]{3}\\d{7}")

    override suspend fun fetch(q: SongQuery): Lyrics? {
        val body = httpGet(url("https://lrc.red/search.json", "q" to "${q.title} ${q.artist}".trim())) ?: return null
        val hits = JSONObject(body).arr("hits")?.objects() ?: return null
        val wantTitle = norm(q.title)
        val wantArtist = norm(q.artist)
        val hit = hits.filter { h ->
            val isrc = h.str("isrc")?.uppercase() ?: return@filter false
            val t = norm(h.str("title") ?: return@filter false)
            val a = norm(h.str("artist") ?: "")
            val dur = h.optDouble("duration", 0.0)
            isrcRegex.matches(isrc) &&
                (t == wantTitle || t.startsWith(wantTitle) || wantTitle.startsWith(t)) &&
                (wantArtist.isEmpty() || a.contains(wantArtist) || wantArtist.contains(a.substringBefore(" "))) &&
                (q.durationSec <= 0 || dur <= 0 || abs(dur - q.durationSec) <= 4)
        }.minByOrNull { if (q.durationSec > 0) abs(it.optDouble("duration", 0.0) - q.durationSec) else 0.0 }
            ?: return null
        val ttml = httpGet("https://lrc.red/s/${hit.str("isrc")!!.uppercase()}.ttml") ?: return null
        return TtmlParser.parse(ttml, name)
    }
}

/** BiniLyrics (binimum): TTML Apple Music dicocokkan dengan rekaman. */
object BiniLyricsProvider : LyricsProvider {
    override val name = "BiniLyrics"
    override suspend fun fetch(q: SongQuery): Lyrics? {
        val body = httpGet(
            url(
                "https://lyrics-api.binimum.org/",
                "track" to q.title, "artist" to q.artist, "album" to q.album,
                "duration" to q.durationSec.takeIf { it > 0 },
            )
        ) ?: return null
        val hit = JSONObject(body).arr("results")?.objects()?.firstOrNull() ?: return null
        val doc = hit.str("lyricsUrl") ?: return null
        val ttml = httpGet(doc) ?: return null
        return parseAnyLyrics(ttml, name)
    }
}

/** SimpMusic: dicocokkan langsung lewat videoId, sering punya lirik per kata. */
object SimpMusicProvider : LyricsProvider {
    override val name = "SimpMusic"
    override suspend fun fetch(q: SongQuery): Lyrics? {
        val body = httpGet("https://api-lyrics.simpmusic.org/v1/${q.videoId}") ?: return null
        val json = JSONObject(body)
        if (!json.optBoolean("success", false)) return null
        val track = json.arr("data")?.objects()
            ?.filter { q.durationSec <= 0 || abs(it.optInt("duration", 0) - q.durationSec) <= 10 }
            ?.minByOrNull { abs(it.optInt("duration", 0) - q.durationSec) }
            ?: return null
        track.str("richSyncLyrics")?.let { r -> LyricsUtils.parseLrc(r, name).takeIf { it.lines.isNotEmpty() }?.let { return it } }
        track.str("syncedLyrics")?.let { r -> LyricsUtils.parseLrc(r, name).takeIf { it.lines.isNotEmpty() }?.let { return it } }
        return null
    }
}

/** Unison (boidu): lirik komunitas, TTML / LRC. */
object UnisonProvider : LyricsProvider {
    override val name = "Unison"
    override suspend fun fetch(q: SongQuery): Lyrics? {
        val body = httpGet(
            url(
                "https://unison.boidu.dev/lyrics",
                "song" to q.title, "artist" to q.artist, "album" to q.album,
                "duration" to q.durationSec.takeIf { it > 0 },
            )
        ) ?: return null
        val json = JSONObject(body)
        if (!json.optBoolean("success", false)) return null
        val data = json.obj("data") ?: return null
        val text = data.str("lyrics") ?: return null
        return if (data.str("syncType").equals("plain", true)) LyricsUtils.plain(text, name) else parseAnyLyrics(text, name)
    }
}

/** LyricsPlus (beberapa server cermin), sering punya waktu per suku kata. */
object LyricsPlusProvider : LyricsProvider {
    override val name = "LyricsPlus"
    private val servers = listOf(
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus.atomix.one",
        "https://lyricsplus-seven.vercel.app",
    )

    override suspend fun fetch(q: SongQuery): Lyrics? {
        for (server in servers) {
            val body = try {
                httpGet(
                    url(
                        "$server/v2/lyrics/get",
                        "title" to q.title, "artist" to q.artist,
                        "duration" to q.durationSec.takeIf { it > 0 }, "album" to q.album,
                    )
                )
            } catch (_: Exception) {
                null
            } ?: continue
            val json = try {
                JSONObject(body)
            } catch (_: Exception) {
                continue
            }
            val arr = json.arr("lyrics")?.objects() ?: continue
            if (arr.isEmpty()) continue
            var anyWords = false
            val lines = arr.mapNotNull { l ->
                val start = l.optLong("time")
                val end = start + l.optLong("duration")
                val syl = l.arr("syllabus")?.objects()
                    ?.filter { !it.optBoolean("isBackground", false) }
                    ?.map { w -> LyricWord(w.optString("text"), w.optLong("time"), w.optLong("time") + w.optLong("duration")) }
                    ?: emptyList()
                val text = l.optString("text").ifBlank { syl.joinToString("") { it.text } }.trim()
                if (text.isEmpty()) return@mapNotNull null
                if (syl.isNotEmpty()) anyWords = true
                val words = if (syl.isNotEmpty()) LyricsUtils.alignWords(text, syl) else LyricsUtils.estimateWords(text, start, end)
                LyricLine(start, end, text, words)
            }
            if (lines.isEmpty()) continue
            val synced = json.str("type") != "None" && lines.any { it.start > 0 }
            return Lyrics(name, lines, synced = synced, wordSynced = anyWords && synced)
        }
        return null
    }
}

/** LRCLIB: database lirik terbuka, sinkron per baris. */
object LrcLibProvider : LyricsProvider {
    override val name = "LRCLIB"
    override suspend fun fetch(q: SongQuery): Lyrics? {
        // 1. Pencocokan tepat
        try {
            httpGet(
                url(
                    "https://lrclib.net/api/get",
                    "track_name" to q.title, "artist_name" to q.artist,
                    "album_name" to q.album, "duration" to q.durationSec.takeIf { it > 0 },
                )
            )?.let { body ->
                val j = JSONObject(body)
                j.str("syncedLyrics")?.let { return LyricsUtils.parseLrc(it, name) }
                j.str("plainLyrics")?.let { return LyricsUtils.plain(it, name) }
            }
        } catch (_: Exception) {
        }
        // 2. Pencarian, pilih durasi terdekat
        val body = httpGet(url("https://lrclib.net/api/search", "track_name" to q.title, "artist_name" to q.artist))
            ?: httpGet(url("https://lrclib.net/api/search", "q" to "${q.title} ${q.artist}"))
            ?: return null
        val list = JSONArray(body).objects()
        val best = list
            .filter { q.durationSec <= 0 || abs(it.optDouble("duration", 0.0) - q.durationSec) < 6 }
            .sortedByDescending { if (it.str("syncedLyrics") != null) 1 else 0 }
            .firstOrNull() ?: return null
        best.str("syncedLyrics")?.let { return LyricsUtils.parseLrc(it, name) }
        best.str("plainLyrics")?.let { return LyricsUtils.plain(it, name) }
        return null
    }
}

/** KuGou: koleksi lirik sinkron yang sangat besar (terutama lagu Asia). */
object KuGouProvider : LyricsProvider {
    override val name = "KuGou"
    override suspend fun fetch(q: SongQuery): Lyrics? {
        val search = httpGet(
            url(
                "https://lyrics.kugou.com/search",
                "ver" to 1, "man" to "yes", "client" to "pc",
                "keyword" to "${q.title} - ${q.artist}",
                "duration" to q.durationSec.takeIf { it > 0 }?.times(1000),
            )
        ) ?: return null
        val cand = JSONObject(search).arr("candidates")?.objects()?.firstOrNull() ?: return null
        val id = cand.opt("id")?.toString() ?: return null
        val key = cand.str("accesskey") ?: return null
        val dl = httpGet(
            url(
                "https://lyrics.kugou.com/download",
                "fmt" to "lrc", "charset" to "utf8", "client" to "pc", "ver" to 1, "id" to id, "accesskey" to key,
            )
        ) ?: return null
        val content = JSONObject(dl).str("content") ?: return null
        val lrc = String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
        val lyrics = LyricsUtils.parseLrc(lrc, name)
        // Buang baris kredit KuGou (mis. "作词 : ...")
        val cleaned = lyrics.lines.filterNot { it.text.contains("：") || it.text.contains(" : ") && it.start < 15000 }
        return if (cleaned.isEmpty()) null else lyrics.copy(lines = cleaned)
    }
}

/** Lirik bawaan YouTube Music (tidak sinkron). */
object YouTubeMusicProvider : LyricsProvider {
    override val name = "YouTube Music"
    override suspend fun fetch(q: SongQuery): Lyrics? {
        val queue = Repo.musicQueue(q.videoId)
        val id = queue.lyricsBrowseId ?: return null
        val text = Repo.lyrics(id) ?: return null
        return LyricsUtils.plain(text, name)
    }
}

// ------------------------------------------------------------------ Repo

object LyricsRepo {

    /** Urutan prioritas (diadaptasi dari BitChord & ArchiveTune): sumber Apple per kata dulu. */
    val providers: List<LyricsProvider> = listOf(
        LrcRedProvider, BiniLyricsProvider, BetterLyricsProvider, SimpMusicProvider, LyricsPlusProvider,
        BetterLyricsQQProvider, UnisonProvider, LrcLibProvider, KuGouProvider, YouTubeMusicProvider,
    )

    /**
     * Ambil lirik dari semua penyedia yang aktif secara paralel.
     * Hasil diurutkan: sinkron per kata > sinkron per baris > teks biasa, lalu sesuai prioritas penyedia.
     */
    suspend fun fetchAll(q: SongQuery, onUpdate: (List<Lyrics>) -> Unit = {}): List<Lyrics> = withContext(Dispatchers.IO) {
        val enabled = providers.filter { Session.isLyricsProviderEnabled(it.name) }
        val clean = q.copy(title = LyricsUtils.cleanTitle(q.title), artist = LyricsUtils.cleanArtist(q.artist))
        val found = java.util.Collections.synchronizedList(mutableListOf<Pair<Int, Lyrics>>())
        fun sorted(): List<Lyrics> = synchronized(found) {
            found.sortedWith(compareByDescending<Pair<Int, Lyrics>> { it.second.quality }.thenBy { it.first })
                .map { it.second }
        }
        coroutineScope {
            enabled.mapIndexed { idx, p ->
                async {
                    val r = try {
                        withTimeoutOrNull(10_000) { p.fetch(clean) }
                    } catch (_: Exception) {
                        null
                    }
                    if (r != null && r.lines.isNotEmpty()) {
                        found.add(idx to r)
                        // Tampilkan segera bila hasil sinkron (atau satu-satunya hasil).
                        if (r.synced || found.size == 1) onUpdate(sorted())
                    }
                }
            }.awaitAll()
        }
        sorted()
    }

    /** Indeks baris aktif untuk posisi [ms]. */
    fun activeLine(lines: List<LyricLine>, ms: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].start <= ms) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}
