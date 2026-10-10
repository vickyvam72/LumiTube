package com.lumitube.app.data

enum class ItemType { VIDEO, SHORT, SONG, ALBUM, PLAYLIST, ARTIST, CHANNEL }

data class Item(
    val type: ItemType,
    /** videoId untuk VIDEO/SHORT/SONG, browseId untuk tipe lain. */
    val id: String,
    val title: String,
    val subtitle: String = "",
    val thumbnail: String? = null,
    val duration: String? = null,
    val channelName: String? = null,
    val channelId: String? = null,
    val channelThumb: String? = null,
    val playlistId: String? = null,
    val wide: Boolean = false,
    /** true bila berasal dari baris daftar (musicResponsiveListItemRenderer). */
    val row: Boolean = false,
    /** Lokasi file hasil download (content:// atau file://), jika diputar offline. */
    val localUri: String? = null,
) {
    val isPlayable: Boolean get() = type == ItemType.VIDEO || type == ItemType.SHORT || type == ItemType.SONG
    val key: String get() = "${type.name}:$id"
}

data class Shelf(
    val title: String,
    val strapline: String? = null,
    val items: List<Item>,
    val moreBrowseId: String? = null,
    val moreParams: String? = null,
)

data class Chip(
    val title: String,
    val browseId: String? = null,
    val params: String? = null,
    val continuation: String? = null,
    val selected: Boolean = false,
)

data class Page(
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val thumbnail: String? = null,
    val chips: List<Chip> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val items: List<Item> = emptyList(),
    val continuation: String? = null,
)

data class Account(val name: String, val handle: String?, val photo: String?)

data class WatchInfo(
    val title: String?,
    val views: String?,
    val date: String?,
    val channel: String?,
    val channelId: String?,
    val channelThumb: String?,
    val subscribers: String?,
    val likes: String?,
    val description: String?,
    val related: List<Item>,
    val subscribed: Boolean = false,
    val liked: Boolean = false,
    val commentsToken: String? = null,
    val commentCount: String? = null,
)

data class Comment(
    val id: String,
    val author: String,
    val authorId: String?,
    val avatar: String?,
    val text: String,
    val published: String,
    val likes: String,
    val replyCount: String,
    val repliesToken: String?,
    val isCreator: Boolean,
    val isReply: Boolean,
)

data class CommentPage(val comments: List<Comment>, val next: String?, val count: String?)

data class MusicQueue(val items: List<Item>, val lyricsBrowseId: String?)
