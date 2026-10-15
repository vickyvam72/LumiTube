package com.lumitube.app.ui.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.data.Chip
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.lumitube.app.data.Item
import com.lumitube.app.data.ItemType
import com.lumitube.app.data.Repo
import com.lumitube.app.data.Session
import com.lumitube.app.player.PlaybackManager
import com.lumitube.app.ui.common.FeedLoader
import com.lumitube.app.ui.common.Img
import com.lumitube.app.ui.common.LoadingBox
import com.lumitube.app.ui.common.MessageBox
import com.lumitube.app.ui.common.MusicColors
import com.lumitube.app.ui.common.rememberFeed
import com.lumitube.app.ui.common.DownloadsList
import com.lumitube.app.ui.common.MusicPanelColors
import com.lumitube.app.ui.common.toItem
import com.lumitube.app.ui.common.reorderItem

sealed interface MRoute {
    data class Browse(val id: String, val params: String?, val title: String?) : MRoute
    data object Downloads : MRoute
}

/** Putar download tanpa internet (semua jadi satu antrean). */
private val playOffline: (List<com.lumitube.app.data.DownloadRecord>, Int) -> Unit = { recs, start ->
    val items = recs.map { it.toItem().copy(type = ItemType.SONG) }
    PlaybackManager.playMusic(items, start.coerceIn(0, items.lastIndex), radio = false)
}

@Composable
private fun OfflineScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White) }
            Text("Download (offline)", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        DownloadsList(MusicPanelColors, playOffline) { }
    }
}

/** Aplikasi "YouTube Music" di dalam LumiTube. */
@Composable
fun MusicApp(
    pendingPlay: Item?,
    onPendingConsumed: () -> Unit,
    onSwitchVideo: () -> Unit,
    onLogin: () -> Unit,
) {
    val stack = remember { mutableStateListOf<MRoute>() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var homeTop by remember { mutableIntStateOf(0) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(pendingPlay) {
        if (pendingPlay != null) {
            val song = pendingPlay.copy(type = ItemType.SONG)
            if (PlaybackManager.current?.id == song.id) {
                PlaybackManager.switchMode(video = false)
            } else {
                PlaybackManager.playMusic(listOf(song), 0, radio = song.localUri == null)
            }
            showPlayer = true
            onPendingConsumed()
        }
    }

    val onItem: (Item, List<Item>) -> Unit = { item, context ->
        when (item.type) {
            ItemType.SONG, ItemType.VIDEO, ItemType.SHORT -> {
                val song = item.copy(type = ItemType.SONG)
                val songs = context.filter { it.type == ItemType.SONG && it.row }
                if (item.row && songs.size > 1 && songs.any { it.id == item.id }) {
                    PlaybackManager.playMusic(songs, songs.indexOfFirst { it.id == item.id }, radio = false)
                } else {
                    PlaybackManager.playMusic(listOf(song), 0, radio = true)
                }
            }
            else -> stack.add(MRoute.Browse(item.id, null, item.title))
        }
    }
    val openBrowse: (String, String?, String?) -> Unit = { id, params, title -> stack.add(MRoute.Browse(id, params, title)) }
    // Video diputar di pemutar penuh (mode Video), tetap di dalam YT Music Lumi.
    val onOpenVideo: (Item) -> Unit = { item ->
        if (PlaybackManager.current?.id == item.id) {
            PlaybackManager.switchMode(video = true)
        } else {
            PlaybackManager.playVideo(item.copy(type = ItemType.VIDEO))
        }
        showPlayer = true
    }

    BackHandler(enabled = showPlayer || stack.isNotEmpty() || tab != 0) {
        when {
            showPlayer -> showPlayer = false
            stack.isNotEmpty() -> stack.removeAt(stack.lastIndex)
            else -> tab = 0
        }
    }

    val loggedIn = Session.loggedIn
    var chipIndex by remember(loggedIn) { mutableIntStateOf(-1) }
    var homeChips by remember(loggedIn) { mutableStateOf(emptyList<Chip>()) }
    val selectedChip = homeChips.getOrNull(chipIndex)
    val home = rememberFeed(
        key = "mhome$loggedIn${selectedChip?.params}", more = { Repo.musicMore(it) },
        peek = if (selectedChip == null) ({ Repo.musicHomeCached() }) else null,
    ) {
        Repo.musicBrowse(selectedChip?.browseId ?: "FEmusic_home", selectedChip?.params)
    }
    LaunchedEffect(home.page) {
        val c = home.page?.chips ?: emptyList()
        if (homeChips.isEmpty() && c.isNotEmpty()) homeChips = c
    }

    Box(
        Modifier.fillMaxSize().background(MusicColors.bg)
    ) {
        Box(
            Modifier.fillMaxWidth().height(420.dp)
                .background(Brush.verticalGradient(listOf(MusicColors.top, MusicColors.bg)))
        )
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                when (val top = stack.lastOrNull()) {
                    is MRoute.Browse -> MusicBrowseScreen(top, { stack.removeAt(stack.lastIndex) }, onItem, openBrowse)
                    MRoute.Downloads -> OfflineScreen { stack.removeAt(stack.lastIndex) }
                    null -> when (tab) {
                        0 -> MusicHome(home, homeChips, chipIndex, { i -> chipIndex = if (chipIndex == i) -1 else i }, onItem, openBrowse, onSwitchVideo, onLogin, onOffline = { stack.add(MRoute.Downloads) }, scrollToTop = homeTop) { showSettings = true }
                        1 -> ExploreScreen(onItem, openBrowse)
                        2 -> MusicSearchScreen(onItem)
                        else -> LibraryScreen(onItem, onLogin, onOpenVideo)
                    }
                }
            }
            if (PlaybackManager.current != null) MusicMiniPlayer(onOpen = { showPlayer = true })
            MusicBottomNav(tab) {
                if (it == 0 && tab == 0 && stack.isEmpty()) homeTop++
                stack.clear(); tab = it
            }
        }
        AnimatedVisibility(
            visible = showPlayer && PlaybackManager.current != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            FullPlayer(
                onClose = { showPlayer = false },
                onOpenArtist = { id -> showPlayer = false; openBrowse(id, null, null) },
                onOpenVideo = onOpenVideo,
            )
        }
        if (showSettings) MusicSettingsPanel { showSettings = false }
    }
}

