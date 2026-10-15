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
    private fun pid(item: Item) = item.playlistId ?: item.id.removePrefix("VL")

    /**
     * Sama seperti aplikasi asli: hanya playlist milik akun yang bisa dihapus, dan hanya playlist
     * simpanan yang ditawarkan YouTube untuk dikeluarkan dari koleksi. Playlist buatan otomatis
     * (Recap, "Dibuat untuk…", Musik yang Disukai, Tonton nanti) tidak bisa dihapus.
     */
    fun canDelete(item: Item): Boolean {
        if (!Session.loggedIn || item.type != ItemType.PLAYLIST) return false
        if (pid(item) in setOf("LL", "LM", "WL", "SE")) return false
        return isOwnLike(item) || item.canRemove == true
    }

    /** Playlist milik akun (dari daftar playlist yang bisa diubah di akun YouTube). */
    fun isOwnLike(item: Item) = pid(item) in Repo.ownedPlaylists || item.canDelete == true

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
                                Repo.deletePlaylist(item.id)
                                "Playlist dihapus"
                            } else {
                                Repo.removePlaylistFromLibrary(item.id)
                                // Pastikan YouTube benar-benar mengeluarkannya (sinkron dengan aplikasi asli).
                                kotlinx.coroutines.delay(1500)
                                val still = try {
                                    Repo.inLibrary(item.id, com.lumitube.app.CrossApp.isMusic)
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    false
                                }
                                if (still) {
                                    Repo.unforgetPlaylist(item.id)
                                    deleted.remove(item.id)
                                    "Playlist ini dibuat otomatis oleh YouTube dan tidak bisa dihapus"
                                } else "Dihapus dari koleksi"
                            }
                        } catch (e: Exception) {
                            deleted.remove(item.id)
                            "Gagal menghapus: ${e.message ?: e}"
                        }
                        FeedLoader.libraryChanged()
                        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text("Batal") } },
        )
    }
    return deleter
}
