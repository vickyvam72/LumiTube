package com.lumitube.app.ui.youtube

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lumitube.app.data.Item
import com.lumitube.app.data.ItemType
import com.lumitube.app.data.InnerTube
import com.lumitube.app.data.Page
import com.lumitube.app.data.Repo
import com.lumitube.app.data.Session
import com.lumitube.app.data.WatchInfo
import com.lumitube.app.player.PlaybackManager
import com.lumitube.app.ui.common.FeedLoader
import com.lumitube.app.ui.common.Img
import com.lumitube.app.ui.common.LoadMoreEffect
import com.lumitube.app.ui.common.LoadingBox
import com.lumitube.app.ui.common.LocalYt
import com.lumitube.app.ui.common.MessageBox
import com.lumitube.app.ui.common.VideoSurface
import com.lumitube.app.ui.common.YtRed
import com.lumitube.app.ui.common.findActivity
import com.lumitube.app.ui.common.rememberFeed
import com.lumitube.app.ui.common.shareVideo
import com.lumitube.app.ui.common.toItem
import com.lumitube.app.ui.common.CommentsPanel
import com.lumitube.app.ui.common.VideoSettingsPanel
import com.lumitube.app.ui.common.DownloadDialog
import com.lumitube.app.ui.common.ytPanelColors
import kotlinx.coroutines.launch

sealed interface YtRoute {
    data class Watch(val item: Item) : YtRoute
    data class Browse(val id: String, val title: String?) : YtRoute
    data object Search : YtRoute
    data class Shorts(val items: List<Item>, val index: Int) : YtRoute
    data object Downloads : YtRoute
}

/** Aplikasi "YouTube" di dalam LumiTube. */
@Composable
fun YouTubeApp(
    pendingWatch: Item?,
    onPendingConsumed: () -> Unit,
    onSwitchMusic: () -> Unit,
    onLogin: () -> Unit,
    onPlayInMusic: (Item) -> Unit,
) {
    val yt = LocalYt.current
    val stack = remember { mutableStateListOf<YtRoute>() }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    fun openWatch(item: Item) {
        val video = item.copy(type = if (item.type == ItemType.SHORT) ItemType.SHORT else ItemType.VIDEO)
        if (PlaybackManager.current?.id != item.id || !PlaybackManager.isVideo) {
            PlaybackManager.playVideo(video)
        }
        stack.removeAll { it is YtRoute.Watch }
        stack.add(YtRoute.Watch(video))
    }

    val openShorts: (List<Item>, Int) -> Unit = { list, idx ->
        stack.removeAll { it is YtRoute.Shorts }
        stack.add(YtRoute.Shorts(list, idx.coerceAtLeast(0)))
    }
    val open: (Item) -> Unit = { item ->
        when {
            item.type == ItemType.SHORT -> openShorts(listOf(item), 0)
            item.isPlayable -> openWatch(item)
            else -> stack.add(YtRoute.Browse(item.id, item.title))
        }
    }
    val openChannel: (String) -> Unit = { id -> stack.add(YtRoute.Browse(id, null)) }

    LaunchedEffect(pendingWatch) {
        if (pendingWatch != null) {
            stack.removeAll { it is YtRoute.Watch }
            stack.add(YtRoute.Watch(pendingWatch))
            onPendingConsumed()
        }
    }

    BackHandler(enabled = stack.isNotEmpty() || tab != 0) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else tab = 0
    }

    val loggedIn = Session.loggedIn
    val home = rememberFeed(key = "home$loggedIn", more = { Repo.ytMore(it) }) { Repo.ytHome() }
    val shorts = rememberFeed(key = "shorts", more = { Repo.ytMore(it, "search") }) { Repo.ytShorts() }
    val subs = rememberFeed(key = "subs$loggedIn", more = { Repo.ytMore(it) }) {
        if (Session.loggedIn) Repo.ytBrowse("FEsubscriptions") else Page()
    }

    val top = stack.lastOrNull()
    if (top is YtRoute.Watch) {
        WatchScreen(
            item = top.item,
            onBack = { stack.removeAt(stack.lastIndex) },
            onOpen = open,
            onChannel = openChannel,
            onLogin = onLogin,
            onPlayInMusic = onPlayInMusic,
        )
        return
    }

    val shortsMode = top is YtRoute.Shorts || (top == null && tab == 1)
    val context = LocalContext.current
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    DisposableEffect(shortsMode) {
        val window = context.findActivity()?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !shortsMode && !systemDark
        }
        onDispose {}
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalYt provides if (shortsMode) com.lumitube.app.ui.common.YtDark else yt
    ) {
    val colors = LocalYt.current
    Column(Modifier.fillMaxSize().background(if (shortsMode) Color.Black else colors.bg)) {
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
            when (top) {
                is YtRoute.Browse -> BrowseScreen(top.id, top.title, { stack.removeAt(stack.lastIndex) }, open)
                YtRoute.Search -> SearchScreen({ stack.removeAt(stack.lastIndex) }, open, openChannel)
                is YtRoute.Shorts -> ShortsPlayer(
                    items = top.items, startIndex = top.index, onLoadMore = null,
                    onChannel = openChannel, onLogin = onLogin,
                    onBack = { stack.removeAt(stack.lastIndex) },
                )
                YtRoute.Downloads -> DownloadsScreen({ stack.removeAt(stack.lastIndex) }) { rec ->
                    val it = rec.toItem()
                    if (rec.audioOnly) onPlayInMusic(it) else openWatch(it)
                }
                else -> when (tab) {
                    0 -> HomeTab(home, open, openChannel, openShorts, onSearch = { stack.add(YtRoute.Search) }, onSwitchMusic = onSwitchMusic, onLogin = onLogin)
                    1 -> ShortsTabPlayer(shorts, openChannel, onLogin)
                    2 -> SubscriptionsTab(subs, open, openChannel, onLogin)
                    else -> YouTab(open, { id, title -> stack.add(YtRoute.Browse(id, title)) }, onLogin, onSwitchMusic) { stack.add(YtRoute.Downloads) }
                }
            }
        }
        if (PlaybackManager.current != null && !shortsMode && PlaybackManager.current?.type != ItemType.SHORT) {
            YtMiniPlayer(onOpen = {
                val cur = PlaybackManager.current
                if (cur != null) {
                    if (PlaybackManager.isVideo) {
                        stack.removeAll { it is YtRoute.Watch }
                        stack.add(YtRoute.Watch(cur))
                    } else {
                        onPlayInMusic(cur)
                    }
                }
            })
        }
        YtBottomNav(
            selected = tab,
            onSelect = { stack.clear(); tab = it },
            onSwitchMusic = onSwitchMusic,
        )
    }
    }
}

