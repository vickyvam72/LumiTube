package com.lumitube.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import com.lumitube.app.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.data.Chip
import com.lumitube.app.data.Item
import com.lumitube.app.data.ItemType
import com.lumitube.app.data.Session
import com.lumitube.app.data.Shelf
import com.lumitube.app.player.PlaybackManager
import com.lumitube.app.ui.common.Img
import com.lumitube.app.ui.common.MusicColors
import com.lumitube.app.ui.common.YtRed
import kotlinx.coroutines.delay
import kotlin.math.ceil

@Composable
fun MusicLogo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(YtRed), contentAlignment = Alignment.Center) {
            Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text("Music", color = MusicColors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
    }
}

@Composable
fun Avatar(size: Int = 34, onClick: (() -> Unit)? = null) {
    val acc = Session.account
    val m = Modifier.size(size.dp).clip(CircleShape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    if (acc?.photo != null) {
        Img(acc.photo, m)
    } else {
        Box(m.background(Color(0xFF6D4C41)), contentAlignment = Alignment.Center) {
            Text((acc?.name ?: "?").take(1).uppercase(), color = Color.White, fontSize = (size / 2).sp)
        }
    }
}

@Composable
fun MoodChips(chips: List<Chip>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips.size) { i ->
            val sel = i == selected
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (sel) MusicColors.text else MusicColors.chip)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(chips[i].title, color = if (sel) MusicColors.bg else MusicColors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun ShelfHeader(shelf: Shelf, avatar: Boolean, onMore: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatar) {
            Avatar(48)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            shelf.strapline?.let { Text(it.uppercase(), color = MusicColors.sub, fontSize = 13.sp, letterSpacing = 0.5.sp) }
            Text(shelf.title, color = MusicColors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Lainnya", tint = MusicColors.text)
            }
        }
    }
}

/** Grid 3x3 berhalaman seperti "Pintasan cepat" di YT Music. */
@Composable
fun SpeedDial(items: List<Item>, onClick: (Item) -> Unit) {
    val pages = ceil(items.size / 9.0).toInt().coerceAtLeast(1)
    val state = rememberPagerState(pageCount = { pages })
    val current = PlaybackManager.current?.id
    Column {
        HorizontalPager(state = state, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 8.dp) { page ->
            val chunk = items.drop(page * 9).take(9)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 0 until 3) {
                            val cell = chunk.getOrNull(r * 3 + c)
                            Box(Modifier.weight(1f).aspectRatio(1f)) {
                                if (cell != null) DialTile(cell, cell.id == current) { onClick(cell) }
                            }
                        }
                    }
                }
            }
        }
        if (pages > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages) { i ->
                    Box(
                        Modifier.padding(4.dp).size(if (i == state.currentPage) 9.dp else 7.dp).clip(CircleShape)
                            .background(if (i == state.currentPage) MusicColors.text else MusicColors.sub)
                    )
                }
            }
        }
    }
}

@Composable
private fun DialTile(item: Item, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.fillMaxSize().clip(shape)
            .then(if (active) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .clickable(onClick = onClick)
    ) {
        Img(item.thumbnail, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xCC000000))))
        Text(
            item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        )
    }
}