// ---------------------------------------------------------------- Beranda

@Composable
private fun MusicTopBar(onSwitchVideo: () -> Unit, onLogin: () -> Unit, onSettings: () -> Unit, onOffline: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MusicLogo()
        Spacer(Modifier.weight(1f))
        com.lumitube.app.ui.common.TogetherButton(Color.White)
        IconButton(onClick = onOffline) {
            Icon(Icons.Filled.DownloadForOffline, "Download (offline)", tint = Color.White)
        }
        IconButton(onClick = onSwitchVideo) {
            Icon(Icons.Filled.OndemandVideo, "Buka YouTube Lumi", tint = Color.White)
        }
        Box {
            Box(Modifier.padding(horizontal = 8.dp)) { Avatar(34) { menu = true } }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                val acc = Session.account
                if (Session.loggedIn) {
                    DropdownMenuItem(text = { Text(acc?.name ?: "Akun Google") }, onClick = { menu = false })
                    DropdownMenuItem(text = { Text("Keluar") }, onClick = { menu = false; Session.logout() })
                } else {
                    DropdownMenuItem(text = { Text("Login") }, onClick = { menu = false; onLogin() })
                }
                DropdownMenuItem(text = { Text("Setelan") }, onClick = { menu = false; onSettings() })
                DropdownMenuItem(text = { Text("Buka YouTube Lumi") }, onClick = { menu = false; onSwitchVideo() })
            }
        }
    }
}

