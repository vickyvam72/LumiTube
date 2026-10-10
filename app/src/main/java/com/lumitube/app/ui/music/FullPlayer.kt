package com.lumitube.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.data.Item
import com.lumitube.app.data.Repo
import com.lumitube.app.data.WatchInfo
import com.lumitube.app.ui.common.CommentsPanel
import com.lumitube.app.ui.common.DownloadDialog
import com.lumitube.app.ui.common.MusicPanelColors
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.filled.Download
import com.lumitube.app.player.PlaybackManager
import com.lumitube.app.player.RepeatMode
import com.lumitube.app.ui.common.Img
import com.lumitube.app.ui.common.MusicColors
import com.lumitube.app.ui.common.VideoSurface
import com.lumitube.app.ui.common.formatTime
import com.lumitube.app.ui.common.shareVideo
import kotlinx.coroutines.delay

/** Pemutar layar penuh gaya YouTube Music, dengan sakelar Lagu / Video. */
@Composable
fun FullPlayer(onClose: () -> Unit, onOpenArtist: (String) -> Unit, onOpenVideo: (Item) -> Unit) {
    val item = PlaybackManager.current ?: return
    val context = LocalContext.current
    var panel by remember { mutableIntStateOf(0) } // 0 = tidak ada, 1 = berikutnya, 2 = lirik
    var showDownload by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    val commentsInfo by produceState<WatchInfo?>(null, item.id, showComments) {
        if (showComments && (value == null || value?.title == null)) {
            value = try {
                Repo.watch(item.id)
            } catch (_: Exception) {
                WatchInfo(null, null, null, null, null, null, null, null, null, emptyList())
            }
        }
    }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(item.id) {
        while (true) {
            if (!dragging) {
                position = PlaybackManager.exo.currentPosition
                duration = PlaybackManager.exo.duration.coerceAtLeast(0L)
            }
            delay(300)
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2440), Color(0xFF0B0B0B))))
            .clickable(enabled = false) {}
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Bar atas
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (panel != 0) panel = 0 else onClose() }) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Tutup", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.weight(1f))
                ModeToggle()
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { shareVideo(context, item.id, item.title, true) }) {
                    Icon(Icons.Filled.Share, "Bagikan", tint = Color.White)
                }
            }

            if (panel == 0) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (PlaybackManager.isVideo) {
                        VideoSurface(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)), controller = false)
                    } else {
                        Img(item.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
                    }
                    if (PlaybackManager.loading) CircularProgressIndicator(color = Color.White)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Img(item.thumbnail, Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(item.title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.channelName ?: item.subtitle, color = MusicColors.sub, fontSize = 13.sp, maxLines = 1)
                    }
                    IconButton(onClick = { PlaybackManager.togglePlay() }) {
                        Icon(if (PlaybackManager.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (panel == 1) QueuePanel() else SyncedLyricsView(Modifier.fillMaxSize())
                }
            }

            if (panel == 0) {
                // Judul & artis
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(item.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.channelName ?: item.subtitle, color = MusicColors.sub, fontSize = 16.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = item.channelId != null) { item.channelId?.let(onOpenArtist) },
                    )
                    PlaybackManager.error?.let { err ->
                        Text(
                            "$err (ketuk untuk coba lagi)", color = Color(0xFFFF8A80), fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp).clickable { PlaybackManager.retry() },
                        )
                    }
                }

                // Seekbar
                val progress = if (duration > 0) position.toFloat() / duration else 0f
                Slider(
                    value = if (dragging) dragValue else progress,
                    onValueChange = { dragging = true; dragValue = it },
                    onValueChangeFinished = {
                        PlaybackManager.exo.seekTo((dragValue * duration).toLong())
                        position = (dragValue * duration).toLong()
                        dragging = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color(0x33FFFFFF),
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Text(formatTime(if (dragging) (dragValue * duration).toLong() else position), color = MusicColors.sub, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(duration), color = MusicColors.sub, fontSize = 12.sp)
                }

                // Kontrol
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { PlaybackManager.shuffle() }) {
                        Icon(Icons.Filled.Shuffle, "Acak", tint = Color.White)
                    }
                    IconButton(onClick = { PlaybackManager.previous() }) {
                        Icon(Icons.Filled.SkipPrevious, "Sebelumnya", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable { PlaybackManager.togglePlay() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (PlaybackManager.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "Putar/Jeda", tint = Color.Black, modifier = Modifier.size(40.dp),
                        )
                    }
                    IconButton(onClick = { PlaybackManager.next() }) {
                        Icon(Icons.Filled.SkipNext, "Berikutnya", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    IconButton(onClick = {
                        PlaybackManager.repeat = when (PlaybackManager.repeat) {
                            RepeatMode.OFF -> RepeatMode.ALL
                            RepeatMode.ALL -> RepeatMode.ONE
                            RepeatMode.ONE -> RepeatMode.OFF
                        }
                    }) {
                        Icon(
                            if (PlaybackManager.repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            "Ulangi",
                            tint = if (PlaybackManager.repeat == RepeatMode.OFF) MusicColors.sub else Color.White,
                        )
                    }
                }
            }

            // Tab bawah
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                BottomTab("BERIKUTNYA", panel == 1) { panel = if (panel == 1) 0 else 1 }
                BottomTab("LIRIK", panel == 2) {
                    panel = if (panel == 2) 0 else 2
                    if (panel == 2) PlaybackManager.loadLyrics()
                }
                BottomTab("KOMENTAR", showComments) { showComments = true }
                Box(
                    Modifier.weight(1f).height(52.dp).clickable { showDownload = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Download, null, tint = MusicColors.sub, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("DOWNLOAD", color = MusicColors.sub, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        if (showComments) {
            val ci = commentsInfo
            if (ci == null) {
                Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                CommentsPanel(
                    token = ci.commentsToken, count = ci.commentCount, colors = MusicPanelColors,
                    onClose = { showComments = false },
                    onChannel = { showComments = false; onOpenArtist(it) },
                )
            }
        }
    }
    if (showDownload) DownloadDialog(item) { showDownload = false }
}

@Composable
private fun ModeToggle() {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x33FFFFFF)).padding(3.dp)
    ) {
        listOf("Lagu" to false, "Video" to true).forEach { (label, video) ->
            val sel = PlaybackManager.isVideo == video
            Box(
                Modifier.clip(RoundedCornerShape(17.dp))
                    .background(if (sel) Color(0x55FFFFFF) else Color.Transparent)
                    .clickable { PlaybackManager.switchMode(video) }
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) { Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.weight(1f).height(52.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Color.White else MusicColors.sub, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QueuePanel() {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = PlaybackManager.index.coerceAtLeast(0))
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        items(PlaybackManager.queue.size) { i ->
            val q = PlaybackManager.queue[i]
            Box(Modifier.background(if (i == PlaybackManager.index) Color(0x22FFFFFF) else Color.Transparent)) {
                SongRow(q) { PlaybackManager.playAt(i) }
            }
        }
    }
}
