package com.lumitube.app.ui.common

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.data.PlaylistOption
import com.lumitube.app.data.Repo
import com.lumitube.app.data.Session
import kotlinx.coroutines.launch

/**
 * "Simpan ke playlist" seperti YouTube / YouTube Music: centang playlist yang ada
 * (ketuk lagi untuk menghapus), atau buat playlist baru berisi video ini.
 */
@Composable
fun AddToPlaylistSheet(videoId: String, music: Boolean, colors: PanelColors, onClose: () -> Unit) {
    val context = LocalContext.current
    // Simpan di scope aplikasi: menutup sheet tidak membatalkan penyimpanan.
    val scope = EditScope.scope
    val appCtx = context.applicationContext
    val options = remember(videoId) { mutableStateListOf<PlaylistOption>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val noun = if (music) "lagu" else "video"

    LaunchedEffect(videoId) {
        if (!Session.loggedIn) {
            error = "Login dulu untuk menyimpan ke playlist"
            loading = false
            return@LaunchedEffect
        }
        try {
            options.clear()
            val list = Repo.addToPlaylistOptions(videoId, music)
            options.addAll(list)
            // YouTube: "Tonton nanti" selalu ada di atas daftar playlist.
            if (!music && list.none { it.id == "WL" }) {
                options.add(0, PlaylistOption(id = "WL", title = "Tonton nanti", count = null, thumbnail = null, contains = false))
            }
        } catch (e: Exception) {
            error = "Gagal memuat playlist"
        } finally {
            loading = false
        }
    }

    fun toggle(o: PlaylistOption) {
        val i = options.indexOfFirst { it.id == o.id }
        if (i < 0) return
        val target = !o.contains
        options[i] = o.copy(contains = target)
        scope.launch {
            try {
                Repo.setInPlaylist(o.id, videoId, target)
                FeedLoader.invalidate("VL${o.id}")
                Toast.makeText(
                    appCtx,
                    if (target) "Disimpan ke ${o.title}" else "Dihapus dari ${o.title}",
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val k = options.indexOfFirst { it.id == o.id }
                if (k >= 0) options[k] = options[k].copy(contains = !target)
Toast.makeText(appCtx, "Gagal menyimpan: ${e.message ?: e}", Toast.LENGTH_LONG).show()
            }
        }
    }

    BottomPanel("Simpan 1 $noun ke playlist", colors, onClose, heightFraction = 0.75f) {
        when {
            loading -> LoadingBox(color = colors.text)
            error != null -> Text(error ?: "", color = colors.sub, modifier = Modifier.padding(24.dp))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                val recent = options.filter { it.thumbnail != null && it.id != "WL" }.take(6)
                if (recent.isNotEmpty()) {
                    item {
                        Text("Terbaru", color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(16.dp))
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(recent, key = { "r" + it.id }) { o ->
                                Column(Modifier.width(110.dp).clickable { toggle(o) }) {
                                    Box {
                                        Img(o.thumbnail, Modifier.size(110.dp).clip(RoundedCornerShape(6.dp)))
                                        if (o.contains) {
                                            Icon(
                                                Icons.Filled.CheckBox, null, tint = colors.text,
                                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                                    .background(colors.bg.copy(alpha = 0.7f), RoundedCornerShape(4.dp)),
                                            )
                                        }
                                    }
                                    Text(o.title, color = colors.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                    o.count?.let { Text(it, color = colors.sub, fontSize = 12.sp, maxLines = 1) }
                                }
                            }
                        }
                        Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(0.5.dp).background(colors.sub.copy(alpha = 0.25f)))
                    }
                }
                // "Tonton nanti" (YouTube) di atas "Semua playlist".
                options.firstOrNull { it.id == "WL" }?.let { wl ->
                    item(key = "wl") {
                        Row(
                            Modifier.fillMaxWidth().clickable { toggle(wl) }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(colors.chip), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.WatchLater, null, tint = colors.text, modifier = Modifier.size(28.dp))
                            }
                            Text("Tonton nanti", color = colors.text, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
                            Icon(
                                if (wl.contains) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null,
                                tint = if (wl.contains) colors.accent else colors.sub,
                            )
                        }
                        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(0.5.dp).background(colors.sub.copy(alpha = 0.25f)))
                    }
                }
                item {
                    Text("Semua playlist", color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(16.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(colors.chip), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, null, tint = colors.text, modifier = Modifier.size(30.dp))
                        }
                        Text("Playlist baru", color = colors.text, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp))
                    }
                }
                items(options.filter { it.id != "WL" }, key = { it.id }) { o ->
                    Row(
                        Modifier.fillMaxWidth().clickable { toggle(o) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (o.thumbnail != null) {
                            Img(o.thumbnail, Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)))
                        } else {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(colors.chip), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.PlaylistPlay, null, tint = colors.text)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(o.title, color = colors.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            o.count?.let { Text(it, color = colors.sub, fontSize = 13.sp, maxLines = 1) }
                        }
                        Icon(
                            if (o.contains) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null,
                            tint = if (o.contains) colors.accent else colors.sub,
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var privacy by remember { mutableStateOf("PRIVATE") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Playlist baru") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(150) },
                        label = { Text("Judul") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Text("Visibilitas", fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    for ((v, label) in listOf("PRIVATE" to "Pribadi", "UNLISTED" to "Tidak publik", "PUBLIC" to "Publik")) {
                        Row(Modifier.fillMaxWidth().clickable { privacy = v }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = privacy == v, onClick = { privacy = v })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val title = name.trim()
                    creating = false
                    scope.launch {
                        try {
                            Repo.createPlaylist(title, privacy, videoId)
                            // Daftar Playlist / Koleksi dimuat ulang supaya playlist baru langsung tampil.
                            FeedLoader.clear()
                            Toast.makeText(appCtx, "Playlist \"$title\" dibuat", Toast.LENGTH_SHORT).show()
                            onClose()
                        } catch (_: Exception) {
                            Toast.makeText(appCtx, "Gagal membuat playlist", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Buat") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Batal") } },
        )
    }
}
