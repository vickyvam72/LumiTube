package com.lumitube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Klien InnerTube (API internal YouTube).
 * Login memakai cookie dari WebView + header SAPISIDHASH, berlaku untuk YouTube (WEB)
 * maupun YouTube Music (WEB_REMIX).
 */
object InnerTube {

    enum class Client(
        val clientName: String,
        val clientNameId: Int,
        var version: String,
        val origin: String,
    ) {
        WEB("WEB", 1, "2.20260805.01.00", "https://www.youtube.com"),
        MUSIC("WEB_REMIX", 67, "1.20260804.16.00", "https://music.youtube.com"),
        MWEB("MWEB", 2, "2.20260805.01.00", "https://m.youtube.com"),
    }

    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_TYPE = "application/json".toMediaType()
    private val configured = HashSet<Client>()

    val hl: String get() = Locale.getDefault().language.ifEmpty { "id" }
    val gl: String get() = Locale.getDefault().country.ifEmpty { "ID" }

    /** Ambil versi klien terbaru & visitorData langsung dari halaman YouTube (fallback ke konstanta). */
    private fun ensureConfig(client: Client) {
        if (client in configured) return
        // Versi klien tersimpan dari pembukaan sebelumnya: langsung dipakai (tanpa menunggu
        // halaman YouTube diunduh), lalu diperbarui diam-diam di latar belakang.
        val saved = Session.getString("cver_${client.name}")
        if (saved != null && client !in configured) {
            synchronized(configured) {
                if (client !in configured) {
                    client.version = saved
                    configured.add(client)
                    if (System.currentTimeMillis() - Session.getLong("cver_t_${client.name}") > 12 * 60 * 60 * 1000L) {
                        Thread { fetchConfig(client) }.start()
                    }
                }
            }
            return
        }
        synchronized(configured) {
            if (client in configured) return
            fetchConfig(client)
            configured.add(client)
        }
    }

