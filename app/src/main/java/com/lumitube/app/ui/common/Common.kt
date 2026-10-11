package com.lumitube.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.lumitube.app.data.Item
import com.lumitube.app.data.Page
import com.lumitube.app.player.PlaybackManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun Img(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Color = Color(0x22888888),
    fallback: String? = null,
) {
    // Coba URL utama (mis. versi HD); bila gagal/404, pakai cadangan.
    var current by remember(url, fallback) { mutableStateOf(url ?: fallback) }
    Box(modifier.background(placeholder)) {
        val u = current
        if (u != null) {
            AsyncImage(
                model = u,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onError = { if (fallback != null && current != fallback) current = fallback },
            )
        }
    }
}

/** Thumbnail HD untuk video (maxresdefault) dan Shorts (oar2, vertikal). */
fun hdThumb(item: Item): String? = when (item.type) {
    com.lumitube.app.data.ItemType.VIDEO -> "https://i.ytimg.com/vi/${item.id}/maxresdefault.jpg"
    com.lumitube.app.data.ItemType.SHORT -> "https://i.ytimg.com/vi/${item.id}/oar2.jpg"
    else -> item.thumbnail
}

/** Memuat halaman pertama + halaman lanjutan (infinite scroll). */
class FeedLoader(
    private val cacheKey: Any?,
    private val scope: CoroutineScope,
    private val first: suspend () -> Page,
    private val more: (suspend (String) -> Page)?,
    private val peek: (() -> Page?)? = null,
) {
    var page by mutableStateOf<Page?>(null)
        private set
    val items = mutableStateListOf<Item>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var continuation: String? = null
        private set
    private var loadingMore = false

    private data class Cached(val page: Page?, val items: List<Item>, val continuation: String?, val time: Long)

    companion object {
        /** Cache halaman di memori supaya pindah tab / mode YouTube <-> Music terasa instan. */
        private val cache = HashMap<Any, Cached>()
        fun clear() = cache.clear()

        /** Buang cache satu halaman (setelah playlist diubah) supaya dibuka ulang dari server. */
        fun invalidate(key: Any?) {
            if (key != null) cache.remove(key)
        }
    }

    private fun store() {
        val k = cacheKey ?: return
        cache[k] = Cached(page, items.toList(), continuation, System.currentTimeMillis())
    }

    /** Muat dari cache (bila masih segar) atau dari jaringan. */
    fun loadCachedOrFetch() {
        val c = cacheKey?.let { cache[it] }
        if (c != null && System.currentTimeMillis() - c.time < 20 * 60 * 1000L) {
            page = c.page
            items.clear()
            items.addAll(c.items)
            continuation = c.continuation
            return
        }
        if (peek != null) {
            // Tampilkan salinan terakhir dari HP dulu (seperti aplikasi YouTube asli), lalu perbarui.
            scope.launch {
                val p = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { peek.invoke() }
                } catch (_: Exception) {
                    null
                }
                if (p != null && page == null && items.isEmpty()) {
                    page = p
                    items.addAll(p.items)
                    continuation = p.continuation
                }
                load()
            }
            return
        }
        load()
    }

    /** Kosongkan lalu muat ulang dari awal (mis. Shorts baru setiap tab dibuka). */
    fun refresh() {
        items.clear()
        page = null
        continuation = null
        load()
    }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val p = first()
                page = p
                items.clear()
                items.addAll(p.items)
                continuation = p.continuation
                store()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        val c = continuation ?: return
        val m = more ?: return
        if (loadingMore || loading) return
        loadingMore = true
        scope.launch {
            try {
                val p = m(c)
                val keys = items.map { it.key }.toHashSet()
                items.addAll(p.items.filter { keys.add(it.key) })
                val old = page
                if (old != null && p.shelves.isNotEmpty()) page = old.copy(shelves = old.shelves + p.shelves)
                continuation = p.continuation?.takeIf { it != c }
                store()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continuation = null
            } finally {
                loadingMore = false
            }
        }
    }

    /** Ganti isi feed dengan halaman lain (dipakai saat chip dipilih). */
    fun replaceWith(loader: suspend () -> Page) {
        scope.launch {
            loading = true
            try {
                val p = loader()
                items.clear()
                items.addAll(p.items)
                continuation = p.continuation
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun rememberFeed(
    key: Any?,
    more: (suspend (String) -> Page)? = null,
    peek: (() -> Page?)? = null,
    first: suspend () -> Page,
): FeedLoader {
    val scope = rememberCoroutineScope()
    return remember(key) { FeedLoader(key, scope, first, more, peek).also { it.loadCachedOrFetch() } }
}

/** Panggil loadMore saat daftar hampir mencapai akhir. */
@Composable
fun LoadMoreEffect(state: LazyListState, feed: FeedLoader) {
    val nearEnd by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, feed.items.size) {
        if (nearEnd) feed.loadMore()
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, color: Color = YtRed) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = color, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun MessageBox(
    message: String,
    textColor: Color,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, color = textColor, textAlign = TextAlign.Center)
        if (action != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = YtRed, contentColor = Color.White),
            ) { Text(action) }
        }
    }
}

