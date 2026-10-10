package com.lumitube.app.ui.common

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.data.Comment
import com.lumitube.app.data.DownloadOption
import com.lumitube.app.data.DownloadRecord
import com.lumitube.app.data.Downloader
import com.lumitube.app.data.Item
import com.lumitube.app.data.ItemType
import com.lumitube.app.data.Repo
import com.lumitube.app.data.Session
import com.lumitube.app.player.PlaybackManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Warna panel, supaya bisa dipakai di mode YouTube (terang/gelap) dan Music (gelap). */
data class PanelColors(val bg: Color, val text: Color, val sub: Color, val chip: Color, val accent: Color)

@Composable
fun ytPanelColors(): PanelColors {
    val yt = LocalYt.current
    return PanelColors(yt.bg, yt.text, yt.sub, yt.chip, YtRed)
}

val MusicPanelColors = PanelColors(Color(0xFF1C1B22), Color.White, Color(0xB3FFFFFF), Color(0x1AFFFFFF), Color.White)

/** Panel dari bawah layar (seperti bottom sheet YouTube). */
@Composable
fun BottomPanel(
    title: String,
    colors: PanelColors,
    onClose: () -> Unit,
    heightFraction: Float = 0.72f,
    content: @Composable () -> Unit,
) {
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x66000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(heightFraction)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(colors.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(colors.sub.copy(alpha = 0.4f)))
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Tutup", tint = colors.text) }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.sub.copy(alpha = 0.2f)))
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

// ---------------------------------------------------------------- Komentar

@Composable
fun CommentsPanel(
    token: String?,
    count: String?,
    colors: PanelColors,
    onClose: () -> Unit,
    onChannel: ((String) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val comments = remember(token) { mutableStateListOf<Comment>() }
    var next by remember(token) { mutableStateOf<String?>(null) }
    var loading by remember(token) { mutableStateOf(false) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    var header by remember(token) { mutableStateOf(count) }
    val replies = remember(token) { mutableStateMapOf<String, List<Comment>>() }
    val loadingReplies = remember(token) { mutableStateMapOf<String, Boolean>() }

    fun load(t: String) {
        if (loading) return
        loading = true
        scope.launch {
            try {
                val page = Repo.comments(t)
                comments.addAll(page.comments.filter { c -> comments.none { it.id == c.id } })
                next = page.next?.takeIf { it != t }
                page.count?.let { header = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
                next = null
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(token) { if (token != null) load(token) }

    val state = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, comments.size) { if (nearEnd) next?.let { load(it) } }

    BottomPanel(title = "Komentar" + (header?.let { "  •  $it" } ?: ""), colors = colors, onClose = onClose) {
        when {
            token == null -> Text("Komentar tidak tersedia untuk video ini.", color = colors.sub, modifier = Modifier.padding(24.dp))
            else -> LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                items(comments, key = { it.id }) { c ->
                    CommentRow(c, colors, onChannel)
                    val rs = replies[c.id]
                    if (rs != null) {
                        rs.forEach { r ->
                            Box(Modifier.padding(start = 44.dp)) { CommentRow(r, colors, onChannel) }
                        }
                    } else if (c.repliesToken != null && c.replyCount.isNotBlank()) {
                        Row(
                            Modifier.padding(start = 60.dp, bottom = 6.dp).clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    val rt = c.repliesToken ?: return@clickable
                                    loadingReplies[c.id] = true
                                    scope.launch {
                                        try {
                                            replies[c.id] = Repo.comments(rt).comments
                                        } catch (_: Exception) {
                                        } finally {
                                            loadingReplies[c.id] = false
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${c.replyCount} balasan", color = Color(0xFF3EA6FF), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            if (loadingReplies[c.id] == true) {
                                Spacer(Modifier.width(8.dp))
                                CircularProgressIndicator(Modifier.size(14.dp), color = colors.sub, strokeWidth = 2.dp)
                            }
                        }
                    }
                }
                if (loading) item { LoadingBox(color = colors.accent) }
                error?.let { e ->
                    if (comments.isEmpty()) item { Text("Gagal memuat komentar: $e", color = colors.sub, modifier = Modifier.padding(24.dp)) }
                }
                if (!loading && error == null && comments.isEmpty()) {
                    item { Text("Belum ada komentar.", color = colors.sub, modifier = Modifier.padding(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(c: Comment, colors: PanelColors, onChannel: ((String) -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Img(
            c.avatar,
            Modifier.size(if (c.isReply) 26.dp else 34.dp).clip(CircleShape)
                .clickable(enabled = onChannel != null && c.authorId != null) { c.authorId?.let { onChannel?.invoke(it) } }
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    if (c.isCreator) Modifier.clip(RoundedCornerShape(10.dp)).background(colors.sub.copy(alpha = 0.3f)).padding(horizontal = 6.dp)
                    else Modifier
                ) {
                    Text(c.author, color = if (c.isCreator) colors.text else colors.sub, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Text("  •  ${c.published}", color = colors.sub, fontSize = 12.sp)
            }
            Text(c.text, color = colors.text, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp), lineHeight = 19.sp)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ThumbUp, null, tint = colors.sub, modifier = Modifier.size(16.dp))
                if (c.likes.isNotBlank()) Text("  ${c.likes}", color = colors.sub, fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- Setelan video

@Composable
fun VideoSettingsPanel(colors: PanelColors, onClose: () -> Unit) {
    var loop by remember { mutableStateOf(Session.loopVideo) }
    BottomPanel("Setelan video", colors, onClose, heightFraction = 0.62f) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        loop = !loop
                        Session.loopVideo = loop
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ulangi video", color = colors.text, fontSize = 15.sp)
                        Text("Putar ulang otomatis saat video selesai", color = colors.sub, fontSize = 12.sp)
                    }
                    Switch(
                        checked = loop,
                        onCheckedChange = { loop = it; Session.loopVideo = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = YtRed),
                    )
                }
            }
            item { SettingHeader("Kecepatan putar", colors) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                        val sel = PlaybackManager.speed == sp
                        Box(
                            Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (sel) colors.text else colors.chip)
                                .clickable { PlaybackManager.setPlaybackSpeed(sp) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (sp == 1f) "Normal" else "${sp}x".replace(".0x", "x"),
                                color = if (sel) colors.bg else colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
            item { SettingHeader("Kualitas video", colors) }
            val current = Session.maxQuality
            val list = PlaybackManager.qualities
            if (list.isEmpty()) {
                item { Text("Daftar kualitas belum tersedia.", color = colors.sub, modifier = Modifier.padding(16.dp)) }
            }
            items(list) { h ->
                val chosen = list.firstOrNull { it <= current } ?: list.last()
                Row(
                    Modifier.fillMaxWidth().clickable {
                        PlaybackManager.setQuality(h)
                        onClose()
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${h}p" + when {
                            h >= 2160 -> "  4K"
                            h >= 1440 -> "  QHD"
                            h >= 720 -> "  HD"
                            else -> ""
                        },
                        color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f),
                    )
                    if (h == chosen) Icon(Icons.Filled.Check, null, tint = colors.accent)
                }
            }
        }
    }
}

@Composable
private fun SettingHeader(text: String, colors: PanelColors) {
    Text(text, color = colors.sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
}

// ---------------------------------------------------------------- Download

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / 1024.0 / 1024 / 1024)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024)
}

@Composable
fun DownloadDialog(item: Item, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val options by produceState<Result<List<DownloadOption>>?>(null, item.id) {
        value = try {
            Result.success(Downloader.options(item.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        title = { Text("Download", fontWeight = FontWeight.Bold) },
        text = {
            val r = options
            when {
                r == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                r.isFailure -> Text("Gagal mengambil pilihan: ${r.exceptionOrNull()?.message}")
                else -> {
                    val list = r.getOrNull().orEmpty()
                    if (list.isEmpty()) {
                        Text("Tidak ada format yang bisa diunduh.")
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                            items(list) { o ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                        Downloader.start(item, o)
                                        Toast.makeText(context, "Mengunduh ${o.label}…", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }.padding(horizontal = 4.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(if (o.audioOnly) Icons.Filled.MusicNote else Icons.Filled.Download, null)
                                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                        Text(o.label, fontWeight = FontWeight.Medium)
                                        Text(o.detail, fontSize = 12.sp)
                                    }
                                    Text(formatSize(o.size), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

/** Daftar download aktif & selesai. Ketuk untuk memutar offline. */
@Composable
fun DownloadsList(colors: PanelColors, onPlay: (DownloadRecord) -> Unit) {
    var confirm by remember { mutableStateOf<DownloadRecord?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        val active = Downloader.active.values.toList()
        items(active, key = { "a" + it.key }) { a ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(a.title, color = colors.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${a.label} • ${a.status}", color = colors.sub, fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = { a.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = YtRed,
                )
            }
        }
        if (active.isEmpty() && Downloader.records.isEmpty()) {
            item {
                Text(
                    "Belum ada download. Ketuk tombol Download di halaman video atau pemutar musik.",
                    color = colors.sub, modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(Downloader.records, key = { it.id }) { r ->
            Row(
                Modifier.fillMaxWidth().clickable { onPlay(r) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Img(
                    r.thumbnail,
                    Modifier.size(width = if (r.audioOnly) 56.dp else 96.dp, height = 56.dp).clip(RoundedCornerShape(6.dp))
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(r.title, color = colors.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${r.label} • ${formatSize(r.size)}", color = colors.sub, fontSize = 12.sp, maxLines = 1)
                }
                IconButton(onClick = { confirm = r }) { Icon(Icons.Filled.Delete, "Hapus", tint = colors.sub) }
            }
        }
    }
    confirm?.let { r ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Hapus download?") },
            text = { Text(r.title) },
            confirmButton = { TextButton(onClick = { Downloader.delete(r); confirm = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } },
        )
    }
}

/** Ubah record download menjadi Item yang bisa diputar offline. */
fun DownloadRecord.toItem(): Item = Item(
    type = if (audioOnly) ItemType.SONG else ItemType.VIDEO,
    id = videoId,
    title = title,
    subtitle = author,
    thumbnail = thumbnail,
    channelName = author,
    localUri = uri,
)