/** Kartu persegi (album, playlist, mix) atau lebar (video musik). */
@Composable
fun MusicCard(item: Item, onClick: () -> Unit) {
    val width = if (item.wide) 260.dp else 150.dp
    val circle = item.type == ItemType.ARTIST
    Column(Modifier.width(width).clickable(onClick = onClick)) {
        Img(
            item.thumbnail,
            Modifier.fillMaxWidth().aspectRatio(if (item.wide) 16f / 9f else 1f)
                .clip(if (circle) CircleShape else RoundedCornerShape(6.dp))
        )
        Text(
            item.title, color = MusicColors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
        )
        if (item.subtitle.isNotBlank()) {
            Text(item.subtitle, color = MusicColors.sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SongRow(
    item: Item,
    index: Int? = null,
    fallbackThumb: String? = null,
    menu: List<Pair<String, () -> Unit>>? = null,
    /** false = playlist/album tanpa "Tambahkan ke playlist" (sudah ada di Koleksi). */
    addToPlaylist: Boolean = true,
    onClick: () -> Unit,
) {
    val playing = PlaybackManager.current?.id == item.id
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null && item.thumbnail == null && fallbackThumb == null) {
            Text("$index", color = MusicColors.sub, fontSize = 15.sp, modifier = Modifier.width(32.dp))
        } else {
            Img(
                item.thumbnail ?: fallbackThumb,
                Modifier.size(52.dp).clip(if (item.type == ItemType.ARTIST) CircleShape else RoundedCornerShape(4.dp))
            )
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                item.title, color = if (playing) YtRed else MusicColors.text, fontSize = 15.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, color = MusicColors.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val actions = SongActions.menuFor(item).filter {
            addToPlaylist || item.type == ItemType.SONG || item.type == ItemType.VIDEO || it.first != "Tambahkan ke playlist"
        } + (menu ?: emptyList())
        if (actions.isNotEmpty()) {
            com.lumitube.app.ui.common.ItemMenu(actions, MusicColors.sub)
        } else {
            // Ukuran sama dengan tombol menu supaya titik tiga sejajar.
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MoreVert, null, tint = MusicColors.sub, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Shelf generik: memilih tampilan sesuai isi. */
@Composable
fun ShelfView(shelf: Shelf, index: Int, onItem: (Item, List<Item>) -> Unit, onMore: ((Shelf) -> Unit)?) {
    val rows = shelf.items.count { it.row } * 2 > shelf.items.size
    val speedDial = !rows && index == 0 && shelf.strapline != null
    ShelfHeader(shelf, avatar = speedDial, onMore = if (shelf.moreBrowseId != null && onMore != null) ({ onMore(shelf) }) else null)
    when {
        speedDial -> SpeedDial(shelf.items) { onItem(it, shelf.items) }
        rows -> {
            val n = shelf.items.size.coerceAtMost(4).coerceAtLeast(1)
            LazyHorizontalGrid(
                rows = GridCells.Fixed(n),
                modifier = Modifier.fillMaxWidth().height((n * 68).dp),
            ) {
                items(shelf.items, key = { it.key }) { item ->
                    Box(Modifier.width(320.dp)) { SongRow(item) { onItem(item, shelf.items) } }
                }
            }
        }
        else -> LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shelf.items, key = { it.key }) { item -> MusicCard(item) { onItem(item, shelf.items) } }
        }
    }
}

@Composable
fun MusicMiniPlayer(onOpen: () -> Unit) {
    val item = PlaybackManager.current ?: return
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(item.id) {
        while (true) {
            val d = PlaybackManager.exo.duration
            progress = if (d > 0) PlaybackManager.exo.currentPosition.toFloat() / d else 0f
            delay(500)
        }
    }
    Column(Modifier.fillMaxWidth().background(MusicColors.mini)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MusicColors.text,
            trackColor = MusicColors.chip,
        )
        Row(
            Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onOpen).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Img(item.thumbnail, Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.title, color = MusicColors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.channelName ?: item.subtitle, color = MusicColors.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (PlaybackManager.loading) {
                CircularProgressIndicator(color = MusicColors.text, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
            } else {
                IconButton(onClick = { PlaybackManager.togglePlay() }) {
                    Icon(
                        if (PlaybackManager.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Putar/Jeda",
                        tint = MusicColors.text, modifier = Modifier.size(32.dp),
                    )
                }
            }
            IconButton(onClick = { PlaybackManager.next() }) {
                Icon(Icons.Filled.SkipNext, "Berikutnya", tint = MusicColors.text)
            }
            IconButton(onClick = { PlaybackManager.stop() }) {
                Icon(Icons.Filled.Close, "Tutup", tint = MusicColors.text)
            }
        }
    }
}

private data class MNav(val label: String, val on: ImageVector, val off: ImageVector)

@Composable
fun MusicBottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val specs = listOf(
        MNav("Beranda", Icons.Filled.Home, Icons.Outlined.Home),
        MNav("Jelajahi", Icons.Filled.Explore, Icons.Outlined.Explore),
        MNav("Telusuri", Icons.Filled.Search, Icons.Outlined.Search),
        MNav("Koleksi", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    )
    Row(
        Modifier.fillMaxWidth().background(MusicColors.nav).navigationBarsPadding().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        specs.forEachIndexed { i, s ->
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(if (i == selected) s.on else s.off, s.label, tint = MusicColors.text, modifier = Modifier.size(26.dp))
                Text(
                    s.label, color = if (i == selected) MusicColors.text else MusicColors.sub, fontSize = 12.sp,
                    fontWeight = if (i == selected) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}