/** Semua PlayerView yang sedang tampil; yang terakhir dibuat memegang permukaan video. */
private val activeViews = mutableListOf<PlayerView>()

/** Permukaan video Media3. */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    modifier: Modifier = Modifier,
    controller: Boolean = true,
    onFullscreen: ((Boolean) -> Unit)? = null,
    zoom: Boolean = false,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.background(Color.Black)) {
    AndroidView(
        modifier = Modifier.matchParentSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                player = PlaybackManager.player
                useController = controller
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowNextButton(false)
                setShowPreviousButton(false)
                setKeepScreenOn(true)
                // Subtitle selalu di tengah bawah dengan gaya YouTube (abaikan posisi dari file).
                subtitleView?.apply {
                    setApplyEmbeddedStyles(false)
                    setApplyEmbeddedFontSizes(false)
                    setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.05f)
                    setBottomPaddingFraction(0.06f)
                    setStyle(
                        androidx.media3.ui.CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            0xB3000000.toInt(),
                            android.graphics.Color.TRANSPARENT,
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE,
                            android.graphics.Color.TRANSPARENT,
                            null,
                        )
                    )
                }
                if (onFullscreen != null) {
                    setFullscreenButtonClickListener { full -> onFullscreen(full) }
                }
                activeViews.add(this)
            }
        },
        update = { view ->
            if (view.player !== PlaybackManager.player) view.player = PlaybackManager.player
            view.useController = controller
            view.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        onRelease = { view ->
            activeViews.remove(view)
            view.player = null
            // Kembalikan permukaan video ke PlayerView lain yang masih tampil.
            activeViews.lastOrNull()?.let {
                it.player = null
                it.player = PlaybackManager.player
            }
        },
    )
    SubtitleOverlay(maxWidth)
    }
}

/** Subtitle di tengah bawah video, digambar Compose (tanpa memuat ulang pemutar). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SubtitleOverlay(width: androidx.compose.ui.unit.Dp) {
    val cues = PlaybackManager.subtitleCues
    if (cues.isEmpty()) return
    var pos by remember { mutableLongStateOf(PlaybackManager.exo.currentPosition) }
    LaunchedEffect(cues) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            pos = PlaybackManager.exo.currentPosition
        }
    }
    val cue = com.lumitube.app.data.Subtitles.active(cues, pos) ?: return
    val size = (width.value * 0.032f).coerceIn(9f, 18f)
    Text(
        cue.text,
        color = Color.White,
        fontSize = size.sp,
        lineHeight = (size * 1.3f).sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.align(Alignment.BottomCenter)
            .padding(start = 12.dp, end = 12.dp, bottom = (width.value * 0.035f).coerceIn(4f, 28f).dp)
            .background(Color(0xB3000000), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun shareVideo(context: Context, videoId: String, title: String, music: Boolean) {
    val url = if (music) "https://music.youtube.com/watch?v=$videoId" else "https://youtu.be/$videoId"
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    context.startActivity(Intent.createChooser(intent, "Bagikan"))
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

/**
 * Scope untuk perubahan ke akun (urutan/hapus playlist) yang tidak boleh batal
 * saat layar ditutup (misalnya langsung menekan kembali setelah menggeser).
 */
object EditScope {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
}
