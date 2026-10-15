package com.lumitube.app.ui.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lumitube.app.data.Item
import com.lumitube.app.data.ItemType
import com.lumitube.app.ui.common.AddToPlaylistSheet
import com.lumitube.app.ui.common.DownloadDialog
import com.lumitube.app.ui.common.MusicPanelColors
import com.lumitube.app.ui.common.shareVideo

/**
 * Menu titik tiga di setiap lagu YT Music Lumi (Pilihan cepat, pencarian, playlist, …):
 * Tambahkan ke playlist, Download, Bagikan. Panel/dialognya digambar oleh [SongActionsHost].
 */
object SongActions {
    var addTarget by mutableStateOf<Item?>(null)
    var downloadTarget by mutableStateOf<Item?>(null)

    @Composable
    fun menuFor(item: Item): List<Pair<String, () -> Unit>> {
        val ctx = LocalContext.current
        if (item.type != ItemType.SONG && item.type != ItemType.VIDEO) return emptyList()
        return listOf(
            "Tambahkan ke playlist" to { addTarget = item },
            "Download" to { downloadTarget = item },
            "Bagikan" to { shareVideo(ctx, item.id, item.title, true) },
        )
    }
}

@Composable
fun SongActionsHost() {
    SongActions.addTarget?.let { item ->
        AddToPlaylistSheet(item.id, music = true, colors = MusicPanelColors) { SongActions.addTarget = null }
    }
    SongActions.downloadTarget?.let { item ->
        DownloadDialog(item.copy(type = ItemType.SONG)) { SongActions.downloadTarget = null }
    }
}
