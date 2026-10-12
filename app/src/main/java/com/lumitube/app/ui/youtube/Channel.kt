package com.lumitube.app.ui.youtube

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.data.ChannelAbout
import com.lumitube.app.data.ChannelInfo
import com.lumitube.app.data.ChannelPost
import com.lumitube.app.data.ChannelRepo
import com.lumitube.app.data.InnerTube
import com.lumitube.app.data.Item
import com.lumitube.app.data.ItemType
import com.lumitube.app.data.Repo
import com.lumitube.app.data.Session
import com.lumitube.app.ui.common.Img
import com.lumitube.app.ui.common.LoadingBox
import com.lumitube.app.ui.common.LocalYt
import com.lumitube.app.ui.common.MessageBox
import com.lumitube.app.ui.common.YtRed
import kotlinx.coroutines.launch

/** Satu baris di daftar isi tab channel. */
private sealed interface ChRow {
    data class One(val item: Item) : ChRow
    data class Shorts(val items: List<Item>) : ChRow
    data class Grid(val items: List<Item>) : ChRow
    data class Post(val post: ChannelPost) : ChRow
}

/**
 * Halaman channel seperti aplikasi YouTube: banner, foto, nama, @handle, subscriber • video,
 * deskripsi (…selengkapnya), tombol Subscribe/Disubscribe, lalu tab yang memang tersedia
 * (Beranda, Video, Shorts, Rilis, Playlist, Postingan).
 */
