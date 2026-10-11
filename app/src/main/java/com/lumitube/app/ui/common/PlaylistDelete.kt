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
    /** Semua playlist di koleksi, kecuali "Video/Lagu yang disukai", "Tonton nanti", dan Episode. */
    fun canDelete(item: Item): Boolean {
        if (!Session.loggedIn || item.type != ItemType.PLAYLIST) return false
        return item.id.removePrefix("VL") !in setOf("LL", "LM", "WL", "SE")
    }

    /** Playlist buatan akun (ID "PL…"); lainnya (Recap, mix, playlist orang lain) hanya bisa dikeluarkan dari koleksi. */
    fun isOwnLike(item: Item) = item.id.removePrefix("VL").startsWith("PL")

    fun menu(item: Item): List<Pair<String, () -> Unit>>? =
        if (canDelete(item)) listOf((if (isOwnLike(item)) "Hapus playlist" else "Hapus dari koleksi") to { ask(item) }) else null
}

@Composable
fun rememberPlaylistDeleter(): PlaylistDeleter {
    val ctx = LocalContext.current
    val deleted = remember { mutableStateListOf<String>() }
    var target by remember { mutableStateOf<Item?>(null) }
    val deleter = remember { PlaylistDeleter(deleted) { target = it } }
    target?.let { item ->
        val own = deleter.isOwnLike(item)
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text(if (own) "Hapus playlist?" else "Hapus dari koleksi?") },
            text = {
                Text(
                    if (own) "\"${item.title.ifBlank { "Playlist" }}\" akan dihapus permanen dari akunmu."
                    else "\"${item.title.ifBlank { "Playlist" }}\" akan dikeluarkan dari koleksimu."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    target = null
                    deleted.add(item.id)
                    val app = ctx.applicationContext
                    EditScope.scope.launch {
                        val msg = try {
                            if (own) {
                                try {
                                    Repo.deletePlaylist(item.id)
                                    "Playlist dihapus"
                                } catch (e: Exception) {
                                    // Playlist orang lain yang disimpan: keluarkan dari koleksi saja.
                                    Repo.removePlaylistFromLibrary(item.id)
                                    "Dihapus dari koleksi"
                                }
                            } else {
                                Repo.removePlaylistFromLibrary(item.id)
                                "Dihapus dari koleksi"
                            }
                        } catch (e: Exception) {
                            deleted.remove(item.id)
                            "Gagal menghapus: ${e.message ?: e}"
                        }
                        FeedLoader.clear()
                        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text("Batal") } },
        )
    }
    return deleter
}
