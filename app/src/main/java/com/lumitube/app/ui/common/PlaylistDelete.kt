package com.lumitube.app.ui.common

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import com.lumitube.app.data.Item
import com.lumitube.app.data.ItemType
import com.lumitube.app.data.Repo
import com.lumitube.app.data.Session
import kotlinx.coroutines.launch

/**
 * Menu titik tiga "Hapus playlist" untuk daftar playlist (Koleksi / Library).
 * [deleted] berisi id playlist yang sudah dihapus supaya langsung hilang dari daftar.
 */
class PlaylistDeleter(
    val deleted: SnapshotStateList<String>,
    private val ask: (Item) -> Unit,
) {
    /** Playlist yang mungkin milik akun (bukan "Video/Lagu yang disukai", "Tonton nanti", mix, atau album). */
    fun canDelete(item: Item): Boolean {
        if (!Session.loggedIn || item.type != ItemType.PLAYLIST) return false
        val id = item.id.removePrefix("VL")
        if (id in setOf("LL", "LM", "WL", "SE")) return false
        return id.startsWith("PL")
    }

    fun menu(item: Item): List<Pair<String, () -> Unit>>? =
        if (canDelete(item)) listOf("Hapus playlist" to { ask(item) }) else null
}

@Composable
fun rememberPlaylistDeleter(): PlaylistDeleter {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val deleted = remember { mutableStateListOf<String>() }
    var target by remember { mutableStateOf<Item?>(null) }
    val deleter = remember { PlaylistDeleter(deleted) { target = it } }
    target?.let { item ->
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text("Hapus playlist?") },
            text = { Text("\"${item.title.ifBlank { "Playlist" }}\" akan dihapus permanen dari akunmu.") },
            confirmButton = {
                TextButton(onClick = {
                    target = null
                    deleted.add(item.id)
                    scope.launch {
                        try {
                            Repo.deletePlaylist(item.id)
                            Toast.makeText(ctx, "Playlist dihapus", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            deleted.remove(item.id)
                            Toast.makeText(ctx, "Gagal menghapus (hanya playlist milikmu)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text("Batal") } },
        )
    }
    return deleter
}
