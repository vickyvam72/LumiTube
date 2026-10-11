package com.lumitube.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser generik: menelusuri respons InnerTube dan mengenali renderer yang dikenal.
 * Pendekatan "telusuri semua" membuatnya lebih tahan terhadap perubahan struktur halaman.
 */
object Parser {

    private val videoKeys = setOf(
        "videoRenderer", "compactVideoRenderer", "gridVideoRenderer",
        "playlistVideoRenderer", "videoWithContextRenderer", "playlistPanelVideoRenderer",
    )

    fun items(root: Any?, music: Boolean): List<Item> {
        val out = mutableListOf<Item>()
        val seen = HashSet<String>()
        walk(root) { k, v ->
            val item: Item? = when (k) {
                in videoKeys -> video(v, music)
                "lockupViewModel" -> lockup(v)
                "reelItemRenderer" -> reel(v)
                "shortsLockupViewModel" -> shortsLockup(v)
                "channelRenderer", "gridChannelRenderer" -> channel(v)
                "playlistRenderer", "gridPlaylistRenderer" -> playlist(v)
                "musicResponsiveListItemRenderer" -> musicListItem(v)
                "musicTwoRowItemRenderer" -> musicTwoRow(v)
                "musicCardShelfRenderer" -> {
                    musicCard(v)?.let { if (seen.add(it.key)) out.add(it) }
                    for (i in items(v.arr("contents"), true)) if (seen.add(i.key)) out.add(i)
                    null
                }
                "feedFilterChipBarRenderer", "chipCloudRenderer", "chipBarViewModel" -> null
                else -> return@walk false
            }
            if (item != null && seen.add(item.key)) out.add(item)
            true
        }
        return out
    }

    // ---------------- YouTube ----------------

    private fun video(v: JSONObject, music: Boolean): Item? {
        val id = v.str("videoId") ?: return null
        val title = text(v.obj("title")) ?: text(v.obj("headline")) ?: return null
        val byline = v.obj("longBylineText") ?: v.obj("ownerText") ?: v.obj("shortBylineText")
        val channel = text(v.obj("shortBylineText")) ?: text(byline)
        val channelId = byline.str("runs", 0, "navigationEndpoint", "browseEndpoint", "browseId")
        val views = text(v.obj("shortViewCountText")) ?: text(v.obj("viewCountText"))
        val published = text(v.obj("publishedTimeText"))
        val duration = text(v.obj("lengthText"))
            ?: findFirst(v.arr("thumbnailOverlays"), "thumbnailOverlayTimeStatusRenderer")
                ?.let { text(it.obj("text")) }
        val isShort = v.obj("navigationEndpoint", "reelWatchEndpoint") != null
        val overlay = findFirst(v.arr("thumbnailOverlays"), "thumbnailOverlayTimeStatusRenderer")
        val live = overlay?.str("style") == "LIVE" ||
            v.arr("badges")?.objects()?.any { it.str("metadataBadgeRenderer", "style")?.contains("LIVE_NOW") == true } == true
        val chThumb = bestThumb(findFirst(v.obj("channelThumbnailSupportedRenderers"), "thumbnail"))
            ?: bestThumb(v.obj("channelThumbnail"))
        return if (music) {
            Item(
                type = ItemType.SONG,
                id = id,
                title = title,
                subtitle = text(v.obj("longBylineText")) ?: channel ?: "",
                thumbnail = musicThumb(v.obj("thumbnail")) ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                duration = duration,
                channelName = channel,
            )
        } else {
            Item(
                type = if (isShort) ItemType.SHORT else ItemType.VIDEO,
                id = id,
                title = title,
                subtitle = listOfNotNull(channel, views, published).joinToString(" • "),
                thumbnail = bestThumb(v.obj("thumbnail")) ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                duration = if (live) null else duration,
                channelName = channel,
                channelId = channelId,
                channelThumb = chThumb,
                live = live,
            )
        }
    }

