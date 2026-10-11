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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.lumitube.app.ui.common.SearchSuggestions
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
import com.lumitube.app.ui.common.reorderItem
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
            when {
                pendingWatch.type == ItemType.SHORT -> openShorts(listOf(pendingWatch), 0)
                !pendingWatch.isPlayable -> stack.add(YtRoute.Browse(pendingWatch.id, pendingWatch.title))
                else -> {
                    stack.removeAll { it is YtRoute.Watch }
                    stack.add(YtRoute.Watch(pendingWatch))
                }
            }
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
                YtRoute.Downloads -> DownloadsScreen(
                    { stack.removeAt(stack.lastIndex) },
                    onPlayAll = { recs, start ->
                        // Putar offline: semua download jadi satu antrean.
                        val items = recs.map { it.toItem().copy(type = ItemType.VIDEO) }
                        PlaybackManager.playVideos(items, start)
                        stack.removeAll { it is YtRoute.Watch }
                        stack.add(YtRoute.Watch(items[start.coerceIn(0, items.lastIndex)]))
                    },
                ) { rec ->
                    val it = rec.toItem()
                    if (rec.audioOnly) PlaybackManager.playMusic(listOf(it), 0, radio = false) else openWatch(it)
                }
                else -> when (tab) {
                    0 -> HomeTab(
                        home, open, openChannel, openShorts, onSearch = { stack.add(YtRoute.Search) },
                        onSwitchMusic = onSwitchMusic, onLogin = onLogin, onOffline = { stack.add(YtRoute.Downloads) },
                    )
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
                    if (!PlaybackManager.isVideo && cur.localUri == null) PlaybackManager.switchMode(video = true)
                    if (PlaybackManager.isVideo || cur.localUri == null) {
                        stack.removeAll { it is YtRoute.Watch }
                        stack.add(YtRoute.Watch(cur.copy(type = ItemType.VIDEO)))
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
private fun DownloadsScreen(
    onBack: () -> Unit,
    onPlayAll: (List<com.lumitube.app.data.DownloadRecord>, Int) -> Unit,
    onPlay: (com.lumitube.app.data.DownloadRecord) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        BackBar("Download (offline)", onBack)
        com.lumitube.app.ui.common.DownloadsList(com.lumitube.app.ui.common.ytPanelColors(), onPlayAll, onPlay)
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
    onOffline: () -> Unit,
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
                YtTopBar(onSearch, onSwitchMusic, onOffline)
                if (feed.error != null && feed.items.isEmpty() && com.lumitube.app.data.Downloader.records.isNotEmpty()) {
                    MessageBox("Sedang offline? Putar video yang sudah kamu download.", yt.sub, "Buka Download", onOffline)
                }
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
        item { MenuRow(Icons.Filled.MusicNote, "Buka YT Music Lumi", onSwitchMusic) }
        item {
            var closePip by remember { mutableStateOf(Session.closeOnPipClose) }
            MenuRow(Icons.Filled.Settings, "Tutup aplikasi saat PiP ditutup: ${if (closePip) "Aktif" else "Nonaktif"}") {
                closePip = !closePip
                Session.closeOnPipClose = closePip
            }
        }
        item {
            val ctx = LocalContext.current
            var links by remember { mutableStateOf(com.lumitube.app.CrossApp.linksEnabled(ctx)) }
            MenuRow(Icons.Filled.Share, "Buka link YouTube di YouTube Lumi: ${if (links) "Aktif" else "Nonaktif"}") {
                links = !links
                com.lumitube.app.CrossApp.setLinksEnabled(ctx, links)
                if (links) com.lumitube.app.CrossApp.startLinkSetup(ctx)
            }
            if (links) {
                MenuRow(Icons.Filled.Settings, "Atur ulang link (YouTube asli → YouTube Lumi)") {
                    com.lumitube.app.CrossApp.startLinkSetup(ctx)
                }
            }
        }
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
private fun BackBar(title: String?, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    val yt = LocalYt.current
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = yt.text) }
        Text(
            title ?: "", color = yt.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** Kotak cari gaya YouTube (dipakai di halaman cari & cari di channel). */
@Composable
private fun YtSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    onFocus: () -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val yt = LocalYt.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = yt.sub) },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        trailingIcon = if (value.text.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange(TextFieldValue("")); focus.requestFocus() }) {
                    Icon(Icons.Filled.Close, "Hapus", tint = yt.text)
                }
            }
        } else null,
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
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier.height(52.dp).focusRequester(focus).onFocusChanged { if (it.isFocused) onFocus() },
    )
}

