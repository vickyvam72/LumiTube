package com.lumitube.app.data

import com.lumitube.app.data.InnerTube.Client

/** Fungsi tingkat tinggi yang dipakai UI. */
object Repo {

    // ---------------- YouTube ----------------

    suspend fun ytHome(): Page {
        val r = InnerTube.browse(Client.WEB, "FEwhat_to_watch")
        return Page(
            chips = Parser.ytChips(r),
            items = Parser.items(r, false),
            continuation = Parser.continuation(r),
        )
    }

    suspend fun ytMore(token: String, endpoint: String = "browse"): Page {
        val r = InnerTube.continuation(Client.WEB, endpoint, token)
        return Page(items = Parser.items(r, false), continuation = Parser.continuation(r))
    }

    suspend fun ytSearch(query: String): Page {
        val r = InnerTube.search(Client.WEB, query)
        return Page(items = Parser.items(r, false), continuation = Parser.continuation(r))
    }

    suspend fun ytShorts(): Page {
        val r = InnerTube.search(Client.WEB, "#shorts")
        val list = Parser.items(r, false).filter { it.type == ItemType.SHORT }
        return Page(items = list, continuation = Parser.continuation(r))
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
        return header.copy(items = Parser.items(r, false), continuation = Parser.continuation(r))
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
            org.json.JSONObject().put("videoIds", org.json.JSONArray().put(videoId)).put("excludeWatchLater", true)
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
        InnerTube.post(
            Client.WEB, "browse/edit_playlist",
            org.json.JSONObject().put("playlistId", playlistId).put("actions", org.json.JSONArray().put(action))
        )
    }

    /**
     * Pindahkan item playlist: diletakkan setelah [afterSetVideoId], atau ke paling atas
     * (sebelum [beforeSetVideoId]) bila [afterSetVideoId] null.
     */
    suspend fun movePlaylistItem(playlistId: String, setVideoId: String, afterSetVideoId: String?, beforeSetVideoId: String?) {
        val action = org.json.JSONObject().put("setVideoId", setVideoId)
        if (afterSetVideoId != null) {
            action.put("action", "ACTION_MOVE_VIDEO_AFTER").put("movedSetVideoIdPredecessor", afterSetVideoId)
        } else {
            action.put("action", "ACTION_MOVE_VIDEO_BEFORE")
            beforeSetVideoId?.let { action.put("movedSetVideoIdSuccessor", it) }
        }
        InnerTube.post(
            Client.WEB, "browse/edit_playlist",
            org.json.JSONObject().put("playlistId", playlistId.removePrefix("VL")).put("actions", org.json.JSONArray().put(action))
        )
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

    suspend fun musicSearch(query: String): Page {
        val r = InnerTube.search(Client.MUSIC, query)
        return Page(shelves = Parser.musicShelves(r))
    }

    /** Antrean radio/playlist untuk sebuah lagu. */
    suspend fun musicQueue(videoId: String, playlistId: String? = null): MusicQueue {
        val pid = playlistId ?: "RDAMVM$videoId"
        return Parser.musicQueue(InnerTube.next(Client.MUSIC, videoId, pid))
    }

    suspend fun lyrics(browseId: String): String? =
        Parser.lyrics(InnerTube.browse(Client.MUSIC, browseId))
}