    private fun lockup(v: JSONObject): Item? {
        val id = v.str("contentId") ?: return null
        val type = v.str("contentType") ?: ""
        val meta = v.obj("metadata", "lockupMetadataViewModel") ?: return null
        val title = meta.str("title", "content") ?: return null
        val rows = meta.arr("metadata", "contentMetadataViewModel", "metadataRows")?.objects()
            ?.map { row ->
                row.arr("metadataParts")?.objects()?.mapNotNull { it.str("text", "content") }
                    ?.joinToString(" • ") ?: ""
            }?.filter { it.isNotBlank() } ?: emptyList()
        val image = v.obj("contentImage")
        val thumb = bestThumb(findFirst(image, "image"))
        val avatar = bestThumb(findFirst(meta.obj("image"), "image"))
        val badgeVm = findFirst(image, "thumbnailBadgeViewModel")
        val badge = badgeVm?.str("text")
        val live = badgeVm?.str("badgeStyle")?.contains("LIVE") == true ||
            badge?.trim()?.uppercase() in setOf("LIVE", "LANGSUNG", "EN DIRECT", "EN VIVO")
        val channelId = findFirst(meta.obj("image"), "browseEndpoint")?.str("browseId")
        return when {
            type.contains("VIDEO") -> Item(
                type = ItemType.VIDEO,
                id = id,
                title = title,
                subtitle = rows.joinToString(" • "),
                thumbnail = thumb ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                duration = if (live) null else badge,
                channelName = rows.firstOrNull(),
                channelId = channelId,
                channelThumb = avatar,
                live = live,
            )
            type.contains("PLAYLIST") || type.contains("ALBUM") || type.contains("PODCAST") -> {
                if (id.startsWith("RD")) return null
                Item(
                    type = ItemType.PLAYLIST,
                    id = "VL$id",
                    title = title,
                    subtitle = rows.joinToString(" • "),
                    thumbnail = thumb,
                    duration = badge,
                    playlistId = id,
                )
            }
            type.contains("CHANNEL") -> Item(ItemType.CHANNEL, id, title, rows.joinToString(" • "), thumb ?: avatar)
            else -> null
        }
    }

    private fun reel(v: JSONObject): Item? {
        val id = v.str("videoId") ?: return null
        return Item(
            type = ItemType.SHORT,
            id = id,
            title = text(v.obj("headline")) ?: "",
            subtitle = text(v.obj("viewCountText")) ?: "",
            thumbnail = bestThumb(v.obj("thumbnail")) ?: "https://i.ytimg.com/vi/$id/hq2.jpg",
        )
    }

    private fun shortsLockup(v: JSONObject): Item? {
        val id = v.str("onTap", "innertubeCommand", "reelWatchEndpoint", "videoId")
            ?: v.str("entityId")?.removePrefix("shorts-shelf-item-")
            ?: return null
        return Item(
            type = ItemType.SHORT,
            id = id,
            title = v.str("overlayMetadata", "primaryText", "content") ?: "",
            subtitle = v.str("overlayMetadata", "secondaryText", "content") ?: "",
            thumbnail = bestThumb(v.obj("thumbnail")) ?: "https://i.ytimg.com/vi/$id/hq2.jpg",
        )
    }

    private fun channel(v: JSONObject): Item? {
        val id = v.str("channelId") ?: return null
        val title = text(v.obj("title")) ?: return null
        val sub = listOfNotNull(
            text(v.obj("subscriberCountText")),
            text(v.obj("videoCountText")),
        ).joinToString(" • ")
        return Item(ItemType.CHANNEL, id, title, sub, bestThumb(v.obj("thumbnail")))
    }

    private fun playlist(v: JSONObject): Item? {
        val pid = v.str("playlistId") ?: return null
        val title = text(v.obj("title")) ?: return null
        val thumb = bestThumb(v.arr("thumbnails")?.optJSONObject(0)) ?: bestThumb(v.obj("thumbnail"))
        val count = text(v.obj("videoCountText")) ?: v.str("videoCount")?.let { "$it video" }
        val owner = text(v.obj("shortBylineText")) ?: text(v.obj("longBylineText"))
        return Item(
            type = ItemType.PLAYLIST,
            id = "VL$pid",
            title = title,
            subtitle = listOfNotNull(owner, count).joinToString(" • "),
            thumbnail = thumb,
            duration = count,
            playlistId = pid,
        )
    }

