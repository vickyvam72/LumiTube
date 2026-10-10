package com.lumitube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val bitrate: Int,
    val contentLength: Long,
    val isAudio: Boolean,
) {
    val isMp4: Boolean get() = mimeType.contains("mp4")
    val codec: String get() = Regex("codecs=\"([^.\"]+)").find(mimeType)?.groupValues?.get(1) ?: ""
}

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
) {
    /** Daftar resolusi video yang tersedia (tanpa AV1), urut dari tertinggi. */
    val qualities: List<Int>
        get() = formats.filter { !it.isAudio && it.codec != "av01" }.map { it.height }.filter { it > 0 }.distinct().sortedDescending()
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
        cache.keys.filter { it.startsWith("$videoId:") }.forEach { cache.remove(it) }
    }

    suspend fun resolve(videoId: String, audioOnly: Boolean): Stream = withContext(Dispatchers.IO) {
        val key = "$videoId:$audioOnly:${Session.maxQuality}"
        cache[key]?.let { (time, s) ->
            if (System.currentTimeMillis() - time < 3 * 60 * 60 * 1000L) return@withContext s
        }
        var lastError: Exception? = null
        for (c in clients) {
            try {
                val s = request(c, videoId, audioOnly)
                cache[key] = System.currentTimeMillis() to s
                return@withContext s
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Video tidak bisa diputar")
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

    private fun request(c: PlayerClient, videoId: String, audioOnly: Boolean): Stream {
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

        fun withCpn(u: String?) = u?.let { if (it.contains("cpn=")) it else "$it&cpn=$cpn" }

        val audio = adaptive
            .filter { it.str("mimeType")?.startsWith("audio/") == true }
            .maxByOrNull { it.optInt("bitrate", 0) }
            ?.str("url")

        val maxH = Session.maxQuality
        // Hindari AV1 (belum semua HP bisa memutarnya); utamakan H.264 lalu VP9.
        val videos = adaptive.filter {
            val mime = it.str("mimeType") ?: ""
            mime.startsWith("video/") && !mime.contains("av01")
        }
        val video = videos
            .filter { it.optInt("height", 0) <= maxH }
            .sortedWith(
                compareByDescending<JSONObject> { it.optInt("height", 0) }
                    .thenByDescending { if (it.str("mimeType")?.contains("avc1") == true) 1 else 0 }
                    .thenByDescending { it.optInt("bitrate", 0) }
            )
            .firstOrNull()?.str("url")
            ?: videos.minByOrNull { it.optInt("height", 0) }?.str("url")

        val ok = when {
            isLive -> hls != null
            audioOnly -> audio != null || hls != null
            else -> (video != null && audio != null) || hls != null
        }
        if (!ok) throw IOException("Format stream tidak tersedia")

        val formats = adaptive.mapNotNull { f ->
            val u = withCpn(f.str("url")) ?: return@mapNotNull null
            val mime = f.str("mimeType") ?: return@mapNotNull null
            Format(
                itag = f.optInt("itag"),
                url = u,
                mimeType = mime,
                height = f.optInt("height", 0),
                bitrate = f.optInt("bitrate", 0),
                contentLength = f.str("contentLength")?.toLongOrNull() ?: 0L,
                isAudio = mime.startsWith("audio/"),
            )
        }
        return Stream(
            formats = formats,
            lengthSeconds = json.str("videoDetails", "lengthSeconds")?.toLongOrNull() ?: 0L,
            videoUrl = withCpn(video),
            audioUrl = withCpn(audio),
            hlsUrl = hls,
            userAgent = c.userAgent,
            isLive = isLive,
            title = json.str("videoDetails", "title"),
            author = json.str("videoDetails", "author"),
        )
    }
}
