package com.lumitube.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
        items = Parser.items(r, false).filter { !it.membersOnly },
        continuation = Parser.continuation(r),
    )

    /** Lanjutan Beranda (juga chip Beranda): video & Shorts khusus anggota tidak ditampilkan. */
    suspend fun ytHomeMore(token: String): Page {
        val p = ytMore(token)
        return p.copy(items = p.items.filter { !it.membersOnly })
    }

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

    /**
     * Filter "Shorts" di pencarian: hasil asli YouTube hanya satu rak berisi beberapa Shorts,
     * jadi setelah habis dilanjutkan dengan pencarian "<kata> shorts" (diambil Shorts-nya saja).
     * Token: "x:" = mulai pencarian tambahan, "y:" = lanjutan pencarian tambahan.
     */
    suspend fun ytSearchShorts(query: String, chipToken: String, token: String?): Page {
        fun shorts(p: Page) = p.items.filter { it.type == ItemType.SHORT }
        return when {
            token == null -> {
                val p = ytSearchChip(chipToken)
                Page(items = shorts(p), continuation = p.continuation ?: "x:")
            }
            token == "x:" -> {
                val p = ytSearch("$query shorts", auth = false)
                Page(items = shorts(p), continuation = p.continuation?.let { "y:$it" })
            }
            token.startsWith("y:") -> {
                val p = ytMore(token.removePrefix("y:"), "search")
                Page(items = shorts(p), continuation = p.continuation?.let { "y:$it" })
            }
            else -> {
                val p = ytMore(token, "search")
                Page(items = shorts(p), continuation = p.continuation ?: "x:")
            }
        }
    }

    /** Hasil pencarian dengan filter (chip) — token lanjutan dari chip. */
    suspend fun ytSearchChip(token: String): Page {
        val r = InnerTube.continuation(Client.WEB, "search", "b:${token.removePrefix("b:")}")
        return Page(items = Parser.items(r, false), continuation = Parser.continuation(r))
    }

    suspend fun ytSearch(query: String, params: String? = null, auth: Boolean = true): Page {
        val r = InnerTube.search(Client.WEB, query, params, auth)
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
    private var recentShortIds: List<String> = emptyList()

    private suspend fun shortsTaste(): List<String> {
        tasteCache?.let { (t, list) -> if (System.currentTimeMillis() - t < 30 * 60 * 1000L) return list }
        if (!Session.loggedIn) return emptyList()
        val list = try {
            val r = InnerTube.browse(Client.WEB, "FEhistory")
            val items = Parser.items(r, false).take(40)
            recentShortIds = items.filter { it.type == ItemType.SHORT }.map { it.id }.distinct()
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

    /**
     * Feed Shorts memakai jalur rekomendasi asli YouTube (reel_watch_sequence, sama seperti
     * youtube.com/shorts) dengan akun — bukan pencarian, jadi tidak menambah histori pencarian.
     * Titik awalnya Shorts yang baru ditonton (Histori) atau Shorts dari Beranda.
     * Bila gagal, kembali ke cara lama (pencarian selera Histori).
     */
    suspend fun ytShorts(): Page {
        try {
            shortsTaste()
            val home = try {
                ytHomeCached()?.items?.filter { it.type == ItemType.SHORT }?.map { it.id } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val seeds = (recentShortIds.take(5).shuffled() + home.shuffled()).distinct()
            for (seed in seeds.take(3).ifEmpty { listOf("") }) {
                try {
                    val p = reelSequence(seedParams(seed.ifEmpty { null }))
                    if (p.items.size >= 3) return p
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
        return ytShortsBySearch()
    }

    /** Lanjutan feed Shorts: token "r:" = jalur rekomendasi asli, lainnya = pencarian (cara lama). */
    suspend fun ytShortsMore(token: String): Page =
        if (token.startsWith("r:")) reelSequence(token.removePrefix("r:")) else ytMore(token, "search")

    private fun seedParams(videoId: String?): String? {
        if (videoId == null) return null
        val id = videoId.toByteArray()
        val bytes = byteArrayOf(0x0a, id.size.toByte()) + id
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
    }

    private suspend fun reelSequence(sequenceParams: String?): Page {
        val body = org.json.JSONObject()
        if (sequenceParams != null) body.put("sequenceParams", sequenceParams)
        val r = InnerTube.post(Client.WEB, "reel/reel_watch_sequence", body)
        val items = r.arr("entries")?.objects()?.mapNotNull { e ->
            val rw = findFirst(e, "reelWatchEndpoint") ?: return@mapNotNull null
            val id = rw.str("videoId") ?: return@mapNotNull null
            val vd = rw.obj("unserializedPrefetchData", "playerResponse", "videoDetails")
            Item(
                type = ItemType.SHORT,
                id = id,
                title = vd.str("title") ?: "",
                thumbnail = bestThumb(rw.obj("thumbnail")) ?: "https://i.ytimg.com/vi/$id/oar2.jpg",
                channelName = vd.str("author"),
                channelId = vd.str("channelId"),
            )
        }?.distinctBy { it.id } ?: emptyList()
        val tok = findFirst(r.obj("continuationEndpoint"), "continuationCommand")?.str("token")
            ?: r.obj("continuationEndpoint", "continuationCommand").str("token")
        synchronized(servedShorts) { items.forEach { servedShorts.add(it.id) } }
        return Page(items = items, continuation = tok?.let { "r:$it" })
    }

    private suspend fun ytShortsBySearch(): Page {
        // Cara cadangan: pencarian dari selera Histori; tagar umum hanya bila selera kosong.
        val taste = shortsTaste().shuffled().take(3)
        val queries = taste.ifEmpty { listOf(shortQueries.random()) }
        var r: org.json.JSONObject? = null
        val fromSearch = mutableListOf<Item>()
        for (q in queries) {
            try {
                val res = InnerTube.search(Client.WEB, q)
                // Lanjutan diambil dari pencarian selera pertama, bukan tagar acak.
                if (r == null) r = res
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
        val header0 = Parser.ytHeader(r)
        if (browseId == "FEplaylist_aggregation" || browseId.startsWith("VL")) ensureOwned()
        val header = header0.copy(editable = browseId.startsWith("VL") && browseId.removePrefix("VL") in ownedPlaylists)
        if (browseId.startsWith("VL")) {
            // Isi playlist ada di playlistVideoListRenderer; sisanya adalah video saran.
            val list = Parser.findFirstPublic(r, "playlistVideoListRenderer")
            if (list != null) {
                val own = Parser.items(list, false)
                val keys = own.map { it.key }.toHashSet()
                val rest = Parser.items(r, false).filter { it.key !in keys }
                // Lanjutan hanya dari daftar playlist itu sendiri (bukan video saran di bawahnya).
                return header.copy(items = own, continuation = Parser.continuation(list), suggestions = rest)
            }
        }
        if (browseId == "FEhistory") {
            val shelves = historySections(r)
            if (shelves.isNotEmpty()) return header.copy(shelves = shelves, continuation = Parser.continuation(r))
        }
        val items = Parser.items(r, false)
        return header.copy(
            items = if (browseId == "FEplaylist_aggregation") withRecentPlaylists(items) else items,
            continuation = Parser.continuation(r),
        )
    }

    /** Halaman lanjutan Histori: tetap dikelompokkan per hari. */
    suspend fun ytHistoryMore(token: String): Page {
        val r = InnerTube.continuation(Client.WEB, "browse", token)
        val shelves = historySections(r)
        return if (shelves.isNotEmpty()) Page(shelves = shelves, continuation = Parser.continuation(r))
        else Page(items = withHistoryTokens(Parser.items(r, false), historyTokens(r)), continuation = Parser.continuation(r))
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
        val tokens = historyTokens(r)
        return out.map { sh -> sh.copy(items = withHistoryTokens(sh.items, tokens)) }
    }

    /** videoId -> token "Hapus dari histori tontonan" dari menu setiap item Histori. */
    private fun historyTokens(r: org.json.JSONObject): Map<String, String> {
        val out = HashMap<String, String>()
        fun first(node: Any?, key: String): String? {
            when (node) {
                is org.json.JSONObject -> {
                    (node.opt(key) as? String)?.takeIf { it.isNotEmpty() }?.let { return it }
                    for (k in node.keys()) first(node.opt(k), key)?.let { return it }
                }
                is org.json.JSONArray -> for (i in 0 until node.length()) first(node.opt(i), key)?.let { return it }
            }
            return null
        }
        fun visit(node: Any?) {
            when (node) {
                is org.json.JSONObject -> for (k in node.keys()) {
                    val v = node.opt(k)
                    if (k in historyItemKeys && v is org.json.JSONObject) {
                        val id = first(v, "videoId") ?: (v.opt("contentId") as? String)
                        val token = removeToken(v) ?: first(v, "feedbackToken")
                        if (id != null && token != null) out.putIfAbsent(id, token)
                    } else visit(v)
                }
                is org.json.JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
            }
        }
        visit(r)
        return out
    }

    /**
     * Token dari pilihan menu "Hapus dari histori tontonan" (ikon DELETE / teks histori),
     * bukan token feedback lain di menu yang sama.
     */
    private fun removeToken(item: org.json.JSONObject): String? {
        val entryKeys = setOf("menuServiceItemRenderer", "listItemViewModel", "menuNavigationItemRenderer", "menuFlexibleItemRenderer")
        val hint = Regex("DELETE|histor|Hapus|Remove", RegexOption.IGNORE_CASE)
        var best: String? = null
        fun walk(node: Any?, entry: org.json.JSONObject?) {
            if (best != null) return
            when (node) {
                is org.json.JSONObject -> {
                    (node.opt("feedbackToken") as? String)?.takeIf { it.isNotEmpty() }?.let { t ->
                        if (entry != null && hint.containsMatchIn(entry.toString().replace(t, ""))) { best = t; return }
                    }
                    for (k in node.keys()) {
                        val v = node.opt(k)
                        walk(v, if (k in entryKeys && v is org.json.JSONObject) v else entry)
                    }
                }
                is org.json.JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), entry)
            }
        }
        walk(item, null)
        return best
    }

    private fun withHistoryTokens(items: List<Item>, tokens: Map<String, String>) =
        items.map { it.copy(historyToken = tokens[it.id] ?: it.historyToken) }

    /** Hapus satu video/Short dari histori tontonan akun (seperti menu di YouTube asli). */
    suspend fun removeFromHistory(token: String) {
        val r = InnerTube.post(
            Client.WEB, "feedback",
            org.json.JSONObject().put("feedbackTokens", org.json.JSONArray().put(token))
                .put("isFeedbackTokenUnencrypted", false).put("shouldMerge", false),
        )
        val ok = r.optJSONArray("feedbackResponses")?.optJSONObject(0)?.optBoolean("isProcessed", true) ?: true
        if (!ok) throw java.io.IOException("YouTube menolak permintaan")
    }

    /**
     * Hapus satu penelusuran dari histori penelusuran akun, seperti tombol hapus di kotak cari
     * YouTube / YouTube Music asli. Gagal diam-diam bila belum login atau tidak ditemukan.
     */
    suspend fun removeSearchFromAccount(query: String, music: Boolean) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!Session.loggedIn || query.isBlank()) return@withContext
        if (music) {
            // YT Music: saran histori membawa token feedback (sama seperti music.youtube.com).
            val r = InnerTube.post(Client.MUSIC, "music/get_search_suggestions", org.json.JSONObject().put("input", query))
            val tokens = mutableListOf<String>()
            fun textOf(o: org.json.JSONObject?): String {
                val runs = o?.optJSONArray("runs") ?: return o?.optString("simpleText") ?: ""
                return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text") ?: "" }
            }
            fun find(node: Any?, key: String): String? {
                when (node) {
                    is org.json.JSONObject -> {
                        (node.opt(key) as? String)?.let { return it }
                        for (k in node.keys()) find(node.opt(k), key)?.let { return it }
                    }
                    is org.json.JSONArray -> for (i in 0 until node.length()) find(node.opt(i), key)?.let { return it }
                }
                return null
            }
            fun visit(node: Any?) {
                when (node) {
                    is org.json.JSONObject -> for (k in node.keys()) {
                        val v = node.opt(k)
                        if (k == "historySuggestionRenderer" && v is org.json.JSONObject) {
                            val text = textOf(v.optJSONObject("suggestion"))
                            val t = find(v, "feedbackToken")
                            if (t != null && text.trim().equals(query.trim(), ignoreCase = true)) tokens.add(t)
                        } else visit(v)
                    }
                    is org.json.JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
                }
            }
            visit(r)
            if (tokens.isNotEmpty()) {
                InnerTube.post(Client.MUSIC, "feedback", org.json.JSONObject().put("feedbackTokens", org.json.JSONArray(tokens)))
            }
        } else {
            // YouTube: saran berisi histori akun (bila dengan cookie) + token hapus "q".
            val cookie = Session.cookie ?: return@withContext
            val q = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val base = "https://suggestqueries-clients6.youtube.com/complete"
            val common = "client=youtube&ds=yt&hl=${InnerTube.hl}&gl=${InnerTube.gl}"
            fun get(url: String): String = InnerTube.http.newCall(
                okhttp3.Request.Builder().url(url)
                    .header("User-Agent", InnerTube.DESKTOP_UA)
                    .header("Cookie", cookie)
                    .header("Origin", "https://www.youtube.com")
                    .header("Referer", "https://www.youtube.com/")
                    .build()
            ).execute().use { it.body?.string() ?: "" }
            val text = get("$base/search?$common&xhr=t&q=$q")
            val json = text.substring(text.indexOf('[').coerceAtLeast(0), text.lastIndexOf(']') + 1)
            val arr = org.json.JSONArray(json)
            val deltok = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("q")?.takeIf { s -> s.isNotEmpty() } }.firstOrNull()
                ?: return@withContext
            get("$base/deleteitems?$common&delq=$q&deltok=${java.net.URLEncoder.encode(deltok, "UTF-8")}")
        }
    }

    /** "Telusuri histori tontonan". */
    suspend fun ytHistorySearch(query: String): Page {
        val r = InnerTube.post(Client.WEB, "browse", org.json.JSONObject().put("browseId", "FEhistory").put("query", query))
        return Page(items = withHistoryTokens(Parser.items(r, false), historyTokens(r)), continuation = Parser.continuation(r))
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

    private var subsCache: Pair<Long, MutableSet<String>>? = null

    /** Apakah channel ini di-subscribe (daftar channel akun, disimpan 10 menit). */
    suspend fun isSubscribed(channelId: String): Boolean {
        if (!Session.loggedIn) return false
        val c = subsCache
        val set = if (c != null && System.currentTimeMillis() - c.first < 10 * 60 * 1000L) c.second else {
            val ids = mutableSetOf<String>()
            var r = InnerTube.browse(Client.WEB, "FEchannels")
            var pages = 0
            while (true) {
                Parser.items(r, false).filter { it.type == ItemType.CHANNEL }.forEach { ids.add(it.id) }
                val next = Parser.continuation(r) ?: break
                if (++pages >= 8) break
                r = InnerTube.continuation(Client.WEB, "browse", next)
            }
            subsCache = System.currentTimeMillis() to ids
            ids
        }
        return channelId in set
    }

    /** Status dari daftar yang sudah tersimpan (tanpa jaringan), null bila belum ada / kedaluwarsa. */
    fun cachedSubscribed(channelId: String): Boolean? {
        val c = subsCache ?: return null
        if (System.currentTimeMillis() - c.first >= 10 * 60 * 1000L) return null
        return channelId in c.second
    }

    /** Catat perubahan subscribe supaya status di hasil pencarian langsung benar. */
    fun markSubscribed(channelId: String, subscribed: Boolean) {
        subsCache?.second?.let { if (subscribed) it.add(channelId) else it.remove(channelId) }
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
            if (id in recentDeleted) return@mapNotNull null
            ownedPlaylists.add(id)
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

    /** Tambahkan banyak video/lagu sekaligus ke playlist; yang sudah ada dilewati. */
    suspend fun addVideosToPlaylist(playlistId: String, videoIds: List<String>) {
        val pid = playlistId.removePrefix("VL")
        for (chunk in videoIds.distinct().chunked(100)) {
            val actions = org.json.JSONArray()
            chunk.forEach { v ->
                actions.put(
                    org.json.JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", v)
                        .put("dedupeOption", "DEDUPE_OPTION_SKIP")
                )
            }
            var last: Exception? = null
            var ok = false
            for (c in listOf(Client.MUSIC, Client.WEB)) {
                try {
                    val r = InnerTube.post(c, "browse/edit_playlist", org.json.JSONObject().put("playlistId", pid).put("actions", actions))
                    if (r.optString("status") == "STATUS_FAILED") throw java.io.IOException("ditolak YouTube")
                    ok = true
                    break
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (e.message?.contains("409") == true) { ok = true; break }
                    last = e
                }
            }
            if (!ok) throw last ?: java.io.IOException("Gagal")
        }
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
        forgetPlaylist(pid)
    }

    /** Hapus playlist milik akun. [playlistId] tanpa awalan "VL". */
    suspend fun deletePlaylist(playlistId: String) {
        InnerTube.post(Client.WEB, "playlist/delete", org.json.JSONObject().put("playlistId", playlistId.removePrefix("VL")))
        forgetPlaylist(playlistId)
    }

    /**
     * Playlist yang baru dibuat di aplikasi ini. Halaman koleksi YouTube / YT Music kadang baru
     * menampilkan playlist baru beberapa saat kemudian, jadi selama 15 menit playlist ini
     * disisipkan sendiri di daftar Playlist bila belum ada.
     */
    private val recentCreated = java.util.Collections.synchronizedList(mutableListOf<Pair<Long, Item>>())

    /**
     * Playlist yang baru dihapus / dikeluarkan dari koleksi. Server YouTube kadang masih
     * mengembalikannya beberapa saat, jadi disaring sendiri supaya langsung hilang.
     */
    private val recentDeleted = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Playlist milik akun yang sudah diketahui (dari daftar "Simpan ke playlist" & playlist buatan aplikasi). */
    val ownedPlaylists: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun withRecentPlaylists(items: List<Item>): List<Item> {
        val now = System.currentTimeMillis()
        fun pid(i: Item) = i.playlistId ?: i.id.removePrefix("VL")
        val shown = items.filter { pid(it) !in recentDeleted }
        val fresh = synchronized(recentCreated) {
            recentCreated.removeAll { now - it.first > 15 * 60 * 1000L }
            // Sudah muncul di daftar dari server: tidak perlu disisipkan lagi.
            recentCreated.removeAll { r -> shown.any { pid(it) == pid(r.second) } }
            recentCreated.map { it.second }
        }
        val missing = fresh.filter { pid(it) !in recentDeleted }
        return if (missing.isEmpty()) shown else missing + shown
    }

    private var ownedAt = 0L

    /**
     * Daftar playlist milik akun langsung dari YouTube (daftar "Simpan ke playlist" hanya berisi
     * playlist yang bisa diubah pemiliknya). Dipakai untuk menentukan playlist yang boleh dihapus.
     */
    suspend fun ensureOwned(force: Boolean = false) {
        if (!Session.loggedIn) return
        if (!force && System.currentTimeMillis() - ownedAt < 5 * 60 * 1000L) return
        try {
            val r = InnerTube.post(
                Client.WEB, "playlist/get_add_to_playlist",
                org.json.JSONObject().put("videoIds", org.json.JSONArray().put("jNQXAC9IVRw")).put("excludeWatchLater", true)
            )
            val ids = Parser.findAllPublic(r, "playlistAddToOptionRenderer").mapNotNull { it.str("playlistId") }
            if (ids.isNotEmpty()) {
                synchronized(ownedPlaylists) {
                    ownedPlaylists.clear()
                    ownedPlaylists.addAll(ids)
                }
                ownedAt = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    /** Apakah playlist ini masih ada di daftar playlist koleksi YouTube / YT Music (tanpa saringan lokal). */
    suspend fun inLibrary(playlistId: String, music: Boolean): Boolean {
        val pid = playlistId.removePrefix("VL")
        val items = if (music) {
            val r = InnerTube.browse(Client.MUSIC, "FEmusic_liked_playlists", null)
            Parser.musicShelves(r).flatMap { it.items } + Parser.items(r, true)
        } else {
            Parser.items(InnerTube.browse(Client.WEB, "FEplaylist_aggregation", null), false)
        }
        return items.any { (it.playlistId ?: it.id.removePrefix("VL")) == pid }
    }

    /** Batalkan saringan "sudah dihapus" (YouTube ternyata tidak menghapusnya). */
    fun unforgetPlaylist(playlistId: String) {
        recentDeleted.remove(playlistId.removePrefix("VL"))
    }

    private fun forgetPlaylist(playlistId: String) {
        val pid = playlistId.removePrefix("VL")
        recentDeleted.add(pid)
        ownedPlaylists.remove(pid)
        synchronized(recentCreated) { recentCreated.removeAll { (it.second.playlistId ?: it.second.id.removePrefix("VL")) == pid } }
    }

    /** Buat playlist baru langsung berisi video ini. privacy: PRIVATE / UNLISTED / PUBLIC. */
    suspend fun createPlaylist(title: String, privacy: String, videoId: String) = createPlaylist(title, privacy, listOf(videoId))

    /** Buat playlist baru berisi beberapa video/lagu sekaligus (misalnya seluruh isi playlist lain). */
    suspend fun createPlaylist(title: String, privacy: String, videoIds: List<String>) {
        val videoId = videoIds.first()
        val r = InnerTube.post(
            Client.WEB, "playlist/create",
            org.json.JSONObject().put("title", title).put("privacyStatus", privacy)
                .put("videoIds", org.json.JSONArray(videoIds))
        )
        val pid = r.str("playlistId")
        if (pid != null) {
            ownedPlaylists.add(pid)
            recentDeleted.remove(pid)
            val label = when (privacy) { "PUBLIC" -> "Publik"; "UNLISTED" -> "Tidak publik"; else -> "Pribadi" }
            recentCreated.add(
                System.currentTimeMillis() to Item(
                    type = ItemType.PLAYLIST, id = "VL$pid", title = title,
                    subtitle = "$label • ${videoIds.size} video", thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    playlistId = pid,
                )
            )
        }
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
        if (browseId == "FEmusic_liked_playlists" || browseId.startsWith("VL")) ensureOwned()
        if (browseId == "FEmusic_home" && params == null) saveRaw("music_home_${Session.loggedIn}", r)
        val header = Parser.musicHeader(r)
        var shelves = Parser.musicShelves(r)
        if (browseId == "FEmusic_liked_playlists" && shelves.isNotEmpty()) {
            shelves = listOf(shelves[0].copy(items = withRecentPlaylists(shelves[0].items))) + shelves.drop(1)
            // Playlist buatan akun ("Nama akun • 19 lagu") dicatat sebagai milik sendiri.
            if (ownedAt == 0L) Session.account?.name?.takeIf { it.isNotBlank() }?.let { me ->
                shelves.flatMap { it.items }.filter { it.type == ItemType.PLAYLIST && it.subtitle.startsWith(me) }
                    .forEach { ownedPlaylists.add(it.playlistId ?: it.id.removePrefix("VL")) }
            }
        }
        val pid = browseId.removePrefix("VL")
        return header.copy(
            editable = browseId.startsWith("VL") && pid !in setOf("LM", "SE") &&
                (Parser.findFirstPublic(r, "musicEditablePlaylistDetailHeaderRenderer") != null || pid in ownedPlaylists),
            chips = Parser.musicChips(r),
            shelves = shelves,
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
        val r = InnerTube.search(Client.MUSIC, query, params, auth = true)
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

    /**
     * Lirik YT Music yang bergerak (bersinkron per baris), seperti di aplikasi YT Music:
     * diminta sebagai aplikasi Android YT Music (web hanya memberi teks biasa), lalu dengan akun.
     * Hasil: daftar (mulai ms, selesai ms, teks), atau null bila YouTube hanya punya teks biasa.
     */
    suspend fun timedLyrics(browseId: String): List<Triple<Long, Long, String>>? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        fun parse(r: org.json.JSONObject): List<Triple<Long, Long, String>>? {
            var data: org.json.JSONArray? = null
            fun find(node: Any?) {
                if (data != null) return
                when (node) {
                    is org.json.JSONObject -> {
                        (node.opt("timedLyricsData") as? org.json.JSONArray)?.let { data = it; return }
                        for (k in node.keys()) find(node.opt(k))
                    }
                    is org.json.JSONArray -> for (i in 0 until node.length()) find(node.opt(i))
                }
            }
            find(r)
            val arr = data ?: return null
            val out = ArrayList<Triple<Long, Long, String>>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val cue = o.optJSONObject("cueRange") ?: return null
                val start = cue.optString("startTimeMilliseconds").toLongOrNull() ?: return null
                val end = cue.optString("endTimeMilliseconds").toLongOrNull() ?: start
                out.add(Triple(start, end, o.optString("lyricLine")))
            }
            return out.takeIf { it.isNotEmpty() }
        }
        val ver = "8.40.54"
        val ua = "com.google.android.apps.youtube.music/$ver (Linux; U; Android 15) gzip"
        val body = org.json.JSONObject()
            .put("context", org.json.JSONObject().put("client", org.json.JSONObject()
                .put("clientName", "ANDROID_MUSIC").put("clientVersion", ver)
                .put("androidSdkVersion", 35).put("osName", "Android").put("osVersion", "15")
                .put("hl", InnerTube.hl).put("gl", InnerTube.gl)))
            .put("browseId", browseId)
        try {
            val req = okhttp3.Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .header("User-Agent", ua)
                .header("X-YouTube-Client-Name", "21")
                .header("X-YouTube-Client-Version", ver)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val r = InnerTube.http.newCall(req).execute().use { org.json.JSONObject(it.body?.string() ?: "{}") }
            parse(r)?.let { return@withContext it }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
        if (Session.loggedIn) {
            try { parse(InnerTube.browse(Client.MUSIC, browseId))?.let { return@withContext it } } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
        null
    }

    /** ID channel artis sebuah lagu (bila tidak ikut di data lagu), dari antrean YT Music. */
    suspend fun artistIdFor(videoId: String): String? {
        val r = InnerTube.next(Client.MUSIC, videoId)
        var found: String? = null
        fun walk(node: Any?, inSong: Boolean) {
            if (found != null) return
            when (node) {
                is org.json.JSONObject -> {
                    val song = inSong || (node.opt("videoId") == videoId && node.has("longBylineText"))
                    if (song) {
                        val id = node.optJSONObject("browseEndpoint")?.optString("browseId")
                        if (id != null && id.startsWith("UC")) { found = id; return }
                    }
                    for (k in node.keys()) walk(node.opt(k), song)
                }
                is org.json.JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), inSong)
            }
        }
        walk(r, false)
        return found
    }
}