    // ---------------- YouTube Music ----------------

    private val typeLabels = setOf(
        "lagu", "song", "video", "single", "album", "ep", "episode", "podcast", "playlist", "artis", "artist",
        "video musik", "music video",
    )

    /** Ambil nama artis dari subtitle "Lagu • Taylor Swift • 3:55" (lewati label tipe). */
    fun artistFromSubtitle(sub: String): String? =
        sub.split(" • ").map { it.trim() }.firstOrNull { seg ->
            seg.isNotEmpty() && seg.lowercase() !in typeLabels &&
                !Regex("^\\d+(:\\d+)+$").matches(seg) && !seg.contains("x diputar") && !seg.contains("views") &&
                !seg.contains("penayangan") && !seg.contains("plays")
        }

    private fun pageTypeOf(browse: JSONObject?): String? =
        browse.str("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType")

    private fun typeForPage(pageType: String?): ItemType = when {
        pageType == null -> ItemType.PLAYLIST
        pageType.contains("ALBUM") -> ItemType.ALBUM
        pageType.contains("ARTIST") || pageType.contains("USER_CHANNEL") -> ItemType.ARTIST
        else -> ItemType.PLAYLIST
    }

    private fun musicListItem(v: JSONObject): Item? {
        val cols = v.arr("flexColumns")?.objects()
            ?.map { text(it.obj("musicResponsiveListItemFlexColumnRenderer", "text")) ?: "" }
            ?: return null
        val title = cols.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val subtitle = cols.drop(1).filter { it.isNotBlank() }.joinToString(" • ")
        val thumb = musicThumb(v.obj("thumbnail", "musicThumbnailRenderer", "thumbnail"))
        val duration = v.arr("fixedColumns")?.objects()?.firstOrNull()
            ?.let { text(it.obj("musicResponsiveListItemFixedColumnRenderer", "text")) }
        val videoId = v.str("playlistItemData", "videoId")
            ?: v.str(
                "flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text", "runs", 0,
                "navigationEndpoint", "watchEndpoint", "videoId"
            )
            ?: findFirst(v.obj("overlay"), "watchEndpoint")?.str("videoId")
        val browse = v.obj("navigationEndpoint", "browseEndpoint")
        val artistRun = v.arr(
            "flexColumns", 1, "musicResponsiveListItemFlexColumnRenderer", "text", "runs"
        )?.objects()?.firstOrNull {
            it.str("navigationEndpoint", "browseEndpoint", "browseId")?.startsWith("UC") == true
        }
        if (browse != null) {
            val type = typeForPage(pageTypeOf(browse))
            return Item(
                type = type,
                id = browse.str("browseId") ?: return null,
                title = title,
                subtitle = subtitle,
                thumbnail = thumb,
                row = true,
            )
        }
        if (videoId != null) {
            return Item(
                type = ItemType.SONG,
                id = videoId,
                title = title,
                subtitle = subtitle,
                thumbnail = thumb,
                duration = duration,
                channelName = artistRun?.str("text") ?: artistFromSubtitle(subtitle),
                channelId = artistRun.str("navigationEndpoint", "browseEndpoint", "browseId"),
                row = true,
            )
        }
        return null
    }

    private fun musicTwoRow(v: JSONObject): Item? {
        val title = text(v.obj("title")) ?: return null
        val subtitle = text(v.obj("subtitle")) ?: ""
        val thumb = musicThumb(v.obj("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail"))
        val wide = v.str("aspectRatio")?.contains("16_9") == true
        val nav = v.obj("navigationEndpoint")
        nav.obj("watchEndpoint")?.let { w ->
            val id = w.str("videoId") ?: return null
            return Item(
                ItemType.SONG, id, title, subtitle, thumb,
                channelName = artistFromSubtitle(subtitle),
                playlistId = w.str("playlistId"), wide = wide
            )
        }
        nav.obj("browseEndpoint")?.let { b ->
            val id = b.str("browseId") ?: return null
            return Item(typeForPage(pageTypeOf(b)), id, title, subtitle, thumb, wide = wide)
        }
        nav.obj("watchPlaylistEndpoint")?.let { w ->
            val pid = w.str("playlistId") ?: return null
            return Item(ItemType.PLAYLIST, "VL$pid", title, subtitle, thumb, playlistId = pid, wide = wide)
        }
        return null
    }