@Composable
fun ChannelScreen(channelId: String, onBack: () -> Unit, open: (Item) -> Unit, onLogin: () -> Unit) {
    val yt = LocalYt.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember(channelId) { mutableStateOf<ChannelInfo?>(null) }
    var error by remember(channelId) { mutableStateOf<String?>(null) }
    var tabIndex by remember(channelId) { mutableIntStateOf(0) }
    val items = remember(channelId) { mutableStateListOf<Item>() }
    val posts = remember(channelId) { mutableStateListOf<ChannelPost>() }
    var continuation by remember(channelId) { mutableStateOf<String?>(null) }
    var tabLoading by remember(channelId) { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var subscribed by remember(channelId) { mutableStateOf(false) }
    var showAbout by remember(channelId) { mutableStateOf(false) }
    var searching by remember(channelId) { mutableStateOf(false) }

    LaunchedEffect(channelId) {
        try {
            val (ci, first) = ChannelRepo.channel(channelId)
            info = ci
            subscribed = ci.subscribed
            tabIndex = ci.tabs.indexOfFirst { it.selected }.coerceAtLeast(0)
            items.clear(); items.addAll(first.items)
            posts.clear(); posts.addAll(first.posts)
            continuation = first.continuation
        } catch (e: Exception) {
            error = e.message ?: "Gagal memuat channel"
        } finally {
            tabLoading = false
        }
    }

    fun selectTab(i: Int) {
        val ci = info ?: return
        if (i == tabIndex) return
        tabIndex = i
        tabLoading = true
        items.clear(); posts.clear(); continuation = null
        scope.launch {
            try {
                val p = ChannelRepo.tab(channelId, ci.tabs.getOrNull(i)?.params)
                if (tabIndex == i) {
                    items.addAll(p.items); posts.addAll(p.posts); continuation = p.continuation
                }
            } catch (_: Exception) {
            } finally {
                if (tabIndex == i) tabLoading = false
            }
        }
    }

    if (searching) {
        ChannelSearch(channelId, info?.title ?: "channel", { searching = false }, open)
        return
    }
    if (showAbout && info != null) {
        ChannelAboutScreen(info!!) { showAbout = false }
        return
    }

    val tabTitle = info?.tabs?.getOrNull(tabIndex)?.title ?: ""
    val isShortsTab = tabTitle.equals("Shorts", true)
    // Shorts berurutan jadi rak geser (Beranda) atau kisi 3 kolom (tab Shorts).
    val rows = remember(items.size, posts.size, tabIndex) {
        val out = mutableListOf<ChRow>()
        posts.forEach { out.add(ChRow.Post(it)) }
        val buf = mutableListOf<Item>()
        fun flush() {
            if (buf.isEmpty()) return
            if (isShortsTab) buf.chunked(3).forEach { out.add(ChRow.Grid(it)) } else out.add(ChRow.Shorts(buf.toList()))
            buf.clear()
        }
        for (it in items) {
            if (it.type == ItemType.SHORT) buf.add(it) else { flush(); out.add(ChRow.One(it)) }
        }
        flush()
        out
    }

    val state = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 3 } }
    LaunchedEffect(nearEnd, continuation) {
        val c = continuation
        if (nearEnd && c != null && !loadingMore && !tabLoading) {
            loadingMore = true
            try {
                val p = ChannelRepo.more(c)
                val keys = items.map { it.key }.toHashSet()
                items.addAll(p.items.filter { keys.add(it.key) })
                posts.addAll(p.posts.filter { np -> posts.none { it.id == np.id } })
                continuation = p.continuation?.takeIf { it != c }
            } catch (_: Exception) {
                continuation = null
            } finally {
                loadingMore = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = yt.text) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Cari di channel", tint = yt.text) }
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            val ci = info
            if (ci == null) {
                item { if (error != null) MessageBox("Gagal memuat: $error", yt.sub) else LoadingBox() }
                return@LazyColumn
            }
            item(key = "head") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    if (ci.banner != null) {
                        Img(ci.banner, Modifier.fillMaxWidth().aspectRatio(6.2f / 1.7f).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.height(14.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Img(ci.avatar, Modifier.size(84.dp).clip(CircleShape))
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(ci.title, color = yt.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            ci.handle?.let { Text(it, color = yt.text, fontSize = 14.sp, maxLines = 1) }
                            ci.stats?.let { Text(it, color = yt.sub, fontSize = 13.sp, maxLines = 2) }
                        }
                    }
                    if (!ci.description.isNullOrBlank() || ci.aboutToken != null) {
                        Row(
                            Modifier.padding(top = 12.dp).clickable { showAbout = true },
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                (ci.description ?: "").replace("\n", " "), color = yt.sub, fontSize = 13.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                            )
                            Text(" …selengkapnya", color = yt.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    }
                    ci.link?.let { link ->
                        Row(Modifier.padding(top = 8.dp).clickable { showAbout = true }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Link, null, tint = yt.text, modifier = Modifier.size(18.dp))
                            Text(link, color = yt.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    // Subscribe / Disubscribe
                    Box(
                        Modifier.padding(top = 14.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (subscribed) yt.chip else yt.text)
                            .clickable {
                                if (!Session.loggedIn) {
                                    onLogin()
                                } else {
                                    val target = !subscribed
                                    subscribed = target
                                    com.lumitube.app.ui.common.EditScope.scope.launch {
                                        try {
                                            InnerTube.subscribe(channelId, target)
                                        } catch (_: Exception) {
                                            subscribed = !target
                                            android.widget.Toast.makeText(ctx.applicationContext, "Gagal mengubah subscribe", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (subscribed) "Disubscribe" else "Subscribe",
                            color = if (subscribed) yt.text else yt.bg, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            if (ci.tabs.size > 1) {
                item(key = "tabs") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    ) {
                        items(ci.tabs.size) { i ->
                            val on = i == tabIndex
                            Column(
                                Modifier.clickable { selectTab(i) }.padding(horizontal = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    ci.tabs[i].title, color = if (on) yt.text else yt.sub, fontSize = 15.sp,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                                Box(Modifier.height(3.dp).width(40.dp).background(if (on) yt.text else Color.Transparent))
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(yt.sub.copy(alpha = 0.3f)))
                }
            }
            if (tabLoading) item { LoadingBox() }
            if (!tabLoading && rows.isEmpty()) item { MessageBox("Belum ada konten di sini.", yt.sub) }
            items(rows.size) { i ->
                when (val r = rows[i]) {
                    is ChRow.Post -> PostCard(r.post)
                    is ChRow.Shorts -> ShortsShelf(r.items) { open(it) }
                    is ChRow.Grid -> Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        r.items.forEach { s -> ShortCard(s, Modifier.weight(1f)) { open(s) } }
                        repeat(3 - r.items.size) { Spacer(Modifier.weight(1f)) }
                    }
                    is ChRow.One -> when (r.item.type) {
                        ItemType.VIDEO -> VideoCard(r.item, { open(r.item) }, null)
                        ItemType.CHANNEL -> ChannelRow(r.item) { open(r.item) }
                        else -> VideoRow(r.item) { open(r.item) }
                    }
                }
            }
            if (loadingMore) item { LoadingBox() }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PostCard(p: ChannelPost) {
    val yt = LocalYt.current
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Img(p.authorThumb, Modifier.size(36.dp).clip(CircleShape))
            Column(Modifier.padding(start = 10.dp)) {
                Text(p.author, color = yt.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(p.published, color = yt.sub, fontSize = 12.sp)
            }
        }
        if (p.text.isNotBlank()) {
            var expanded by remember { mutableStateOf(false) }
            Text(
                p.text, color = yt.text, fontSize = 14.sp, maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clickable { expanded = !expanded },
            )
        }
        p.image?.let { Img(it, Modifier.fillMaxWidth().aspectRatio(1f)) }
        p.likes?.let {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ThumbUp, null, tint = yt.sub, modifier = Modifier.size(18.dp))
                Text(it, color = yt.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).background(yt.chip))
    }
}

/** "…selengkapnya": Deskripsi, Link, Info selengkapnya (seperti aplikasi YouTube). */
@Composable
private fun ChannelAboutScreen(ci: ChannelInfo, onBack: () -> Unit) {
    val yt = LocalYt.current
    val ctx = LocalContext.current
    BackHandler { onBack() }
    var about by remember(ci.id) { mutableStateOf<ChannelAbout?>(null) }
    var loading by remember(ci.id) { mutableStateOf(true) }
    LaunchedEffect(ci.id) {
        about = try { ci.aboutToken?.let { ChannelRepo.about(it) } } catch (_: Exception) { null }
        loading = false
    }
    fun openUrl(u: String) {
        val url = if (u.startsWith("http")) u else "https://$u"
        try {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = yt.text) }
            Text(ci.title, color = yt.text, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            val a = about
            val desc = a?.description ?: ci.description
            if (!desc.isNullOrBlank()) {
                item {
                    Text("Deskripsi", color = yt.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(desc, color = yt.text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                    }
                }
            }
            if (loading) item { LoadingBox() }
            if (a != null && a.links.isNotEmpty()) {
                item { Text("Link", color = yt.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
                items(a.links) { (title, url, icon) ->
                    Row(Modifier.fillMaxWidth().clickable { openUrl(url) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) Img(icon, Modifier.size(28.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit, placeholder = Color.Transparent)
                        else Icon(Icons.Filled.Link, null, tint = yt.text, modifier = Modifier.size(28.dp))
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(title, color = yt.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(url, color = Color(0xFF3EA6FF), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
            if (a != null) {
                item {
                    Text("Info selengkapnya", color = yt.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    @Composable
                    fun infoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, link: Boolean = false) {
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = link) { openUrl(text) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, null, tint = yt.text, modifier = Modifier.size(26.dp))
                            Text(text, color = if (link) Color(0xFF3EA6FF) else yt.text, fontSize = 15.sp, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                    a.url?.let { infoRow(Icons.Filled.Language, it, link = true) }
                    a.country?.let { infoRow(Icons.Filled.Language, it) }
                    a.joined?.let { infoRow(Icons.Filled.Info, it) }
                    a.subscribers?.let { infoRow(Icons.Filled.Info, it) }
                    a.videos?.let { infoRow(Icons.Filled.Info, it) }
                    a.views?.let { infoRow(Icons.AutoMirrored.Filled.TrendingUp, it) }
                }
            }
        }
    }
}

/** Cari video di dalam channel. */
@Composable
private fun ChannelSearch(channelId: String, title: String, onBack: () -> Unit, open: (Item) -> Unit) {
    val yt = LocalYt.current
    BackHandler { onBack() }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val results = remember { mutableStateListOf<Item>() }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        loading = true
        results.clear()
        try { results.addAll(Repo.ytChannelSearch(channelId, query).items) } catch (_: Exception) {}
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = yt.text) }
            YtSearchField(field, { field = it }, "Telusuri $title", onSearch = { query = field.text.trim() }, onFocus = {}, focus = focus, modifier = Modifier.weight(1f))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (loading) item { LoadingBox() }
            if (!loading && query.isNotBlank() && results.isEmpty()) item { MessageBox("Tidak ada video yang cocok di channel ini.", yt.sub) }
            items(results, key = { it.key }) { v -> VideoRow(v) { open(v) } }
        }
    }
}
