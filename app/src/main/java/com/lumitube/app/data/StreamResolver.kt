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
    /** Manifest DASH (dipakai untuk live bila tidak ada HLS, mis. live untuk anak). */
    val dashUrl: String? = null,
    /** Pengganti parameter "n" (klien web) untuk semua URL HLS live; tanpa ini YouTube menolak (403). */
    val nMap: Map<String, String> = emptyMap(),
    /** Catatan singkat untuk laporan (mis. "n✓"). */
    val note: String = "",
    val lengthSeconds: Long = 0,
    val captions: List<Caption> = emptyList(),
    /** Bahasa terjemahan otomatis yang didukung YouTube (kode, nama). */
    val translations: List<Pair<String, String>> = emptyList(),
    /** Nama klien /player yang menghasilkan URL ini. */
    val client: String = "",
    /** Header wajib saat mengambil media (Referer/Origin untuk klien web). */
    val headers: Map<String, String> = emptyMap(),
    /** Ukuran potongan Range tetap (0 = adaptif). Klien Android VR maksimal 512 KB. */
    val chunk: Long = 0,
    /** Spesifikasi storyboard (gambar pratinjau timeline). */
    val storyboardSpec: String? = null,
    /** URL pelacakan tontonan dari respons player (untuk Histori akun). */
    val trackPlayback: String? = null,
    val trackWatchtime: String? = null,
    val trackClient: String = "",
    val trackVersion: String = "",
) {
    /** Daftar resolusi video yang tersedia (tanpa AV1), urut dari tertinggi. */
    val qualities: List<Int>
        get() = formats.filter { !it.isAudio && it.codec != "av01" }.map { it.quality }.filter { it > 0 }.distinct().sortedDescending()
}

/**
 * Mengambil URL stream lewat endpoint /player.
 *
 * Urutan klien mengikuti InnerTubeX/Metrolist (Oktober 2026):
 * 1. WEB_EMBEDDED_PLAYER: URL dengan signatureCipher + parameter n yang dipecahkan lewat
 *    skrip pemutar asli (lihat [Cipher]). Klien ini tidak kena batas 1 MB.
 * 2. VISIONOS lewat endpoint music.youtube.com.
 * 3. VISIONOS & ANDROID_VR lama sebagai cadangan. URL Android VR sekarang sering ditolak
 *    (HTTP 403) setelah 1 MB, jadi setiap URL diuji dulu di atas 1 MB sebelum dipakai.
 */
object StreamResolver {

    private const val GAPIS = "https://youtubei.googleapis.com/youtubei/v1/"
    private const val WWW = "https://www.youtube.com/youtubei/v1/"
    private const val MUSIC = "https://music.youtube.com/youtubei/v1/"
    private const val NONCE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private enum class Kind { NATIVE, MUSIC_VISION, EMBED }

    private class PlayerClient(
        val name: String,
        val id: Int,
        val version: String,
        val userAgent: String,
        val base: String,
        /** Klien ini butuh visitorData miliknya sendiri (diambil dari endpoint visitor_id). */
        val ownVisitor: Boolean,
        val headers: Map<String, String>,
        val kind: Kind = Kind.NATIVE,
        /** User-Agent untuk mengambil media; null = bawaan OkHttp (seperti InnerTubeX). */
        val mediaUa: String? = null,
        val mediaHeaders: Map<String, String> = emptyMap(),
        val chunk: Long = 0,
        /** Pakai cookie akun (lolos "konfirmasi bukan bot"). */
        val auth: Boolean = false,
        /** Hanya pakai HLS (format terpisahnya butuh PO token). */
        val hlsOnly: Boolean = false,
        val extra: (JSONObject) -> Unit,
    )

    private val visionUa: String
        get() = "com.google.visionos.youtube/1.04(RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; ${InnerTube.gl.uppercase()})"