    private fun musicCard(v: JSONObject): Item? {
        val title = text(v.obj("title")) ?: return null
        val subtitle = text(v.obj("subtitle")) ?: ""
        val thumb = musicThumb(v.obj("thumbnail", "musicThumbnailRenderer", "thumbnail"))
        val nav = v.obj("title", "runs", 0, "navigationEndpoint") ?: v.obj("onTap")
        nav.obj("watchEndpoint")?.str("videoId")?.let {
            return Item(ItemType.SONG, it, title, subtitle, thumb, channelName = artistFromSubtitle(subtitle))
        }
        nav.obj("browseEndpoint")?.let { b ->
            val id = b.str("browseId") ?: return null
            return Item(typeForPage(pageTypeOf(b)), id, title, subtitle, thumb)
        }
        return null
    }

    // ---------------- Struktur halaman ----------------

    fun musicShelves(root: Any?): List<Shelf> {
        val out = mutableListOf<Shelf>()
        walk(root) { k, v ->
            when (k) {
                "musicCarouselShelfRenderer" -> {
                    val h = v.obj("header", "musicCarouselShelfBasicHeaderRenderer")
                    val more = h.obj("moreContentButton", "buttonRenderer", "navigationEndpoint", "browseEndpoint")
                        ?: h.obj("title", "runs", 0, "navigationEndpoint", "browseEndpoint")
                    out.add(
                        Shelf(
                            title = text(h.obj("title")) ?: "",
                            strapline = text(h.obj("strapline")),
                            items = items(v.arr("contents"), true),
                            moreBrowseId = more.str("browseId"),
                            moreParams = more.str("params"),
                        )
                    )
                    true
                }
                "musicImmersiveCarouselShelfRenderer" -> {
                    val h = v.obj("header", "musicImmersiveCarouselShelfBasicHeaderRenderer")
                    out.add(Shelf(text(h.obj("title")) ?: "", text(h.obj("strapline")), items(v.arr("contents"), true)))
                    true
                }
                "musicShelfRenderer" -> {
                    out.add(Shelf(text(v.obj("title")) ?: "", null, items(v.arr("contents"), true)))
                    true
                }
                "musicPlaylistShelfRenderer" -> {
                    out.add(Shelf("", null, items(v.arr("contents"), true)))
                    true
                }
                "musicCardShelfRenderer" -> {
                    val card = musicCard(v)
                    out.add(Shelf(text(v.obj("header", "musicCardShelfHeaderBasicRenderer", "title")) ?: "Hasil teratas", null, listOfNotNull(card) + items(v.arr("contents"), true)))
                    true
                }
                "gridRenderer" -> {
                    out.add(Shelf(text(v.obj("header", "gridHeaderRenderer", "title")) ?: "", null, items(v.arr("items"), true)))
                    true
                }
                "header", "musicResponsiveHeaderRenderer", "musicImmersiveHeaderRenderer" -> true
                else -> false
            }
        }
        return out.filter { it.items.isNotEmpty() }
    }

    fun ytChips(root: Any?): List<Chip> {
        val out = mutableListOf<Chip>()
        walk(root) { k, v ->
            when (k) {
                "chipCloudChipRenderer" -> {
                    val t = text(v.obj("text"))
                    if (t != null) {
                        out.add(
                            Chip(
                                title = t,
                                continuation = v.str("navigationEndpoint", "continuationCommand", "token"),
                                selected = v.optBoolean("isSelected", false),
                            )
                        )
                    }
                    true
                }
                "chipViewModel" -> {
                    val t = v.str("text")
                    if (t != null) {
                        out.add(
                            Chip(
                                title = t,
                                continuation = findFirst(v, "continuationCommand")?.str("token"),
                                selected = v.str("selected") == "true" || v.optBoolean("selected", false),
                            )
                        )
                    }
                    true
                }
                else -> false
            }
        }
        return out
    }

