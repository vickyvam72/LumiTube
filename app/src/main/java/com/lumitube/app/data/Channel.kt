package com.lumitube.app.data

import org.json.JSONObject

/** Tab di halaman channel (Beranda, Video, Shorts, Rilis, Playlist, Postingan, …). */
data class ChannelTab(val title: String, val params: String?, val selected: Boolean)

data class ChannelPost(
    val id: String,
    val author: String,
    val authorThumb: String?,
    val published: String,
    val text: String,
    val image: String?,
    val likes: String?,
)

data class ChannelInfo(
    val id: String,
    val title: String,
    val handle: String?,
    val avatar: String?,
    val banner: String?,
    /** "30,7 rb subscriber • 897 video" */
    val stats: String?,
    val description: String?,
    val link: String?,
    val aboutToken: String?,
    val tabs: List<ChannelTab>,
    val subscribed: Boolean,
)

data class ChannelAbout(
    val description: String?,
    val links: List<Triple<String, String, String?>>, // judul, url, ikon
    val url: String?,
    val country: String?,
    val joined: String?,
    val views: String?,
    val subscribers: String?,
    val videos: String?,
)

data class ChannelTabPage(val items: List<Item>, val posts: List<ChannelPost>, val continuation: String?)

object ChannelRepo {
    private val keepTabs = listOf("Beranda", "Home", "Video", "Videos", "Shorts", "Live", "Rilis", "Releases", "Playlist", "Playlists", "Postingan", "Posts", "Komunitas", "Community", "Podcast", "Podcasts")

    /** Halaman channel: header lengkap + daftar tab + isi tab pertama (Beranda). */
    suspend fun channel(channelId: String): Pair<ChannelInfo, ChannelTabPage> {
        val r = InnerTube.browse(InnerTube.Client.WEB, channelId)
        return parseInfo(channelId, r) to parseTab(r)
    }

    suspend fun tab(channelId: String, params: String?): ChannelTabPage {
        val r = InnerTube.browse(InnerTube.Client.WEB, channelId, params)
        return parseTab(r)
    }

    suspend fun more(token: String): ChannelTabPage {
        val r = InnerTube.continuation(InnerTube.Client.WEB, "browse", token)
        return parseTab(r)
    }

    suspend fun about(token: String): ChannelAbout? {
        val r = InnerTube.continuation(InnerTube.Client.WEB, "browse", token)
        val a = findFirst(r, "aboutChannelViewModel") ?: return null
        val links = findAll(a, "channelExternalLinkViewModel").mapNotNull { l ->
            val title = text(l.obj("title")) ?: return@mapNotNull null
            val url = text(l.obj("link")) ?: return@mapNotNull null
            Triple(title, url, bestThumb(l.obj("favicon")))
        }
        return ChannelAbout(
            description = a.str("description"),
            links = links,
            url = a.str("displayCanonicalChannelUrl") ?: a.str("canonicalChannelUrl"),
            country = a.str("country"),
            joined = text(a.obj("joinedDateText")),
            views = a.str("viewCountText"),
            subscribers = a.str("subscriberCountText"),
            videos = a.str("videoCountText"),
        )
    }

    private fun parseInfo(id: String, r: JSONObject): ChannelInfo {
        val h = findFirst(r.obj("header"), "pageHeaderViewModel")
        val c4 = findFirst(r.obj("header"), "c4TabbedHeaderRenderer")
        val meta = r.obj("metadata", "channelMetadataRenderer")
        val title = h.str("title", "dynamicTextViewModel", "text", "content") ?: meta.str("title") ?: text(c4?.obj("title")) ?: ""
        val rows = h?.arr("metadata", "contentMetadataViewModel", "metadataRows")?.objects()
            ?.map { row -> row.arr("metadataParts")?.objects()?.mapNotNull { it.str("text", "content") } ?: emptyList() }
            ?: emptyList()
        val parts = rows.flatten()
        val handle = parts.firstOrNull { it.startsWith("@") } ?: text(c4?.obj("channelHandleText"))
        val stats = parts.filter { !it.startsWith("@") }.joinToString(" • ").ifBlank {
            listOfNotNull(text(c4?.obj("subscriberCountText")), text(c4?.obj("videosCountText"))).joinToString(" • ")
        }.ifBlank { null }
        val avatar = bestThumb(findFirst(h?.obj("image"), "image")) ?: bestThumb(meta.obj("avatar")) ?: bestThumb(c4?.obj("avatar"))
        val banner = bestThumb(findFirst(h?.obj("banner"), "image")) ?: bestThumb(c4?.obj("banner"))
        val descVm = findFirst(h, "descriptionPreviewViewModel")
        val description = descVm.str("description", "content") ?: meta.str("description")
        val aboutToken = descVm?.let { findFirst(it, "continuationCommand")?.str("token") }
            ?: findFirst(h, "continuationCommand")?.str("token")
        val link = findFirst(h, "attributionViewModel")?.let { text(it.obj("text")) }
            ?: findFirst(h, "linkViewModel")?.let { text(it.obj("text")) }
        val tabs = findAll(r.obj("contents"), "tabRenderer").mapNotNull { t ->
            val tt = t.str("title") ?: return@mapNotNull null
            ChannelTab(tt, t.obj("endpoint", "browseEndpoint").str("params"), t.optBoolean("selected"))
        }.filter { tab -> keepTabs.any { it.equals(tab.title, ignoreCase = true) } }
        // Status subscribe: entitas subscriptionState (format baru) atau tombol lama.
        val subscribed = findFirst(r.obj("frameworkUpdates"), "subscriptionStateEntity")?.optBoolean("subscribed")
            ?: findFirst(c4, "subscribeButtonRenderer")?.optBoolean("subscribed")
            ?: findFirst(h, "subscribeButtonViewModel")?.let { findFirst(it, "subscribeButtonContent")?.optBoolean("isSubscribed") }
            ?: false
        return ChannelInfo(id, title, handle, avatar, banner, stats, description, link, aboutToken, tabs, subscribed)
    }

    private fun parseTab(r: JSONObject): ChannelTabPage {
        // Isi tab yang sedang dipilih saja (bukan tab lain).
        val content = findAll(r.obj("contents"), "tabRenderer").firstOrNull { it.optBoolean("selected") }?.obj("content")
            ?: r.obj("onResponseReceivedActions") ?: r
        val posts = (findAll(content, "backstagePostRenderer") + findAll(content, "postRenderer")).mapNotNull { p ->
            val pid = p.str("postId") ?: return@mapNotNull null
            ChannelPost(
                id = pid,
                author = text(p.obj("authorText")) ?: "",
                authorThumb = bestThumb(p.obj("authorThumbnail")),
                published = text(p.obj("publishedTimeText")) ?: "",
                text = text(p.obj("contentText")) ?: "",
                image = bestThumb(findFirst(p.obj("backstageAttachment"), "image")),
                likes = text(p.obj("voteCount")),
            )
        }
        val items = if (posts.isNotEmpty()) emptyList() else Parser.items(content, false)
        return ChannelTabPage(items, posts, Parser.continuation(content) ?: Parser.continuation(r))
    }
}
