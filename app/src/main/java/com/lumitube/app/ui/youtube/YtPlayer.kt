package com.lumitube.app.ui.youtube

import androidx.activity.compose.BackHandler
import com.lumitube.app.ui.common.sheetContainer
import com.lumitube.app.ui.common.sheetHandle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.blur
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionOff
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.data.Session
import com.lumitube.app.player.PlaybackManager
import com.lumitube.app.ui.common.LocalYt
import com.lumitube.app.ui.common.VideoSurface
import com.lumitube.app.ui.common.YtRed
import com.lumitube.app.ui.common.formatTime
import kotlinx.coroutines.delay
import java.util.Locale

private val Shade = Color(0x66000000)

/**
 * Pemutar video dengan kontrol seperti aplikasi YouTube:
 * ketuk untuk menampilkan kontrol, ketuk 2x kiri/kanan untuk mundur/maju 10 detik.
 */
@Composable
fun YtVideoPlayer(
    modifier: Modifier,
    full: Boolean,
    onFullscreen: (Boolean) -> Unit,
    onMinimize: () -> Unit,
    onSettings: () -> Unit,
    onNext: (() -> Unit)?,
    /** Layar penuh tanpa panel chat: tombol untuk membuka live chat di samping. */
    onLiveChat: (() -> Unit)? = null,
) {
    var show by remember { mutableStateOf(true) }
    var touch by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var seekSide by remember { mutableIntStateOf(0) } // -1 kiri, 1 kanan
    var holdSide by remember { mutableIntStateOf(0) } // tahan: 1 maju 2x, -1 mundur 2x
    var scrub by remember { mutableStateOf<Float?>(null) } // posisi saat menggeser timeline
    var seekAmount by remember { mutableIntStateOf(0) }
    var seekKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            position = PlaybackManager.exo.currentPosition
            duration = PlaybackManager.exo.duration.coerceAtLeast(0)
            buffered = PlaybackManager.exo.bufferedPosition
            delay(250)
        }
    }
    // Sembunyikan kontrol otomatis setelah 3 detik saat video berjalan
    LaunchedEffect(show, touch, PlaybackManager.isPlaying) {
        if (show && PlaybackManager.isPlaying) {
            delay(3000)
            show = false
        }
    }
    LaunchedEffect(seekKey) {
        if (seekKey > 0) {
            delay(800)
            seekSide = 0
            seekAmount = 0
        }
    }

    Box(modifier.background(Color.Black)) {
        VideoSurface(Modifier.fillMaxSize(), controller = false)

        // Menggeser timeline: gambar pratinjau (diburamkan) + waktu & judul bab, seperti YouTube.
        scrub?.let { f ->
            val t = (f * duration).toLong()
            val sb = PlaybackManager.storyboard
            Box(Modifier.fillMaxSize().background(Color.Black))
            // Gambar pratinjau tajam (tanpa buram), dihaluskan saat diperbesar.
            if (sb != null) StoryboardFrame(sb, t, Modifier.fillMaxSize())
            val chapter = PlaybackManager.chapters.lastOrNull { it.first <= t }?.second
            Text(
                listOfNotNull(formatTime(t), chapter?.takeIf { it.isNotBlank() }).joinToString("   "),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0x99000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // Lapisan gestur: ketuk = tampil/sembunyi kontrol, ketuk 2x = lompat 10 detik
        val holdScope = androidx.compose.runtime.rememberCoroutineScope()
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    // Tahan kanan = maju cepat 2x, tahan kiri = mundur 2x; lepas = normal lagi.
                    onPress = { o ->
                        val side = 1 // tahan kiri maupun kanan = maju cepat 2x
                        var started = false
                        val job = holdScope.launch {
                            kotlinx.coroutines.delay(450)
                            started = true
                            holdSide = side
                            PlaybackManager.startHoldSeek(side)
                        }
                        tryAwaitRelease()
                        job.cancel()
                        if (started) {
                            PlaybackManager.stopHoldSeek()
                            holdSide = 0
                        }
                    },
                    onLongPress = { },
                    onTap = {
                        show = !show
                        touch = System.currentTimeMillis()
                    },
                    onDoubleTap = { o ->
                        val side = if (o.x > size.width / 2f) 1 else -1
                        PlaybackManager.seekBy(side * 10_000L)
                        seekAmount = if (seekSide == side) seekAmount + 10 else 10
                        seekSide = side
                        seekKey++
                    },
                )
            }
        )

        // Penanda tahan untuk 2x
        if (holdSide != 0) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 14.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0x99000000)).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (holdSide < 0) Icon(Icons.Filled.FastRewind, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(" 2x ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (holdSide > 0) Icon(Icons.Filled.FastForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Umpan balik lompat 10 detik
        if (seekSide != 0) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(0.42f)
                    .align(if (seekSide > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(
                        if (seekSide > 0) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                        else RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                    )
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (seekSide > 0) Icons.Filled.FastForward else Icons.Filled.FastRewind, null, tint = Color.White)
                    Text("$seekAmount detik", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        val waiting = PlaybackManager.loading || PlaybackManager.buffering
        if (waiting && !show) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(44.dp))
        }

        AnimatedVisibility(visible = show, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Shade)) {
                // Baris atas
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopStart).padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (full) onFullscreen(false) else onMinimize() }) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Perkecil", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    AutoplayToggle()
                    val hasCaptions = PlaybackManager.captions.isNotEmpty()
                    IconButton(
                        onClick = {
                            touch = System.currentTimeMillis()
                            if (PlaybackManager.captionIndex >= 0) {
                                PlaybackManager.setCaption(-1)
                            } else {
                                val (idx, tl) = defaultCaption()
                                PlaybackManager.setCaption(idx, tl)
                            }
                        },
                        enabled = hasCaptions,
                    ) {
                        Icon(
                            if (PlaybackManager.captionIndex >= 0) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaption,
                            "Teks", tint = if (hasCaptions) Color.White else Color(0x66FFFFFF),
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, "Setelan", tint = Color.White)
                    }
                }

                // Tombol tengah
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundButton(Icons.Filled.SkipPrevious, 50.dp, enabled = true) {
                        touch = System.currentTimeMillis()
                        PlaybackManager.previous()
                    }
                    if (waiting) {
                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(44.dp))
                        }
                    } else {
                        RoundButton(if (PlaybackManager.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, 64.dp, enabled = true) {
                            touch = System.currentTimeMillis()
                            PlaybackManager.togglePlay()
                        }
                    }
                    val canNext = onNext != null || PlaybackManager.index + 1 < PlaybackManager.queue.size
                    RoundButton(Icons.Filled.SkipNext, 50.dp, enabled = canNext) {
                        touch = System.currentTimeMillis()
                        if (PlaybackManager.index + 1 < PlaybackManager.queue.size) PlaybackManager.next() else onNext?.invoke()
                    }
                }

                // Baris bawah
                Row(
                    Modifier.fillMaxWidth().align(Alignment.BottomStart)
                        .padding(start = if (full) 24.dp else 14.dp, end = if (full) 12.dp else 4.dp, bottom = if (full) 44.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (PlaybackManager.isLiveStream) {
                        // Titik merah = sedang di bagian terkini. Ketuk untuk kembali ke live.
                        val offset = PlaybackManager.exo.currentLiveOffset
                        val atLive = offset == androidx.media3.common.C.TIME_UNSET || offset < 15_000 || duration - position < 15_000
                        Row(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x66000000))
                                .clickable { touch = System.currentTimeMillis(); PlaybackManager.goLive() }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (atLive) YtRed else Color(0xFFAAAAAA)))
                            Text("Live", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
                        }
                    } else {
                        Text(
                            "${formatTime(position)} / ${formatTime(duration)}",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (onLiveChat != null) {
                        IconButton(onClick = { touch = System.currentTimeMillis(); onLiveChat() }) {
                            Icon(Icons.Outlined.ChatBubbleOutline, "Live chat", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    IconButton(onClick = { onFullscreen(!full) }) {
                        Icon(if (full) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, "Layar penuh", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }

        if (PlaybackManager.captionLoading) {
            Text(
                "Memuat subtitle…", color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        PlaybackManager.captionError?.let { msg ->
            LaunchedEffect(msg) {
                delay(2500)
                PlaybackManager.captionError = null
            }
            Text(
                msg, color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

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

        // Seekbar merah di tepi bawah video. Layar penuh: hanya tampil bersama kontrol
        // (seperti YouTube), tidak ada garis tipis yang terus terlihat saat menonton.
        if (!full || show) YtSeekBar(
            position = position,
            duration = duration,
            buffered = buffered,
            expanded = show,
            // Layar penuh: naikkan garis merah dari tepi bawah supaya mudah digeser
            // (tidak bentrok dengan area gestur navigasi HP).
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .then(if (full) Modifier.padding(start = 16.dp, end = 16.dp, bottom = 22.dp) else Modifier),
            chapters = PlaybackManager.chapters.map { it.first },
            onScrub = { scrub = it; touch = System.currentTimeMillis() },
            onSeekStart = { touch = System.currentTimeMillis() },
        ) { fraction ->
            touch = System.currentTimeMillis()
            if (duration > 0) PlaybackManager.exo.seekTo((fraction * duration).toLong())
        }
    }
}

/**
 * Subtitle bawaan: bahasa HP bila ada; kalau tidak ada, terjemahkan otomatis
 * subtitle terbaik ke bahasa HP (bila YouTube mendukung).
 */
internal fun defaultCaption(): Pair<Int, String?> {
    val list = PlaybackManager.captions
    val lang = Locale.getDefault().language
    list.indexOfFirst { it.lang.startsWith(lang) && !it.auto }.takeIf { it >= 0 }?.let { return it to null }
    list.indexOfFirst { it.lang.startsWith(lang) }.takeIf { it >= 0 }?.let { return it to null }
    val base = list.indexOfFirst { !it.auto }.takeIf { it >= 0 } ?: 0
    val canTranslate = PlaybackManager.translations.any { it.first == lang }
    return base to (if (canTranslate) lang else null)
}

@Composable
private fun RoundButton(icon: ImageVector, size: androidx.compose.ui.unit.Dp, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0x55000000))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) Color.White else Color(0x55FFFFFF), modifier = Modifier.size(size * 0.62f))
    }
}

/** Sakelar putar otomatis (seperti di aplikasi YouTube). */
@Composable
private fun AutoplayToggle() {
    var on by remember { mutableStateOf(Session.autoplay) }
    Box(
        Modifier.padding(horizontal = 6.dp).size(width = 46.dp, height = 24.dp).clip(RoundedCornerShape(12.dp))
            .background(if (on) Color(0xCCFFFFFF) else Color(0x66FFFFFF))
            .clickable {
                on = !on
                Session.autoplay = on
            },
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(2.dp).size(20.dp).clip(CircleShape).background(if (on) Color.White else Color(0xFF606060)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (on) Icons.Filled.PlayArrow else Icons.Filled.Pause, null,
                tint = if (on) Color.Black else Color.White, modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
internal fun YtSeekBar(
    position: Long,
    duration: Long,
    buffered: Long,
    expanded: Boolean,
    modifier: Modifier,
    chapters: List<Long> = emptyList(),
    onScrub: (Float?) -> Unit = {},
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    val played = if (dragging) drag else if (duration > 0) position.toFloat() / duration else 0f
    val buf = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Box(
        modifier.height(if (expanded) 22.dp else 3.dp)
            .pointerInput(expanded) {
                if (!expanded) return@pointerInput
                detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(expanded) {
                if (!expanded) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        drag = (o.x / size.width).coerceIn(0f, 1f)
                        onSeekStart()
                        onScrub(drag)
                    },
                    onDragEnd = {
                        dragging = false
                        onScrub(null)
                        onSeek(drag)
                    },
                    onDragCancel = { dragging = false; onScrub(null) },
                ) { change, _ ->
                    drag = (change.position.x / size.width).coerceIn(0f, 1f)
                    onScrub(drag)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height - 1.5.dp.toPx()
            val stroke = 3.dp.toPx()
            // Bab dari kreator: timeline dipecah jadi beberapa garis (putus-putus).
            val gap = if (chapters.size >= 2 && duration > 0) 2.dp.toPx() else 0f
            val bounds = if (gap > 0) {
                (chapters.filter { it in 1 until duration }.map { it.toFloat() / duration } + 1f).sorted()
            } else listOf(1f)
            fun seg(color: Color, upTo: Float) {
                var start = 0f
                for (end in bounds) {
                    val a = size.width * start
                    val b = size.width * minOf(end, upTo) - (if (end < 1f) gap / 2 else 0f)
                    if (b > a) drawLine(color, Offset(a + (if (start > 0f) gap / 2 else 0f), y), Offset(b, y), stroke, cap = StrokeCap.Butt)
                    if (end >= upTo) break
                    start = end
                }
            }
            seg(Color(0x55FFFFFF), 1f)
            seg(Color(0x99FFFFFF), buf)
            seg(YtRed, played.coerceIn(0f, 1f))
            if (expanded) drawCircle(YtRed, radius = (if (dragging) 9 else 7).dp.toPx(), center = Offset(size.width * played.coerceIn(0f, 1f), y))
        }
    }
}

// ------------------------------------------------------------------ Sheet setelan

/** Sheet setelan video: Kualitas, Kecepatan, Teks, dan Ulang video. */
@Composable
fun YtSettingsSheet(onListenMusic: (() -> Unit)? = null, onClose: () -> Unit) {
    val yt = LocalYt.current
    var page by remember { mutableIntStateOf(0) }
    var loop by remember { mutableStateOf(Session.loopVideo) }
    var showSleep by remember { mutableStateOf(false) }
    if (showSleep) com.lumitube.app.ui.common.SleepTimerDialog { showSleep = false }
    BackHandler { if (page != 0) page = 0 else onClose() }
    val drag = com.lumitube.app.ui.common.rememberSheetDragState(onClose)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x88000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .sheetContainer(drag)
                .clip(RoundedCornerShape(20.dp))
                .sheetHandle(drag).background(if (yt.bg == Color.White) Color.White else Color(0xFF282828))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(bottom = 8.dp)
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(yt.sub.copy(alpha = 0.4f)))
            }
            when (page) {
                0 -> {
                    val q = Session.maxQuality
                    val actual = PlaybackManager.qualities.firstOrNull { it <= q }
                    SettingRow(Icons.Outlined.Tune, "Kualitas video", if (actual != null) "${actual}p" else "Otomatis", arrow = true) { page = 1 }
                    SettingRow(Icons.Outlined.Speed, "Kecepatan pemutaran", speedLabel(PlaybackManager.speed), arrow = true) { page = 2 }
                    SettingRow(Icons.Outlined.Bedtime, "Timer tidur", com.lumitube.app.ui.common.sleepTimerLabel(), arrow = true) { showSleep = true }
                    val cap = PlaybackManager.captions.getOrNull(PlaybackManager.captionIndex)
                    val tl = PlaybackManager.captionTranslate?.let { code ->
                        PlaybackManager.translations.firstOrNull { it.first == code }?.second ?: code
                    }
                    SettingRow(
                        if (cap != null) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaptionOff, "Teks",
                        when {
                            cap == null -> if (PlaybackManager.captions.isEmpty()) "Tidak tersedia" else "Nonaktif"
                            tl != null -> "$tl (terjemahan)"
                            else -> cap.name
                        },
                        arrow = true,
                    ) { if (PlaybackManager.captions.isNotEmpty()) page = 3 }
                    if (onListenMusic != null) {
                        SettingRow(Icons.Outlined.MusicNote, "Dengarkan di YT Music Lumi", "", arrow = true) { onListenMusic() }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            loop = !loop
                            Session.loopVideo = loop
                        }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.RepeatOne, null, tint = yt.text)
                        Text("Ulang video", color = yt.text, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 20.dp))
                        Switch(
                            checked = loop,
                            onCheckedChange = { loop = it; Session.loopVideo = it },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = yt.text, checkedThumbColor = yt.bg,
                            ),
                        )
                    }
                }
                1 -> SubPage("Kualitas video", { page = 0 }) {
                    val list = PlaybackManager.qualities
                    val q = Session.maxQuality
                    val chosen = list.firstOrNull { it <= q } ?: list.lastOrNull()
                    if (list.isEmpty()) {
                        Text("Daftar kualitas belum tersedia.", color = yt.sub, modifier = Modifier.padding(20.dp))
                    }
                    LazyColumn(Modifier.fillMaxWidth().height((list.size.coerceAtMost(7) * 52).dp)) {
                        items(list) { h ->
                            CheckRow(
                                "${h}p" + when {
                                    h >= 2160 -> " 4K"
                                    h >= 1440 -> " QHD"
                                    h >= 1080 -> " Full HD"
                                    h >= 720 -> " HD"
                                    else -> ""
                                },
                                h == chosen,
                            ) {
                                PlaybackManager.setQuality(h)
                                onClose()
                            }
                        }
                    }
                }
                2 -> SubPage("Kecepatan pemutaran", { page = 0 }) {
                    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { sp ->
                        CheckRow(if (sp == 1f) "Normal" else speedLabel(sp), PlaybackManager.speed == sp) {
                            PlaybackManager.setPlaybackSpeed(sp)
                            onClose()
                        }
                    }
                }
                3 -> SubPage("Teks", { page = 0 }) {
                    CheckRow("Nonaktif", PlaybackManager.captionIndex < 0) {
                        PlaybackManager.setCaption(-1)
                        onClose()
                    }
                    LazyColumn(Modifier.fillMaxWidth().height(((PlaybackManager.captions.size + 1).coerceAtMost(6) * 52).dp)) {
                        items(PlaybackManager.captions.size) { i ->
                            val c = PlaybackManager.captions[i]
                            CheckRow(
                                c.name + if (c.auto && !c.name.contains("(")) " (otomatis)" else "",
                                PlaybackManager.captionIndex == i && PlaybackManager.captionTranslate == null,
                            ) {
                                PlaybackManager.setCaption(i)
                                onClose()
                            }
                        }
                        if (PlaybackManager.translations.isNotEmpty()) {
                            item {
                                SettingRow(Icons.Outlined.Translate, "Terjemahkan otomatis", PlaybackManager.captionTranslate ?: "", arrow = true) { page = 4 }
                            }
                        }
                    }
                }
                4 -> SubPage("Terjemahkan ke", { page = 3 }) {
                    // Dasar terjemahan: subtitle aktif, atau subtitle buatan manusia, atau yang pertama
                    val base = PlaybackManager.captionIndex.takeIf { it >= 0 }
                        ?: PlaybackManager.captions.indexOfFirst { !it.auto }.takeIf { it >= 0 } ?: 0
                    val list = PlaybackManager.translations
                    val mine = Locale.getDefault().language
                    val ordered = list.filter { it.first == mine } + list.filter { it.first != mine }
                    LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                        items(ordered.size) { i ->
                            val (code, name) = ordered[i]
                            CheckRow(name, PlaybackManager.captionTranslate == code) {
                                PlaybackManager.setCaption(base, code)
                                onClose()
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun speedLabel(v: Float): String =
    (if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()) + "x"

@Composable
private fun SettingRow(icon: ImageVector, label: String, value: String, arrow: Boolean, onClick: () -> Unit) {
    val yt = LocalYt.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = yt.text)
        Text(label, color = yt.text, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 20.dp))
        Text(value, color = yt.sub, fontSize = 14.sp)
        if (arrow) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = yt.sub)
    }
}

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val yt = LocalYt.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = yt.text) }
        Text(title, color = yt.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
    content()
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onClick: () -> Unit) {
    val yt = LocalYt.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp)) {
            if (checked) Icon(Icons.Filled.Check, null, tint = yt.text)
        }
        Text(label, color = yt.text, fontSize = 15.sp)
    }
}