    fun musicChips(root: Any?): List<Chip> {
        val chips = findAll(root, "chipCloudChipRenderer").mapNotNull { v ->
            val t = text(v.obj("text")) ?: return@mapNotNull null
            val b = v.obj("navigationEndpoint", "browseEndpoint")
            Chip(t, b.str("browseId"), b.str("params"), selected = v.optBoolean("isSelected", false))
        }
        val buttons = findAll(root, "musicNavigationButtonRenderer").mapNotNull { v ->
            val t = text(v.obj("buttonText")) ?: return@mapNotNull null
            val b = v.obj("clickCommand", "browseEndpoint") ?: return@mapNotNull null
            Chip(t, b.str("browseId"), b.str("params"))
        }
        return chips + buttons
    }

    private val skipForContinuation = setOf(
        "feedFilterChipBarRenderer", "chipCloudRenderer", "chipCloudChipRenderer", "chipViewModel",
        "sortFilterSubMenuRenderer", "header", "itemSectionRenderer_comments",
    )

    /** Token lanjutan terakhir (biasanya di ujung daftar). */
    fun continuation(root: Any?): String? {
        var last: String? = null
        walk(root) { k, v ->
            when {
                k in skipForContinuation -> true
                k == "continuationCommand" -> {
                    v.str("token")?.let { last = "b:$it" }
                    true
                }
                k == "nextContinuationData" -> {
                    v.str("continuation")?.let { last = "q:$it" }
                    true
                }
                else -> false
            }
        }
        return last
    }

    fun ytHeader(root: JSONObject): Page {
        val ch = root.obj("metadata", "channelMetadataRenderer")
        val pl = root.obj("metadata", "playlistMetadataRenderer")
        val headerVm = findFirst(root.obj("header"), "pageHeaderViewModel")
        val title = ch.str("title") ?: pl.str("title")
            ?: headerVm.str("title", "dynamicTextViewModel", "text", "content")
            ?: text(findFirst(root.obj("header"), "playlistHeaderRenderer")?.obj("title"))
        val subtitle = headerVm?.let { h ->
            h.arr("metadata", "contentMetadataViewModel", "metadataRows")?.objects()
                ?.flatMap { row -> row.arr("metadataParts")?.objects()?.mapNotNull { it.str("text", "content") } ?: emptyList() }
                ?.joinToString(" • ")
        }
        val thumb = bestThumb(ch.obj("avatar"))
            ?: bestThumb(findFirst(headerVm.obj("image"), "image"))
            ?: bestThumb(findFirst(root.obj("header"), "heroImage"))
        return Page(
            title = title,
            subtitle = subtitle,
            description = ch.str("description") ?: pl.str("description"),
            thumbnail = thumb,
        )
    }

    fun musicHeader(root: JSONObject): Page {
        val h = findFirst(root, "musicResponsiveHeaderRenderer")
            ?: findFirst(root, "musicImmersiveHeaderRenderer")
            ?: findFirst(root, "musicDetailHeaderRenderer")
            ?: findFirst(root, "musicVisualHeaderRenderer")
            ?: return Page()
        val subtitle = listOfNotNull(
            text(h.obj("straplineTextOne")),
            text(h.obj("subtitle")),
            text(h.obj("secondSubtitle")),
        ).joinToString(" • ")
        val desc = text(findFirst(h, "musicDescriptionShelfRenderer")?.obj("description"))
            ?: text(h.obj("description"))
        val thumb = musicThumb(findFirst(h.obj("thumbnail"), "thumbnail"))
            ?: musicThumb(h.obj("thumbnail"))
            ?: musicThumb(findFirst(h.obj("foregroundThumbnail"), "thumbnail"))
        return Page(title = text(h.obj("title")), subtitle = subtitle, description = desc, thumbnail = thumb)
    }

