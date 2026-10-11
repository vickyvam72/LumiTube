package com.lumitube.app.ui.youtube

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.data.InnerTube
import com.lumitube.app.data.Item
import com.lumitube.app.data.ItemType
import com.lumitube.app.data.Repo
import com.lumitube.app.data.Session
import com.lumitube.app.data.WatchInfo
import com.lumitube.app.player.PlaybackManager
import com.lumitube.app.ui.common.CommentsPanel
import com.lumitube.app.ui.common.DownloadDialog
import com.lumitube.app.ui.common.Img
import com.lumitube.app.ui.common.PanelColors
import com.lumitube.app.ui.common.VideoSurface
import com.lumitube.app.ui.common.YtRed
import com.lumitube.app.ui.common.shareVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ShortsPanelColors = PanelColors(Color(0xFF212121), Color.White, Color(0xFFAAAAAA), Color(0xFF3F3F3F), YtRed)
private val overlayShadow = TextStyle(shadow = Shadow(Color(0x99000000), blurRadius = 8f))

/** Pemutar Shorts layar penuh: geser atas/bawah untuk pindah video. */
@Composable
fun ShortsPlayer(
    items: List<Item>,
    startIndex: Int,
    onLoadMore: (() -> Unit)?,
    onChannel: (String) -> Unit,
    onLogin: () -> Unit,
    onBack: (() -> Unit)?,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.size - 1)) { items.size }
    var commentsFor by remember { mutableStateOf<WatchInfo?>(null) }
    var showComments by remember { mutableStateOf(false) }
    var downloadItem by remember { mutableStateOf<Item?>(null) }

    LaunchedEffect(pager.settledPage, items.size) {
        val it = items.getOrNull(pager.settledPage) ?: return@LaunchedEffect
        if (PlaybackManager.current?.id != it.id) PlaybackManager.playVideo(it.copy(type = ItemType.SHORT))
        // Siapkan stream Shorts berikutnya supaya geser terasa instan.
        // Bagian awal 2 Shorts berikutnya disiapkan (bergantian, setelah video ini lancar).
        for (d in listOf(1, 2)) items.getOrNull(pager.settledPage + d)?.let { n -> PlaybackManager.prefetchMedia(n.id, short = true) }
        if (pager.settledPage >= items.size - 3) onLoadMore?.invoke()
    }
    DisposableEffect(Unit) {
        onDispose {
            if (PlaybackManager.current?.type == ItemType.SHORT) PlaybackManager.stop()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val item = items[page]
            ShortPage(
                item = item,
                active = page == pager.settledPage,
                onChannel = onChannel,
                onLogin = onLogin,
                onComments = { info -> commentsFor = info; showComments = true },
                onDownload = { downloadItem = item },
            )
        }
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White)
            }
        }
        if (showComments) {
            CommentsPanel(
                token = commentsFor?.commentsToken,
                count = commentsFor?.commentCount,
                colors = ShortsPanelColors,
                onClose = { showComments = false },
                onChannel = { showComments = false; onChannel(it) },
                edgeToEdge = false,
            )
        }
    }
    downloadItem?.let { DownloadDialog(it) { downloadItem = null } }
}

@Composable
private fun ShortPage(
    item: Item,
    active: Boolean,
    onChannel: (String) -> Unit,
    onLogin: () -> Unit,
    onComments: (WatchInfo?) -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info by produceState<WatchInfo?>(null, item.id, active) {
        if (active && value == null) {
            value = try {
                Repo.watch(item.id)
            } catch (_: Exception) {
                null
            }
        }
    }
    var liked by remember(item.id, info?.liked) { mutableStateOf(info?.liked == true) }
    var subscribed by remember(item.id, info?.subscribed) { mutableStateOf(info?.subscribed == true) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(active, item.id) {
        while (active) {
            val d = PlaybackManager.exo.duration
            progress = if (d > 0) PlaybackManager.exo.currentPosition.toFloat() / d else 0f
            delay(200)
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (active) PlaybackManager.togglePlay()
            }
    ) {
        // Langsung video. Selama data belum siap layar cukup hitam (tanpa thumbnail/loading).
        if (active) VideoSurface(Modifier.fillMaxSize(), controller = false, zoom = true)
        if (active && !PlaybackManager.playWhenReady && !PlaybackManager.loading && PlaybackManager.current?.id == item.id) {
            Box(
                Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
        }
        PlaybackManager.error?.let { err ->
            if (active) Text(err, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp))
        }

        // Gradasi bawah agar teks terbaca
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
        )

        // Tombol kanan
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SideButton(if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, info?.likes ?: "Suka", if (liked) YtRed else Color.White) {
                if (!Session.loggedIn) {
                    onLogin()
                } else {
                    val target = !liked
                    liked = target
                    scope.launch {
                        try {
                            InnerTube.like(item.id, target)
                        } catch (_: Exception) {
                            liked = !target
                        }
                    }
                }
            }
            SideButton(Icons.Outlined.ChatBubbleOutline, info?.commentCount ?: "Komentar") { onComments(info) }
            SideButton(Icons.Filled.Share, "Bagikan") { shareVideo(context, item.id, item.title, false) }
            SideButton(Icons.Outlined.Download, "Download") { onDownload() }
            Img(
                info?.channelThumb ?: item.channelThumb,
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color.White),
            )
        }

        // Info kiri bawah
        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 80.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val chId = info?.channelId
                Row(
                    Modifier.clickable(enabled = chId != null) { chId?.let(onChannel) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Img(info?.channelThumb ?: item.channelThumb, Modifier.size(34.dp).clip(CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        info?.channel?.let { if (it.startsWith("@")) it else "@$it" } ?: (item.channelName ?: ""),
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = overlayShadow,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(18.dp))
                        .background(if (subscribed) Color(0x55FFFFFF) else Color.White)
                        .clickable {
                            val id = chId
                            if (!Session.loggedIn) {
                                onLogin()
                            } else if (id != null) {
                                val target = !subscribed
                                subscribed = target
                                scope.launch {
                                    try {
                                        InnerTube.subscribe(id, target)
                                    } catch (_: Exception) {
                                        subscribed = !target
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        if (subscribed) "Disubscribe" else "Subscribe",
                        color = if (subscribed) Color.White else Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    )
                }
            }
            Text(
                info?.title ?: item.title, color = Color.White, fontSize = 15.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, style = overlayShadow, modifier = Modifier.padding(top = 10.dp),
            )
        }

        if (active) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
                color = YtRed,
                trackColor = Color(0x33FFFFFF),
            )
        }
    }
}

@Composable
private fun SideButton(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(32.dp).shadow(0.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, style = overlayShadow, maxLines = 1)
    }
}
