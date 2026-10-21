package com.lumitube.app.ui.common

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

val MusicPanelColors: PanelColors
    get() = PanelColors(MusicColors.panel, MusicColors.text, MusicColors.sub, MusicColors.chip, if (LumiStyle.blue) YtRed else MusicColors.text)

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
    val drag = rememberSheetDragState(onClose)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x66000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(heightFraction)
                .sheetContainer(drag)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(colors.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
        ) {
            Box(Modifier.fillMaxWidth().sheetHandle(drag).padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(colors.sub.copy(alpha = 0.4f)))
            }
            Row(Modifier.fillMaxWidth().sheetHandle(drag).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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

private val urlRegex = Regex("(?:https?://|www\\.)[^\\s]+|\\b[a-z0-9-]+\\.(?:me|link|com|ru|id|io|co|ly|gg|tv)/[^\\s]+", RegexOption.IGNORE_CASE)

/**
 * Deskripsi video dengan tautan yang bisa dipencet: link web, timestamp (lompat di video),
 * channel/hashtag, dan link video YouTube (dibuka di aplikasi).
 * [onTarget] menerima "http..", "t:<ms>", "c:<browseId>", atau "v:<videoId>".
 */
fun descriptionText(
    text: String,
    links: List<Triple<Int, Int, String>>,
    onTarget: (String) -> Unit,
) = androidx.compose.ui.text.buildAnnotatedString {
    data class Span(val start: Int, val end: Int, val target: String)
    val spans = mutableListOf<Span>()
    for ((st, len, t) in links) {
        val e = (st + len).coerceAtMost(text.length)
        if (st in text.indices && e > st) spans.add(Span(st, e, if (t.startsWith("t:")) "t:" + (t.substring(2).toLongOrNull() ?: 0) * 1000 else t))
    }
    fun free(a: Int, b: Int) = spans.none { a < it.end && b > it.start }
    for (m in urlRegex.findAll(text)) {
        val v = m.value.trimEnd('.', ',', ')', '!', '?')
        val e = m.range.first + v.length
        if (free(m.range.first, e)) spans.add(Span(m.range.first, e, if (v.startsWith("http", true)) v else "https://$v"))
    }
    for (m in timestampRegex.findAll(text)) {
        if (free(m.range.first, m.range.last + 1)) spans.add(Span(m.range.first, m.range.last + 1, "t:" + timestampMs(m.value)))
    }
    spans.sortBy { it.start }
    var last = 0
    for (sp in spans) {
        if (sp.start < last) continue
        append(text.substring(last, sp.start))
        var target = sp.target
        Regex("(?:youtube\\.com/(?:watch\\?v=|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})").find(target)?.let { target = "v:" + it.groupValues[1] }
        withLink(
            androidx.compose.ui.text.LinkAnnotation.Clickable(
                tag = "link",
                styles = androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = LinkBlue)),
                linkInteractionListener = { onTarget(target) },
            )
        ) { append(text.substring(sp.start, sp.end)) }
        last = sp.end
    }
    append(text.substring(last))
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
    val drag = rememberSheetDragState(onClose)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x66000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.70f)
                .sheetContainer(drag)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(colors.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .then(if (edgeToEdge) Modifier.navigationBarsPadding().imePadding() else Modifier)
        ) {
            Box(Modifier.fillMaxWidth().sheetHandle(drag).padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(colors.sub.copy(alpha = 0.4f)))
            }
            Row(Modifier.fillMaxWidth().sheetHandle(drag).padding(start = 16.dp, end = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                    Text("•  " + com.lumitube.app.tr("%s balasan", c.replyCount), color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                            if (comments.isEmpty()) item { Text(com.lumitube.app.tr("Gagal memuat komentar: %s", e), color = colors.sub, modifier = Modifier.padding(24.dp)) }
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
                                !Session.loggedIn -> com.lumitube.app.ui.common.LumiToast.makeText(context, "Login dulu untuk berkomentar", Toast.LENGTH_SHORT).show()
                                params == null -> com.lumitube.app.ui.common.LumiToast.makeText(context, "Komentar tidak bisa dikirim untuk video ini", Toast.LENGTH_SHORT).show()
                                else -> {
                                    sending = true
                                    val text = draft.trim()
                                    scope.launch {
                                        try {
                                            Repo.postComment(params, text)
                                            draft = ""
                                            com.lumitube.app.ui.common.LumiToast.makeText(context, "Komentar terkirim", Toast.LENGTH_SHORT).show()
                                            val newest = sorts.getOrNull(1)?.second ?: sorts.getOrNull(sortSel)?.second ?: token
                                            if (newest != null) {
                                                sortSel = if (sorts.size > 1) 1 else sortSel
                                                load(newest, reset = true)
                                            }
                                        } catch (e: Exception) {
                                            com.lumitube.app.ui.common.LumiToast.makeText(context, com.lumitube.app.tr("Gagal mengirim: %s", e.message), Toast.LENGTH_SHORT).show()
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
            item { PanelHeader("Kecepatan putar", colors) }
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
            item { PanelHeader("Kualitas video", colors) }
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
private fun PanelHeader(text: String, colors: PanelColors) {
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
    val choice by produceState<Result<DlChoice>?>(null, item.id) {
        value = try {
            val s = com.lumitube.app.data.StreamResolver.resolve(item.id, audioOnly = false)
            if (s.isLive) {
                Result.success(DlChoice.Live(com.lumitube.app.data.LiveDownloader.variants(item.id)))
            } else {
                val list = Downloader.options(item.id)
                // YT Music Lumi: pilihan audio dulu, baru video.
                Result.success(DlChoice.Normal(if (com.lumitube.app.CrossApp.isMusic) list.sortedBy { !it.audioOnly } else list))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    var fromStart by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        title = { Text(if (choice?.getOrNull() is DlChoice.Live) "Download live" else "Download", fontWeight = FontWeight.Bold) },
        text = {
            val r = choice
            when {
                r == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                r.isFailure -> Text(com.lumitube.app.tr("Gagal mengambil pilihan: %s", r.exceptionOrNull()?.message ?: ""))
                else -> when (val c = r.getOrNull()) {
                    is DlChoice.Live -> Column {
                        Text(
                            "Siaran ini sedang live. Rekaman berjalan sampai live selesai atau kamu menekan \"Berhenti & simpan\" di notifikasi.",
                            fontSize = 13.sp, lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        listOf(true to "Dari awal live", false to "Mulai dari detik ini").forEach { (v, label) ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { fromStart = v },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.RadioButton(selected = fromStart == v, onClick = { fromStart = v })
                                Text(label, fontSize = 15.sp)
                            }
                        }
                        if (fromStart) {
                            Text(
                                "Bagian awal hanya bisa diambil selama YouTube masih menyimpannya.",
                                fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                            )
                        }
                        Text("Kualitas", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                        if (c.list.isEmpty()) Text("Tidak ada format yang bisa diunduh.")
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                            items(c.list) { v ->
                                val key = "${item.id}:live:${v.label}"
                                val recording = com.lumitube.app.data.LiveDownloader.isRecording(key)
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                        if (recording) {
                                            com.lumitube.app.data.LiveDownloader.stop(key)
                                        } else {
                                            com.lumitube.app.data.LiveDownloader.start(item, v, fromStart)
                                            com.lumitube.app.ui.common.LumiToast.makeText(context, "Merekam live…", Toast.LENGTH_SHORT).show()
                                        }
                                        onDismiss()
                                    }.padding(horizontal = 4.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.Download, null)
                                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                        androidx.compose.material3.Text(v.label, fontWeight = FontWeight.Medium)
                                        Text(if (recording) "Sedang direkam • ketuk untuk berhenti & simpan" else "Video MP4", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    is DlChoice.Normal -> {
                        val list = c.list
                        if (list.isEmpty()) {
                            Text("Tidak ada format yang bisa diunduh.")
                        } else {
                            LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                                items(list) { o ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            Downloader.start(item, o)
                                            com.lumitube.app.ui.common.LumiToast.makeText(context, com.lumitube.app.tr("Mengunduh %s…", o.label), Toast.LENGTH_SHORT).show()
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
                    null -> Unit
                }
            }
        },
    )
}

private sealed interface DlChoice {
    data class Normal(val list: List<DownloadOption>) : DlChoice
    data class Live(val list: List<com.lumitube.app.data.LiveVariant>) : DlChoice
}

/** Kumpulan di halaman Download: lagu/video yang didownload (otomatis) atau playlist yang didownload. */
private data class DlCollection(
    val key: String,
    val title: String,
    val subtitle: String,
    val records: List<DownloadRecord>,
    val auto: Boolean,
    val playlist: com.lumitube.app.data.DownloadedPlaylist? = null,
)

/**
 * Halaman Download seperti YT Music asli: "Lagu yang didownload" / "Video yang didownload"
 * (playlist otomatis) dan playlist yang di-download. Ketuk untuk membuka isinya.
 */
@Composable
fun DownloadsList(
    colors: PanelColors,
    onPlayAll: ((List<DownloadRecord>, Int) -> Unit)? = null,
    onPlay: (DownloadRecord) -> Unit,
) {
    var confirm by remember { mutableStateOf<DownloadRecord?>(null) }
    var confirmPl by remember { mutableStateOf<com.lumitube.app.data.DownloadedPlaylist?>(null) }
    var openKey by remember { mutableStateOf<String?>(null) }
    val all = Downloader.records.toList()
    val songs = all.filter { it.audioOnly }
    val videos = all.filter { !it.audioOnly }
    val collections = buildList {
        if (songs.isNotEmpty()) add(DlCollection("auto-songs", "Lagu yang didownload", com.lumitube.app.tr("Playlist otomatis • %s lagu", songs.size), songs, true))
        if (videos.isNotEmpty()) add(DlCollection("auto-videos", "Video yang didownload", com.lumitube.app.tr("Playlist otomatis • %s video", videos.size), videos, true))
        for (pl in Downloader.playlists) {
            val recs = pl.videoIds.mapNotNull { id -> all.firstOrNull { it.videoId == id && it.audioOnly == pl.audioOnly } }
            val unit = if (pl.audioOnly) "lagu" else "video"
            val count = if (recs.size < pl.videoIds.size) "${recs.size}/${pl.videoIds.size} $unit" else "${recs.size} $unit"
            add(DlCollection("pl-${pl.id}-${pl.audioOnly}", pl.title, listOf("Playlist", pl.author.takeIf { it.isNotBlank() }, count).filterNotNull().joinToString(" • "), recs, false, pl))
        }
    }
    val opened = collections.firstOrNull { it.key == openKey }
    BackHandler(enabled = opened != null) { openKey = null }

    LazyColumn(Modifier.fillMaxSize()) {
        // Download yang sedang berjalan selalu terlihat di atas.
        // "Download semua" yang sedang berjalan, dengan tombol Hentikan.
        val batches = Downloader.batches.toList()
        items(batches, key = { "b" + it.first }) { (id, info) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(info.first, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(com.lumitube.app.tr("Download playlist %s", info.second), color = colors.sub, fontSize = 12.sp)
                }
                Box(
                    Modifier.padding(start = 12.dp).clip(RoundedCornerShape(16.dp)).background(YtRed)
                        .clickable { Downloader.stopBatch(id) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) { Text("Hentikan", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
            }
        }
        val active = Downloader.active.values.toList()
        items(active, key = { "a" + it.key }) { a ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(a.title, color = colors.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.label + " • " + com.lumitube.app.tr(a.status), color = colors.sub, fontSize = 12.sp)
                if (a.live) {
                    // Rekaman live: berjalan terus sampai dihentikan.
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(modifier = Modifier.weight(1f), color = YtRed)
                        Box(
                            Modifier.padding(start = 12.dp).clip(RoundedCornerShape(16.dp)).background(YtRed)
                                .clickable { com.lumitube.app.data.LiveDownloader.stop(a.key) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text("Berhenti & simpan", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                    }
                } else {
                    LinearProgressIndicator(
                        progress = { a.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        color = YtRed,
                    )
                }
            }
        }
        if (opened == null) {
            if (active.isEmpty() && collections.isEmpty()) {
                item {
                    Text(
                        "Belum ada download. Ketuk tombol Download di halaman video atau pemutar musik, atau \"Download semua\" di playlist.",
                        color = colors.sub, modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(collections, key = { it.key }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { openKey = c.key }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (c.auto) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(6.dp))
                                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF9F8CFF), Color(0xFF5B5BD6)))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Check, null, tint = Color(0xFF5B5BD6), modifier = Modifier.size(22.dp))
                            }
                        }
                    } else {
                        DlMosaic(c.records.mapNotNull { it.thumbnail }, Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)))
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(c.title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(16.dp).clip(androidx.compose.foundation.shape.CircleShape).background(colors.sub), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Check, null, tint = colors.bg, modifier = Modifier.size(12.dp))
                            }
                            Text(c.subtitle, color = colors.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    if (c.playlist != null) {
                        ItemMenu(listOf("Hapus dari Download" to { confirmPl = c.playlist }), colors.sub)
                    }
                }
            }
        } else {
            item(key = "open-head") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { openKey = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = colors.text)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(opened.title, color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(opened.subtitle, color = colors.sub, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    if (onPlayAll != null && opened.records.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(colors.text)
                                    .clickable { onPlayAll(opened.records, 0) },
                                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, tint = colors.bg)
                                Text("Putar semua", color = colors.bg, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
                            }
                            Row(
                                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(colors.chip)
                                    .clickable { onPlayAll(opened.records.shuffled(), 0) },
                                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Shuffle, null, tint = colors.text)
                                Text("Acak", color = colors.text, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        Text("Bisa diputar tanpa internet", color = colors.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp))
                    }
                }
            }
            val shown = opened.records
            items(shown, key = { "r" + it.id }) { r ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (onPlayAll != null) onPlayAll(shown, shown.indexOf(r)) else onPlay(r)
                    }.padding(horizontal = 16.dp, vertical = 8.dp),
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
    confirmPl?.let { pl ->
        AlertDialog(
            onDismissRequest = { confirmPl = null },
            title = { Text(com.lumitube.app.tr("Hapus \"%s\" dari Download?", pl.title)) },
            text = { Text(if (pl.audioOnly) "Pilih apakah lagu di dalamnya ikut dihapus dari HP." else "Pilih apakah video di dalamnya ikut dihapus dari HP.") },
            confirmButton = {
                TextButton(onClick = { Downloader.deletePlaylist(pl, withFiles = true); confirmPl = null; openKey = null }) { Text("Hapus beserta file") }
            },
            dismissButton = {
                TextButton(onClick = { Downloader.deletePlaylist(pl, withFiles = false); confirmPl = null; openKey = null }) { Text("Hapus playlist saja") }
            },
        )
    }
}

/** Sampul playlist 2x2 dari gambar lagu/video di dalamnya. */
@Composable
private fun DlMosaic(thumbs: List<String>, modifier: Modifier) {
    if (thumbs.size < 4) {
        Img(thumbs.firstOrNull(), modifier)
        return
    }
    Column(modifier) {
        for (r in 0..1) {
            Row(Modifier.weight(1f)) {
                for (c in 0..1) Img(thumbs[r * 2 + c], Modifier.weight(1f).fillMaxHeight())
            }
        }
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
