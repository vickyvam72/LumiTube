package com.lumitube.app.data

import kotlinx.coroutines.sync.withLock

import com.lumitube.app.data.InnerTube.Client

/** Fungsi tingkat tinggi yang dipakai UI. */
object Repo {

    // ---------------- YouTube ----------------

    suspend fun ytHome(): Page {
        val r = InnerTube.browse(Client.WEB, "FEwhat_to_watch")
        saveRaw("yt_home_${Session.loggedIn}", r)
        return ytHomePage(r)
    }

    private fun ytHomePage(r: org.json.JSONObject) = Page(
        chips = Parser.ytChips(r),
        items = Parser.items(r, false),
        continuation = Parser.continuation(r),
    )

    /** Beranda terakhir yang tersimpan di HP: tampil seketika saat aplikasi dibuka. */
    fun ytHomeCached(): Page? = loadRaw("yt_home_${Session.loggedIn}")?.let {
        try { ytHomePage(it).takeIf { p -> p.items.isNotEmpty() } } catch (_: Exception) { null }
    }

    fun musicHomeCached(): Page? = loadRaw("music_home_${Session.loggedIn}")?.let {
        try {
            val header = Parser.musicHeader(it)
            header.copy(chips = Parser.musicChips(it), shelves = Parser.musicShelves(it), continuation = Parser.continuation(it))
                .takeIf { p -> p.shelves.isNotEmpty() }
        } catch (_: Exception) { null }
    }

    private fun rawFile(name: String) = Cipher.cacheDir?.let { java.io.File(it, "feed_$name.json") }

    private fun saveRaw(name: String, r: org.json.JSONObject) {
        try {
            rawFile(name)?.writeText(r.toString())
        } catch (_: Exception) {
        }
    }

    private fun loadRaw(name: String): org.json.JSONObject? = try {
        rawFile(name)?.takeIf { it.exists() && System.currentTimeMillis() - it.lastModified() < 3 * 24 * 60 * 60 * 1000L }
            ?.let { org.json.JSONObject(it.readText()) }
    } catch (_: Exception) {
        null
    }

    suspend fun ytMore(token: String, endpoint: String = "browse"): Page {
        val r = InnerTube.continuation(Client.WEB, endpoint, token)
        return Page(items = Parser.items(r, false), continuation = Parser.continuation(r))
    }

    suspend fun ytSearch(query: String, params: String? = null): Page {
        val r = InnerTube.search(Client.WEB, query, params)
        return Page(items = Parser.items(r, false), chips = Parser.ytSearchChips(r), continuation = Parser.continuation(r))
    }

    private val shortQueries = listOf(
        "#shorts", "#shortsvideo", "#shorts lucu", "#shorts viral", "#funny #shorts", "#shortsindonesia",
        "#trending #shorts", "#fyp #shorts", "#comedy #shorts", "#music #shorts", "#gaming #shorts",
        "#food #shorts", "#prank #shorts", "#cat #shorts", "#football #shorts", "#dance #shorts",
    )
    /** Shorts yang sudah pernah disajikan (sesi ini) supaya tiap kali dibuka isinya baru. */
    private val servedShorts = LinkedHashSet<String>()

    /**
     * Feed Shorts: campuran Shorts pilihan untuk akun (dari Beranda) dan pencarian dengan
     * tagar acak, diacak dan tanpa yang sudah pernah tampil — jadi tidak "itu-itu saja".
     */
    private var tasteCache: Pair<Long, List<String>>? = null