@Composable
private fun DownloadsScreen(onBack: () -> Unit, onPlay: (com.lumitube.app.data.DownloadRecord) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BackBar("Download", onBack)
        com.lumitube.app.ui.common.DownloadsList(com.lumitube.app.ui.common.ytPanelColors(), onPlay)
    }
}

// ---------------------------------------------------------------- Beranda

private sealed interface FeedRow {
    data class Single(val item: Item) : FeedRow
    data class Shorts(val items: List<Item>) : FeedRow
}

private fun groupRows(items: List<Item>): List<FeedRow> {
    val rows = mutableListOf<FeedRow>()
    val buf = mutableListOf<Item>()
    for (it in items) {
        if (it.type == ItemType.SHORT) {
            buf.add(it)
        } else {
            if (buf.isNotEmpty()) {
                rows.add(FeedRow.Shorts(buf.toList()))
                buf.clear()
            }
            rows.add(FeedRow.Single(it))
        }
    }
    if (buf.isNotEmpty()) rows.add(FeedRow.Shorts(buf.toList()))
    return rows
}

@Composable
private fun FeedList(
    feed: FeedLoader,
    open: (Item) -> Unit,
    openChannel: (String) -> Unit,
    openShorts: ((List<Item>, Int) -> Unit)? = null,
    header: @Composable () -> Unit = {},
    empty: @Composable () -> Unit = {},
) {
    val yt = LocalYt.current
    val state = rememberLazyListState()
    LoadMoreEffect(state, feed)
    val rows = remember(feed.items.size, feed.items.firstOrNull()?.key) { groupRows(feed.items.toList()) }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        item { header() }
        if (feed.loading && feed.items.isEmpty()) item { LoadingBox() }
        feed.error?.let { err ->
            if (feed.items.isEmpty()) item { MessageBox("Gagal memuat: $err", yt.sub, "Coba lagi") { feed.load() } }
        }
        if (!feed.loading && feed.error == null && feed.items.isEmpty()) item { empty() }
        items(rows.size) { i ->
            when (val row = rows[i]) {
                is FeedRow.Shorts -> ShortsShelf(row.items) { s ->
                    if (openShorts != null) openShorts(row.items, row.items.indexOf(s)) else open(s)
                }
                is FeedRow.Single -> {
                    val v = row.item
                    when (v.type) {
                        ItemType.CHANNEL -> ChannelRow(v) { open(v) }
                        ItemType.PLAYLIST -> VideoRow(v) { open(v) }
                        else -> VideoCard(v, { open(v) }, v.channelId?.let { id -> { openChannel(id) } })
                    }
                }
            }
        }
        if (feed.continuation != null && feed.items.isNotEmpty()) item { LoadingBox() }
    }
}

