package com.lumitube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** Satu format stream (untuk pilihan kualitas & download). */
data class Format(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val height: Int,
    val width: Int = 0,
    val bitrate: Int,
    val contentLength: Long,
    val isAudio: Boolean,
) {
    val isMp4: Boolean get() = mimeType.contains("mp4")
    /** Kualitas = sisi terpendek (video vertikal 720x1280 tetap dianggap 720p). */
    val quality: Int get() = if (width > 0) minOf(width, height) else height
    val codec: String get() = Regex("codecs=\"([^.\"]+)").find(mimeType)?.groupValues?.get(1) ?: ""
}

data class Caption(val name: String, val lang: String, val url: String, val auto: Boolean)

data class Stream(
    val videoUrl: String?,
    val audioUrl: String?,
    val hlsUrl: String?,
    val userAgent: String,
    val isLive: Boolean,
    val title: String?,
    val author: String?,
    val formats: List<Format> = emptyList(),
    val lengthSeconds: Long = 0,
    val captions: List<Caption> = emptyList(),
    /** Bahasa terjemahan otomatis yang didukung YouTube (kode, nama). */
    val translations: List<Pair<String, String>> = emptyList(),
) {
    /** Daftar resolusi video yang tersedia (tanpa AV1), urut dari tertinggi. */
    val qualities: List<Int>
        get() = formats.filter { !it.isAudio && it.codec != "av01" }.map { it.quality }.filter { it > 0 }.distinct().sortedDescending()
}

/**
 * Mengambil URL stream lewat endpoint /player.
 *
 * Browsing memakai klien WEB (dengan login), sedangkan stream diambil lewat klien
 * yang memberi URL langsung tanpa PO token / dekripsi signature.
 * Klien utama VISIONOS mengikuti cara NewPipe (Agustus 2026); ANDROID_VR dan IOS sebagai cadangan.
 * Jika YouTube mengubah aturannya, cukup perbarui daftar [clients] di bawah.
 */
object StreamResolver {

    private const val GAPIS = "https://youtubei.googleapis.com/youtubei/v1/"
    private const val WWW = "https://www.youtube.com/youtubei/v1/"
    private const val NONCE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private class PlayerClient(
        val name: String,
        val id: Int,
        val version: String,
        val userAgent: String,
        val base: String,
        /** Klien ini butuh visitorData miliknya sendiri (diambil dari endpoint visitor_id). */
        val ownVisitor: Boolean,
        val headers: Map<String, String>,
        val extra: (JSONObject) -> Unit,
    )

    private val visionUa: String
        get() = "com.google.visionos.youtube/1.04(RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; ${InnerTube.gl.uppercase()})"

    private val clients: List<PlayerClient>
        get() = listOf(
            PlayerClient(
                "VISIONOS", 101, "1.04", visionUa, GAPIS, true,
                mapOf("X-Goog-Api-Format-Version" to "2"),
            ) {
                it.put("clientScreen", "WATCH").put("platform", "MOBILE")
                    .put("deviceMake", "Apple").put("deviceModel", "RealityDevice17,1")
                    .put("osName", "visionOS").put("osVersion", "26.6.0.23O770")
            },
            // Klien dari Morphe (ClientType.ANDROID_VR_DASH / ANDROID_XR_DASH)
            PlayerClient(
                "ANDROID_VR", 28, "1.64.34",
                "com.google.android.apps.youtube.vr.pico/1.64.34 (Linux; U; Android 10; en_US; A8110 Build/5.13.7)",
                WWW, false, emptyMap(),
            ) {
                it.put("deviceMake", "Pico").put("deviceModel", "A8110")
                    .put("androidSdkVersion", 29).put("osName", "Android").put("osVersion", "10")
            },
            PlayerClient(
                "ANDROID_VR", 28, "1.69.27",
                "com.google.android.apps.youtube.xr/1.69.27 (Linux; U; Android 14; en_US; SM-I610 Build/UML1.250710.002.A1)",
                WWW, false, emptyMap(),
            ) {
                it.put("deviceMake", "Samsung").put("deviceModel", "SM-I610")
                    .put("androidSdkVersion", 34).put("osName", "Android").put("osVersion", "14")
            },
            PlayerClient(
                "ANDROID_VR", 28, "1.65.10",
                "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                WWW, false, emptyMap(),
            ) {
                it.put("deviceMake", "Oculus").put("deviceModel", "Quest 3")
                    .put("androidSdkVersion", 32).put("osName", "Android").put("osVersion", "12L")
            },
            PlayerClient(
                "IOS", 5, "20.10.4",
                "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                WWW, false, emptyMap(),
            ) {
                it.put("deviceMake", "Apple").put("deviceModel", "iPhone16,2")
                    .put("osName", "iPhone").put("osVersion", "18.3.2.22D82")
            },
        )

