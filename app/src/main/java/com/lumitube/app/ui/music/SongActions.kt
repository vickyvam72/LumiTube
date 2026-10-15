package com.lumitube.app.ui.music

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lumitube.app.data.Downloader
import com.lumitube.app.data.Item
import com.lumitube.app.data.ItemType
import com.lumitube.app.data.Repo
import com.lumitube.app.ui.common.AddToPlaylistSheet
import com.lumitube.app.ui.common.DownloadDialog
import com.lumitube.app.ui.common.EditScope
import com.lumitube.app.ui.common.MusicPanelColors
import com.lumitube.app.ui.common.shareVideo
import kotlinx.coroutines.launch

/**
 * Menu titik tiga YT Music Lumi:
 * - lagu: Tambahkan ke playlist, Download, Bagikan;
 * - playlist / album (misalnya di hasil pencarian): Tambahkan ke playlist (seluruh isinya),
 *   Download (semua lagu), Bagikan.
 * Panel/dialognya digambar oleh [SongActionsHost].
 */
object SongActions {
    var addTarget by mutableStateOf<Item?>(null)
    var downloadTarget by mutableStateOf<Item?>(null)
    /** Isi playlist/album yang akan disimpan ke playlist lain. */
    var addMany by mutableStateOf<List<Item>?>(null)

    @Composable
    fun menuFor(item: Item): List<Pair<String, () -> Unit>> {
        val ctx = LocalContext.current
        return when (item.type) {
            ItemType.SONG, ItemType.VIDEO -> listOf(
                "Tambahkan ke playlist" to { addTarget = item },
                "Download" to { downloadTarget = item },
                "Bagikan" to { shareVideo(ctx, item.id, item.title, true) },
            )
            ItemType.PLAYLIST, ItemType.ALBUM -> listOf(
                "Tambahkan ke playlist" to { addCollection(ctx, item) },
                "Download" to { downloadCollection(ctx, item) },
                "Bagikan" to { shareCollection(ctx, item) },
            )
            else -> emptyList()
        }
    }

    private class Collection(val songs: List<Item>, val title: String?, val author: String?, val thumb: String?)

    /** Semua lagu playlist/album, termasuk halaman berikutnya. */
    private suspend fun load(item: Item): Collection {
        val browseId = when {
            item.type == ItemType.ALBUM -> item.id
            item.id.startsWith("VL") || item.id.startsWith("MPRE") -> item.id
            else -> "VL" + (item.playlistId ?: item.id)
        }
        val page = Repo.musicBrowse(browseId)
        val all = ArrayList<Item>()
        fun take(p: com.lumitube.app.data.Page) {
            all += p.items.filter { it.type == ItemType.SONG } + p.shelves.flatMap { sh -> sh.items.filter { it.type == ItemType.SONG } }
        }
        take(page)
        var c = page.continuation
        try {
            while (c != null) {
                val p = Repo.musicMore(c)
                take(p)
                c = p.continuation
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
        // Playlist: hanya lagu milik playlist (punya setVideoId), bukan lagu saran di bawahnya.
        val inPlaylist = all.filter { it.setVideoId != null }
        val base = if (browseId.startsWith("VL") && inPlaylist.isNotEmpty()) inPlaylist else all
        val author = page.subtitle?.split(" • ")?.map { it.trim() }?.firstOrNull { p ->
            p.isNotBlank() && p !in setOf("Playlist", "Album", "Single", "EP") &&
                !Regex("^\\d[\\d.,]*\\s").containsMatchIn(p) && !Regex("^\\d{4}$").matches(p)
        }
        val thumb = page.thumbnail ?: item.thumbnail
        return Collection(
            base.distinctBy { it.id }.map { it.copy(thumbnail = it.thumbnail ?: thumb) },
            page.title ?: item.title, author ?: item.channelName, thumb,
        )
    }

    private fun addCollection(ctx: Context, item: Item) {
        val app = ctx.applicationContext
        Toast.makeText(app, "Memuat lagu…", Toast.LENGTH_SHORT).show()
        EditScope.scope.launch {
            try {
                val c = load(item)
                if (c.songs.isEmpty()) Toast.makeText(app, "Tidak ada lagu", Toast.LENGTH_SHORT).show()
                else addMany = c.songs
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(app, "Gagal memuat lagu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadCollection(ctx: Context, item: Item) {
        val app = ctx.applicationContext
        Toast.makeText(app, "Memuat lagu…", Toast.LENGTH_SHORT).show()
        EditScope.scope.launch {
            try {
                val c = load(item)
                if (c.songs.isEmpty()) {
                    Toast.makeText(app, "Tidak ada lagu", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val todo = c.songs.count { !Downloader.isDownloaded(it.id, true) }
                val pid = if (item.type == ItemType.ALBUM || item.id.startsWith("VL") || item.id.startsWith("MPRE")) item.id
                else "VL" + (item.playlistId ?: item.id)
                Downloader.downloadAll(c.songs, audioOnly = true, playlistId = pid, playlistTitle = c.title, playlistAuthor = c.author)
                Toast.makeText(
                    app,
                    if (todo == 0) "Semua lagu sudah di-download" else "Mendownload $todo lagu + lirik…",
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(app, "Gagal memuat lagu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareCollection(ctx: Context, item: Item) {
        val url = when {
            item.type == ItemType.ALBUM || item.id.startsWith("MPRE") ->
                item.playlistId?.let { "https://music.youtube.com/playlist?list=$it" }
                    ?: "https://music.youtube.com/browse/${item.id}"
            else -> "https://music.youtube.com/playlist?list=" + (item.playlistId ?: item.id.removePrefix("VL"))
        }
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${item.title}\n$url")
        ctx.startActivity(Intent.createChooser(intent, "Bagikan"))
    }
}

@Composable
fun SongActionsHost() {
    SongActions.addTarget?.let { item ->
        AddToPlaylistSheet(item.id, music = true, colors = MusicPanelColors) { SongActions.addTarget = null }
    }
    SongActions.addMany?.let { songs ->
        AddToPlaylistSheet(songs.first().id, music = true, colors = MusicPanelColors, videoIds = songs.map { it.id }) {
            SongActions.addMany = null
        }
    }
    SongActions.downloadTarget?.let { item ->
        DownloadDialog(item.copy(type = ItemType.SONG)) { SongActions.downloadTarget = null }
    }
}