@Composable
private fun HomeTab(
    feed: FeedLoader,
    open: (Item) -> Unit,
    openChannel: (String) -> Unit,
    openShorts: (List<Item>, Int) -> Unit,
    onSearch: () -> Unit,
    onSwitchMusic: () -> Unit,
    onLogin: () -> Unit,
) {
    val yt = LocalYt.current
    var selected by remember(feed) { mutableIntStateOf(0) }
    val chips = remember(feed.page) {
        val c = feed.page?.chips ?: emptyList()
        if (c.isEmpty()) listOf(com.lumitube.app.data.Chip("Semua", selected = true)) else c
    }
    FeedList(
        feed, open, openChannel, openShorts,
        header = {
            Column {
                YtTopBar(onSearch, onSwitchMusic)
                ChipRow(chips, selected, onExplore = onSearch) { i ->
                    selected = i
                    val token = chips[i].continuation
                    if (i == 0 || token == null) feed.load() else feed.replaceWith { Repo.ytMore(token) }
                }
            }
        },
        empty = {
            if (!Session.loggedIn) {
                MessageBox(
                    "Login untuk melihat rekomendasi, subscription, histori, dan playlist kamu.",
                    yt.sub, "Login", onLogin
                )
            } else {
                MessageBox("Beranda masih kosong. Coba cari video dulu.", yt.sub, "Cari", onSearch)
            }
        },
    )
}

@Composable
private fun ShortsTabPlayer(feed: FeedLoader, openChannel: (String) -> Unit, onLogin: () -> Unit) {
    val yt = LocalYt.current
    when {
        feed.items.isNotEmpty() -> ShortsPlayer(
            items = feed.items.toList(), startIndex = 0, onLoadMore = { feed.loadMore() },
            onChannel = openChannel, onLogin = onLogin, onBack = null,
        )
        feed.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingBox(color = Color.White) }
        else -> MessageBox("Gagal memuat Shorts: ${feed.error ?: "kosong"}", yt.sub, "Coba lagi") { feed.load() }
    }
}

