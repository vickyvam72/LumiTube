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
) {
    Box(modifier.background(placeholder)) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Memuat halaman pertama + halaman lanjutan (infinite scroll). */
class FeedLoader(
    private val scope: CoroutineScope,
    private val first: suspend () -> Page,
    private val more: (suspend (String) -> Page)?,
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
    first: suspend () -> Page,
): FeedLoader {
    val scope = rememberCoroutineScope()
    return remember(key) { FeedLoader(scope, first, more).also { it.load() } }
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
) {
    AndroidView(
        modifier = modifier.background(Color.Black),
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
                if (onFullscreen != null) {
                    setFullscreenButtonClickListener { full -> onFullscreen(full) }
                }
                activeViews.add(this)
            }
        },
        update = { view ->
            if (view.player !== PlaybackManager.player) view.player = PlaybackManager.player
            view.useController = controller
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
