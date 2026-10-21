package com.lumitube.app.ui.youtube

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lumitube.app.CrossApp
import com.lumitube.app.data.Item
import com.lumitube.app.data.ItemType
import com.lumitube.app.ui.common.AddToPlaylistSheet
import com.lumitube.app.ui.common.DownloadDialog
import com.lumitube.app.ui.common.ytPanelColors
import com.lumitube.app.ui.common.shareVideo
import kotlinx.coroutines.launch

/**
 * Menu titik tiga di setiap video (Beranda, pencarian, channel, …):
 * Tambahkan ke playlist, Download, Bagikan, Dengarkan di YT Music Lumi.
 * Panel/dialognya digambar oleh [VideoActionsHost] di atas seluruh layar.
 */
object VideoActions {
    var addTarget by mutableStateOf<Item?>(null)
    var downloadTarget by mutableStateOf<Item?>(null)

    @Composable
    fun menuFor(item: Item): List<Pair<String, () -> Unit>> {
        val ctx = LocalContext.current
        if (item.type == ItemType.PLAYLIST) {
            // Playlist (Koleksi, pencarian, channel): download semua video & bagikan, seperti YT Music Lumi.
            return listOf(
                "Download" to { downloadPlaylist(ctx, item) },
                "Bagikan" to { sharePlaylist(ctx, item) },
            )
        }
        if (item.type != ItemType.VIDEO && item.type != ItemType.SHORT) return emptyList()
        return listOf(
            "Tambahkan ke playlist" to { addTarget = item },
            "Download" to { downloadTarget = item },
            "Bagikan" to { shareVideo(ctx, item.id, item.title, false) },
            "Dengarkan di YT Music Lumi" to { CrossApp.openOther(ctx, item.copy(type = ItemType.SONG)) },
        )
    }
}

private fun downloadPlaylist(ctx: android.content.Context, item: Item) {
    val app = ctx.applicationContext
    val browseId = if (item.id.startsWith("VL")) item.id else "VL" + (item.playlistId ?: item.id)
    com.lumitube.app.ui.common.LumiToast.makeText(app, "Memuat video…", android.widget.Toast.LENGTH_SHORT).show()
    com.lumitube.app.ui.common.EditScope.scope.launch {
        try {
            val first = com.lumitube.app.data.Repo.ytBrowse(browseId)
            val all = first.items.toMutableList()
            var c = first.continuation
            while (c != null) {
                val p = com.lumitube.app.data.Repo.ytMore(c)
                all += p.items
                c = p.continuation
            }
            val videos = all.filter { it.type == ItemType.VIDEO }.distinctBy { it.id }
            if (videos.isEmpty()) {
                com.lumitube.app.ui.common.LumiToast.makeText(app, "Tidak ada format yang bisa diunduh.", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val todo = videos.count { !com.lumitube.app.data.Downloader.isDownloaded(it.id, false) }
            com.lumitube.app.data.Downloader.downloadAll(
                videos, audioOnly = false, playlistId = browseId,
                playlistTitle = first.title ?: item.title, playlistAuthor = first.subtitle?.substringBefore(" • "),
            )
            com.lumitube.app.ui.common.LumiToast.makeText(
                app,
                if (todo == 0) "Semua video sudah di-download" else com.lumitube.app.tr("Mendownload %s video… (lihat di Download)", todo),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            com.lumitube.app.ui.common.LumiToast.makeText(app, "Gagal memuat playlist", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

private fun sharePlaylist(ctx: android.content.Context, item: Item) {
    val pid = item.playlistId ?: item.id.removePrefix("VL")
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_TEXT, "${item.title}\nhttps://www.youtube.com/playlist?list=$pid")
    ctx.startActivity(android.content.Intent.createChooser(intent, com.lumitube.app.tr("Bagikan")))
}

@Composable
fun VideoActionsHost() {
    VideoActions.addTarget?.let { item ->
        AddToPlaylistSheet(item.id, music = false, colors = ytPanelColors()) { VideoActions.addTarget = null }
    }
    VideoActions.downloadTarget?.let { item ->
        DownloadDialog(item) { VideoActions.downloadTarget = null }
    }
}