@Composable
private fun BrowseScreen(id: String, title: String?, onBack: () -> Unit, open: (Item) -> Unit) {
    val yt = LocalYt.current
    val feed = rememberFeed(key = id, more = { Repo.ytMore(it) }) { Repo.ytBrowse(id) }
    val state = rememberLazyListState()
    LoadMoreEffect(state, feed)
    val page = feed.page
    val isChannel = id.startsWith("UC")
    // Playlist: jumlah video asli dari header ("3 video"). Item setelahnya adalah saran YouTube.
    val plCount = if (id.startsWith("VL")) {
        Regex("(\\d[\\d.,]*)\\s*(?:video|lagu|videos|songs)", RegexOption.IGNORE_CASE).find(page?.subtitle ?: "")
            ?.groupValues?.get(1)?.replace(Regex("[.,]"), "")?.toIntOrNull()
    } else null
    // Playlist milik akun: bisa dihapus, dan isinya bisa dihapus satu per satu.
    val editable = id.startsWith("VL") && id != "VLLL" && Session.loggedIn
    val removed = remember(id) { androidx.compose.runtime.mutableStateListOf<String>() }
    var confirmDelete by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val editScope = rememberCoroutineScope()
    val ownAll = if (plCount != null && plCount < feed.items.size) feed.items.take(plCount) else feed.items.toList()
    // Daftar playlist (Koleksi): titik tiga untuk menghapus playlist milik akun.
    val plDeleter = com.lumitube.app.ui.common.rememberPlaylistDeleter()
    val baseOwn = ownAll.filter { it.id !in removed && it.id !in plDeleter.deleted }
    // Urutan lokal setelah diseret (kunci item); kosong = urutan dari YouTube.
    val order = remember(id) { androidx.compose.runtime.mutableStateListOf<String>() }
    val own = if (order.isEmpty()) baseOwn else {
        order.mapNotNull { k -> baseOwn.firstOrNull { it.key == k } } + baseOwn.filter { it.key !in order }
    }
    val reorder = com.lumitube.app.ui.common.rememberReorderState(
        state,
        canDrop = { k -> own.any { it.key == k && it.setVideoId != null } },
        onMove = { from, to ->
            if (order.isEmpty()) order.addAll(own.map { it.key })
            val a = order.indexOf(from)
            val b = order.indexOf(to)
            if (a >= 0 && b >= 0) order.add(b, order.removeAt(a))
        },
        onEnd = { key ->
            val list = if (order.isEmpty()) own else order.mapNotNull { k -> own.firstOrNull { it.key == k } }
            val i = list.indexOfFirst { it.key == key }
            val item = list.getOrNull(i)
            val sv = item?.setVideoId
            if (item != null && sv != null) {
                editScope.launch {
                    try {
                        Repo.movePlaylistItem(id, sv, list.getOrNull(i - 1)?.setVideoId, list.getOrNull(i + 1)?.setVideoId)
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(ctx, "Gagal menyimpan urutan, memuat ulang playlist…", android.widget.Toast.LENGTH_SHORT).show()
                        // Muat ulang supaya urutan di layar sama dengan yang tersimpan di YouTube.
                        order.clear()
                        feed.load()
                    }
                }
            }
        },
    )
    val extra = (if (plCount != null && plCount < feed.items.size) feed.items.drop(plCount) else emptyList()) +
        (page?.suggestions ?: emptyList())
    // Cari di dalam channel: null = tidak sedang mencari
    var searching by rememberSaveable(id) { mutableStateOf(false) }
    var field by remember(id) { mutableStateOf(TextFieldValue("")) }
    var query by rememberSaveable(id) { mutableStateOf("") }
    var editing by remember(id) { mutableStateOf(true) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = searching) {
        if (editing && query.isNotEmpty()) {
            editing = false
            focusManager.clearFocus()
        } else {
            searching = false
            query = ""
            field = TextFieldValue("")
        }
    }
    if (searching) {
        fun submit(q: String) {
            val t = q.trim()
            if (t.isEmpty()) return
            field = TextFieldValue(t, TextRange(t.length))
            query = t
            editing = false
            focusManager.clearFocus()
        }
        LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { searching = false; query = ""; field = TextFieldValue("") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = yt.text)
                }
                YtSearchField(
                    field, { field = it }, "Telusuri ${page?.title ?: "channel"}",
                    onSearch = { submit(field.text) }, onFocus = { editing = true }, focus = focus,
                    modifier = Modifier.weight(1f),
                )
            }
            if (editing && field.text.isNotBlank() && field.text.trim() != query) {
                // Saran mengikuti teks yang diketik (tanpa histori global).
                Box(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { submit(field.text) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, null, tint = yt.text)
                        Text("Cari \"${field.text.trim()}\" di channel ini", color = yt.text, fontSize = 15.sp, modifier = Modifier.padding(start = 18.dp))
                    }
                }
            } else if (query.isNotEmpty()) {
                val results = rememberFeed(key = "$id|search|$query", more = { Repo.ytMore(it) }) { Repo.ytChannelSearch(id, query) }
                val rs = rememberLazyListState()
                LoadMoreEffect(rs, results)
                LazyColumn(state = rs, modifier = Modifier.fillMaxSize()) {
                    if (results.loading && results.items.isEmpty()) item { LoadingBox() }
                    results.error?.let { err -> item { MessageBox("Gagal mencari: $err", yt.sub, "Coba lagi") { results.load() } } }
                    if (!results.loading && results.error == null && results.items.isEmpty()) {
                        item { MessageBox("Tidak ada video yang cocok di channel ini.", yt.sub, null) {} }
                    }
                    items(results.items, key = { it.key }) { v -> VideoRow(v) { open(v) } }
                    if (results.continuation != null && results.items.isNotEmpty()) item { LoadingBox() }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        BackBar(page?.title ?: title, onBack) {
            if (isChannel) {
                IconButton(onClick = { searching = true; editing = true }) {
                    Icon(Icons.Filled.Search, "Cari di channel", tint = yt.text)
                }
            }
            if (editable && id != "VLWL") {
                com.lumitube.app.ui.common.ItemMenu(listOf("Hapus playlist" to { confirmDelete = true }), yt.text)
            }
        }
        if (confirmDelete) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Hapus playlist?") },
                text = { Text("\"${page?.title ?: title ?: "Playlist"}\" akan dihapus permanen dari akunmu.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        confirmDelete = false
                        editScope.launch {
                            try {
                                Repo.deletePlaylist(id)
                                android.widget.Toast.makeText(ctx, "Playlist dihapus", android.widget.Toast.LENGTH_SHORT).show()
                                onBack()
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(ctx, "Gagal menghapus (hanya playlist milikmu)", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("Hapus") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
            )
        }
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
                        val videos = own.filter { it.type == ItemType.VIDEO }
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
                            PlaylistOfflineRow(feed, videos, open, plCount)
                        }
                    }
                }
            }
            if (feed.loading && feed.items.isEmpty()) item { LoadingBox() }
            feed.error?.let { err -> item { MessageBox("Gagal memuat: $err", yt.sub, "Coba lagi") { feed.load() } } }
            items(own, key = { it.key }) { item ->
                when (item.type) {
                    ItemType.CHANNEL -> ChannelRow(item) { open(item) }
                    else -> Row(
                        if (editable && item.setVideoId != null) Modifier.reorderItem(reorder, item.key) else Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    if (editable && item.setVideoId != null) {
                        com.lumitube.app.ui.common.DragHandle(reorder, item.key, yt.sub)
                    }
                    Box(Modifier.weight(1f)) {
                    VideoRow(
                        item,
                        menu = if (editable) listOf(
                            "Hapus dari playlist" to {
                                removed.add(item.id)
                                editScope.launch {
                                    try {
                                        Repo.setInPlaylist(id.removePrefix("VL"), item.id, false)
                                        android.widget.Toast.makeText(ctx, "Dihapus dari playlist", android.widget.Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {
                                        removed.remove(item.id)
                                        android.widget.Toast.makeText(ctx, "Gagal menghapus (hanya playlist milikmu)", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        ) else if (id == "FEplaylist_aggregation") plDeleter.menu(item) else null,
                    ) {
                        val videos = own.filter { it.type == ItemType.VIDEO }
                        val idx = videos.indexOfFirst { it.id == item.id }
                        if (!isChannel && idx >= 0) {
                            PlaybackManager.playVideos(videos, idx)
                        }
                        open(item)
                    }
                    }
                    }
                }
            }
            val sugg = extra.distinctBy { it.key }.filter { s -> own.none { it.key == s.key } }
            if (sugg.isNotEmpty()) {
                item {
                    Text(
                        "Saran", color = yt.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
                    )
                }
                items(sugg, key = { "s" + it.key }) { v -> VideoRow(v) { open(v) } }
            }
            if (feed.continuation != null && feed.items.isNotEmpty()) item { LoadingBox() }
        }
    }
}

/** Tombol "Download semua" dan "Putar offline" untuk playlist. */
@Composable
private fun PlaylistOfflineRow(feed: FeedLoader, videos: List<Item>, open: (Item) -> Unit, limit: Int?) {
    val yt = LocalYt.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ids = videos.map { it.id }
    val offline = ids.mapNotNull { id -> com.lumitube.app.data.Downloader.records.firstOrNull { it.videoId == id && !it.audioOnly } }
    Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(yt.chip).clickable {
                scope.launch {
                    // Ambil semua isi playlist (bukan hanya yang sudah tampil), lalu download satu per satu.
                    val all = feed.items.toMutableList()
                    var c = if (limit != null && all.size >= limit) null else feed.continuation
                    try {
                        while (c != null) {
                            val p = Repo.ytMore(c)
                            all += p.items
                            c = if (limit != null && all.size >= limit) null else p.continuation
                        }
                    } catch (_: Exception) {
                    }
                    val list = (if (limit != null) all.take(limit) else all).filter { it.type == ItemType.VIDEO }
                    all.clear(); all.addAll(list)
                    val todo = all.distinctBy { it.id }.count { !com.lumitube.app.data.Downloader.isDownloaded(it.id, false) }
                    com.lumitube.app.data.Downloader.downloadAll(all, audioOnly = false)
                    android.widget.Toast.makeText(
                        context,
                        if (todo == 0) "Semua video sudah di-download" else "Mendownload $todo video… (lihat di Download)",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Download, null, tint = yt.text, modifier = Modifier.size(20.dp))
            Text("Download semua", color = yt.text, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        }
        if (offline.isNotEmpty()) {
            Row(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(yt.chip).clickable {
                    val items = offline.map { it.toItem().copy(type = ItemType.VIDEO) }
                    PlaybackManager.playVideos(items, 0)
                    open(items[0])
                },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.DownloadForOffline, null, tint = yt.text, modifier = Modifier.size(20.dp))
                Text("Putar offline (${offline.size})", color = yt.text, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Pencarian

@Composable
private fun SearchScreen(onBack: () -> Unit, open: (Item) -> Unit, openChannel: (String) -> Unit) {
    val yt = LocalYt.current
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(true) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        if (query.isEmpty()) focus.requestFocus() else field = TextFieldValue(query, TextRange(query.length))
    }
    fun submit(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        field = TextFieldValue(t, TextRange(t.length))
        query = t
        Session.addSearch(t)
        editing = false
        focusManager.clearFocus()
    }
    BackHandler(enabled = editing && query.isNotEmpty()) {
        editing = false
        field = TextFieldValue(query, TextRange(query.length))
        focusManager.clearFocus()
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = yt.text) }
            YtSearchField(
                field, { field = it }, "Telusuri YouTube",
                onSearch = { submit(field.text) }, onFocus = { editing = true }, focus = focus,
                modifier = Modifier.weight(1f),
            )
        }
        if (editing || query.isEmpty()) {
            SearchSuggestions(
                text = field.text, textColor = yt.text, subColor = yt.sub,
                onPick = { submit(it) },
                onFill = { field = TextFieldValue("$it ", TextRange(it.length + 1)); focus.requestFocus() },
            )
        } else {
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

    // Bab video untuk garis putus-putus di timeline.
    LaunchedEffect(info) {
        if (info != null && PlaybackManager.current?.id == current.id) PlaybackManager.chapters = info?.chapters ?: emptyList()
    }

    // Putar otomatis: saat video selesai, lanjut ke video terkait pertama.
    LaunchedEffect(info) {
        val next = info?.related?.firstOrNull { it.type == ItemType.VIDEO }
        if (next != null && PlaybackManager.current?.id == current.id) PlaybackManager.relatedNext = next
    }

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
        val nextRelated = info?.related?.firstOrNull { it.type == ItemType.VIDEO }
        val chatToken = info?.liveChatToken
        // Layar penuh + live chat terbuka: video di kiri, live chat di kanan (seperti YouTube).
        val chatSide = full && panel == 3 && chatToken != null
        Row(if (full) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            YtVideoPlayer(
                modifier = if (full) Modifier.weight(1f).fillMaxHeight() else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                full = full,
                onFullscreen = { full = it },
                onMinimize = onBack,
                onSettings = { panel = 2 },
                onNext = if (nextRelated != null || PlaybackManager.index + 1 < PlaybackManager.queue.size) {
                    { PlaybackManager.nextVideo() }
                } else null,
                onLiveChat = if (full && chatToken != null && panel != 3) ({ panel = 3 }) else null,
            )
            if (chatSide && chatToken != null) {
                com.lumitube.app.ui.common.LiveChatPanel(
                    chatToken, current.id, ytPanelColors(), side = true,
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.38f),
                ) { panel = 0 }
            }
        }
        if (!full) {
            WatchDetails(
                current, info, onOpen, onChannel, onLogin, onPlayInMusic,
                onComments = { panel = if (info?.liveChatToken != null) 3 else 1 },
                onSettings = { panel = 2 },
                onDownload = { downloadItem = current },
                onSave = { panel = 4 },
            )
        }
    }
    when (panel) {
        1 -> CommentsPanel(
            token = info?.commentsToken, count = info?.commentCount, colors = ytPanelColors(),
            onClose = { panel = 0 }, onChannel = { panel = 0; onChannel(it) },
        )
        2 -> YtSettingsSheet(onListenMusic = { panel = 0; onPlayInMusic(current) }) { panel = 0 }
        4 -> com.lumitube.app.ui.common.AddToPlaylistSheet(current.id, false, ytPanelColors()) { panel = 0 }
        3 -> if (!full) info?.liveChatToken?.let { t ->
            com.lumitube.app.ui.common.LiveChatPanel(t, current.id, ytPanelColors()) { panel = 0 }
        }
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
    onSave: () -> Unit,
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
                Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp).animateContentSize()) {
                    Text(
                        i?.title ?: current.title, color = yt.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        maxLines = if (expanded) 10 else 2, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp,
                    )
                    val meta = listOfNotNull(i?.views, i?.date).joinToString("  ")
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (i?.channel ?: current.channelName)?.let { if (it.startsWith("@")) it else it } ?: "",
                            color = yt.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        Text("  $meta  ...selengkapnya", color = yt.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (expanded) {
                        i?.description?.let {
                            Box(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(yt.surface).padding(12.dp)) {
                                val dl = i?.descriptionLinks ?: emptyList()
                                val desc = remember(it, dl) {
                                    com.lumitube.app.ui.common.descriptionText(it, dl) { target ->
                                        when {
                                            target.startsWith("t:") -> target.substring(2).toLongOrNull()?.let { ms ->
                                                PlaybackManager.exo.seekTo(ms)
                                            }
                                            target.startsWith("c:") -> {
                                                val id = target.substring(2)
                                                if (id.startsWith("UC")) onChannel(id)
                                            }
                                            target.startsWith("v:") -> onOpen(Item(ItemType.VIDEO, target.substring(2), ""))
                                            else -> try {
                                                context.startActivity(
                                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(target))
                                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                }
                                Text(desc, color = yt.text, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            item {
                // Baris aksi seperti aplikasi YouTube: avatar, Subscribe, lalu suka/tidak suka/bagikan/simpan/unduh
                var disliked by remember(current.id) { mutableStateOf(false) }
                val chId = i?.channelId ?: current.channelId
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Img(
                        i?.channelThumb ?: current.channelThumb,
                        Modifier.size(36.dp).clip(CircleShape).clickable(enabled = chId != null) { chId?.let(onChannel) }
                    )
                    Spacer(Modifier.width(10.dp))
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
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (subscribed) "Disubscribe" else "Subscribe",
                            color = if (subscribed) yt.text else yt.bg, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // Suka (dengan jumlah)
                    Row(
                        Modifier.clip(RoundedCornerShape(18.dp)).clickable {
                            if (!Session.loggedIn) {
                                onLogin()
                            } else {
                                val target = !liked
                                liked = target
                                if (target) disliked = false
                                scope.launch {
                                    try {
                                        InnerTube.like(current.id, target)
                                    } catch (_: Exception) {
                                        liked = !target
                                    }
                                }
                            }
                        }.padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Suka", tint = yt.text, modifier = Modifier.size(24.dp))
                        i?.likes?.let { Text(" $it", color = yt.text, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    }
                    WatchIcon(if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, "Tidak suka") {
                        if (!Session.loggedIn) {
                            onLogin()
                        } else {
                            val target = !disliked
                            disliked = target
                            if (target) liked = false
                            scope.launch {
                                try {
                                    InnerTube.dislike(current.id, target)
                                } catch (_: Exception) {
                                    disliked = !target
                                }
                            }
                        }
                    }
                    WatchIcon(Icons.AutoMirrored.Outlined.Reply, "Bagikan", mirror = true) {
                        shareVideo(context, current.id, current.title, false)
                    }
                    WatchIcon(Icons.AutoMirrored.Filled.PlaylistAdd, "Simpan") {
                        if (Session.loggedIn) onSave() else onLogin()
                    }
                    WatchIcon(Icons.Outlined.Download, "Unduh") { onDownload() }
                }
            }
            if (i?.liveChatToken != null) item {
                com.lumitube.app.ui.common.LiveChatCard(ytPanelColors(), onComments)
            } else item {
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
private fun WatchIcon(icon: ImageVector, label: String, mirror: Boolean = false, onClick: () -> Unit) {
    val yt = LocalYt.current
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, label, tint = yt.text,
            modifier = Modifier.size(24.dp).then(if (mirror) Modifier.graphicsLayer { scaleX = -1f } else Modifier),
        )
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
