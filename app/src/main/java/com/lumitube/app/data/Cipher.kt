package com.lumitube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException

/** Mesin JavaScript (diisi WebView dari sisi Android). */
interface JsRunner {
    /** Muat skrip pemutar YouTube; [key] = hash pemutar (skrip yang sama tidak dimuat ulang). */
    suspend fun load(key: String, script: String): Boolean

    /** Jalankan ekspresi, hasilnya dalam bentuk JSON (seperti WebView.evaluateJavascript). */
    suspend fun eval(js: String): String?
}

/** Isi halaman embed YouTube yang dibutuhkan klien WEB_EMBEDDED_PLAYER. */
data class EmbedPage(
    val playerUrl: String,
    val visitorData: String?,
    val hostFlags: String?,
    val clientVersion: String?,
    val sts: Int?,
)

/**
 * Dekripsi "signatureCipher" dan parameter "n" pada URL stream klien web.
 *
 * Cara Metrolist/InnerTubeX (Oktober 2026): nama fungsi sig & kelas n untuk tiap versi
 * pemutar diambil dari tabel Zemer yang selalu diperbarui, lalu dijalankan di dalam skrip
 * pemutar asli (base.js) memakai mesin JavaScript.
 */
object Cipher {
    var runner: JsRunner? = null
    var cacheDir: File? = null

    const val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    private const val TABLE_URL =
        "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
    private const val TABLE_MIRROR =
        "https://cdn.jsdelivr.net/gh/ZemerTeam/zemer-cipher@master/library/src/main/assets/player_configs.json"
    private const val IIFE_TRAILER = "})(_yt_player);"
    private const val N_PROBE = "KdrqFlzJXl9EcCwlmEy"

    data class PlayerConfig(val hash: String, val sig: String, val nClass: String, val sts: Int)

