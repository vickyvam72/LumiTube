package com.lumitube.app.ui.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.data.lyrics.LyricLine
import com.lumitube.app.data.lyrics.LyricsRepo
import com.lumitube.app.player.PlaybackManager
import com.lumitube.app.ui.common.Img
import com.lumitube.app.ui.common.MusicColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

private val Dim = Color(0x59FFFFFF)
private val Lit = Color.White

/** Lirik layar penuh bergaya Apple Music. */
@Composable
fun SyncedLyricsView(modifier: Modifier = Modifier, fontSize: TextUnit = 28.sp) {
    val lyrics = PlaybackManager.lyrics
    val clock = rememberLyricClock()
    val state = rememberLazyListState()
    var lastTouch by remember { mutableLongStateOf(0L) }
    var auto by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress) {
        // Jika pengguna menggulir sendiri, jeda auto-scroll selama 3 detik.
        if (state.isScrollInProgress && !auto) lastTouch = System.currentTimeMillis()
    }

    Column(modifier) {
        LyricsHeader()
        when {
            PlaybackManager.lyricsLoading && lyrics == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            lyrics == null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Lirik tidak ditemukan untuk lagu ini.", color = MusicColors.sub, textAlign = TextAlign.Center)
            }
            !lyrics.synced -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
                itemsIndexed(lyrics.lines) { _, l ->
                    Text(l.text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val lines = lyrics.lines
                // Hanya berubah saat baris aktif berganti (bukan setiap frame)
                val active by remember(lines) { derivedStateOf { LyricsRepo.activeLine(lines, clock.value) } }
                val topPad = maxHeight * 0.3f

                LaunchedEffect(active, lyrics) {
                    if (active >= 0 && System.currentTimeMillis() - lastTouch > 3000) {
                        auto = true
                        try {
                            state.animateScrollToItem(active)
                        } finally {
                            auto = false
                        }
                    }
                }
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topPad, bottom = maxHeight * 0.6f),
                ) {
                    itemsIndexed(lines) { i, line ->
                        val isActive = i == active
                        val distance = if (active < 0) i + 1 else abs(i - active)
                        val scale by animateFloatAsState(if (isActive) 1f else 0.94f, tween(380), label = "scale")
                        val blur = if (isActive) 0f else min(distance, 4) * 1.2f
                        SweptLyricLine(
                            line = line,
                            clock = clock,
                            active = isActive,
                            style = TextStyle(fontSize = fontSize, lineHeight = fontSize * 1.25f, fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                }
                                .blur(blur.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    lastTouch = 0L
                                    PlaybackManager.exo.seekTo(line.start)
                                }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsHeader() {
    val lyrics = PlaybackManager.lyrics
    val count = PlaybackManager.lyricsList.size
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val kind = when {
            lyrics == null -> ""
            lyrics.wordSynced -> "sinkron per kata"
            lyrics.synced -> "sinkron per baris"
            else -> "teks"
        }
        Text(
            if (lyrics != null) "Sumber: ${lyrics.source} • $kind" else if (PlaybackManager.lyricsLoading) "Mencari lirik…" else "",
            color = MusicColors.sub, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (count > 1) {
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x22FFFFFF))
                    .clickable { PlaybackManager.nextLyricsSource() }.padding(horizontal = 12.dp, vertical = 5.dp)
            ) { Text("Ganti sumber (${PlaybackManager.lyricsIndex + 1}/$count)", color = Color.White, fontSize = 12.sp) }
        } else if (lyrics == null && !PlaybackManager.lyricsLoading) {
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x22FFFFFF))
                    .clickable { PlaybackManager.loadLyrics(force = true) }.padding(horizontal = 12.dp, vertical = 5.dp)
            ) { Text("Cari lagi", color = Color.White, fontSize = 12.sp) }
        }
    }
}

/** Tampilan lirik untuk jendela Picture-in-Picture saat aplikasi diminimalkan. */
@Composable
fun LyricsPip() {
    val item = PlaybackManager.current
    val lyrics = PlaybackManager.lyrics
    val clock = rememberLyricClock()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Img(item?.thumbnail, Modifier.fillMaxSize().blur(24.dp), placeholder = Color.Black)
        Box(Modifier.fillMaxSize().background(Color(0x99000000)))
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (lyrics == null || !lyrics.synced) {
                Text(item?.title ?: "", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item?.channelName ?: "", color = MusicColors.sub, fontSize = 12.sp, maxLines = 1)
                if (lyrics == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(if (PlaybackManager.lyricsLoading) "Mencari lirik…" else "Lirik tidak tersedia", color = MusicColors.sub, fontSize = 11.sp)
                }
            } else {
                val lines = lyrics.lines
                val idx by remember(lines) { derivedStateOf { LyricsRepo.activeLine(lines, clock.value) } }
                val cur = lines.getOrNull(idx)
                val next = lines.getOrNull(idx + 1)
                if (cur != null) {
                    SweptLyricLine(
                        line = cur, clock = clock, active = true, rise = 2.dp, maxLines = 3,
                        style = TextStyle(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.ExtraBold),
                    )
                } else {
                    Text("♪", color = Color.White, fontSize = 20.sp)
                }
                if (next != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(next.text, color = Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