    fun watchInfo(next: JSONObject): WatchInfo {
        val primary = findFirst(next, "videoPrimaryInfoRenderer")
        val secondary = findFirst(next, "videoSecondaryInfoRenderer")
        val owner = secondary.obj("owner", "videoOwnerRenderer")
        val secondaryResults = next.obj("contents", "twoColumnWatchNextResults", "secondaryResults")
        val related = items(secondaryResults ?: JSONArray(), false)
            .filter { it.type == ItemType.VIDEO || it.type == ItemType.PLAYLIST || it.type == ItemType.SHORT }
        val likes = findFirst(primary, "likeButtonViewModel")?.let { findFirst(it, "buttonViewModel")?.str("title") }
        val likeStatus = findFirst(primary, "likeStatusEntity")?.str("likeStatus")
            ?: findFirst(next.obj("frameworkUpdates"), "likeStatusEntity")?.str("likeStatus")
        val subscribed = secondary.obj("subscribeButton", "subscribeButtonRenderer")?.optBoolean("subscribed", false)
            ?: findFirst(next.obj("frameworkUpdates"), "subscriptionStateEntity")?.optBoolean("subscribed", false)
            ?: false
        var commentsToken: String? = null
        findAll(next.obj("contents"), "itemSectionRenderer").forEach { sec ->
            if (sec.str("sectionIdentifier") == "comment-item-section") {
                commentsToken = findFirst(sec, "continuationCommand")?.str("token")
            }
        }
        if (commentsToken == null) {
            findAll(next.arr("engagementPanels"), "engagementPanelSectionListRenderer").forEach { p ->
                if (commentsToken == null && p.str("panelIdentifier")?.contains("comment") == true) {
                    commentsToken = findFirst(p.obj("content"), "continuationCommand")?.str("token")
                }
            }
        }
        val commentCount = findAll(next.arr("engagementPanels"), "engagementPanelSectionListRenderer")
            .firstOrNull { it.str("panelIdentifier")?.contains("comment") == true }
            ?.let { text(findFirst(it.obj("header"), "contextualInfo")) }
        return WatchInfo(
            title = text(primary.obj("title")),
            views = text(primary.obj("viewCount", "videoViewCountRenderer", "shortViewCount"))
                ?: text(primary.obj("viewCount", "videoViewCountRenderer", "viewCount")),
            date = text(primary.obj("relativeDateText")) ?: text(primary.obj("dateText")),
            channel = text(owner.obj("title")),
            // Video kolaborasi: data channel ada di dalam dialog/avatar stack, cari lebih dalam.
            channelId = owner.str("navigationEndpoint", "browseEndpoint", "browseId")
                ?: findFirst(owner, "browseEndpoint")?.str("browseId")?.takeIf { it.startsWith("UC") },
            channelThumb = bestThumb(owner.obj("thumbnail"))
                ?: findFirst(owner, "avatarViewModel")?.let { bestThumb(it.obj("image")) }
                ?: bestThumb(findFirst(owner, "thumbnail")),
            subscribers = text(owner.obj("subscriberCountText")),
            likes = likes,
            description = secondary.str("attributedDescription", "content") ?: text(secondary.obj("description")),
            related = related,
            subscribed = subscribed,
            liked = likeStatus == "LIKE",
            commentsToken = commentsToken,
            commentCount = commentCount,
            descriptionLinks = descriptionLinks(secondary.obj("attributedDescription")),
            liveChatToken = findFirst(next.obj("contents"), "liveChatRenderer")?.let { lc ->
                findFirst(lc.arr("continuations"), "reloadContinuationData")?.str("continuation")
            },
            isLive = primary.obj("viewCount", "videoViewCountRenderer")?.optBoolean("isLive", false) == true,
            chapters = chapters(next),
        )
    }

    fun musicQueue(next: JSONObject): MusicQueue {
        val panel = findFirst(next, "playlistPanelRenderer")
        val items = items(panel?.arr("contents"), true)
        var lyricsId: String? = null
        findAll(next, "tabRenderer").forEach { tab ->
            val id = tab.str("endpoint", "browseEndpoint", "browseId")
            if (id != null && id.startsWith("MPLYt")) lyricsId = id
        }
        return MusicQueue(items, lyricsId)
    }