    private fun fetchConfig(client: Client) {
        run {
            try {
                val req = Request.Builder().url(client.origin + "/")
                    .header("User-Agent", DESKTOP_UA)
                    .header("Accept-Language", "$hl-$gl,$hl;q=0.9")
                    .header("Cookie", "SOCS=CAE=")
                    .build()
                http.newCall(req).execute().use { resp ->
                    val html = resp.body?.string() ?: ""
                    Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").find(html)?.let {
                        client.version = it.groupValues[1]
                        Session.putString("cver_${client.name}", client.version)
                        Session.putLong("cver_t_${client.name}", System.currentTimeMillis())
                    }
                    if (Session.visitorData == null) {
                        Regex("\"VISITOR_DATA\":\"([^\"]+)\"").find(html)?.let {
                            Session.visitorData = it.groupValues[1]
                        }
                    }
                }
            } catch (_: Exception) {
                // pakai versi bawaan
            }
        }
    }

    /**
     * Catat ke Histori tontonan akun: ambil URL "videostatsPlaybackUrl" dari respons player
     * yang login, lalu panggil URL itu seperti yang dilakukan pemutar web YouTube.
     */
    /** Data pelacakan yang sudah ada dari stream yang sedang diputar. */
    data class Tracking(val playback: String, val watchtime: String?, val clientName: String, val version: String, val lengthSeconds: Long)

    /** Hasil terakhir pencatatan histori (untuk diagnosis). */
    @Volatile var lastHistoryResult: String = ""

    suspend fun registerWatch(videoId: String, playlistId: String?, music: Boolean, stream: Tracking? = null, short: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val cookie = Session.cookie ?: return@withContext false
        if (!Session.loggedIn) return@withContext false
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val log = mutableListOf<String>()

        fun ping(base: String, clientName: String, version: String, origin: String, len: Double, extra: Map<String, String>, cpn: String): Int {
            val lenStr = String.format(Locale.US, "%.3f", len.coerceAtLeast(1.0))
            val url0 = if (origin.contains("music.")) base.replace("https://s.youtube.com", origin) else base
            // Cara yt-dlp (--mark-watched): ver, cpn, cmt dan el=detailpage (tanpa ini dianggap "shorts").
            // Shorts dicatat sebagai Shorts (el=shortspage), video biasa sebagai detailpage.
            val set = linkedMapOf("ver" to "2", "cpn" to cpn, "cmt" to lenStr, "el" to (if (short) "shortspage" else "detailpage"), "c" to clientName, "cver" to version)
            if (playlistId != null) set["list"] = playlistId
            set.putAll(extra.mapValues { if (it.value == "LEN") lenStr else it.value })
            val path = url0.substringBefore('?')
            val kept = url0.substringAfter('?', "").split('&').filter { it.isNotEmpty() && it.substringBefore('=') !in set.keys }
            val url = path + "?" + (kept + set.map { (k, v) -> "$k=" + java.net.URLEncoder.encode(v, "UTF-8") }).joinToString("&")
            val b = Request.Builder().url(url)
                .header("User-Agent", DESKTOP_UA)
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("Cookie", cookie)
                .header("X-Goog-AuthUser", "0")
            authHeader(cookie, origin)?.let { b.header("Authorization", it) }
            Session.visitorData?.let { b.header("X-Goog-Visitor-Id", it) }
            return http.newCall(b.build()).execute().use { it.code }
        }

        fun send(playback: String, watchtime: String?, clientName: String, version: String, origin: String, len: Double, tag: String): Boolean {
            val cpn = (1..16).map { chars[kotlin.random.Random.nextInt(chars.length)] }.joinToString("")
            val code = ping(playback, clientName, version, origin, len, emptyMap(), cpn)
            if (watchtime != null) try {
                ping(watchtime, clientName, version, origin, len, mapOf("st" to "0", "et" to "LEN"), cpn)
            } catch (_: Exception) {
            }
            log += "$tag:$code"
            return code in 200..299
        }

        var ok = false
        // 1) URL pelacakan dari stream yang sedang diputar (respons yang sama dengan pemutarannya).
        if (stream != null) {
            try {
                ok = send(stream.playback, stream.watchtime, stream.clientName, stream.version, Client.WEB.origin, stream.lengthSeconds.toDouble(), "stream")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                log += "stream:${e.message}"
            }
        }
        // 2) Respons player yang login (klien web / mobile web / YT Music), sampai satu berhasil.
        val order = if (music) listOf(Client.MUSIC, Client.WEB, Client.MWEB) else listOf(Client.WEB, Client.MWEB, Client.MUSIC)
        for (client in order) {
            try {
                val body = JSONObject().put("videoId", videoId)
                if (playlistId != null) body.put("playlistId", playlistId)
                val r = post(client, "player", body)
                val tracking = r.optJSONObject("playbackTracking")
                val playback = tracking?.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl")?.takeIf { it.startsWith("http") }
                if (playback == null) {
                    log += "${client.name}:${r.optJSONObject("playabilityStatus")?.optString("status") ?: "no-tracking"}"
                    continue
                }
                val watchtime = tracking.optJSONObject("videostatsWatchtimeUrl")?.optString("baseUrl")?.takeIf { it.startsWith("http") }
                val len = r.optJSONObject("videoDetails")?.optString("lengthSeconds")?.toDoubleOrNull() ?: 1.0
                if (send(playback, watchtime, client.clientName, client.version, client.origin, len, client.name)) {
                    ok = true
                    break
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                log += "${client.name}:${e.message}"
            }
        }
        lastHistoryResult = log.joinToString(", ")
        ok
    }

    private fun context(client: Client): JSONObject {
        val c = JSONObject()
            .put("clientName", client.clientName)
            .put("clientVersion", client.version)
            .put("hl", hl)
            .put("gl", gl)
            .put("userAgent", DESKTOP_UA)
            .put("platform", "DESKTOP")
        Session.visitorData?.let { c.put("visitorData", it) }
        return JSONObject()
            .put("client", c)
            .put("user", JSONObject().put("lockedSafetyMode", false))
    }

    fun parseCookies(cookie: String): Map<String, String> =
        cookie.split(";").mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).trim() to it.substring(i + 1).trim()
        }.toMap()

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Header Authorization yang dipakai web YouTube untuk request yang login. */
    fun authHeader(cookie: String, origin: String): String? {
        val map = parseCookies(cookie)
        val ts = System.currentTimeMillis() / 1000
        val parts = mutableListOf<String>()
        map["SAPISID"]?.let { parts.add("SAPISIDHASH ${ts}_${sha1("$ts $it $origin")}") }
        map["__Secure-1PAPISID"]?.let { parts.add("SAPISID1PHASH ${ts}_${sha1("$ts $it $origin")}") }
        map["__Secure-3PAPISID"]?.let { parts.add("SAPISID3PHASH ${ts}_${sha1("$ts $it $origin")}") }
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    suspend fun post(
        client: Client,
        endpoint: String,
        body: JSONObject = JSONObject(),
        query: String = "",
        auth: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        ensureConfig(client)
        body.put("context", context(client))
        val url = "${client.origin}/youtubei/v1/$endpoint?prettyPrint=false$query"
        val builder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_TYPE))
            .header("User-Agent", DESKTOP_UA)
            .header("Origin", client.origin)
            .header("Referer", client.origin + "/")
            .header("X-Origin", client.origin)
            .header("X-YouTube-Client-Name", client.clientNameId.toString())
            .header("X-YouTube-Client-Version", client.version)
            .header("Accept-Language", "$hl-$gl,$hl;q=0.9")
        Session.visitorData?.let { builder.header("X-Goog-Visitor-Id", it) }
        val cookie = Session.cookie
        if (auth && cookie != null && Session.loggedIn) {
            builder.header("Cookie", cookie)
            authHeader(cookie, client.origin)?.let {
                builder.header("Authorization", it)
                builder.header("X-Goog-AuthUser", "0")
            }
        } else {
            builder.header("Cookie", "SOCS=CAE=")
        }
        http.newCall(builder.build()).execute().use { resp ->
            val s = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val msg = try {
                    JSONObject(s).str("error", "message")
                } catch (_: Exception) {
                    null
                }
                throw IOException("HTTP ${resp.code}${if (msg != null) ": $msg" else ""}")
            }
            val json = JSONObject(s)
            if (Session.visitorData == null) {
                json.str("responseContext", "visitorData")?.let { Session.visitorData = it }
            }
            json
        }
    }

    // ---------- Endpoint ----------

    suspend fun browse(client: Client, browseId: String, params: String? = null): JSONObject {
        val body = JSONObject().put("browseId", browseId)
        if (params != null) body.put("params", params)
        return post(client, "browse", body)
    }

    /**
     * Token lanjutan. Prefix "q:" berarti token gaya lama (dikirim lewat query),
     * prefix "b:" dikirim di body.
     */
    suspend fun continuation(client: Client, endpoint: String, token: String): JSONObject {
        return if (token.startsWith("q:")) {
            val t = java.net.URLEncoder.encode(token.substring(2), "UTF-8")
            post(client, endpoint, JSONObject(), "&ctoken=$t&continuation=$t&type=next")
        } else {
            post(client, endpoint, JSONObject().put("continuation", token.removePrefix("b:")))
        }
    }

    suspend fun search(client: Client, query: String, params: String? = null): JSONObject {
        val body = JSONObject().put("query", query)
        if (params != null) body.put("params", params)
        return post(client, "search", body)
    }

    suspend fun next(client: Client, videoId: String, playlistId: String? = null): JSONObject {
        val body = JSONObject().put("videoId", videoId)
        if (playlistId != null) body.put("playlistId", playlistId)
        if (client == Client.MUSIC) {
            body.put("isAudioOnly", true)
            body.put("enablePersistentPlaylistPanel", true)
            body.put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
        }
        return post(client, "next", body)
    }

    suspend fun accountMenu(): JSONObject = post(Client.WEB, "account/account_menu")

    suspend fun subscribe(channelId: String, subscribe: Boolean) {
        post(
            Client.WEB,
            if (subscribe) "subscription/subscribe" else "subscription/unsubscribe",
            JSONObject().put("channelIds", JSONArray().put(channelId))
        )
    }

    suspend fun dislike(videoId: String, dislike: Boolean) {
        post(
            Client.WEB,
            if (dislike) "like/dislike" else "like/removelike",
            JSONObject().put("target", JSONObject().put("videoId", videoId))
        )
    }

    suspend fun like(videoId: String, like: Boolean) {
        post(
            Client.WEB,
            if (like) "like/like" else "like/removelike",
            JSONObject().put("target", JSONObject().put("videoId", videoId))
        )
        // Daftar "Video yang disukai" yang tersimpan sementara dibuang supaya langsung terlihat.
        listOf("VLLL", "VLLM", "FEmusic_liked_videos").forEach { com.lumitube.app.ui.common.FeedLoader.invalidate(it) }
    }
}