    private val mutex = Mutex()
    private var table: Map<String, PlayerConfig> = emptyMap()
    private var tableTime = 0L
    private var lastEmbed: Pair<Long, EmbedPage>? = null

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val req = Request.Builder().url(url).apply {
            header("User-Agent", WEB_UA)
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        return InnerTube.http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string() ?: throw IOException("Respons kosong")
        }
    }

    private fun unescape(s: String) = s.replace("\\/", "/").replace("\\u0026", "&")

    /** Halaman embed: URL pemutar, visitorData, encryptedHostFlags, signatureTimestamp. */
    /** Isi halaman embed tidak bergantung pada video: simpan 30 menit (hemat satu permintaan per video). */
    private val embedCache = java.util.concurrent.ConcurrentHashMap<Boolean, Pair<Long, EmbedPage>>()

    /** Mesin cipher sudah siap (skrip pemutar sudah dimuat di WebView). */
    val isReady: Boolean get() = loadedHash != null

    suspend fun embedPage(videoId: String, cookie: String? = null): EmbedPage {
        embedCache[cookie != null]?.let { (t, p) -> if (System.currentTimeMillis() - t < 30 * 60 * 1000L) return p }
        return fetchEmbedPage(videoId, cookie).also { embedCache[cookie != null] = System.currentTimeMillis() to it }
    }

    private suspend fun fetchEmbedPage(videoId: String, cookie: String? = null): EmbedPage = withContext(Dispatchers.IO) {
        val html = get(
            "https://www.youtube.com/embed/$videoId?html5=1&hl=${InnerTube.hl}",
            mapOf(
                "Referer" to "https://www.reddit.com/",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language" to "${InnerTube.hl}-${InnerTube.gl},${InnerTube.hl};q=0.9",
                "Cookie" to (cookie ?: "SOCS=CAE=; PREF=hl=${InnerTube.hl}&gl=${InnerTube.gl}"),
            ),
        )
        val player = Regex("\"(?:PLAYER_JS_URL|jsUrl)\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.let(::unescape)
            ?: Regex("(/s/player/[A-Za-z0-9_-]+/[^\"'\\\\]*base\\.js)").find(unescape(html))?.groupValues?.get(1)
            ?: throw IOException("URL pemutar tidak ditemukan")
        val page = EmbedPage(
            playerUrl = if (player.startsWith("http")) player else "https://www.youtube.com$player",
            visitorData = Regex("\"(?:VISITOR_DATA|visitorData)\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1),
            hostFlags = Regex("\"encryptedHostFlags\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1),
            clientVersion = Regex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1),
            sts = Regex("(?:signatureTimestamp|sts)\"?\\s*:\\s*([0-9]{5})").find(html)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("\"STS\":\\s*([0-9]{5})").find(html)?.groupValues?.get(1)?.toIntOrNull(),
        )
        if (cookie == null) lastEmbed = System.currentTimeMillis() to page
        page
    }

    /** visitorData terbaru dari halaman YouTube (dipakai klien VISIONOS). */
    val recentVisitor: String?
        get() = lastEmbed?.takeIf { System.currentTimeMillis() - it.first < 30 * 60 * 1000L }?.second?.visitorData

    fun playerHash(playerUrl: String): String? =
        Regex("/s/player/([A-Za-z0-9_-]+)/").find(playerUrl)?.groupValues?.get(1)

    private fun parseTable(text: String): Map<String, PlayerConfig> {
        val players = JSONObject(text).optJSONObject("players") ?: return emptyMap()
        val out = HashMap<String, PlayerConfig>()
        for (hash in players.keys()) {
            val o = players.optJSONObject(hash) ?: continue
            val sig = o.optString("sig").takeIf { Regex("^[A-Za-z0-9\$_]{1,8}\\(\\d+,\\d+,INPUT\\)$").matches(it) } ?: continue
            val n = o.optString("nClass").takeIf { Regex("^[A-Za-z0-9\$_]{1,8}$").matches(it) } ?: continue
            val sts = o.optInt("sts", 0).takeIf { it > 0 } ?: continue
            val cfg = PlayerConfig(hash, sig, n, sts)
            out[hash] = cfg
            o.optJSONArray("aliases")?.let { a -> for (i in 0 until a.length()) out[a.optString(i)] = cfg.copy(hash = a.optString(i)) }
        }
        return out
    }

    /** Konfigurasi sig/n untuk versi pemutar ini (tabel di-refresh bila versi baru belum ada). */
    suspend fun config(playerUrl: String): PlayerConfig? = withContext(Dispatchers.IO) {
        val hash = playerHash(playerUrl) ?: return@withContext null
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (table.isEmpty()) {
                // Salinan terakhir di disk dulu supaya cepat.
                val f = cacheDir?.let { File(it, "zemer_players.json") }
                if (f != null && f.exists()) {
                    try {
                        table = parseTable(f.readText())
                        tableTime = f.lastModified()
                    } catch (_: Exception) {
                    }
                }
            }
            val stale = now - tableTime > 6 * 60 * 60 * 1000L
            val recentlyMissing = missingHash == hash && now - missingTime < 15 * 60 * 1000L
            if ((table[hash] == null && !recentlyMissing) || stale) {
                for (u in listOf(TABLE_URL, TABLE_MIRROR)) {
                    try {
                        val text = get(u)
                        val parsed = parseTable(text)
                        if (parsed.isNotEmpty()) {
                            table = parsed
                            tableTime = now
                            cacheDir?.let { File(it, "zemer_players.json").writeText(text) }
                            break
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            table[hash] ?: run {
                missingHash = hash
                missingTime = now
                // Versi pemutar terbaru belum masuk tabel: pakai versi terbaru yang dikenal
                // (YouTube masih menerima pemutar lama beberapa waktu, seperti opsi player_js_version yt-dlp).
                table.values.maxByOrNull { it.sts }
            }
        }
    }

    private var missingHash: String? = null
    private var missingTime = 0L

    private suspend fun playerScript(hash: String): String = withContext(Dispatchers.IO) {
        val f = cacheDir?.let { File(it, "player_$hash.js") }
        if (f != null && f.exists() && f.length() > 100_000) return@withContext f.readText()
        val js = get("https://www.youtube.com/s/player/$hash/player_ias.vflset/en_GB/base.js")
        cacheDir?.listFiles { file -> file.name.startsWith("player_") && file.name.endsWith(".js") }?.forEach { it.delete() }
        f?.writeText(js)
        js
    }

    private fun modified(script: String, cfg: PlayerConfig): String {
        val sigExpr = cfg.sig.replace("INPUT", "sig")
        val nExpr = "(function(n){try{var u=new g.${cfg.nClass}('https://x.googlevideo.com/videoplayback?n='+n,true);" +
            "var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(n)"
        val exports = ";window._cipherSigFunc=function(sig){try{return $sigExpr;}catch(e){return null;}};" +
            "window._nTransformFunc=function(n){try{return $nExpr;}catch(e){return n;}};"
        val i = script.lastIndexOf(IIFE_TRAILER)
        return if (i >= 0) script.substring(0, i) + exports + script.substring(i) else "$script\n$exports"
    }

    private fun decode(result: String?): String? {
        if (result == null) return null
        return try {
            JSONTokener(result).nextValue() as? String
        } catch (_: Exception) {
            null
        }
    }

    private val loadMutex = Mutex()
    private var loadedHash: String? = null

    /** Siapkan mesin JS untuk versi pemutar ini (dipanggil juga saat aplikasi dibuka). */
    suspend fun prepare(cfg: PlayerConfig): Boolean = loadMutex.withLock {
        val r = runner ?: return@withLock false
        if (loadedHash == cfg.hash) return@withLock true
        val ok = r.load(cfg.hash, modified(playerScript(cfg.hash), cfg))
        if (!ok) return@withLock false
        val probe = decode(r.eval("String(window._nTransformFunc ? window._nTransformFunc(${JSONObject.quote(N_PROBE)}) : '')"))
        val good = probe != null && probe != N_PROBE && probe.length >= 5 && Regex("^[A-Za-z0-9_-]+$").matches(probe)
        if (good) loadedHash = cfg.hash
        good
    }

    /** Pecahkan semua nilai sig dan n sekaligus. */
    suspend fun solve(cfg: PlayerConfig, sigs: List<String>, ns: List<String>): Pair<Map<String, String>, Map<String, String>> {
        if (!prepare(cfg)) throw IOException("Mesin cipher gagal disiapkan")
        val r = runner ?: throw IOException("Mesin cipher tidak ada")
        val js = "(function(){var s=${JSONArray(sigs.distinct())},n=${JSONArray(ns.distinct())},o={s:{},n:{}};" +
            "s.forEach(function(x){try{var v=_cipherSigFunc(x);if(v)o.s[x]=String(v);}catch(e){}});" +
            "n.forEach(function(x){try{var v=_nTransformFunc(x);if(v)o.n[x]=String(v);}catch(e){}});" +
            "return JSON.stringify(o);})()"
        val out = JSONObject(decode(r.eval(js)) ?: throw IOException("Cipher tidak menjawab"))
        fun map(o: JSONObject?) = o?.keys()?.asSequence()?.associateWith { o.optString(it) } ?: emptyMap()
        return map(out.optJSONObject("s")) to map(out.optJSONObject("n"))
    }

    /** Pemanasan saat aplikasi dibuka: tabel, skrip pemutar, dan mesin JS. */
    suspend fun prewarm() {
        try {
            val page = embedPage("jNQXAC9IVRw")
            val cfg = config(page.playerUrl) ?: return
            prepare(cfg)
            // Halaman embed versi akun juga disiapkan supaya pemutaran pertama tidak menunggu.
            Session.cookie?.takeIf { Session.loggedIn }?.let { embedPage("jNQXAC9IVRw", it) }
        } catch (_: Exception) {
        }
    }

    /** Halaman embed bermasalah (mis. kedaluwarsa): ambil ulang pada permintaan berikutnya. */
    fun dropEmbed() = embedCache.clear()
}