    /**
     * Selera dari Histori: tagar & channel dari Shorts/video yang baru ditonton
     * (disimpan 30 menit). Dipakai supaya feed Shorts mirip rekomendasi YouTube asli.
     */
    private suspend fun shortsTaste(): List<String> {
        tasteCache?.let { (t, list) -> if (System.currentTimeMillis() - t < 30 * 60 * 1000L) return list }
        if (!Session.loggedIn) return emptyList()
        val list = try {
            val r = InnerTube.browse(Client.WEB, "FEhistory")
            val items = Parser.items(r, false).take(40)
            val generic = setOf("shorts", "short", "fyp", "foryou", "foryoupage", "viral", "youtubeshorts", "trending", "reels", "tiktok", "explore", "fypシ")
            val tags = items.flatMap { Regex("#([\\p{L}\\p{N}_]{3,30})").findAll(it.title).map { m -> m.groupValues[1].lowercase() }.toList() }
                .filter { it !in generic }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { "#${it.key} #shorts" }.take(12)
            val channels = items.filter { it.type == ItemType.SHORT }.mapNotNull { it.channelName ?: it.subtitle.substringBefore(" • ").takeIf { s -> s.isNotBlank() && !s.first().isDigit() } }
                .distinct().take(6).map { "$it shorts" }
            (tags + channels).distinct()
        } catch (_: Exception) {
            emptyList()
        }
        tasteCache = System.currentTimeMillis() to list
        return list
    }

