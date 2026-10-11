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
    /** Siaran langsung yang sedang berlangsung. */
    val live: Boolean = false,
    /** Bagian yang sudah ditonton (0..100), dari Histori / lanjutkan menonton. */
    val progress: Int? = null,
    /** ID item di dalam playlist (setVideoId), untuk mengubah urutan. */
    val setVideoId: String? = null,
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
    /** Video saran di bawah playlist (bukan isi playlist). */
    val suggestions: List<Item> = emptyList(),
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
    /** Tautan di deskripsi: (awal, panjang, target). Target "http..", "t:<detik>", atau "c:<browseId>". */
    val descriptionLinks: List<Triple<Int, Int, String>> = emptyList(),
    /** Siaran langsung: token untuk live chat (pengganti kolom komentar). */
    val liveChatToken: String? = null,
    val isLive: Boolean = false,
    /** Bab video dari kreator: (mulai ms, judul). */
    val chapters: List<Pair<Long, String>> = emptyList(),
)

/** Lembar gambar pratinjau (storyboard) untuk menggeser timeline. */
data class Storyboard(
    val template: String,
    val width: Int,
    val height: Int,
    val count: Int,
    val cols: Int,
    val rows: Int,
    val intervalMs: Long,
) {
    /** URL lembar + posisi gambar (x, y) untuk waktu [timeMs]. */
    fun frame(timeMs: Long): Triple<String, Int, Int> {
        val idx = (timeMs / intervalMs.coerceAtLeast(1)).toInt().coerceIn(0, (count - 1).coerceAtLeast(0))
        val per = (cols * rows).coerceAtLeast(1)
        val within = idx % per
        return Triple(template.replace("\$M", (idx / per).toString()), (within % cols) * width, (within / cols) * height)
    }

    companion object {
        /** spec: "url|w#h#count#cols#rows#interval#name#sigh|..." -> pakai level terbesar. */
        fun parse(spec: String?, durationMs: Long): Storyboard? {
            if (spec.isNullOrBlank()) return null
            val parts = spec.split("|")
            if (parts.size < 2) return null
            val base = parts[0]
            val level = parts.size - 2
            val f = parts.last().split("#")
            if (f.size < 8) return null
            val w = f[0].toIntOrNull() ?: return null
            val h = f[1].toIntOrNull() ?: return null
            val count = f[2].toIntOrNull() ?: return null
            val cols = f[3].toIntOrNull() ?: return null
            val rows = f[4].toIntOrNull() ?: return null
            var interval = f[5].toLongOrNull() ?: 0L
            if (interval <= 0 && count > 0) interval = durationMs / count
            val url = base.replace("\$L", level.toString()).replace("\$N", f[6]) + "&sigh=" + f[7]
            return Storyboard(url, w, h, count, cols, rows, interval)
        }
    }
}

data class ChatMessage(
    val id: String,
    val author: String,
    val photo: String?,
    val text: String,
    /** Super Chat: nominal, mis. "Rp20.000". */
    val paid: String? = null,
    val member: Boolean = false,
)

data class LiveChatPage(
    val messages: List<ChatMessage>,
    val continuation: String?,
    val timeoutMs: Long,
    val sendParams: String?,
    /** Pesan pembatasan, mis. "Mode khusus subscriber...". */
    val notice: String?,
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
    val pinned: String? = null,
)

data class CommentPage(
    val comments: List<Comment>,
    val next: String?,
    val count: String?,
    /** Pilihan urutan (judul, token), mis. Teratas / Terbaru. */
    val sorts: List<Pair<String, String>> = emptyList(),
    val sortSelected: Int = 0,
    val createParams: String? = null,
)

data class MusicQueue(val items: List<Item>, val lyricsBrowseId: String?)

/** Playlist milik akun untuk dialog "Simpan ke playlist". */
data class PlaylistOption(
    val id: String,
    val title: String,
    val count: String?,
    val thumbnail: String?,
    /** Video ini sudah ada di playlist tersebut. */
    val contains: Boolean,
)