/** Satu gambar pratinjau dari lembar storyboard YouTube (dipotong dari gambar besar). */
@Composable
internal fun StoryboardFrame(sb: com.lumitube.app.data.Storyboard, timeMs: Long, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val (url, x, y) = sb.frame(timeMs)
    var shown by remember { mutableStateOf<Pair<androidx.compose.ui.graphics.ImageBitmap, androidx.compose.ui.unit.IntOffset>?>(null) }
    LaunchedEffect(url, x, y) {
        val bmp = StoryboardCache.get(context, url) ?: return@LaunchedEffect
        shown = bmp to androidx.compose.ui.unit.IntOffset(x, y)
    }
    val cur = shown ?: return
    Canvas(modifier) {
        drawImage(
            cur.first,
            srcOffset = cur.second,
            srcSize = androidx.compose.ui.unit.IntSize(
                sb.width.coerceAtMost(cur.first.width - cur.second.x),
                sb.height.coerceAtMost(cur.first.height - cur.second.y),
            ),
            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
        )
    }
}

private object StoryboardCache {
    private val cache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(6)

    suspend fun get(context: android.content.Context, url: String): androidx.compose.ui.graphics.ImageBitmap? {
        cache.get(url)?.let { return it }
        val req = coil.request.ImageRequest.Builder(context).data(url).allowHardware(false).build()
        val result = coil.Coil.imageLoader(context).execute(req)
        val bmp = ((result as? coil.request.SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            ?: return null
        return bmp.asImageBitmap().also { cache.put(url, it) }
    }
}