@Composable
private fun MusicHome(
    feed: FeedLoader,
    chips: List<Chip>,
    chipIndex: Int,
    onChip: (Int) -> Unit,
    onItem: (Item, List<Item>) -> Unit,
    openBrowse: (String, String?, String?) -> Unit,
    onSwitchVideo: () -> Unit,
    onLogin: () -> Unit,
    onOffline: () -> Unit,
    scrollToTop: Int = 0,
    onSettings: () -> Unit,
) {
    val state = rememberLazyListState()
    // Ketuk "Beranda" saat sudah di Beranda = langsung kembali ke paling atas.
    var handledTop by remember { mutableIntStateOf(scrollToTop) }
    LaunchedEffect(scrollToTop) {
        if (scrollToTop != handledTop) {
            handledTop = scrollToTop
            state.scrollToItem(0)
        }
    }
    val shelves = feed.page?.shelves ?: emptyList()
    // Siapkan stream beberapa lagu teratas (Pilihan cepat dll.) supaya langsung bisa diputar.
    LaunchedEffect(shelves.firstOrNull()?.items?.firstOrNull()?.id) {
        kotlinx.coroutines.delay(300)
        shelves.take(2).flatMap { sh -> sh.items.filter { it.type == ItemType.SONG }.take(2) }
            .forEach { com.lumitube.app.data.StreamResolver.prefetch(it.id) }
    }
    val nearEnd by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(nearEnd, shelves.size, feed.continuation) { if (nearEnd) feed.loadMore() }

    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        item { MusicTopBar(onSwitchVideo, onLogin, onSettings, onOffline) }
        if (feed.error != null && shelves.isEmpty() && com.lumitube.app.data.Downloader.records.isNotEmpty()) {
            item { MessageBox("Sedang offline? Putar lagu yang sudah kamu download.", MusicColors.sub, "Buka Download", onOffline) }
        }
        if (chips.isNotEmpty()) item { MoodChips(chips, chipIndex, onChip) }
        if (feed.loading && shelves.isEmpty()) item { LoadingBox(color = Color.White) }
        feed.error?.let { err ->
            item { MessageBox("Gagal memuat: $err", MusicColors.sub, "Coba lagi") { feed.load() } }
        }
        itemsIndexed(shelves) { i, shelf ->
            ShelfView(shelf, i, onItem) { s -> s.moreBrowseId?.let { openBrowse(it, s.moreParams, s.title) } }
        }
        if (!Session.loggedIn && shelves.isNotEmpty()) {
            item {
                MessageBox("Login untuk rekomendasi pribadi, playlist, dan lagu yang kamu sukai.", MusicColors.sub, "Login", onLogin)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------------------------------------------------------------- Jelajahi

@Composable
private fun ExploreScreen(onItem: (Item, List<Item>) -> Unit, openBrowse: (String, String?, String?) -> Unit) {
    val feed = rememberFeed(key = "explore") { Repo.musicBrowse("FEmusic_explore") }
    val page = feed.page
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("Jelajahi") }
        if (feed.loading && page == null) item { LoadingBox(color = Color.White) }
        feed.error?.let { err -> item { MessageBox("Gagal memuat: $err", MusicColors.sub, "Coba lagi") { feed.load() } } }
        val chips = page?.chips?.filter { it.browseId != null } ?: emptyList()
        if (chips.isNotEmpty()) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(chips) { c -> ExploreButton(c.title) { openBrowse(c.browseId!!, c.params, c.title) } }
                }
            }
        }
        itemsIndexed(page?.shelves ?: emptyList()) { i, shelf ->
            ShelfView(shelf, i + 1, onItem) { s -> s.moreBrowseId?.let { openBrowse(it, s.moreParams, s.title) } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ExploreButton(title: String, onClick: () -> Unit) {
    Box(
        Modifier.height(48.dp).clip(RoundedCornerShape(8.dp)).background(MusicColors.card)
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun PageTitle(text: String) {
    Text(text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
}

// ---------------------------------------------------------------- Telusuri

/**
 * Status pencarian disimpan di luar layar: buka playlist/album dari hasil pencarian lalu
 * kembali = tetap di hasil pencarian (bukan kolom ketik kosong).
 */
private object MusicSearchState {
    var query by mutableStateOf("")
    var editing by mutableStateOf(true)
    var chips by mutableStateOf(emptyList<Chip>())
    var chip by mutableStateOf<Chip?>(null)
}

@Composable
private fun MusicSearchScreen(onItem: (Item, List<Item>) -> Unit) {
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var query by MusicSearchState::query
    var editing by MusicSearchState::editing
    val focus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(Unit) {
        if (query.isEmpty()) focus.requestFocus()
        else field = androidx.compose.ui.text.input.TextFieldValue(query, androidx.compose.ui.text.TextRange(query.length))
    }
    fun submit(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        editing = false
        focusManager.clearFocus()
        field = androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(t.length))
        if (query != t) { MusicSearchState.chips = emptyList(); MusicSearchState.chip = null }
        query = t
        Session.addSearch(t)
    }
    BackHandler(enabled = editing && query.isNotEmpty()) {
        editing = false
        focusManager.clearFocus()
    }
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = field,
            // Setelah memilih saran, keyboard kadang mengirim ulang teks lama ("tre"); abaikan saat tidak mengetik.
            onValueChange = { if (editing) field = it },
            placeholder = { Text("Telusuri lagu, artis, album", color = MusicColors.sub) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            trailingIcon = if (field.text.isNotEmpty()) {
                {
                    IconButton(onClick = { editing = true; field = androidx.compose.ui.text.input.TextFieldValue(""); focus.requestFocus() }) {
                        Icon(Icons.Filled.Close, "Hapus", tint = Color.White)
                    }
                }
            } else null,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MusicColors.card,
                unfocusedContainerColor = MusicColors.card,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit(field.text) }),
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus)
                .onFocusChanged { if (it.isFocused) editing = true },
        )
        if (editing || query.isEmpty()) {
            com.lumitube.app.ui.common.SearchSuggestions(
                text = field.text, textColor = Color.White, subColor = MusicColors.sub,
                onPick = { submit(it) },
                onFill = {
                    field = androidx.compose.ui.text.input.TextFieldValue("$it ", androidx.compose.ui.text.TextRange(it.length + 1))
                    focus.requestFocus()
                },
            )
        } else {
            // Filter seperti YT Music asli: Lagu, Artis, Album, Playlist komunitas, Video, …
            var chips by MusicSearchState::chips
            var chip by MusicSearchState::chip
            val feed = rememberFeed(key = "msearch|$query|${chip?.params}", more = { Repo.musicSearchMore(it) }) {
                Repo.musicSearch(query, chip?.params)
            }
            LaunchedEffect(feed.page) {
                val c = feed.page?.chips ?: emptyList()
                if (chips.isEmpty() && c.isNotEmpty()) chips = c
            }
            Column {
                if (chips.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        items(chips.size) { i ->
                            val c = chips[i]
                            val on = chip?.title == c.title
                            Box(
                                Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Color.White else MusicColors.chip)
                                    .clickable { chip = if (on) null else c }.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) { Text(c.title, color = if (on) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
                ShelfList(feed, onItem, search = true)
            }
        }
    }
}

/** Daftar shelf vertikal (hasil pencarian / koleksi). */
@Composable
private fun ShelfList(
    feed: FeedLoader,
    onItem: (Item, List<Item>) -> Unit,
    header: @Composable () -> Unit = {},
    deleter: com.lumitube.app.ui.common.PlaylistDeleter? = null,
    search: Boolean = false,
) {
    val shelves = feed.page?.shelves ?: emptyList()
    val state = rememberLazyListState()
    if (search) {
        val nearEnd by remember {
            derivedStateOf {
                val info = state.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
            }
        }
        LaunchedEffect(nearEnd, shelves.size, feed.continuation) { if (nearEnd) feed.loadMore() }
    }
    LazyColumn(Modifier.fillMaxSize(), state = state) {
        item { header() }
        if (feed.loading && shelves.isEmpty()) item { LoadingBox(color = Color.White) }
        feed.error?.let { err -> item { MessageBox("Gagal memuat: $err", MusicColors.sub, "Coba lagi") { feed.load() } } }
        if (!feed.loading && feed.error == null && shelves.isEmpty()) {
            item { MessageBox("Tidak ada apa-apa di sini.", MusicColors.sub) }
        }
        shelves.forEachIndexed { si, shelf ->
            // Hasil teratas di pencarian: kartu besar seperti YT Music asli, isinya di bawah.
            if (search && si == 0 && shelf.strapline != "search" && shelf.items.isNotEmpty()) {
                val top = shelf.items.first()
                item(key = "top") { TopResultCard(top, onPlay = { onItem(top, listOf(top)) }) { onItem(top, shelf.items) } }
                items(shelf.items.drop(1).filter { deleter == null || it.id !in deleter.deleted }, key = { "s0" + it.key }) { item ->
                    SongRow(item) { onItem(item, shelf.items) }
                }
                return@forEachIndexed
            }
            if (shelf.title.isNotBlank()) {
                item(key = "h$si") {
                    Text(shelf.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp))
                }
            }
            items(shelf.items.filter { deleter == null || it.id !in deleter.deleted }, key = { "s$si" + it.key }) { item ->
                SongRow(item, menu = deleter?.menu(item)) { onItem(item, shelf.items) }
            }
        }
        items(feed.items.filter { deleter == null || it.id !in deleter.deleted }, key = { "x" + it.key }) { item ->
            SongRow(item, menu = deleter?.menu(item)) { onItem(item, feed.items) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------------------------------------------------------------- Koleksi

private val libraryTabs = listOf(
    "Playlist" to "FEmusic_liked_playlists",
    "Lagu disukai" to "FEmusic_liked_videos",
    "Album" to "FEmusic_liked_albums",
    "Artis" to "FEmusic_library_corpus_track_artists",
    "Riwayat" to "FEmusic_history",
    "Download" to "",
)

@Composable
private fun LibraryScreen(onItem: (Item, List<Item>) -> Unit, onLogin: () -> Unit, onOpenVideo: (Item) -> Unit) {
    var sel by rememberSaveable { mutableIntStateOf(0) }
    val browseId = libraryTabs[sel].second
    val chips: @Composable () -> Unit = {
        Column {
            PageTitle("Koleksi")
            MoodChips(libraryTabs.map { Chip(it.first) }, sel) { sel = it }
        }
    }
    when {
        browseId.isEmpty() -> Column {
            chips()
            DownloadsList(MusicPanelColors, playOffline) { rec ->
                val it = rec.toItem()
                if (rec.audioOnly) PlaybackManager.playMusic(listOf(it), 0, radio = false) else onOpenVideo(it)
            }
        }
        !Session.loggedIn -> Column {
            chips()
            MessageBox("Login untuk melihat playlist, album, artis, dan riwayat YouTube Music kamu.", MusicColors.sub, "Login", onLogin)
        }
        else -> {
            val feed = rememberFeed(key = browseId, more = { Repo.musicMore(it) }) { Repo.musicBrowse(browseId) }
            val deleter = com.lumitube.app.ui.common.rememberPlaylistDeleter()
            ShelfList(feed, onItem, chips, deleter = if (browseId == "FEmusic_liked_playlists") deleter else null)
        }
    }
}

// ---------------------------------------------------------------- Album / Playlist / Artis

@Composable
private fun MusicBrowseScreen(
    route: MRoute.Browse,
    onBack: () -> Unit,
    onItem: (Item, List<Item>) -> Unit,
    openBrowse: (String, String?, String?) -> Unit,
) {
    val feed = rememberFeed(key = route.id + route.params, more = { Repo.musicMore(it) }) {
        Repo.musicBrowse(route.id, route.params)
    }
    val page = feed.page
    val state = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, feed.items.size, page?.shelves?.size, feed.continuation) { if (nearEnd) feed.loadMore() }

    val shelves = page?.shelves ?: emptyList()
    val isArtist = route.id.startsWith("UC") || route.id.startsWith("MPAD")
    val isAlbum = route.id.startsWith("MPRE")
    // Playlist milik akun: bisa dihapus dan lagunya dihapus satu per satu.
    val editable = route.id.startsWith("VL") && route.id != "VLLM" && Session.loggedIn
    val removed = remember(route.id) { androidx.compose.runtime.mutableStateListOf<String>() }
    var confirmDelete by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val editScope = com.lumitube.app.ui.common.EditScope.scope
    val appCtx = ctx.applicationContext
    val feedKey = route.id + route.params
    // Lagu playlist milik akun yang bisa diurutkan ulang (punya setVideoId), dalam satu daftar.
    fun movable(it: Item) = editable && it.type == ItemType.SONG && it.setVideoId != null
    val basePl = (shelves.flatMap { s -> s.items.filter { it.row && movable(it) } } + feed.items.filter { movable(it) })
        .distinctBy { it.key }.filter { it.id !in removed }
    val order = remember(route.id) { androidx.compose.runtime.mutableStateListOf<String>() }
    val plItems = if (order.isEmpty()) basePl else {
        order.mapNotNull { k -> basePl.firstOrNull { it.key == k } } + basePl.filter { it.key !in order }
    }
    val allSongs = (plItems + shelves.flatMap { s -> s.items.filter { it.type == ItemType.SONG && it.row && !movable(it) } } +
        feed.items.filter { it.type == ItemType.SONG && !movable(it) })
        .distinctBy { it.id }
    val reorder = com.lumitube.app.ui.common.rememberReorderState(
        state,
        canDrop = { k -> plItems.any { "p" + it.key == k } },
        onMove = { from, to ->
            if (order.isEmpty()) order.addAll(plItems.map { it.key })
            val a = order.indexOf((from as String).removePrefix("p"))
            val b = order.indexOf((to as String).removePrefix("p"))
            if (a >= 0 && b >= 0) order.add(b, order.removeAt(a))
        },
        onEnd = { key ->
            val list = plItems
            val i = list.indexOfFirst { "p" + it.key == key }
            val sv = list.getOrNull(i)?.setVideoId
            if (sv != null) {
                com.lumitube.app.ui.common.FeedLoader.invalidate(feedKey)
                editScope.launch {
                    try {
                        Repo.movePlaylistItem(route.id, sv, list.getOrNull(i - 1)?.setVideoId, list.getOrNull(i + 1)?.setVideoId)
                        com.lumitube.app.ui.common.FeedLoader.invalidate(feedKey)
                    } catch (e: Exception) {
                        com.lumitube.app.ui.common.FeedLoader.invalidate(feedKey)
                        android.widget.Toast.makeText(appCtx, "Gagal menyimpan urutan: ${e.message ?: e}", android.widget.Toast.LENGTH_LONG).show()
                        // Muat ulang supaya urutan di layar sama dengan yang tersimpan di YouTube.
                        order.clear()
                        feed.load()
                    }
                }
            }
        },
    )
    fun removeMenu(item: Item): List<Pair<String, () -> Unit>>? = if (!editable || item.type != ItemType.SONG) null else listOf(
        "Hapus dari playlist" to {
            removed.add(item.id)
            editScope.launch {
                try {
                    Repo.setInPlaylist(route.id.removePrefix("VL"), item.id, false)
                    com.lumitube.app.ui.common.FeedLoader.invalidate(feedKey)
                    android.widget.Toast.makeText(ctx, "Dihapus dari playlist", android.widget.Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    removed.remove(item.id)
                    android.widget.Toast.makeText(ctx, "Gagal menghapus (hanya playlist milikmu)", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        },
    )

    fun playFrom(item: Item) {
        val idx = allSongs.indexOfFirst { it.id == item.id }
        if (idx >= 0 && !isArtist) {
            PlaybackManager.playMusic(allSongs.map { it.copy(thumbnail = it.thumbnail ?: page?.thumbnail) }, idx, radio = false)
        } else {
            onItem(item, emptyList())
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            if (editable) com.lumitube.app.ui.common.ItemMenu(listOf("Hapus playlist" to { confirmDelete = true }), Color.White)
        }
        if (confirmDelete) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Hapus playlist?") },
                text = { Text("\"${page?.title ?: route.title ?: "Playlist"}\" akan dihapus permanen dari akunmu.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        confirmDelete = false
                        editScope.launch {
                            try {
                                Repo.deletePlaylist(route.id)
                                com.lumitube.app.ui.common.FeedLoader.clear()
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
            if (page != null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isArtist) {
                            Img(page.thumbnail, Modifier.size(200.dp).clip(CircleShape))
                        } else {
                            Img(page.thumbnail, Modifier.size(240.dp).clip(RoundedCornerShape(8.dp)))
                        }
                        Text(
                            page.title ?: route.title ?: "", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp),
                        )
                        page.subtitle?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = MusicColors.sub, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                        }
                        page.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = MusicColors.sub, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                        }
                        if (allSongs.isNotEmpty()) {
                            Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PillButton(Icons.Filled.PlayArrow, "Putar", true) {
                                    PlaybackManager.playMusic(allSongs, 0, radio = isArtist)
                                }
                                PillButton(Icons.Filled.Shuffle, "Acak", false) {
                                    PlaybackManager.playMusic(allSongs.shuffled(), 0, radio = false)
                                }
                            }
                            if (!isArtist) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val scope = rememberCoroutineScope()
                                val offline = allSongs.mapNotNull { s ->
                                    com.lumitube.app.data.Downloader.records.firstOrNull { it.videoId == s.id && it.audioOnly }
                                }
                                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PillButton(Icons.Filled.Download, "Download semua", false) {
                                        scope.launch {
                                            // Semua lagu (termasuk halaman berikutnya), beserta liriknya.
                                            val all = allSongs.toMutableList()
                                            var c = feed.continuation
                                            try {
                                                while (c != null) {
                                                    val p = Repo.musicMore(c)
                                                    all += p.items.filter { it.type == ItemType.SONG } +
                                                        p.shelves.flatMap { sh -> sh.items.filter { it.type == ItemType.SONG } }
                                                    c = p.continuation
                                                }
                                            } catch (_: Exception) {
                                            }
                                            // Playlist: hanya lagu milik playlist (punya setVideoId), bukan lagu saran di bawahnya.
                                            val inPlaylist = all.filter { it.setVideoId != null }
                                            val base = if (route.id.startsWith("VL") && inPlaylist.isNotEmpty()) inPlaylist else all
                                            val songs = base.distinctBy { it.id }.map { it.copy(thumbnail = it.thumbnail ?: page.thumbnail) }
                                            val todo = songs.count { !com.lumitube.app.data.Downloader.isDownloaded(it.id, true) }
                                            com.lumitube.app.data.Downloader.downloadAll(
                                                songs, audioOnly = true,
                                                playlistId = route.id.takeIf { it.startsWith("VL") || it.startsWith("MPRE") }, playlistTitle = page.title ?: route.title,
                                                playlistAuthor = page.subtitle?.split(" • ")?.map { it.trim() }?.firstOrNull { p ->
                                                    p.isNotBlank() && p !in setOf("Playlist", "Album", "Single", "EP") &&
                                                        !Regex("^\\d[\\d.,]*\\s").containsMatchIn(p) && !Regex("^\\d{4}$").matches(p)
                                                },
                                            )
                                            android.widget.Toast.makeText(
                                                context,
                                                if (todo == 0) "Semua lagu sudah di-download" else "Mendownload $todo lagu + lirik…",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                    if (offline.isNotEmpty()) {
                                        PillButton(Icons.Filled.DownloadForOffline, "Offline (${offline.size})", false) {
                                            val items = offline.map { it.toItem().copy(type = ItemType.SONG) }
                                            PlaybackManager.playMusic(items, 0, radio = false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (feed.loading && page == null) item { LoadingBox(color = Color.White) }
            feed.error?.let { err -> item { MessageBox("Gagal memuat: $err", MusicColors.sub, "Coba lagi") { feed.load() } } }
            items(plItems, key = { "p" + it.key }) { item ->
                Row(Modifier.reorderItem(reorder, "p" + item.key), verticalAlignment = Alignment.CenterVertically) {
                    com.lumitube.app.ui.common.DragHandle(reorder, "p" + item.key, MusicColors.sub)
                    Box(Modifier.weight(1f)) {
                        SongRow(item, fallbackThumb = page?.thumbnail, menu = removeMenu(item)) { playFrom(item) }
                    }
                }
            }
            shelves.forEachIndexed { si, shelf ->
                val rows = shelf.items.count { it.row } * 2 > shelf.items.size
                if (rows) {
                    if (shelf.title.isNotBlank()) {
                        item(key = "t$si") {
                            Text(shelf.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
                        }
                    }
                    itemsIndexed(shelf.items.filter { it.id !in removed && !movable(it) }, key = { _, it -> "r$si" + it.key }) { i, item ->
                        SongRow(item, index = if (isAlbum) i + 1 else null, fallbackThumb = if (isAlbum) null else page?.thumbnail, menu = removeMenu(item)) {
                            if (item.type == ItemType.SONG) playFrom(item) else onItem(item, shelf.items)
                        }
                    }
                } else {
                    item(key = "c$si") {
                        ShelfView(shelf, si + 1, onItem) { s -> s.moreBrowseId?.let { openBrowse(it, s.moreParams, s.title) } }
                    }
                }
            }
            items(feed.items.filter { it.id !in removed && !movable(it) }, key = { "m" + it.key }) { item ->
                SongRow(item, fallbackThumb = page?.thumbnail, menu = removeMenu(item)) { playFrom(item) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PillButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, primary: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(42.dp).clip(RoundedCornerShape(21.dp))
            .background(if (primary) Color.White else MusicColors.chip)
            .clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (primary) Color.Black else Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (primary) Color.Black else Color.White, fontWeight = FontWeight.Medium)
    }
}

/** Kartu "Hasil teratas" di pencarian YT Music. */
@Composable
private fun TopResultCard(item: Item, onPlay: () -> Unit, onOpen: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MusicColors.card).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Img(item.thumbnail, Modifier.size(64.dp).clip(if (item.type == ItemType.ARTIST) CircleShape else RoundedCornerShape(4.dp)))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(item.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.subtitle.isNotBlank()) Text(item.subtitle, color = MusicColors.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White)
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).clickable(onClick = onOpen),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Shuffle, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Text(if (item.type == ItemType.SONG) "Radio" else "Buka", color = Color.Black, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
            }
            Row(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(20.dp)).clickable(onClick = if (item.type == ItemType.SONG) onPlay else onOpen),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text("Putar", color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