    suspend fun ytShorts(): Page {
        // 2 pencarian dari selera Histori (bila ada) + 1 tagar umum, lalu dicampur.
        val taste = shortsTaste().shuffled().take(2)
        val queries = (taste + shortQueries.random()).distinct()
        var r: org.json.JSONObject? = null
        val fromSearch = mutableListOf<Item>()
        for ((qi, q) in queries.withIndex()) {
            try {
                val res = InnerTube.search(Client.WEB, q)
                if (qi == queries.lastIndex || r == null) r = res
                fromSearch += Parser.items(res, false).filter { it.type == ItemType.SHORT }
            } catch (_: Exception) {
            }
        }
        if (r == null) r = InnerTube.search(Client.WEB, shortQueries.random())
        val fromHome = try {
            ytHomeCached()?.items?.filter { it.type == ItemType.SHORT } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val all = (fromHome.shuffled().take(6) + fromSearch.shuffled()).distinctBy { it.id }
        val fresh = all.filter { it.id !in servedShorts }
        val list = if (fresh.size >= 6) fresh else all.shuffled()
        synchronized(servedShorts) {
            list.forEach { servedShorts.add(it.id) }
            while (servedShorts.size > 500) servedShorts.remove(servedShorts.first())
        }
        return Page(items = list, continuation = r?.let { Parser.continuation(it) })
    }

    suspend fun ytBrowse(browseId: String, params: String? = null): Page {
        val p = params ?: if (browseId.startsWith("UC")) "EgZ2aWRlb3PyBgQKAjoA" else null
        val r = InnerTube.browse(Client.WEB, browseId, p)
        val header = Parser.ytHeader(r)
        if (browseId.startsWith("VL")) {
            // Isi playlist ada di playlistVideoListRenderer; sisanya adalah video saran.
            val list = Parser.findFirstPublic(r, "playlistVideoListRenderer")
            if (list != null) {
                val own = Parser.items(list, false)
                val keys = own.map { it.key }.toHashSet()
                val rest = Parser.items(r, false).filter { it.key !in keys }
                return header.copy(items = own, continuation = Parser.continuation(list) ?: Parser.continuation(r), suggestions = rest)
            }
        }
        if (browseId == "FEhistory") {
            val shelves = historySections(r)
            if (shelves.isNotEmpty()) return header.copy(shelves = shelves, continuation = Parser.continuation(r))
        }
        return header.copy(items = Parser.items(r, false), continuation = Parser.continuation(r))
    }

    /** Halaman lanjutan Histori: tetap dikelompokkan per hari. */
    suspend fun ytHistoryMore(token: String): Page {
        val r = InnerTube.continuation(Client.WEB, "browse", token)
        val shelves = historySections(r)
        return if (shelves.isNotEmpty()) Page(shelves = shelves, continuation = Parser.continuation(r))
        else Page(items = Parser.items(r, false), continuation = Parser.continuation(r))
    }

    /**
     * Histori YouTube dibaca berurutan seperti tampil di aplikasi asli: setiap judul hari
     * ("Hari ini", "Kemarin", …) memulai kelompok baru; rak Shorts (reelShelf) jadi satu kelompok
     * "reel", item lain jadi baris biasa. Judul di dalam objek dibaca lebih dulu daripada isinya.
     */
    private val historyItemKeys = setOf(
        "videoRenderer", "compactVideoRenderer", "gridVideoRenderer", "videoWithContextRenderer",
        "lockupViewModel", "reelItemRenderer", "shortsLockupViewModel",
        "playlistRenderer", "gridPlaylistRenderer", "channelRenderer",
    )

    private fun historySections(r: org.json.JSONObject): List<Shelf> {
        val out = mutableListOf<Shelf>()
        var day = ""
        fun textOf(o: org.json.JSONObject?): String? {
            if (o == null) return null
            o.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
            o.optString("content").takeIf { it.isNotEmpty() }?.let { return it }
            val runs = o.optJSONArray("runs") ?: return null
            return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text") ?: "" }.takeIf { it.isNotEmpty() }
        }
        fun add(items: List<Item>, reel: Boolean) {
            if (items.isEmpty()) return
            val last = out.lastOrNull()
            if (!reel && last != null && last.title == day && last.strapline == null) {
                out[out.lastIndex] = last.copy(items = last.items + items)
            } else {
                out.add(Shelf(day, strapline = if (reel) "reel" else null, items = items))
            }
        }
        fun visit(node: Any?) {
            when (node) {
                is org.json.JSONObject -> {
                    val keys = node.keys().asSequence().toMutableList()
                    if (keys.remove("header")) keys.add(0, "header")
                    for (k in keys) {
                        val v = node.opt(k)
                        when {
                            k == "itemSectionHeaderRenderer" || k == "sectionHeaderViewModel" -> {
                                val o = v as? org.json.JSONObject
                                (textOf(o?.optJSONObject("title")) ?: textOf(o?.optJSONObject("headline")) ?: textOf(o?.optJSONObject("text")))
                                    ?.takeIf { it.isNotBlank() }?.let { day = it }
                            }
                            k == "reelShelfRenderer" || k == "richShelfRenderer" ->
                                add(Parser.items(v, false), reel = true)
                            k in historyItemKeys ->
                                add(Parser.items(org.json.JSONObject().put(k, v), false), reel = false)
                            k == "continuationItemRenderer" || k == "chipCloudRenderer" || k == "feedFilterChipBarRenderer" -> {}
                            else -> visit(v)
                        }
                    }
                }
                is org.json.JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
            }
        }
        visit(r)
        return out
    }

    /** "Telusuri histori tontonan". */
    suspend fun ytHistorySearch(query: String): Page {
        val r = InnerTube.post(Client.WEB, "browse", org.json.JSONObject().put("browseId", "FEhistory").put("query", query))
        return Page(items = Parser.items(r, false), continuation = Parser.continuation(r))
    }

    /** Cari video di dalam satu channel (tab "Telusuri" di halaman channel). */
    suspend fun ytChannelSearch(channelId: String, query: String): Page {
        val body = org.json.JSONObject()
            .put("browseId", channelId)
            .put("params", "EgZzZWFyY2jyBgQKAloA")
            .put("query", query)
        val r = InnerTube.post(Client.WEB, "browse", body)
        return Page(items = Parser.items(r, false), continuation = Parser.continuation(r))
    }

    /** Saran penelusuran saat mengetik (sama dengan kotak cari YouTube, cara NewPipe). */
    suspend fun searchSuggestions(query: String): List<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val urls = listOf(
            "https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&ds=yt&xhr=t&hl=${InnerTube.hl}&gl=${InnerTube.gl}&q=$q",
            "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=${InnerTube.hl}&q=$q",
        )
        for (url in urls) {
            try {
                val req = okhttp3.Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                val list = InnerTube.http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList<String>()
                    val text = resp.body?.string() ?: return@use emptyList<String>()
                    // Bisa berupa JSON murni atau dibungkus window.google.ac.h(...)
                    val json = text.substring(text.indexOf('[').coerceAtLeast(0), text.lastIndexOf(']') + 1)
                    val arr = org.json.JSONArray(json).optJSONArray(1) ?: return@use emptyList<String>()
                    (0 until arr.length()).mapNotNull { k ->
                        val e = arr.opt(k)
                        (if (e is org.json.JSONArray) e.optString(0) else e as? String)?.takeIf { it.isNotBlank() }
                    }
                }
                if (list.isNotEmpty()) return@withContext list.take(12)
            } catch (_: Exception) {
            }
        }
        emptyList()
    }

    suspend fun ytSubscriptionChannels(): List<Item> {
        val r = InnerTube.browse(Client.WEB, "FEchannels")
        return Parser.items(r, false).filter { it.type == ItemType.CHANNEL }
    }

    suspend fun watch(videoId: String): WatchInfo =
        Parser.watchInfo(InnerTube.next(Client.WEB, videoId))

    suspend fun comments(token: String): CommentPage =
        Parser.comments(InnerTube.continuation(Client.WEB, "next", "b:$token"))

    /** Kirim komentar baru (perlu login). */
    suspend fun postComment(createParams: String, text: String) {
        InnerTube.post(
            Client.WEB, "comment/create_comment",
            org.json.JSONObject().put("commentText", text).put("createCommentParams", createParams)
        )
    }

    /** Ambil pesan live chat (token awal dari halaman tonton, lalu token berikutnya). */
    suspend fun liveChat(continuation: String): LiveChatPage =
        Parser.liveChat(
            InnerTube.post(Client.WEB, "live_chat/get_live_chat", org.json.JSONObject().put("continuation", continuation))
        )

    /** Kirim pesan ke live chat (harus login). */
    suspend fun sendLiveChat(params: String, text: String) {
        InnerTube.post(
            Client.WEB, "live_chat/send_message",
            org.json.JSONObject()
                .put("params", params)
                .put("clientMessageId", "lumi" + System.currentTimeMillis())
                .put(
                    "richMessage",
                    org.json.JSONObject().put("textSegments", org.json.JSONArray().put(org.json.JSONObject().put("text", text)))
                )
        )
    }

    /** Daftar playlist akun + apakah video sudah ada di dalamnya. */
    suspend fun addToPlaylistOptions(videoId: String, music: Boolean): List<PlaylistOption> {
        val r = InnerTube.post(
            Client.WEB, "playlist/get_add_to_playlist",
            org.json.JSONObject().put("videoIds", org.json.JSONArray().put(videoId)).put("excludeWatchLater", music)
        )
        // Thumbnail & jumlah dari halaman koleksi (dialog bawaan YouTube tidak menyertakan gambar).
        val lib: List<Item> = try {
            if (music) musicBrowse("FEmusic_liked_playlists").let { p -> p.items + p.shelves.flatMap { sh -> sh.items } }
            else ytBrowse("FEplaylist_aggregation").items
        } catch (_: Exception) {
            emptyList()
        }
        return Parser.findAllPublic(r, "playlistAddToOptionRenderer").mapNotNull { o ->
            val id = o.str("playlistId") ?: return@mapNotNull null
            val match = lib.firstOrNull { m -> m.id == "VL$id" || m.playlistId == id || m.id == id }
            PlaylistOption(
                id = id,
                title = text(o.obj("title")) ?: match?.title ?: id,
                count = match?.subtitle?.split(" • ")?.lastOrNull { p -> p.any { it.isDigit() } },
                thumbnail = match?.thumbnail,
                contains = o.str("containsSelectedVideos") == "ALL",
            )
        }
    }

    suspend fun setInPlaylist(playlistId: String, videoId: String, add: Boolean) {
        val action = if (add) {
            org.json.JSONObject().put("addedVideoId", videoId).put("action", "ACTION_ADD_VIDEO")
        } else {
            org.json.JSONObject().put("removedVideoId", videoId).put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID")
        }
        // Beberapa klien dicoba bergantian (playlist khusus seperti "Tonton nanti" kadang ditolak satu klien).
        var last: Exception? = null
        for (c in listOf(Client.WEB, Client.MWEB, Client.MUSIC)) {
            try {
                val r = InnerTube.post(
                    c, "browse/edit_playlist",
                    org.json.JSONObject().put("playlistId", playlistId).put("actions", org.json.JSONArray().put(action))
                )
                if (r.optString("status") == "STATUS_FAILED") throw java.io.IOException("ditolak YouTube")
                return
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Video sudah ada di playlist (409) = sudah tersimpan.
                if (add && e.message?.contains("409") == true) return
                last = e
            }
        }
        throw last ?: java.io.IOException("Gagal")
    }

    private val moveMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Pindahkan item playlist ke antara [afterSetVideoId] dan [beforeSetVideoId].
     * Permintaan dijalankan berurutan (seret cepat beberapa kali tidak bertabrakan) dan
     * dicoba dengan beberapa bentuk aksi/klien, karena YouTube kadang menolak salah satunya.
     */
    suspend fun movePlaylistItem(playlistId: String, setVideoId: String, afterSetVideoId: String?, beforeSetVideoId: String?) {
        val pid = playlistId.removePrefix("VL")
        val forms = ArrayList<org.json.JSONObject>()
        fun a() = org.json.JSONObject().put("setVideoId", setVideoId)
        // Bentuk yang dipakai ytmusicapi: pindah ke sebelum item berikutnya.
        if (beforeSetVideoId != null) forms += a().put("action", "ACTION_MOVE_VIDEO_BEFORE").put("movedSetVideoIdSuccessor", beforeSetVideoId)
        // Bentuk yang dipakai youtube.com: pindah ke setelah item sebelumnya (tanpa pendahulu = paling atas).
        if (afterSetVideoId != null) forms += a().put("action", "ACTION_MOVE_VIDEO_AFTER").put("movedSetVideoIdPredecessor", afterSetVideoId)
        else forms += a().put("action", "ACTION_MOVE_VIDEO_AFTER")
        val clients = if (com.lumitube.app.CrossApp.isMusic) listOf(Client.MUSIC, Client.WEB) else listOf(Client.WEB, Client.MUSIC)
        moveMutex.withLock {
            var last: Exception? = null
            for (form in forms) for (c in clients) for (attempt in 0..1) {
                try {
                    val r = InnerTube.post(
                        c, "browse/edit_playlist",
                        org.json.JSONObject().put("playlistId", pid).put("actions", org.json.JSONArray().put(form))
                    )
                    if (r.optString("status") == "STATUS_FAILED") throw java.io.IOException("Ditolak YouTube")
                    return@withLock
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    last = e
                    // Galat HTTP 4xx = bentuk/klien ini ditolak, langsung coba yang lain; galat jaringan diulang sekali.
                    if (e.message?.startsWith("HTTP 4") == true || e.message == "Ditolak YouTube") break
                    kotlinx.coroutines.delay(600)
                }
            }
            throw last ?: java.io.IOException("Gagal")
        }
    }

    /** Keluarkan playlist (milik orang lain / buatan YouTube seperti Recap) dari koleksi. */
    suspend fun removePlaylistFromLibrary(playlistId: String) {
        val pid = playlistId.removePrefix("VL")
        val client = if (com.lumitube.app.CrossApp.isMusic) Client.MUSIC else Client.WEB
        InnerTube.post(client, "like/removelike", org.json.JSONObject().put("target", org.json.JSONObject().put("playlistId", pid)))
    }

    /** Hapus playlist milik akun. [playlistId] tanpa awalan "VL". */
    suspend fun deletePlaylist(playlistId: String) {
        InnerTube.post(Client.WEB, "playlist/delete", org.json.JSONObject().put("playlistId", playlistId.removePrefix("VL")))
    }

    /** Buat playlist baru langsung berisi video ini. privacy: PRIVATE / UNLISTED / PUBLIC. */
    suspend fun createPlaylist(title: String, privacy: String, videoId: String) {
        InnerTube.post(
            Client.WEB, "playlist/create",
            org.json.JSONObject().put("title", title).put("privacyStatus", privacy)
                .put("videoIds", org.json.JSONArray().put(videoId))
        )
    }

    /** Hapus dari "Tonton nanti". */
    suspend fun removeWatchLater(videoId: String) {
        InnerTube.post(
            Client.WEB, "browse/edit_playlist",
            org.json.JSONObject()
                .put("playlistId", "WL")
                .put(
                    "actions",
                    org.json.JSONArray().put(
                        org.json.JSONObject().put("removedVideoId", videoId).put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID")
                    )
                )
        )
    }

    /** Simpan ke "Tonton nanti". */
    suspend fun saveWatchLater(videoId: String) {
        InnerTube.post(
            Client.WEB, "browse/edit_playlist",
            org.json.JSONObject()
                .put("playlistId", "WL")
                .put(
                    "actions",
                    org.json.JSONArray().put(
                        org.json.JSONObject().put("addedVideoId", videoId).put("action", "ACTION_ADD_VIDEO")
                    )
                )
        )
    }

    suspend fun account(): Account? {
        val a = Parser.account(InnerTube.accountMenu())
        if (a != null) Session.saveAccount(a)
        return a
    }

    // ---------------- YouTube Music ----------------

    suspend fun musicBrowse(browseId: String, params: String? = null): Page {
        val r = InnerTube.browse(Client.MUSIC, browseId, params)
        if (browseId == "FEmusic_home" && params == null) saveRaw("music_home_${Session.loggedIn}", r)
        val header = Parser.musicHeader(r)
        return header.copy(
            chips = Parser.musicChips(r),
            shelves = Parser.musicShelves(r),
            continuation = Parser.continuation(r),
        )
    }

    suspend fun musicMore(token: String): Page {
        val r = InnerTube.continuation(Client.MUSIC, "browse", token)
        val shelves = Parser.musicShelves(r)
        val items = if (shelves.isEmpty()) Parser.items(r, true) else emptyList()
        return Page(shelves = shelves, items = items, continuation = Parser.continuation(r))
    }

    suspend fun musicSearch(query: String, params: String? = null): Page {
        val r = InnerTube.search(Client.MUSIC, query, params)
        return Page(shelves = Parser.musicShelves(r), chips = Parser.musicSearchChips(r), continuation = Parser.continuation(r))
    }

    suspend fun musicSearchMore(token: String): Page {
        val r = InnerTube.continuation(Client.MUSIC, "search", token)
        val shelves = Parser.musicShelves(r)
        return Page(shelves = shelves, items = if (shelves.isEmpty()) Parser.items(r, true) else emptyList(), continuation = Parser.continuation(r))
    }

    /** Antrean radio/playlist untuk sebuah lagu. */
    suspend fun musicQueue(videoId: String, playlistId: String? = null): MusicQueue {
        val pid = playlistId ?: "RDAMVM$videoId"
        return Parser.musicQueue(InnerTube.next(Client.MUSIC, videoId, pid))
    }

    suspend fun lyrics(browseId: String): String? =
        Parser.lyrics(InnerTube.browse(Client.MUSIC, browseId))
}