    fun lyrics(root: JSONObject): String? =
        text(findFirst(root, "musicDescriptionShelfRenderer")?.obj("description"))

    /** Komentar format baru: commentViewModel (urutan) + commentEntityPayload (isi). */
    fun comments(root: JSONObject): CommentPage {
        val payloads = HashMap<String, JSONObject>()
        root.arr("frameworkUpdates", "entityBatchUpdate", "mutations")?.objects()?.forEach { m ->
            val p = m.obj("payload", "commentEntityPayload")
            val key = m.str("entityKey") ?: p.str("key")
            if (p != null && key != null) payloads[key] = p
        }
        val out = mutableListOf<Comment>()
        var count: String? = null
        var nextToken: String? = null
        var sorts: List<Pair<String, String>> = emptyList()
        var sortSelected = 0
        var createParams: String? = null
        val actions = root.arr("onResponseReceivedEndpoints")?.objects() ?: emptyList()
        for (a in actions) {
            val items = (a.obj("reloadContinuationItemsCommand") ?: a.obj("appendContinuationItemsAction"))
                ?.arr("continuationItems")?.objects() ?: continue
            for (it in items) {
                it.obj("commentsHeaderRenderer")?.let { h ->
                    count = text(h.obj("countText")) ?: count
                    val subs = findFirst(h.obj("sortMenu"), "sortFilterSubMenuRenderer")?.arr("subMenuItems")?.objects().orEmpty()
                    sorts = subs.mapNotNull { m ->
                        val t = m.str("title") ?: return@mapNotNull null
                        val tok = findFirst(m.obj("serviceEndpoint"), "continuationCommand")?.str("token") ?: return@mapNotNull null
                        t to tok
                    }
                    sortSelected = subs.indexOfFirst { m -> m.optBoolean("selected", false) }.coerceAtLeast(0)
                    createParams = findFirst(h.obj("createRenderer"), "createCommentEndpoint")?.str("createCommentParams")
                }
                it.obj("continuationItemRenderer")?.let { c ->
                    nextToken = findFirst(c, "continuationCommand")?.str("token")
                }
                val thread = it.obj("commentThreadRenderer")
                val vmOuter = thread?.obj("commentViewModel") ?: it.obj("commentViewModel")
                val vm = vmOuter?.obj("commentViewModel") ?: vmOuter ?: continue
                val key = vm.str("commentKey") ?: continue
                val p = payloads[key] ?: continue
                val repliesToken = thread?.obj("replies")?.let { findFirst(it, "continuationCommand")?.str("token") }
                out.add(
                    Comment(
                        id = p.str("properties", "commentId") ?: key,
                        author = p.str("author", "displayName") ?: "",
                        authorId = p.str("author", "channelId"),
                        avatar = p.str("author", "avatarThumbnailUrl"),
                        text = p.str("properties", "content", "content") ?: "",
                        published = p.str("properties", "publishedTime") ?: "",
                        likes = p.str("toolbar", "likeCountNotliked")?.trim() ?: "",
                        replyCount = p.str("toolbar", "replyCount")?.trim() ?: "",
                        repliesToken = repliesToken,
                        isCreator = p.obj("author")?.optBoolean("isCreator", false) == true,
                        isReply = (p.obj("properties")?.optInt("replyLevel", 0) ?: 0) > 0,
                        pinned = vm.str("pinnedText"),
                    )
                )
            }
        }
        return CommentPage(out, nextToken, count, sorts, sortSelected, createParams)
    }

    fun account(root: JSONObject): Account? {
        val h = findFirst(root, "activeAccountHeaderRenderer") ?: return null
        val name = text(h.obj("accountName")) ?: return null
        return Account(name, text(h.obj("channelHandle")), bestThumb(h.obj("accountPhoto")))
    }

