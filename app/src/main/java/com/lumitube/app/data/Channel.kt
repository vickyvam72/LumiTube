package com.lumitube.app.data

import org.json.JSONObject

/** Tab di halaman channel (Beranda, Video, Shorts, Rilis, Playlist, Postingan, …). */
data class ChannelTab(
    val title: String,
    val params: String?,
    val selected: Boolean,
    /** Jenis tab dari URL-nya (videos, shorts, streams, …), tidak bergantung bahasa. */
    val kind: String = "",
)

data class ChannelPost(
    val id: String,
    val author: String,
    val authorThumb: String?,
    val published: String,
    val text: String,
    /** Rentang [start, end) di [text] yang berupa link, beserta url tujuannya. */
    val links: List<Triple<Int, Int, String>>,
    val images: List<String>,
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

/** Filter urutan di tab Video/Shorts/Live: Terbaru, Populer, Terlama. */
data class ChannelChip(val title: String, val token: String, val selected: Boolean)

data class ChannelTabPage(
    val items: List<Item>,
    val posts: List<ChannelPost>,
    val continuation: String?,
    val chips: List<ChannelChip> = emptyList(),
)

/** Teks dari runs + rentang link (urlEndpoint / watchEndpoint). */
fun linkedText(node: Any?): Pair<String, List<Triple<Int, Int, String>>> {
    val runs = node.arr("runs")?.objects() ?: return (text(node) ?: "") to emptyList()
    val sb = StringBuilder()
    val links = mutableListOf<Triple<Int, Int, String>>()
    for (r in runs) {
        val t = r.str("text") ?: continue
        val start = sb.length
        sb.append(t)
        val ep = r.obj("navigationEndpoint")
        val url = ep.str("urlEndpoint", "url")?.let { realUrl(it) }
            ?: ep.str("watchEndpoint", "videoId")?.let { "https://www.youtube.com/watch?v=$it" }
            ?: ep.str("commandMetadata", "webCommandMetadata", "url")?.let { if (it.startsWith("/")) "https://www.youtube.com$it" else it }
        if (url != null) links.add(Triple(start, sb.length, url))
    }
    return sb.toString() to links
}

/** "youtube.com/redirect?…&q=<url>" → url aslinya. */
fun realUrl(u: String): String {
    if (!u.contains("/redirect")) return if (u.startsWith("/")) "https://www.youtube.com$u" else u
    val q = Regex("[?&]q=([^&]+)").find(u)?.groupValues?.get(1) ?: return u
    return try { java.net.URLDecoder.decode(q, "UTF-8") } catch (_: Exception) { u }
}

object ChannelRepo {
    private val keepKinds = setOf("videos", "shorts", "streams", "releases", "playlists", "posts", "community", "podcasts")
    private val keepTabs = listOf("Video", "Videos", "Shorts", "Live", "Rilis", "Releases", "Playlist", "Playlists", "Postingan", "Posts", "Komunitas", "Community", "Podcast", "Podcasts")

    /** Halaman channel: header lengkap + daftar tab + isi tab pertama (Beranda). */
    /** Parameter tab "Video" — halaman channel langsung dibuka di tab Video (tanpa Beranda). */
    private const val VIDEOS_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"

    suspend fun channel(channelId: String): Pair<ChannelInfo, ChannelTabPage> {
        val r = try {
            InnerTube.browse(InnerTube.Client.WEB, channelId, VIDEOS_PARAMS)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            InnerTube.browse(InnerTube.Client.WEB, channelId)
        }
        val info = parseInfo(channelId, r)
        val page = parseTab(r)
        // Channel konten anak (YouTube Kids) kadang kosong bila dibuka dengan akun: buka tanpa akun.
        if (Session.loggedIn && (info.tabs.isEmpty() || (page.items.isEmpty() && page.posts.isEmpty()))) {
            try {
                val r2 = InnerTube.browse(InnerTube.Client.WEB, channelId, VIDEOS_PARAMS, auth = false)
                val info2 = parseInfo(channelId, r2)
                val page2 = parseTab(r2)
                if (info2.tabs.isNotEmpty() || page2.items.isNotEmpty()) {
                    anonymous.add(channelId)
                    // Status subscribe tetap dari akun.
                    return info2.copy(subscribed = info.subscribed) to page2
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
        return info to page
    }

    /** Channel yang isinya hanya tampil bila dibuka tanpa akun. */
    private val anonymous = java.util.Collections.synchronizedSet(HashSet<String>())

    suspend fun tab(channelId: String, params: String?): ChannelTabPage {
        val r = InnerTube.browse(InnerTube.Client.WEB, channelId, params, auth = channelId !in anonymous)
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
            val url = t.obj("endpoint", "commandMetadata", "webCommandMetadata").str("url") ?: ""
            val kind = url.substringBefore('?').trimEnd('/').substringAfterLast('/').lowercase()
            ChannelTab(tt, t.obj("endpoint", "browseEndpoint").str("params"), t.optBoolean("selected"), kind)
        }.filter { tab ->
            if (tab.kind.isNotEmpty() && !tab.kind.startsWith("@") && !tab.kind.startsWith("uc")) tab.kind in keepKinds
            else keepTabs.any { it.equals(tab.title, ignoreCase = true) }
        }
        // Status subscribe: entitas subscriptionState (format baru) atau tombol lama.
        val subscribed = subscriptionState(id, h, r)
            ?: findFirst(c4, "subscribeButtonRenderer")?.optBoolean("subscribed")
            ?: findFirst(h, "subscribeButtonViewModel")?.let { findFirst(it, "subscribeButtonContent")?.optBoolean("isSubscribed") }
            ?: false
        return ChannelInfo(id, title, handle, avatar, banner, stats, description, link, aboutToken, tabs, subscribed)
    }

    /**
     * Respons channel bisa memuat banyak subscriptionStateEntity (channel lain di Beranda).
     * Pilih yang kuncinya cocok dengan tombol subscribe di header, atau yang kuncinya memuat ID channel.
     */
    private fun subscriptionState(id: String, header: JSONObject?, r: JSONObject): Boolean? {
        val fu = r.obj("frameworkUpdates")
        val muts = findAll(fu, "entityBatchUpdate").flatMap { it.arr("mutations")?.objects() ?: emptyList() }
            .mapNotNull { m -> m.obj("payload", "subscriptionStateEntity")?.let { m.str("entityKey") to it } }
            .ifEmpty { findAll(fu, "subscriptionStateEntity").map { it.str("key") to it } }
        if (muts.isEmpty()) return null
        val wanted = findFirst(header, "subscribeButtonViewModel")?.let { vm ->
            vm.str("stateEntityStoreKey") ?: findFirst(vm, "subscribeButtonContent")?.str("subscribeStateEntityStoreKey")
        }
        fun norm(k: String?) = k?.replace("%3D", "=")?.trimEnd('=')
        if (wanted != null) {
            muts.firstOrNull { norm(it.first) == norm(wanted) || norm(it.second.str("key")) == norm(wanted) }
                ?.let { return it.second.optBoolean("subscribed") }
        }
        // Kunci entitas = protobuf base64 yang berisi ID channel.
        for ((k, e) in muts) {
            val key = norm(k ?: e.str("key")) ?: continue
            val raw = try {
                String(android.util.Base64.decode(key.replace('-', '+').replace('_', '/'), android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP), Charsets.ISO_8859_1)
            } catch (_: Exception) { "" }
            if (raw.contains(id)) return e.optBoolean("subscribed")
        }
        return if (muts.size == 1) muts[0].second.optBoolean("subscribed") else null
    }

    private fun parseChips(content: Any?): List<ChannelChip> {
        val a = findAll(content, "chipCloudChipRenderer").mapNotNull { c ->
            val t = text(c.obj("text")) ?: return@mapNotNull null
            val tok = findFirst(c.obj("navigationEndpoint"), "continuationCommand")?.str("token") ?: return@mapNotNull null
            ChannelChip(t, tok, c.optBoolean("isSelected"))
        }
        if (a.isNotEmpty()) return a
        return findAll(content, "chipViewModel").mapNotNull { c ->
            val t = c.str("text") ?: return@mapNotNull null
            val tok = findFirst(c.obj("tapCommand"), "continuationCommand")?.str("token") ?: return@mapNotNull null
            ChannelChip(t, tok, c.optBoolean("selected"))
        }
    }

    private fun parseTab(r: JSONObject): ChannelTabPage {
        // Isi tab yang sedang dipilih saja (bukan tab lain).
        val content = findAll(r.obj("contents"), "tabRenderer").firstOrNull { it.optBoolean("selected") }?.obj("content")
            ?: r.obj("onResponseReceivedActions") ?: r
        val posts = (findAll(content, "backstagePostRenderer") + findAll(content, "postRenderer")).mapNotNull { p ->
            val pid = p.str("postId") ?: return@mapNotNull null
            val (body, links) = linkedText(p.obj("contentText"))
            val att = p.obj("backstageAttachment")
            val images = findAll(att, "backstageImageRenderer").mapNotNull { bestThumb(it.obj("image")) }
                .ifEmpty { listOfNotNull(bestThumb(findFirst(att, "image"))) }
            ChannelPost(
                id = pid,
                author = text(p.obj("authorText")) ?: "",
                authorThumb = bestThumb(p.obj("authorThumbnail")),
                published = text(p.obj("publishedTimeText")) ?: "",
                text = body,
                links = links,
                images = images,
                likes = text(p.obj("voteCount")),
            )
        }
        val items = if (posts.isNotEmpty()) emptyList() else Parser.items(content, false)
        return ChannelTabPage(items, posts, Parser.continuation(content) ?: Parser.continuation(r), parseChips(content))
    }
}
