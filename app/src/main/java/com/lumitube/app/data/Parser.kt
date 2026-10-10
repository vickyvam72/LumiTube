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
                duration = duration,
                channelName = channel,
                channelId = channelId,
                channelThumb = chThumb,
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
        val badge = findFirst(image, "thumbnailBadgeViewModel")?.str("text")
        val channelId = findFirst(meta.obj("image"), "browseEndpoint")?.str("browseId")
        return when {
            type.contains("VIDEO") -> Item(
                type = ItemType.VIDEO,
                id = id,
                title = title,
                subtitle = rows.joinToString(" • "),
                thumbnail = thumb ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                duration = badge,
                channelName = rows.firstOrNull(),
                channelId = channelId,
                channelThumb = avatar,
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
                channelName = artistRun?.str("text") ?: cols.getOrNull(1)?.substringBefore(" • "),
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
                channelName = subtitle.substringBefore(" • "),
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
            return Item(ItemType.SONG, it, title, subtitle, thumb, channelName = subtitle.substringAfter(" • ", subtitle))
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
        return WatchInfo(
            title = text(primary.obj("title")),
            views = text(primary.obj("viewCount", "videoViewCountRenderer", "shortViewCount"))
                ?: text(primary.obj("viewCount", "videoViewCountRenderer", "viewCount")),
            date = text(primary.obj("relativeDateText")) ?: text(primary.obj("dateText")),
            channel = text(owner.obj("title")),
            channelId = owner.str("navigationEndpoint", "browseEndpoint", "browseId"),
            channelThumb = bestThumb(owner.obj("thumbnail")),
            subscribers = text(owner.obj("subscriberCountText")),
            likes = likes,
            description = secondary.str("attributedDescription", "content") ?: text(secondary.obj("description")),
            related = related,
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

    fun account(root: JSONObject): Account? {
        val h = findFirst(root, "activeAccountHeaderRenderer") ?: return null
        val name = text(h.obj("accountName")) ?: return null
        return Account(name, text(h.obj("channelHandle")), bestThumb(h.obj("accountPhoto")))
    }
}