    private val cache = ConcurrentHashMap<String, Pair<Long, Stream>>()
    private val visitors = ConcurrentHashMap<String, String>()

    private fun nonce(len: Int) = buildString { repeat(len) { append(NONCE_ALPHABET[Random.nextInt(NONCE_ALPHABET.length)]) } }

    /** Ambil ulang tanpa cache (mis. untuk download). */
    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    /**
     * Data stream mentah di-cache per video (berlaku untuk mode video maupun audio),
     * jadi berpindah Video <-> Music tidak perlu meminta ulang ke YouTube.
     */
    suspend fun resolve(videoId: String, audioOnly: Boolean): Stream = withContext(Dispatchers.IO) {
        select(raw(videoId), audioOnly)
    }

    private val inFlight = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Stream>>()
    private val prefetchScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    /** Ambil data stream lebih awal (Shorts berikutnya, lagu berikutnya). */
    fun prefetch(videoId: String) {
        val cached = cache[videoId]
        if (cached != null && System.currentTimeMillis() - cached.first < 3 * 60 * 60 * 1000L) return
        prefetchScope.launch {
            try {
                raw(videoId)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun raw(videoId: String): Stream {
        cache[videoId]?.let { (time, s) ->
            if (System.currentTimeMillis() - time < 3 * 60 * 60 * 1000L) return s
        }
        // Gabungkan permintaan yang sama yang sedang berjalan (mis. dari prefetch).
        val job = inFlight.getOrPut(videoId) {
            prefetchScope.async {
                var lastError: Exception? = null
                for (c in clients) {
                    try {
                        val st = request(c, videoId)
                        cache[videoId] = System.currentTimeMillis() to st
                        return@async st
                    } catch (e: Exception) {
                        lastError = e
                    }
                }
                throw lastError ?: IOException("Video tidak bisa diputar")
            }
        }
        try {
            return job.await()
        } finally {
            inFlight.remove(videoId, job)
        }
    }

    /** Pilih URL video & audio sesuai mode dan kualitas maksimum. */
    private fun select(raw: Stream, audioOnly: Boolean): Stream {
        val audio = raw.formats.filter { it.isAudio }.maxByOrNull { it.bitrate }?.url
        val maxH = Session.maxQuality
        // Hindari AV1 (belum semua HP bisa memutarnya); utamakan H.264 lalu VP9.
        val videos = raw.formats.filter { !it.isAudio && it.codec != "av01" }
        val video = videos
            .filter { it.quality <= maxH }
            .sortedWith(
                compareByDescending<Format> { it.quality }
                    .thenByDescending { if (it.codec == "avc1") 1 else 0 }
                    .thenByDescending { it.bitrate }
            )
            .firstOrNull()?.url
            ?: videos.minByOrNull { it.quality }?.url
        val ok = when {
            raw.isLive -> raw.hlsUrl != null
            audioOnly -> audio != null || raw.hlsUrl != null
            else -> (video != null && audio != null) || raw.hlsUrl != null
        }
        if (!ok) throw IOException("Format stream tidak tersedia")
        return raw.copy(videoUrl = if (audioOnly) null else video, audioUrl = audio)
    }

    private fun context(c: PlayerClient, visitor: String?): JSONObject {
        val client = JSONObject()
            .put("clientName", c.name)
            .put("clientVersion", c.version)
        c.extra(client)
        if (visitor != null) client.put("visitorData", visitor)
        client.put("hl", InnerTube.hl).put("gl", InnerTube.gl).put("utcOffsetMinutes", 0)
        return JSONObject()
            .put("client", client)
            .put("request", JSONObject().put("internalExperimentFlags", JSONArray()).put("useSsl", true))
            .put("user", JSONObject().put("lockedSafetyMode", false))
    }

    private fun call(c: PlayerClient, url: String, body: JSONObject): JSONObject {
        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", c.userAgent)
            .apply {
                if (c.base != GAPIS) {
                    header("X-YouTube-Client-Name", c.id.toString())
                    header("X-YouTube-Client-Version", c.version)
                }
            }
            .header("Accept-Language", "${InnerTube.hl}-${InnerTube.gl}, ${InnerTube.hl};q=0.9")
            .apply { c.headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return InnerTube.http.newCall(req).execute().use { resp ->
            val s = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            JSONObject(s)
        }
    }

    private fun visitorFor(c: PlayerClient): String? {
        if (!c.ownVisitor) return Session.visitorData
        visitors[c.name]?.let { return it }
        val r = call(c, "${WWW}visitor_id?prettyPrint=false", JSONObject().put("context", context(c, null)))
        val v = r.str("responseContext", "visitorData") ?: throw IOException("visitorData kosong")
        visitors[c.name] = v
        return v
    }

    private fun request(c: PlayerClient, videoId: String): Stream {
        val cpn = nonce(16)
        val body = JSONObject()
            .put("context", context(c, visitorFor(c)))
            .put("videoId", videoId)
            .put("cpn", cpn)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        val url = if (c.base == GAPIS) {
            "${c.base}player?prettyPrint=false&t=${nonce(12)}&id=$videoId"
        } else {
            "${c.base}player?prettyPrint=false"
        }
        val json = call(c, url, body)
        val status = json.str("playabilityStatus", "status")
        if (status != "OK") {
            val reason = json.str("playabilityStatus", "reason") ?: status ?: "Tidak bisa diputar"
            throw IOException(reason)
        }
        val sd = json.obj("streamingData") ?: throw IOException("Tidak ada data stream")
        val isLive = json.obj("videoDetails")?.optBoolean("isLive", false) == true
        val hls = sd.str("hlsManifestUrl")
        val adaptive = sd.arr("adaptiveFormats")?.objects()?.filter { it.str("url") != null } ?: emptyList()
        if (adaptive.none { it.str("mimeType")?.startsWith("audio/") == true } && hls == null) {
            throw IOException("Format stream tidak tersedia")
        }

        fun withCpn(u: String?) = u?.let { if (it.contains("cpn=")) it else "$it&cpn=$cpn" }

        val formats = adaptive.mapNotNull { f ->
            val u = withCpn(f.str("url")) ?: return@mapNotNull null
            val mime = f.str("mimeType") ?: return@mapNotNull null
            Format(
                itag = f.optInt("itag"),
                url = u,
                mimeType = mime,
                height = f.optInt("height", 0),
                width = f.optInt("width", 0),
                bitrate = f.optInt("bitrate", 0),
                contentLength = f.str("contentLength")?.toLongOrNull() ?: 0L,
                isAudio = mime.startsWith("audio/"),
            )
        }
        return Stream(
            formats = formats,
            lengthSeconds = json.str("videoDetails", "lengthSeconds")?.toLongOrNull() ?: 0L,
            videoUrl = null,
            audioUrl = null,
            hlsUrl = hls,
            captions = json.arr("captions", "playerCaptionsTracklistRenderer", "captionTracks")?.objects()
                ?.mapNotNull { t ->
                    val base = t.str("baseUrl") ?: return@mapNotNull null
                    Caption(
                        name = text(t.obj("name")) ?: t.str("languageCode") ?: "?",
                        lang = t.str("languageCode") ?: "und",
                        url = (if (base.startsWith("/")) "https://www.youtube.com$base" else base) + "&fmt=vtt",
                        auto = t.str("kind") == "asr",
                    )
                } ?: emptyList(),
            translations = json.arr("captions", "playerCaptionsTracklistRenderer", "translationLanguages")?.objects()
                ?.mapNotNull { t ->
                    val code = t.str("languageCode") ?: return@mapNotNull null
                    code to (text(t.obj("languageName")) ?: code)
                } ?: emptyList(),
            userAgent = c.userAgent,
            isLive = isLive,
            title = json.str("videoDetails", "title"),
            author = json.str("videoDetails", "author"),
        )
    }
}