    /** Ambil tautan asli dari attributedDescription.commandRuns (teks yang tampil sering terpotong "..."). */
    private fun descriptionLinks(desc: JSONObject?): List<Triple<Int, Int, String>> {
        val runs = desc?.arr("commandRuns")?.objects() ?: return emptyList()
        return runs.mapNotNull { r ->
            val start = r.optInt("startIndex", -1)
            val len = r.optInt("length", 0)
            if (start < 0 || len <= 0) return@mapNotNull null
            val cmd = r.obj("onTap", "innertubeCommand") ?: return@mapNotNull null
            val target = cmd.str("urlEndpoint", "url")?.let { u ->
                if (u.contains("youtube.com/redirect")) {
                    Regex("[?&]q=([^&]+)").find(u)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: u
                } else if (u.startsWith("/")) "https://www.youtube.com$u" else u
            }
                ?: cmd.obj("watchEndpoint")?.let { w -> if (w.has("startTimeSeconds")) "t:" + w.optInt("startTimeSeconds") else "https://www.youtube.com/watch?v=" + w.optString("videoId") }
                ?: cmd.str("browseEndpoint", "browseId")?.let { "c:$it" }
                ?: return@mapNotNull null
            Triple(start, len, target)
        }
    }

    /** Satu halaman live chat (pesan baru + token berikutnya). */
    fun liveChat(r: JSONObject): LiveChatPage {
        val c = r.obj("continuationContents", "liveChatContinuation") ?: return LiveChatPage(emptyList(), null, 5000, null, null)
        val msgs = c.arr("actions")?.objects()?.mapNotNull { a ->
            val item = a.obj("addChatItemAction", "item") ?: a.obj("replayChatItemAction")?.let { findFirst(it, "item") }
                ?: return@mapNotNull null
            val m = item.obj("liveChatTextMessageRenderer") ?: item.obj("liveChatPaidMessageRenderer")
                ?: return@mapNotNull null
            val textOut = buildString {
                m.arr("message", "runs")?.objects()?.forEach { run ->
                    val t = run.str("text")
                    if (t != null) append(t) else run.obj("emoji")?.let { e ->
                        if (e.optBoolean("isCustomEmoji")) append(e.arr("shortcuts")?.optString(0) ?: "") else append(e.str("emojiId") ?: "")
                    }
                }
            }
            ChatMessage(
                id = m.str("id") ?: return@mapNotNull null,
                author = text(m.obj("authorName")) ?: "",
                photo = bestThumb(m.obj("authorPhoto")),
                text = textOut,
                paid = text(m.obj("purchaseAmountText")),
                member = m.arr("authorBadges")?.toString()?.contains("member", ignoreCase = true) == true,
            )
        } ?: emptyList()
        val cont = c.arr("continuations")?.objects()?.firstOrNull()
        val data = cont?.let { it.obj("invalidationContinuationData") ?: it.obj("timedContinuationData") ?: it.obj("reloadContinuationData") }
        val panel = c.obj("actionPanel")
        return LiveChatPage(
            messages = msgs,
            continuation = data?.str("continuation"),
            timeoutMs = data?.optLong("timeoutMs", 5000) ?: 5000,
            sendParams = findFirst(panel, "sendLiveChatMessageEndpoint")?.str("params"),
            notice = panel?.obj("liveChatRestrictedParticipationRenderer")?.let { text(it.obj("message")) },
        )
    }

    fun findFirstPublic(root: Any?, key: String): JSONObject? = findFirst(root, key)

    fun findAllPublic(root: Any?, key: String): List<JSONObject> = findAll(root, key)

    /** Bab video (garis putus-putus di timeline). */
    private fun chapters(next: JSONObject): List<Pair<Long, String>> {
        val list = findAll(next, "chapterRenderer").mapNotNull { c ->
            val start = c.optLong("timeRangeStartMillis", -1)
            if (start < 0) null else start to (text(c.obj("title")) ?: "")
        }.ifEmpty {
            findAll(next.arr("engagementPanels"), "macroMarkersListItemRenderer").mapNotNull { m ->
                val sec = findFirst(m.obj("onTap"), "watchEndpoint")?.optInt("startTimeSeconds", -1) ?: -1
                if (sec < 0) null else sec * 1000L to (text(m.obj("title")) ?: "")
            }
        }
        return list.distinctBy { it.first }.sortedBy { it.first }.takeIf { it.size >= 2 } ?: emptyList()
    }
}
