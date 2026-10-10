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
        return header.copy(items = Parser.items(r, false), continuation = Parser.continuation(r))
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