    private fun embedClient(auth: Boolean) = PlayerClient(
        "WEB_EMBEDDED_PLAYER", 56, "2.20260708.00.00", Cipher.WEB_UA, WWW, false,
        mapOf("Origin" to "https://www.youtube.com", "X-Origin" to "https://www.youtube.com", "Referer" to "https://www.reddit.com/"),
        kind = Kind.EMBED,
        mediaUa = Cipher.WEB_UA,
        mediaHeaders = mapOf("Referer" to "https://www.youtube.com/", "Origin" to "https://www.youtube.com", "Accept" to "*/*"),
        auth = auth,
    ) { }

    private const val SAFARI_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)"

    private fun webSafari(auth: Boolean) = PlayerClient(
        "WEB", 1, InnerTube.Client.WEB.version, SAFARI_UA, WWW, false,
        mapOf("Origin" to "https://www.youtube.com", "X-Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/"),
        mediaUa = SAFARI_UA,
        mediaHeaders = mapOf("Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/") +
            (if (auth) Session.cookie?.let { mapOf("Cookie" to it) } ?: emptyMap() else emptyMap()),
        auth = auth,
        hlsOnly = true,
    ) { }

    private val clients: List<PlayerClient>
        get() = listOfNotNull(
            // Sudah login: minta stream sebagai akun sendiri dulu (seperti web YouTube),
            // jadi tidak kena "Login untuk mengonfirmasi bahwa Anda bukan bot".
            if (Session.loggedIn) embedClient(auth = true) else null,
            embedClient(auth = false),
            PlayerClient(
                "VISIONOS", 101, "1.02",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                MUSIC, false,
                mapOf(
                    "X-Goog-Api-Format-Version" to "1",
                    "Origin" to "https://music.youtube.com", "X-Origin" to "https://music.youtube.com",
                    "Referer" to "https://music.youtube.com/",
                ),
                kind = Kind.MUSIC_VISION,
            ) {
                it.put("deviceMake", "Apple").put("deviceModel", "RealityDevice17,1")
                    .put("osName", "visionOS").put("osVersion", "26.5.23O471")
            },
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
                WWW, false, emptyMap(), chunk = 512L * 1024,
            ) {
                it.put("deviceMake", "Oculus").put("deviceModel", "Quest 3")
                    .put("androidSdkVersion", 32).put("osName", "Android").put("osVersion", "12L")
            },
            // Klien iOS: satu-satunya yang bisa memutar video & live "untuk anak" (YouTube Kids)
            // tanpa login; dipakai lewat HLS saja. Dicoba belakangan.
            PlayerClient(
                "IOS", 5, "20.10.4",
                "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                WWW, false, emptyMap(),
                mediaUa = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                hlsOnly = true,
            ) {
                it.put("deviceMake", "Apple").put("deviceModel", "iPhone16,2")
                    .put("osName", "iPhone").put("osVersion", "18.3.2.22D82")
            },
            // Klien web (Safari) memakai akun bila sudah login: video & live "untuk anak" lewat HLS.
            webSafari(auth = Session.loggedIn),
            PlayerClient(
                "ANDROID_VR", 28, "1.64.34",
                "com.google.android.apps.youtube.vr.pico/1.64.34 (Linux; U; Android 10; en_US; A8110 Build/5.13.7)",
                WWW, false, emptyMap(), chunk = 512L * 1024,
            ) {
                it.put("deviceMake", "Pico").put("deviceModel", "A8110")
                    .put("androidSdkVersion", 29).put("osName", "Android").put("osVersion", "10")
            },
        )

    private val cache = ConcurrentHashMap<String, Pair<Long, Stream>>()
    /** Klien yang URL-nya ditolak googlevideo (403) untuk video tertentu. */
    private val rejected = ConcurrentHashMap<String, MutableSet<String>>()
    /** Jumlah penolakan per klien: klien yang sering ditolak dicoba belakangan. */
    private val failures = ConcurrentHashMap<String, Int>()

    private fun key(c: PlayerClient) = "${c.name}/${c.version}" + if (c.auth) "/auth" else ""

    /**
     * URL dari klien ini ditolak di tengah pemutaran (biasanya HTTP 403 karena
     * butuh PO token). Ambil ulang dengan klien lain.
     * @return false bila semua klien sudah dicoba untuk video ini.
     */
    fun markRejected(videoId: String, client: String): Boolean {
        cache.remove(videoId)
        if (client.isEmpty()) return true
        failures[client] = (failures[client] ?: 0) + 1
        val set = rejected.getOrPut(videoId) { java.util.Collections.synchronizedSet(mutableSetOf()) }
        set.add(client)
        return set.size < clients.size
    }

    /** Buang visitorData yang mungkin sudah ditandai bot oleh YouTube. */
    fun resetIdentity() {
        visitors.clear()
        cache.clear()
    }

    /** Lupakan penolakan untuk video ini (mis. saat diputar ulang dari awal). */
    fun resetRejected(videoId: String) {
        rejected.remove(videoId)
    }
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
    /** [short] = Shorts: maksimal 720p supaya geser cepat & tidak buffering. */
    suspend fun resolve(videoId: String, audioOnly: Boolean, short: Boolean = false): Stream = withContext(Dispatchers.IO) {
        select(raw(videoId), audioOnly, if (short) minOf(Session.maxQuality, 720) else Session.maxQuality)
    }

    /** Alasan gagal per klien untuk video terakhir yang tidak bisa diputar (untuk laporan). */
    @Volatile var lastFailure: String = ""

    /** Klien yang URL-nya didapat tapi gagal diputar, per video (untuk laporan). */
    private val playNotes = ConcurrentHashMap<String, MutableList<String>>()

    fun notePlaybackError(videoId: String, client: String, reason: String) {
        playNotes.getOrPut(videoId) { java.util.Collections.synchronizedList(mutableListOf()) }.add("$client: putar gagal ($reason)")
    }

    private val inFlight = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Stream>>()
    private val prefetchScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO + com.lumitube.app.CrashLog.handler)

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

    /**
     * Data stream untuk download: harus punya format terpisah (video/audio), jadi klien yang
     * hanya memberi HLS dilewati.
     */
    suspend fun resolveForDownload(videoId: String): Stream = withContext(Dispatchers.IO) {
        raw(videoId, needFormats = true)
    }

    private suspend fun raw(videoId: String, needFormats: Boolean = false): Stream {
        cache[videoId]?.let { (time, s) ->
            if (System.currentTimeMillis() - time < 3 * 60 * 60 * 1000L && (!needFormats || s.formats.isNotEmpty() || s.isLive)) return s
        }
        // Gabungkan permintaan yang sama yang sedang berjalan (mis. dari prefetch).
        val job = inFlight.getOrPut(videoId + if (needFormats) "|f" else "") {
            prefetchScope.async {
                var lastError: Exception? = null
                val bad = rejected[videoId]?.toSet() ?: emptySet()
                val ordered = clients.filter { !needFormats || !it.hlsOnly }.withIndex()
                    .sortedWith(compareBy<IndexedValue<PlayerClient>> { if (key(it.value) in bad) 1 else 0 }
                        .thenBy { minOf(failures[key(it.value)] ?: 0, 3) }
                        .thenBy { it.index })
                    .map { it.value }
                val list = ordered.filter { c -> !(key(c) in bad && ordered.any { key(it) !in bad }) }
                // Balapan bertahap: klien utama dicoba dulu; bila belum selesai dalam 2,5 detik
                // (atau sudah gagal), klien berikutnya ikut dimulai. Hasil pertama yang berhasil dipakai,
                // jadi satu klien yang lambat tidak membuat video lama dimuat.
                val finished = java.util.concurrent.atomic.AtomicInteger(0)
                val result = kotlinx.coroutines.CompletableDeferred<Stream>()
                val errors = java.util.Collections.synchronizedList(mutableListOf<Exception>())
                val errorsByClient = java.util.Collections.synchronizedList(mutableListOf<String>())
                val jobs = list.mapIndexed { i, c ->
                    launch {
                        var waited = 0L
                        // Baru buka aplikasi (mesin cipher web belum siap): klien yang tidak butuh
                        // cipher langsung ikut balapan, jadi lagu/video pertama tidak menunggu WebView.
                        val wait = if (c.kind != Kind.EMBED && !Cipher.isReady) 0L else i * 2500L
                        while (waited < wait && finished.get() < i && !result.isCompleted) {
                            kotlinx.coroutines.delay(100)
                            waited += 100
                        }
                        if (result.isCompleted) return@launch
                        try {
                            result.complete(request(c, videoId))
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (c.kind == Kind.EMBED) Cipher.dropEmbed()
                            errors.add(e)
                            errorsByClient.add("${c.name}${if (c.auth) "+" else ""}: ${e.message?.take(70)}")
                        } finally {
                            if (finished.incrementAndGet() == list.size && !result.isCompleted) {
                                val err = errors.firstOrNull { it.message?.contains("bot", true) == true }
                                    ?: errors.firstOrNull() ?: lastError ?: IOException("Video tidak bisa diputar")
                                lastFailure = ((playNotes[videoId] ?: emptyList<String>()) + errorsByClient).joinToString(" · ")
                                result.completeExceptionally(err)
                            }
                        }
                    }
                }
                if (list.isEmpty()) throw IOException("Video tidak bisa diputar")
                try {
                    val st = result.await()
                    cache[videoId] = System.currentTimeMillis() to st
                    st
                } finally {
                    jobs.forEach { it.cancel() }
                }
            }
        }
        try {
            return job.await()
        } finally {
            inFlight.remove(videoId + if (needFormats) "|f" else "", job)
        }
    }

    /** Pilih URL video & audio sesuai mode dan kualitas maksimum. */
    private fun select(raw: Stream, audioOnly: Boolean, maxH: Int = Session.maxQuality): Stream {
        val audio = raw.formats.filter { it.isAudio }.maxByOrNull { it.bitrate }?.url
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
            raw.isLive -> raw.hlsUrl != null || raw.dashUrl != null
            audioOnly -> audio != null || raw.hlsUrl != null
            else -> (video != null && audio != null) || raw.hlsUrl != null
        }
        if (!ok) throw IOException("Format stream tidak tersedia")
        return raw.copy(videoUrl = if (audioOnly) null else video, audioUrl = audio)
    }

    private fun context(c: PlayerClient, visitor: String?, version: String = c.version): JSONObject {
        val client = JSONObject()
            .put("clientName", c.name)
            .put("clientVersion", version)
        c.extra(client)
        if (visitor != null) client.put("visitorData", visitor)
        client.put("hl", InnerTube.hl).put("gl", InnerTube.gl).put("utcOffsetMinutes", 0)
        return JSONObject()
            .put("client", client)
            .put("request", JSONObject().put("internalExperimentFlags", JSONArray()).put("useSsl", true))
            .put("user", JSONObject().put("lockedSafetyMode", false))
    }

    private fun call(
        c: PlayerClient,
        url: String,
        body: JSONObject,
        version: String = c.version,
        headers: Map<String, String> = c.headers,
    ): JSONObject {
        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", c.userAgent)
            .apply {
                if (c.base != GAPIS) {
                    header("X-YouTube-Client-Name", c.id.toString())
                    header("X-YouTube-Client-Version", version)
                }
            }
            .header("Accept-Language", "${InnerTube.hl}-${InnerTube.gl}, ${InnerTube.hl};q=0.9")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return InnerTube.http.newCall(req).execute().use { resp ->
            val s = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            JSONObject(s)
        }
    }

    private val nPath = Regex("/n/([^/?#]+)")
    private val nQuery = Regex("([?&])n=([^&#]+)")

    /** Ganti nilai "n" (di path /n/…/ maupun ?n=) memakai peta hasil skrip pemutar. */
    fun rewriteN(url: String, map: Map<String, String>): String {
        if (map.isEmpty()) return url
        var u = nPath.replace(url) { m -> map[m.groupValues[1]]?.let { "/n/$it" } ?: m.value }
        u = nQuery.replace(u) { m -> map[m.groupValues[2]]?.let { "${m.groupValues[1]}n=$it" } ?: m.value }
        return u
    }

    /** Kumpulkan semua nilai "n" di manifest HLS live (utama + satu varian) lalu pecahkan sekaligus. */
    private suspend fun liveNMap(hls: String, cfg: Cipher.PlayerConfig, c: PlayerClient): Map<String, String> {
        fun fetch(url: String): String {
            val b = Request.Builder().url(url).header("User-Agent", c.mediaUa ?: c.userAgent)
            c.mediaHeaders.forEach { (k, v) -> b.header(k, v) }
            return InnerTube.http.newCall(b.build()).execute().use { it.body?.string() ?: "" }
        }
        val ns = LinkedHashSet<String>()
        fun collect(text: String) {
            nPath.findAll(text).forEach { ns.add(it.groupValues[1]) }
            nQuery.findAll(text).forEach { ns.add(it.groupValues[2]) }
        }
        collect(hls)
        val master = try { fetch(hls) } catch (_: Exception) { "" }
        collect(master)
        master.lines().firstOrNull { it.startsWith("http") }?.let { v ->
            try { collect(fetch(v)) } catch (_: Exception) { }
        }
        if (ns.isEmpty()) return emptyMap()
        return Cipher.solve(cfg, emptyList(), ns.toList()).second.filter { (k, v) -> v.isNotBlank() && v != k }
    }

    private fun visitorFor(c: PlayerClient): String? {
        if (!c.ownVisitor) return Session.visitorData
        visitors[c.name]?.let { return it }
        val r = call(c, "${WWW}visitor_id?prettyPrint=false", JSONObject().put("context", context(c, null)))
        val v = r.str("responseContext", "visitorData") ?: throw IOException("visitorData kosong")
        visitors[c.name] = v
        return v
    }

    /**
     * Daftar subtitle dari klien Android (URL-nya tidak butuh PO token, berbeda dengan
     * klien web yang subtitle-nya sering kosong).
     */
    suspend fun altCaptions(videoId: String): List<Caption> = withContext(Dispatchers.IO) {
        for (c in clients.filter { it.kind == Kind.NATIVE }) {
            try {
                val st = request(c, videoId, probeMedia = false)
                if (st.captions.isNotEmpty()) return@withContext st.captions
            } catch (_: Exception) {
            }
        }
        emptyList()
    }

    private suspend fun request(c: PlayerClient, videoId: String, probeMedia: Boolean = true): Stream {
        val cpn = nonce(16)
        var embed: EmbedPage? = null
        var cipherCfg: Cipher.PlayerConfig? = null
        val visitor: String? = when (c.kind) {
            Kind.EMBED -> {
                val page = Cipher.embedPage(videoId, if (c.auth) Session.cookie else null)
                embed = page
                cipherCfg = Cipher.config(page.playerUrl) ?: throw IOException("Versi pemutar belum dikenal")
                page.visitorData
            }
            Kind.MUSIC_VISION -> Cipher.recentVisitor ?: Session.visitorData
            Kind.NATIVE -> visitorFor(c)
        }
        val version = if (c.kind == Kind.EMBED) embed?.clientVersion ?: c.version else c.version
        val ctx = context(c, visitor, version)
        val body = JSONObject()
            .put("context", ctx)
            .put("videoId", videoId)
            .put("cpn", cpn)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        if (c.kind == Kind.EMBED) {
            ctx.put("thirdParty", JSONObject().put("embedUrl", "https://www.reddit.com/"))
            val cpc = JSONObject().put("html5Preference", "HTML5_PREF_WANTS")
                .put("signatureTimestamp", cipherCfg?.sts ?: embed?.sts)
            embed?.hostFlags?.let { cpc.put("encryptedHostFlags", it) }
            body.put("playbackContext", JSONObject().put("contentPlaybackContext", cpc))
                .put("thirdParty", JSONObject().put("embedUrl", "https://www.youtube.com/embed/$videoId"))
                .put("videoCheckOk", true)
        }
        val url = if (c.base == GAPIS) {
            "${c.base}player?prettyPrint=false&t=${nonce(12)}&id=$videoId"
        } else {
            "${c.base}player?prettyPrint=false"
        }
        val headers = HashMap(c.headers)
        if (c.kind != Kind.NATIVE && visitor != null) headers["X-Goog-Visitor-Id"] = visitor
        if (c.auth) {
            val cookie = Session.cookie?.takeIf { Session.loggedIn } ?: throw IOException("Belum login")
            headers["Cookie"] = cookie
            InnerTube.authHeader(cookie, "https://www.youtube.com")?.let {
                headers["Authorization"] = it
                headers["X-Goog-AuthUser"] = "0"
            }
        }
        val json = call(c, url, body, version, headers)
        val status = json.str("playabilityStatus", "status")
        if (status != "OK") {
            val reason = json.str("playabilityStatus", "reason") ?: status ?: "Tidak bisa diputar"
            throw IOException(reason)
        }
        val sd = json.obj("streamingData") ?: throw IOException("Tidak ada data stream")
        val isLive = json.obj("videoDetails")?.optBoolean("isLive", false) == true
        val hls = sd.str("hlsManifestUrl")
        val dash = sd.str("dashManifestUrl")
        // Live dari klien web: parameter "n" di URL HLS harus diubah dengan skrip pemutar YouTube.
        val liveN: Map<String, String>? = if (isLive && hls != null && (c.kind == Kind.EMBED || c.name == "WEB")) {
            try {
                val cfgN = cipherCfg ?: Cipher.config(Cipher.embedPage(videoId).playerUrl)
                if (cfgN != null) liveNMap(hls, cfgN, c) else emptyMap()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emptyMap()
            }
        } else null
        // Siaran langsung diputar lewat HLS (atau DASH bila HLS tidak ada); klien tanpa keduanya dilewati.
        if (isLive && hls == null && dash == null) {
            throw IOException("Live tanpa HLS [" + sd.keys().asSequence().joinToString(",") + "]")
        }
        val raw = if (c.hlsOnly) emptyList() else sd.arr("adaptiveFormats")?.objects()?.filter { it.str("url") != null || it.str("signatureCipher") != null } ?: emptyList()
        if (raw.none { it.str("mimeType")?.startsWith("audio/") == true } && hls == null) {
            throw IOException("Format stream tidak tersedia")
        }

        // Video dengan banyak trek audio (sulih suara otomatis/AI): ambil suara asli saja.
        fun isAudio(f: JSONObject) = f.str("mimeType")?.startsWith("audio/") == true
        fun tags(f: JSONObject) = (f.str("xtags") ?: "") + " " + java.net.URLDecoder.decode(
            f.str("url") ?: f.str("signatureCipher") ?: "", "UTF-8",
        )
        fun isDrc(f: JSONObject) = f.optBoolean("isDrc") || tags(f).contains("drc=1")
        val tracked = raw.filter { isAudio(it) && it.obj("audioTrack") != null }
        val original = tracked.filter { f ->
            val t = f.obj("audioTrack")
            tags(f).contains("acont=original") ||
                t?.optBoolean("audioIsDefault") == true ||
                t?.str("displayName")?.contains("original", ignoreCase = true) == true
        }
        var audios = raw.filter { isAudio(it) }
        if (tracked.isNotEmpty() && original.isNotEmpty()) audios = audios.filter { it in original || it.obj("audioTrack") == null }
        if (audios.any { !isDrc(it) }) audios = audios.filter { !isDrc(it) }
        val usable = raw.filter { !isAudio(it) } + audios.ifEmpty { raw.filter { isAudio(it) } }

        // URL dasar + tanda tangan terenkripsi (klien web) -> pecahkan sekaligus.
        data class Pending(val f: JSONObject, val base: String, val s: String?, val sp: String)
        val pending = usable.mapNotNull { f ->
            val direct = f.str("url")
            if (direct != null) return@mapNotNull Pending(f, direct, null, "sig")
            val q = parseQuery(f.str("signatureCipher") ?: return@mapNotNull null)
            val u = q["url"] ?: return@mapNotNull null
            Pending(f, u, q["s"], q["sp"] ?: "sig")
        }
        val nOf = { u: String -> Regex("[?&]n=([^&]+)").find(u)?.groupValues?.get(1) }
        var sigMap = emptyMap<String, String>()
        var nMap = emptyMap<String, String>()
        val cfg = cipherCfg
        if (cfg != null) {
            val sigs = pending.mapNotNull { it.s }
            val ns = pending.mapNotNull { nOf(it.base) }
            if (sigs.isNotEmpty() || ns.isNotEmpty()) {
                val solved = Cipher.solve(cfg, sigs, ns)
                sigMap = solved.first
                nMap = solved.second
                if (sigs.isNotEmpty() && sigMap.isEmpty()) throw IOException("Tanda tangan gagal dipecahkan")
                // Parameter n yang tidak terpecahkan membuat YouTube membatasi kecepatan unduh
                // (video jadi sering buffering) -> lebih baik pakai klien lain.
                if (ns.isNotEmpty() && nMap.none { (k, v) -> v.isNotBlank() && v != k }) {
                    throw IOException("Parameter n gagal dipecahkan")
                }
            }
        } else if (pending.any { it.s != null }) {
            throw IOException("Butuh dekripsi tanda tangan")
        }

        fun finalUrl(p: Pending): String? {
            var u = p.base
            if (p.s != null) {
                val sig = sigMap[p.s] ?: return null
                u += "&${p.sp}=" + java.net.URLEncoder.encode(sig, "UTF-8")
            }
            val n = nOf(u)
            val nn = n?.let { nMap[it] }
            if (n != null && nn != null) u = u.replace("n=$n", "n=$nn")
            if (!u.contains("cpn=")) u += "&cpn=$cpn"
            return u
        }

        val formats = pending.mapNotNull { p ->
            val f = p.f
            val u = finalUrl(p) ?: return@mapNotNull null
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
        val stream = Stream(
            formats = formats,
            lengthSeconds = json.str("videoDetails", "lengthSeconds")?.toLongOrNull() ?: 0L,
            videoUrl = null,
            audioUrl = null,
            hlsUrl = liveN?.let { rewriteN(hls ?: "", it) } ?: hls,
            dashUrl = dash,
            nMap = liveN ?: emptyMap(),
            note = when {
                liveN == null -> ""
                liveN.isEmpty() -> "n✗"
                else -> "n✓"
            },
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
            userAgent = c.mediaUa ?: "",
            client = key(c),
            headers = c.mediaHeaders,
            chunk = c.chunk,
            storyboardSpec = json.str("storyboards", "playerStoryboardSpecRenderer", "spec"),
            trackPlayback = json.str("playbackTracking", "videostatsPlaybackUrl", "baseUrl"),
            trackWatchtime = json.str("playbackTracking", "videostatsWatchtimeUrl", "baseUrl"),
            trackClient = c.name,
            trackVersion = version,
            isLive = isLive,
            title = json.str("videoDetails", "title"),
            author = json.str("videoDetails", "author"),
        )
        // Klien web dengan cipher tidak kena batas 1 MB; uji hanya klien lain (hemat satu permintaan).
        if (!isLive && probeMedia && c.kind != Kind.EMBED && !c.hlsOnly) probe(stream)
        return stream
    }

    /**
     * Uji URL audio di atas 1 MB. Sejak 2026 YouTube menolak (403) sebagian klien setelah
     * 1 MB pertama — inilah penyebab lagu berhenti di 0:30. Klien seperti itu dilewati.
     */
    private fun probe(s: Stream) {
        val audio = s.formats.filter { it.isAudio }.maxByOrNull { it.bitrate } ?: return
        val len = audio.contentLength
        val start = if (len > 1_300_000) 1_100_000L else 0L
        val req = Request.Builder().url(audio.url)
            .apply {
                if (s.userAgent.isNotEmpty()) header("User-Agent", s.userAgent)
                s.headers.forEach { (k, v) -> header(k, v) }
            }
            .header("Range", "bytes=$start-${start + 2047}")
            .build()
        val code = try {
            InnerTube.http.newCall(req).execute().use { it.code }
        } catch (_: Exception) {
            return // gangguan jaringan: jangan langsung salahkan kliennya
        }
        if (code == 403 || code == 401 || code == 404 || code == 410) throw IOException("Stream ditolak (HTTP $code)")
    }

    private fun parseQuery(q: String): Map<String, String> = q.split("&").mapNotNull {
        val k = it.substringBefore("=", "")
        if (k.isEmpty()) null else k to java.net.URLDecoder.decode(it.substringAfter("="), "UTF-8")
    }.toMap()
}