@Composable
private fun SubscriptionsTab(feed: FeedLoader, open: (Item) -> Unit, openChannel: (String) -> Unit, onLogin: () -> Unit) {
    val yt = LocalYt.current
    if (!Session.loggedIn) {
        Column {
            YtTitle("Subscription")
            MessageBox("Login untuk melihat video terbaru dari channel yang kamu subscribe.", yt.sub, "Login", onLogin)
        }
        return
    }
    val channels by produceState(emptyList<Item>(), Session.loggedIn) {
        value = try {
            Repo.ytSubscriptionChannels()
        } catch (_: Exception) {
            emptyList()
        }
    }
    FeedList(
        feed, open, openChannel,
        header = {
            Column {
                YtTitle("Subscription")
                if (channels.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(bottom = 12.dp),
                    ) {
                        items(channels, key = { it.key }) { ch ->
                            Column(
                                Modifier.width(64.dp).clickable { openChannel(ch.id) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Img(ch.thumbnail, Modifier.size(56.dp).clip(CircleShape))
                                Text(ch.title, color = yt.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun YtTitle(text: String) {
    val yt = LocalYt.current
    Text(text, color = yt.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
}

// ---------------------------------------------------------------- Anda

@Composable
private fun YouTab(
    open: (Item) -> Unit,
    browse: (String, String) -> Unit,
    onLogin: () -> Unit,
    onSwitchMusic: () -> Unit,
    onDownloads: () -> Unit,
) {
    val yt = LocalYt.current
    val account = Session.account
    LaunchedEffect(Session.loggedIn) {
        if (Session.loggedIn && Session.account == null) {
            try {
                Repo.account()
            } catch (_: Exception) {
            }
        }
    }
    val history = rememberFeed(key = "hist${Session.loggedIn}") {
        if (Session.loggedIn) Repo.ytBrowse("FEhistory") else Page()
    }
    var quality by remember { mutableIntStateOf(Session.maxQuality) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (account?.photo != null) {
                    Img(account.photo, Modifier.size(72.dp).clip(CircleShape))
                } else {
                    Icon(Icons.Filled.AccountCircle, null, tint = yt.sub, modifier = Modifier.size(72.dp))
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Text(account?.name ?: if (Session.loggedIn) "Akun Google" else "Belum login", color = yt.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    account?.handle?.let { Text(it, color = yt.sub, fontSize = 13.sp) }
                }
            }
        }
        if (!Session.loggedIn) {
            item { MessageBox("Login agar bisa mengambil subscription, histori, dan playlist dari akun YouTube kamu.", yt.sub, "Login", onLogin) }
        } else {
            if (history.items.isNotEmpty()) {
                item {
                    SectionHeader("Histori") { browse("FEhistory", "Histori") }
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(history.items.take(15), key = { it.key }) { v ->
                            Column(Modifier.width(160.dp).clickable { open(v) }) {
                                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
                                    Img(v.thumbnail, Modifier.fillMaxSize())
                                    v.duration?.let { DurationBadge(it, Modifier.align(Alignment.BottomEnd).padding(4.dp)) }
                                }
                                Text(v.title, color = yt.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                Text(v.channelName ?: "", color = yt.sub, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            item { MenuRow(Icons.Filled.History, "Histori") { browse("FEhistory", "Histori") } }
            item { MenuRow(Icons.Filled.PlaylistPlay, "Playlist") { browse("FEplaylist_aggregation", "Playlist") } }
            item { MenuRow(Icons.Filled.WatchLater, "Tonton nanti") { browse("VLWL", "Tonton nanti") } }
            item { MenuRow(Icons.Filled.ThumbUp, "Video yang disukai") { browse("VLLL", "Video yang disukai") } }
        }
        item { MenuRow(Icons.Filled.Download, "Download", onDownloads) }
        item { MenuRow(Icons.Filled.MusicNote, "Buka LumiTube Music", onSwitchMusic) }
        item {
            MenuRow(Icons.Filled.HighQuality, "Kualitas video maks: ${quality}p") {
                val next = when (quality) { 360 -> 480; 480 -> 720; 720 -> 1080; else -> 360 }
                quality = next
                Session.maxQuality = next
            }
        }
        if (Session.loggedIn) {
            item { MenuRow(Icons.Filled.Logout, "Keluar") { Session.logout() } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String, onMore: () -> Unit) {
    val yt = LocalYt.current
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = yt.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onMore).padding(horizontal = 12.dp, vertical = 6.dp)
        ) { Text("Lihat semua", color = yt.text, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val yt = LocalYt.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = yt.text)
        Text(label, color = yt.text, fontSize = 15.sp, modifier = Modifier.padding(start = 20.dp))
    }
}

// ---------------------------------------------------------------- Channel / Playlist

@Composable
private fun BackBar(title: String?, onBack: () -> Unit) {
    val yt = LocalYt.current
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = yt.text) }
        Text(title ?: "", color = yt.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BrowseScreen(id: String, title: String?, onBack: () -> Unit, open: (Item) -> Unit) {
    val yt = LocalYt.current
    val feed = rememberFeed(key = id, more = { Repo.ytMore(it) }) { Repo.ytBrowse(id) }
    val state = rememberLazyListState()
    LoadMoreEffect(state, feed)
    val page = feed.page
    val isChannel = id.startsWith("UC")
    Column(Modifier.fillMaxSize()) {
        BackBar(page?.title ?: title, onBack)
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            if (page != null && (page.title != null || page.thumbnail != null)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        if (isChannel) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Img(page.thumbnail, Modifier.size(80.dp).clip(CircleShape))
                                Column(Modifier.padding(start = 16.dp)) {
                                    Text(page.title ?: "", color = yt.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    page.subtitle?.let { Text(it, color = yt.sub, fontSize = 12.sp) }
                                }
                            }
                        } else {
                            Text(page.title ?: title ?: "", color = yt.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            page.subtitle?.let { Text(it, color = yt.sub, fontSize = 12.sp) }
                        }
                        page.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = yt.sub, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                        }
                        val videos = feed.items.filter { it.type == ItemType.VIDEO }
                        if (!isChannel && videos.isNotEmpty()) {
                            Box(
                                Modifier.padding(top = 14.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp))
                                    .background(yt.text).clickable {
                                        PlaybackManager.playVideos(videos, 0)
                                        open(videos[0])
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PlayArrow, null, tint = yt.bg)
                                    Text("Putar semua", color = yt.bg, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
            if (feed.loading && feed.items.isEmpty()) item { LoadingBox() }
            feed.error?.let { err -> item { MessageBox("Gagal memuat: $err", yt.sub, "Coba lagi") { feed.load() } } }
            items(feed.items, key = { it.key }) { item ->
                when (item.type) {
                    ItemType.CHANNEL -> ChannelRow(item) { open(item) }
                    else -> VideoRow(item) {
                        val videos = feed.items.filter { it.type == ItemType.VIDEO }
                        val idx = videos.indexOfFirst { it.id == item.id }
                        if (!isChannel && idx >= 0) {
                            PlaybackManager.playVideos(videos, idx)
                        }
                        open(item)
                    }
                }
            }
            if (feed.continuation != null && feed.items.isNotEmpty()) item { LoadingBox() }
        }
    }
}

// ---------------------------------------------------------------- Pencarian

@Composable
private fun SearchScreen(onBack: () -> Unit, open: (Item) -> Unit, openChannel: (String) -> Unit) {
    val yt = LocalYt.current
    var text by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = yt.text) }
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Telusuri YouTube", color = yt.sub) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = yt.chip,
                    unfocusedContainerColor = yt.chip,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = yt.text,
                    unfocusedTextColor = yt.text,
                    cursorColor = YtRed,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) query = text.trim() }),
                modifier = Modifier.weight(1f).height(52.dp).focusRequester(focus),
            )
        }
        if (query.isNotEmpty()) {
            val feed = rememberFeed(key = query, more = { Repo.ytMore(it, "search") }) { Repo.ytSearch(query) }
            FeedList(feed, open, openChannel)
        }
    }
}

// ---------------------------------------------------------------- Halaman tonton

@Composable
fun WatchScreen(
    item: Item,
    onBack: () -> Unit,
    onOpen: (Item) -> Unit,
    onChannel: (String) -> Unit,
    onLogin: () -> Unit,
    onPlayInMusic: (Item) -> Unit,
) {
    val yt = LocalYt.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var full by remember { mutableStateOf(false) }
    val current = PlaybackManager.current ?: item
    val info by produceState<WatchInfo?>(null, current.id) {
        value = null
        value = try {
            Repo.watch(current.id)
        } catch (_: Exception) {
            null
        }
    }

    var panel by remember { mutableIntStateOf(0) } // 1 = komentar, 2 = setelan
    var downloadItem by remember { mutableStateOf<Item?>(null) }

    BackHandler { if (full) full = false else onBack() }

    DisposableEffect(full) {
        val activity = context.findActivity()
        val window = activity?.window
        if (activity != null && window != null) {
            val ctrl = WindowCompat.getInsetsController(window, window.decorView)
            if (full) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (full && activity != null && window != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().background(if (full) Color.Black else yt.bg)
            .then(if (full) Modifier else Modifier.statusBarsPadding())
    ) {
        Box(
            if (full) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center,
        ) {
            VideoSurface(Modifier.fillMaxSize(), onFullscreen = { full = it })
            if (PlaybackManager.loading) CircularProgressIndicator(color = Color.White)
            PlaybackManager.error?.let { err ->
                Column(
                    Modifier.fillMaxSize().background(Color(0xDD000000)).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(err, color = Color.White, fontSize = 13.sp, maxLines = 3)
                    Box(
                        Modifier.padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                            .clickable { PlaybackManager.retry() }.padding(horizontal = 16.dp, vertical = 6.dp)
                    ) { Text("Coba lagi", color = Color.Black) }
                }
            }
        }
        if (!full) {
            WatchDetails(
                current, info, onOpen, onChannel, onLogin, onPlayInMusic,
                onComments = { panel = 1 },
                onSettings = { panel = 2 },
                onDownload = { downloadItem = current },
            )
        }
    }
    when (panel) {
        1 -> CommentsPanel(
            token = info?.commentsToken, count = info?.commentCount, colors = ytPanelColors(),
            onClose = { panel = 0 }, onChannel = { panel = 0; onChannel(it) },
        )
        2 -> VideoSettingsPanel(ytPanelColors()) { panel = 0 }
    }
    }
    downloadItem?.let { DownloadDialog(it) { downloadItem = null } }
}

@Composable
private fun WatchDetails(
    current: Item,
    i: WatchInfo?,
    onOpen: (Item) -> Unit,
    onChannel: (String) -> Unit,
    onLogin: () -> Unit,
    onPlayInMusic: (Item) -> Unit,
    onComments: () -> Unit,
    onSettings: () -> Unit,
    onDownload: () -> Unit,
) {
    val yt = LocalYt.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    run {
        var expanded by remember(current.id) { mutableStateOf(false) }
        var subscribed by remember(current.id, i?.subscribed) { mutableStateOf(i?.subscribed == true) }
        var liked by remember(current.id, i?.liked) { mutableStateOf(i?.liked == true) }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues(),
        ) {
            item {
                Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp).animateContentSize()) {
                    Text(
                        i?.title ?: current.title, color = yt.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        maxLines = if (expanded) 10 else 2, overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(i?.views, i?.date).joinToString("  ")
                    if (meta.isNotBlank()) Text("$meta  ...selengkapnya", color = yt.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    if (expanded) {
                        i?.description?.let {
                            Box(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(yt.surface).padding(12.dp)) {
                                Text(it, color = yt.text, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val chId = i?.channelId ?: current.channelId
                    Row(
                        Modifier.weight(1f).clickable(enabled = chId != null) { chId?.let(onChannel) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Img(i?.channelThumb ?: current.channelThumb, Modifier.size(38.dp).clip(CircleShape))
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(i?.channel ?: current.channelName ?: "", color = yt.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            i?.subscribers?.let { Text(it, color = yt.sub, fontSize = 12.sp, maxLines = 1) }
                        }
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(18.dp))
                            .background(if (subscribed) yt.chip else yt.text)
                            .clickable {
                                if (!Session.loggedIn) {
                                    onLogin()
                                } else if (chId != null) {
                                    val target = !subscribed
                                    subscribed = target
                                    scope.launch {
                                        try {
                                            InnerTube.subscribe(chId, target)
                                        } catch (_: Exception) {
                                            subscribed = !target
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (subscribed) "Disubscribe" else "Subscribe",
                            color = if (subscribed) yt.text else yt.bg, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        ActionChip(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, i?.likes ?: "Suka") {
                            if (!Session.loggedIn) {
                                onLogin()
                            } else {
                                val target = !liked
                                liked = target
                                scope.launch {
                                    try {
                                        InnerTube.like(current.id, target)
                                    } catch (_: Exception) {
                                        liked = !target
                                    }
                                }
                            }
                        }
                    }
                    item { ActionChip(Icons.Filled.MusicNote, "Putar di Music") { onPlayInMusic(current) } }
                    item { ActionChip(Icons.Filled.Download, "Download") { onDownload() } }
                    item { ActionChip(Icons.Filled.Settings, "Setelan") { onSettings() } }
                    item { ActionChip(Icons.Filled.Share, "Bagikan") { shareVideo(context, current.id, current.title, false) } }
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)).background(yt.surface)
                        .clickable(onClick = onComments).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = yt.text, modifier = Modifier.size(20.dp))
                    Text("Komentar", color = yt.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
                    i?.commentCount?.let { Text("  $it", color = yt.sub, fontSize = 14.sp) }
                    Spacer(Modifier.weight(1f))
                    Text("Lihat", color = yt.sub, fontSize = 13.sp)
                }
            }
            if (PlaybackManager.queue.size > 1) {
                item {
                    Text(
                        "Playlist • ${PlaybackManager.index + 1}/${PlaybackManager.queue.size}",
                        color = yt.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                items(PlaybackManager.queue.size) { idx ->
                    val q = PlaybackManager.queue[idx]
                    Box(Modifier.background(if (idx == PlaybackManager.index) yt.surface else Color.Transparent)) {
                        VideoRow(q) { PlaybackManager.playAt(idx) }
                    }
                }
            }
            if (i == null) item { LoadingBox() }
            i?.related?.let { list ->
                items(list, key = { "rel" + it.key }) { r ->
                    if (r.type == ItemType.PLAYLIST) VideoRow(r) { onOpen(r) }
                    else VideoCard(r, { onOpen(r) }, r.channelId?.let { id -> { onChannel(id) } })
                }
            }
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val yt = LocalYt.current
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(yt.chip).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = yt.text, modifier = Modifier.size(20.dp))
        Text(label, color = yt.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
