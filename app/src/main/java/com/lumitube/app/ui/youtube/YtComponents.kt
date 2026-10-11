package com.lumitube.app.ui.youtube

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.lumitube.app.player.PlaybackManager
import com.lumitube.app.ui.common.Img
import com.lumitube.app.ui.common.hdThumb
import com.lumitube.app.ui.common.LocalYt
import com.lumitube.app.ui.common.VideoSurface
import com.lumitube.app.ui.common.YtRed
import kotlinx.coroutines.delay

@Composable
fun LumiLogo(textColor: Color, label: String = "Premium") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 30.dp, height = 21.dp).clip(RoundedCornerShape(6.dp)).background(YtRed),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(label, color = textColor, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
    }
}

@Composable
fun YtTopBar(onSearch: () -> Unit, onSwitchMusic: () -> Unit, onOffline: (() -> Unit)? = null) {
    val yt = LocalYt.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LumiLogo(yt.text)
        Spacer(Modifier.weight(1f))
        if (onOffline != null) {
            IconButton(onClick = onOffline) {
                Icon(Icons.Filled.DownloadForOffline, "Download (offline)", tint = yt.text)
            }
        }
        IconButton(onClick = onSwitchMusic) {
            Icon(Icons.Filled.MusicNote, "Buka Music", tint = yt.text)
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, "Cari", tint = yt.text, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun ChipRow(chips: List<Chip>, selected: Int, onExplore: () -> Unit, onSelect: (Int) -> Unit) {
    val yt = LocalYt.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Box(
                Modifier.height(34.dp).clip(RoundedCornerShape(8.dp)).background(yt.chip)
                    .clickable(onClick = onExplore).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Explore, "Jelajahi", tint = yt.text, modifier = Modifier.size(22.dp)) }
        }
        items(chips.size) { i ->
            val sel = i == selected
            Box(
                Modifier.height(34.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (sel) yt.chipSelected else yt.chip)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    chips[i].title,
                    color = if (sel) yt.chipSelectedText else yt.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000)).padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Kartu video besar seperti di beranda YouTube. */
@Composable
fun VideoCard(item: Item, onClick: () -> Unit, onChannel: (() -> Unit)?) {
    val yt = LocalYt.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 16.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Img(hdThumb(item), Modifier.fillMaxSize(), fallback = item.thumbnail)
            item.duration?.let {
                DurationBadge(it, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
        Row(Modifier.padding(start = 12.dp, top = 10.dp, end = 4.dp)) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(enabled = onChannel != null) { onChannel?.invoke() }
            ) {
                if (item.channelThumb != null) {
                    Img(item.channelThumb, Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(YtRed), contentAlignment = Alignment.Center) {
                        Text(
                            (item.channelName ?: item.title).take(1).uppercase(),
                            color = Color.White, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    item.title, color = yt.text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp,
                )
                Text(
                    item.subtitle, color = yt.sub, fontSize = 12.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(Icons.Outlined.MoreVert, null, tint = yt.text, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        }
    }
}

/** Baris video kecil (thumbnail kiri) untuk daftar playlist / riwayat. */
@Composable
fun VideoRow(item: Item, onClick: () -> Unit) {
    val yt = LocalYt.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
            Img(if (item.type == ItemType.VIDEO) "https://i.ytimg.com/vi/${item.id}/hq720.jpg" else item.thumbnail, Modifier.fillMaxSize(), fallback = item.thumbnail)
            if (item.type == ItemType.PLAYLIST) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(22.dp).background(Color(0xB3000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlaylistPlay, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(item.duration ?: "Playlist", color = Color.White, fontSize = 11.sp)
                    }
                }
            } else {
                item.duration?.let { DurationBadge(it, Modifier.align(Alignment.BottomEnd).padding(4.dp)) }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.title, color = yt.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, color = yt.sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun ChannelRow(item: Item, onClick: () -> Unit) {
    val yt = LocalYt.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Img(item.thumbnail, Modifier.size(64.dp).clip(CircleShape))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(item.title, color = yt.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) Text(item.subtitle, color = yt.sub, fontSize = 12.sp, maxLines = 2)
        }
    }
}

@Composable
fun ShortCard(item: Item, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        Img(hdThumb(item), Modifier.fillMaxSize(), fallback = item.thumbnail)
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0.6f to Color.Transparent, 1f to Color(0xCC000000)
                )
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Text(item.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) Text(item.subtitle, color = Color(0xDDFFFFFF), fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
fun ShortsShelf(items: List<Item>, onClick: (Item) -> Unit) {
    val yt = LocalYt.current
    LaunchedEffect(items.firstOrNull()?.id) {
        items.take(2).forEach { com.lumitube.app.data.StreamResolver.prefetch(it.id) }
    }
    Column(Modifier.padding(bottom = 16.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SlowMotionVideo, null, tint = YtRed, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Shorts", color = yt.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items, key = { it.key }) { ShortCard(it, Modifier.width(170.dp)) { onClick(it) } }
        }
    }
}

/** Mini player di atas navigasi bawah. */
@Composable
fun YtMiniPlayer(onOpen: () -> Unit) {
    val yt = LocalYt.current
    val item = PlaybackManager.current ?: return
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(item.id) {
        while (true) {
            val d = PlaybackManager.exo.duration
            progress = if (d > 0) PlaybackManager.exo.currentPosition.toFloat() / d else 0f
            delay(500)
        }
    }
    Column(Modifier.fillMaxWidth().background(yt.surface)) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f)) {
                if (PlaybackManager.isVideo) {
                    VideoSurface(Modifier.fillMaxSize(), controller = false)
                } else {
                    Img(item.thumbnail, Modifier.fillMaxSize())
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(item.title, color = yt.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.channelName ?: item.subtitle, color = yt.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { PlaybackManager.togglePlay() }) {
                Icon(if (PlaybackManager.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Putar/Jeda", tint = yt.text)
            }
            val canNext = PlaybackManager.index + 1 < PlaybackManager.queue.size || PlaybackManager.relatedNext != null
            IconButton(onClick = { PlaybackManager.nextVideo() }, enabled = canNext) {
                Icon(Icons.Filled.SkipNext, "Berikutnya", tint = if (canNext) yt.text else yt.sub)
            }
            IconButton(onClick = { PlaybackManager.stop() }) {
                Icon(Icons.Filled.Close, "Tutup", tint = yt.text)
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = YtRed,
            trackColor = Color.Transparent,
        )
    }
}

private data class NavSpec(val label: String, val on: ImageVector, val off: ImageVector)

@Composable
fun YtBottomNav(selected: Int, onSelect: (Int) -> Unit, onSwitchMusic: () -> Unit) {
    val yt = LocalYt.current
    val specs = listOf(
        NavSpec("Beranda", Icons.Filled.Home, Icons.Outlined.Home),
        NavSpec("Shorts", Icons.Filled.SlowMotionVideo, Icons.Outlined.SlowMotionVideo),
        NavSpec("Subscription", Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions),
    )
    Column(Modifier.fillMaxWidth().background(yt.bg).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(yt.divider))
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            NavCell(specs[0], selected == 0) { onSelect(0) }
            NavCell(specs[1], selected == 1) { onSelect(1) }
            // Tombol tengah: pindah ke LumiTube Music (posisinya seperti tombol "+" di YouTube)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(yt.chip).clickable(onClick = onSwitchMusic),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.MusicNote, "Music", tint = yt.text) }
            }
            NavCell(specs[2], selected == 2) { onSelect(2) }
            // Tab "Anda" dengan foto profil
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelect(3) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val photo = Session.account?.photo
                if (photo != null) {
                    Img(
                        photo,
                        Modifier.size(26.dp).clip(CircleShape)
                            .then(if (selected == 3) Modifier.background(yt.text) else Modifier)
                    )
                } else {
                    Icon(Icons.Filled.AccountCircle, null, tint = yt.text, modifier = Modifier.size(26.dp))
                }
                Text("Anda", color = yt.text, fontSize = 10.sp, fontWeight = if (selected == 3) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavCell(spec: NavSpec, selected: Boolean, onClick: () -> Unit) {
    val yt = LocalYt.current
    Column(
        Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (selected) spec.on else spec.off, spec.label, tint = yt.text, modifier = Modifier.size(26.dp))
        Text(spec.label, color = yt.text, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
