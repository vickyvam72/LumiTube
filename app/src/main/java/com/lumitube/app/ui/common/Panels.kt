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
import androidx.compose.ui.text.withLink
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
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

private val LinkBlue = Color(0xFF3EA6FF)
private val timestampRegex = Regex("(?<![\\d:])(\\d{1,2}:)?\\d{1,2}:\\d{2}(?![\\d:])")

private fun timestampMs(t: String): Long {
    val parts = t.split(":").map { it.toLongOrNull() ?: 0L }
    return when (parts.size) {
        3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
        2 -> (parts[0] * 60 + parts[1]) * 1000
        else -> 0L
    }
}

/** Teks komentar dengan timestamp (mis. 4:19) yang bisa diklik untuk lompat di video. */
private fun commentText(text: String, onSeek: (Long) -> Unit) = androidx.compose.ui.text.buildAnnotatedString {
    var last = 0
    for (m in timestampRegex.findAll(text)) {
        append(text.substring(last, m.range.first))
        val ms = timestampMs(m.value)
        withLink(
            androidx.compose.ui.text.LinkAnnotation.Clickable(
                tag = "ts",
                styles = androidx.compose.ui.text.TextLinkStyles(
                    androidx.compose.ui.text.SpanStyle(color = LinkBlue, background = LinkBlue.copy(alpha = 0.12f))
                ),
                linkInteractionListener = { onSeek(ms) },
            )
        ) { append(m.value) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
fun CommentsPanel(
    token: String?,
    count: String?,
    colors: PanelColors,
    onClose: () -> Unit,
    onChannel: ((String) -> Unit)? = null,
    onSeek: (Long) -> Unit = { PlaybackManager.exo.seekTo(it) },
    /** false bila panel sudah berada di atas navigasi bawah (mis. di Shorts). */
    edgeToEdge: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val comments = remember(token) { mutableStateListOf<Comment>() }
    var next by remember(token) { mutableStateOf<String?>(null) }
    var loading by remember(token) { mutableStateOf(false) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    var header by remember(token) { mutableStateOf(count) }
    var sorts by remember(token) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var sortSel by remember(token) { mutableStateOf(0) }
    var createParams by remember(token) { mutableStateOf<String?>(null) }
    var draft by remember(token) { mutableStateOf("") }
    var sending by remember(token) { mutableStateOf(false) }
    val replies = remember(token) { mutableStateMapOf<String, List<Comment>>() }
    val loadingReplies = remember(token) { mutableStateMapOf<String, Boolean>() }

    fun load(t: String, reset: Boolean = false) {
        if (loading && !reset) return
        loading = true
        scope.launch {
            try {
                val page = Repo.comments(t)
                if (reset) {
                    comments.clear()
                    replies.clear()
                }
                comments.addAll(page.comments.filter { c -> comments.none { it.id == c.id } })
                next = page.next?.takeIf { it != t }
                page.count?.let { header = it }
                if (page.sorts.isNotEmpty()) {
                    sorts = page.sorts
                    sortSel = page.sortSelected
                }
                page.createParams?.let { createParams = it }
                error = null
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

    BackHandler { onClose() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x66000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.70f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(colors.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .then(if (edgeToEdge) Modifier.navigationBarsPadding().imePadding() else Modifier)
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(colors.sub.copy(alpha = 0.4f)))
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Komentar", color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                header?.let {
                    Text(
                        "  " + it.replace(Regex("(?i)\\s*(komentar|comments?)$"), ""),
                        color = colors.sub, fontSize = 16.sp, modifier = Modifier.weight(1f),
                    )
                } ?: Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Tutup", tint = colors.text) }
            }
            if (sorts.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sorts.forEachIndexed { i, (title, tok) ->
                        val sel = i == sortSel
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) colors.text else colors.chip)
                                .clickable {
                                    if (!sel) {
                                        sortSel = i
                                        load(tok, reset = true)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                when (title.lowercase()) {
                                    "top", "top comments" -> "Teratas"
                                    "newest", "newest first" -> "Terbaru"
                                    else -> title
                                },
                                color = if (sel) colors.bg else colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.sub.copy(alpha = 0.25f)))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (token == null) {
                    Text("Komentar tidak tersedia untuk video ini.", color = colors.sub, modifier = Modifier.padding(24.dp))
                } else {
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                        items(comments, key = { it.id }) { c ->
                            val rs = replies[c.id]
                            CommentRow(
                                c, colors, onChannel, onSeek,
                                thread = c.repliesToken != null && c.replyCount.isNotBlank(),
                            )
                            if (rs != null) {
                                rs.forEach { r -> Box(Modifier.padding(start = 48.dp)) { CommentRow(r, colors, onChannel, onSeek, false) } }
                            } else if (c.repliesToken != null && c.replyCount.isNotBlank()) {
                                Row(
                                    Modifier.padding(start = 56.dp, bottom = 8.dp).clip(RoundedCornerShape(16.dp))
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
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("•  ${c.replyCount} balasan", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    if (loadingReplies[c.id] == true) {
                                        Spacer(Modifier.width(8.dp))
                                        CircularProgressIndicator(Modifier.size(14.dp), color = colors.sub, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = colors.text, modifier = Modifier.size(20.dp))
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
            // Kolom tulis komentar
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.sub.copy(alpha = 0.25f)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Session.account?.photo?.let { Img(it, Modifier.size(30.dp).clip(CircleShape)) }
                androidx.compose.foundation.text.BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = colors.text, fontSize = 15.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    maxLines = 4,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(20.dp)).background(colors.chip)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) Text("Komentar…", color = colors.sub, fontSize = 15.sp)
                        inner()
                    },
                )
                if (draft.isNotBlank()) {
                    IconButton(
                        enabled = !sending,
                        onClick = {
                            val params = createParams
                            when {
                                !Session.loggedIn -> Toast.makeText(context, "Login dulu untuk berkomentar", Toast.LENGTH_SHORT).show()
                                params == null -> Toast.makeText(context, "Komentar tidak bisa dikirim untuk video ini", Toast.LENGTH_SHORT).show()
                                else -> {
                                    sending = true
                                    val text = draft.trim()
                                    scope.launch {
                                        try {
                                            Repo.postComment(params, text)
                                            draft = ""
                                            Toast.makeText(context, "Komentar terkirim", Toast.LENGTH_SHORT).show()
                                            val newest = sorts.getOrNull(1)?.second ?: sorts.getOrNull(sortSel)?.second ?: token
                                            if (newest != null) {
                                                sortSel = if (sorts.size > 1) 1 else sortSel
                                                load(newest, reset = true)
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Gagal mengirim: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            sending = false
                                        }
                                    }
                                }
                            }
                        },
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = colors.accent) }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    c: Comment,
    colors: PanelColors,
    onChannel: ((String) -> Unit)?,
    onSeek: (Long) -> Unit,
    thread: Boolean,
) {
    var expanded by remember(c.id) { mutableStateOf(false) }
    var overflow by remember(c.id) { mutableStateOf(false) }
    val lineColor = colors.sub.copy(alpha = 0.25f)
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
            .drawBehind {
                // Garis utas di bawah avatar saat komentar punya balasan (seperti YouTube)
                if (thread) {
                    val x = 16.dp.toPx()
                    drawLine(lineColor, androidx.compose.ui.geometry.Offset(x, 40.dp.toPx()), androidx.compose.ui.geometry.Offset(x, size.height + 4.dp.toPx()), 1.5.dp.toPx())
                }
            }
    ) {
        Img(
            c.avatar,
            Modifier.size(if (c.isReply) 24.dp else 32.dp).clip(CircleShape)
                .clickable(enabled = onChannel != null && c.authorId != null) { c.authorId?.let { onChannel?.invoke(it) } }
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            c.pinned?.let { pin ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(Icons.Filled.PushPin, null, tint = colors.sub, modifier = Modifier.size(14.dp))
                    Text(
                        " " + pin.replace("Pinned by", "Disematkan oleh"),
                        color = colors.sub, fontSize = 12.sp,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.isCreator) {
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(colors.text).padding(horizontal = 7.dp, vertical = 1.dp)) {
                        Text(c.author, color = colors.bg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    Text(c.author, color = colors.sub, fontSize = 12.sp)
                }
                Text(" • ${c.published}", color = colors.sub, fontSize = 12.sp)
            }
            Text(
                commentText(c.text, onSeek),
                style = androidx.compose.ui.text.TextStyle(color = colors.text, fontSize = 14.sp, lineHeight = 20.sp),
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow },
                modifier = Modifier.padding(top = 3.dp),
            )
            if (overflow || expanded) {
                Text(
                    if (expanded) "Lebih sedikit" else "Selengkapnya",
                    color = colors.sub, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp).clickable { expanded = !expanded },
                )
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ThumbUp, null, tint = colors.text, modifier = Modifier.size(18.dp))
                Text(if (c.likes.isNotBlank()) "  ${c.likes}" else "", color = colors.sub, fontSize = 12.sp)
                Spacer(Modifier.width(22.dp))
                Icon(Icons.Outlined.ThumbDown, null, tint = colors.text, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(26.dp))
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = colors.text, modifier = Modifier.size(18.dp))
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
