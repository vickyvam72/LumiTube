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
        if (item.type != ItemType.VIDEO && item.type != ItemType.SHORT) return emptyList()
        return listOf(
            "Tambahkan ke playlist" to { addTarget = item },
            "Download" to { downloadTarget = item },
            "Bagikan" to { shareVideo(ctx, item.id, item.title, false) },
            "Dengarkan di YT Music Lumi" to { CrossApp.openOther(ctx, item.copy(type = ItemType.SONG)) },
        )
    }
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
