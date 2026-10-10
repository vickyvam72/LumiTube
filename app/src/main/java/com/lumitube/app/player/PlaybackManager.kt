package com.lumitube.app.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.lumitube.app.data.Item
import com.lumitube.app.data.Repo
import com.lumitube.app.data.Stream
import com.lumitube.app.data.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, ALL, ONE }

/**
 * Satu pemutar untuk seluruh aplikasi: mode video (YouTube) dan mode audio (Music).
 * Antrean dikelola sendiri karena URL stream baru diambil saat lagu akan diputar.
 */
@OptIn(UnstableApi::class)
object PlaybackManager {

    lateinit var exo: ExoPlayer
        private set
    lateinit var player: Player
        private set

    val queue = mutableStateListOf<Item>()
    var index by mutableIntStateOf(-1)
        private set
    var current by mutableStateOf<Item?>(null)
        private set
    var isVideo by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var lyrics by mutableStateOf<String?>(null)
        private set
    var lyricsLoading by mutableStateOf(false)
        private set
    var repeat by mutableStateOf(RepeatMode.OFF)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null
    private var queueJob: Job? = null
    private var lyricsBrowseId: String? = null

    fun init(context: Context) {
        if (::exo.isInitialized) return
        exo = ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }

            override fun onPlayerError(e: PlaybackException) {
                error = "Gagal memutar: ${e.errorCodeName}"
            }
        })
        player = QueuePlayer(exo)
    }

    // ---------------- Perintah publik ----------------

    fun playVideo(item: Item) = playVideos(listOf(item), 0)

    /** Putar daftar video (mis. playlist); lanjut otomatis ke video berikutnya. */
    fun playVideos(items: List<Item>, start: Int) {
        if (items.isEmpty()) return
        queueJob?.cancel()
        queue.clear()
        queue.addAll(items)
        index = start.coerceIn(0, items.size - 1)
        isVideo = true
        load(queue[index])
    }

    /** Ganti mode (video <-> audio) untuk item yang sedang diputar, lanjut dari posisi yang sama. */
    fun switchMode(video: Boolean) {
        val item = current ?: return
        if (isVideo == video) return
        val pos = exo.currentPosition
        isVideo = video
        load(item, pos)
    }

    /** Putar daftar lagu. Jika [radio] true, antrean diisi otomatis dari radio YT Music. */
    fun playMusic(items: List<Item>, start: Int = 0, radio: Boolean = items.size == 1) {
        if (items.isEmpty()) return
        queueJob?.cancel()
        queue.clear()
        queue.addAll(items)
        index = start.coerceIn(0, items.size - 1)
        isVideo = false
        val item = queue[index]
        load(item)
        if (radio) {
            queueJob = scope.launch {
                try {
                    val q = Repo.musicQueue(item.id, item.playlistId)
                    if (current?.id == item.id) {
                        lyricsBrowseId = q.lyricsBrowseId
                        val rest = q.items.filter { it.id != item.id }
                        queue.clear()
                        queue.add(item)
                        queue.addAll(rest)
                        index = 0
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    fun playAt(i: Int) {
        if (i !in queue.indices) return
        index = i
        load(queue[i])
    }

    fun addNext(item: Item) {
        if (queue.isEmpty()) {
            playMusic(listOf(item), 0, radio = false)
        } else {
            queue.add(index + 1, item)
        }
    }

    fun next() {
        if (index + 1 < queue.size) {
            index++
            load(queue[index])
        } else if (repeat == RepeatMode.ALL && queue.isNotEmpty()) {
            index = 0
            load(queue[0])
        }
    }

    fun previous() {
        if (exo.currentPosition > 3000 || index <= 0) {
            exo.seekTo(0)
        } else {
            index--
            load(queue[index])
        }
    }

    fun togglePlay() {
        if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun shuffle() {
        if (queue.size <= index + 2) return
        val rest = queue.subList(index + 1, queue.size).shuffled()
        while (queue.size > index + 1) queue.removeAt(queue.size - 1)
        queue.addAll(rest)
    }

    fun stop() {
        loadJob?.cancel()
        queueJob?.cancel()
        exo.stop()
        exo.clearMediaItems()
        queue.clear()
        index = -1
        current = null
        isVideo = false
    }

    fun retry() {
        current?.let { load(it) }
    }

    fun loadLyrics() {
        if (lyrics != null || lyricsLoading) return
        val item = current ?: return
        lyricsLoading = true
        scope.launch {
            try {
                val id = lyricsBrowseId ?: Repo.musicQueue(item.id, item.playlistId).lyricsBrowseId
                lyricsBrowseId = id
                if (current?.id == item.id) {
                    lyrics = id?.let { Repo.lyrics(it) } ?: ""
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                lyrics = ""
            } finally {
                lyricsLoading = false
            }
        }
    }

    // ---------------- Internal ----------------

    private fun onEnded() {
        if (repeat == RepeatMode.ONE) {
            exo.seekTo(0)
            exo.play()
        } else {
            next()
        }
    }

    private fun load(item: Item, startPosition: Long = 0L) {
        loadJob?.cancel()
        current = item
        error = null
        lyrics = null
        lyricsBrowseId = null
        loading = true
        exo.stop()
        val audioOnly = !isVideo
        loadJob = scope.launch {
            try {
                val stream = StreamResolver.resolve(item.id, audioOnly)
                exo.setMediaSource(buildSource(item, stream, audioOnly), startPosition)
                exo.prepare()
                exo.play()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Gagal memutar"
            } finally {
                if (current?.id == item.id) loading = false
            }
        }
    }

    private fun buildSource(item: Item, s: Stream, audioOnly: Boolean): MediaSource {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(s.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val artist = item.channelName ?: s.author ?: item.subtitle.substringBefore(" • ")
        val meta = MediaMetadata.Builder()
            .setTitle(s.title ?: item.title)
            .setArtist(artist)
            .apply { item.thumbnail?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
        fun mediaItem(url: String) = MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(url)
            .setMediaMetadata(meta)
            .build()

        val videoUrl = s.videoUrl
        val audioUrl = s.audioUrl
        val hls = s.hlsUrl
        return when {
            s.isLive && hls != null ->
                HlsMediaSource.Factory(http).createMediaSource(mediaItem(hls))
            audioOnly && audioUrl != null ->
                ProgressiveMediaSource.Factory(http).createMediaSource(mediaItem(audioUrl))
            !audioOnly && videoUrl != null && audioUrl != null -> MergingMediaSource(
                ProgressiveMediaSource.Factory(http).createMediaSource(mediaItem(videoUrl)),
                ProgressiveMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(audioUrl)),
            )
            hls != null -> HlsMediaSource.Factory(http).createMediaSource(mediaItem(hls))
            else -> throw IllegalStateException("Stream tidak tersedia")
        }
    }
}

/** Membuat tombol next/prev di notifikasi & layar kunci memakai antrean milik kita. */
@OptIn(UnstableApi::class)
class QueuePlayer(player: Player) : ForwardingPlayer(player) {
    private val extra = intArrayOf(
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    )

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(*extra).build()

    override fun isCommandAvailable(command: Int): Boolean =
        command in extra || super.isCommandAvailable(command)

    override fun hasNextMediaItem(): Boolean = PlaybackManager.index + 1 < PlaybackManager.queue.size
    override fun hasPreviousMediaItem(): Boolean = PlaybackManager.index > 0
    override fun seekToNext() = PlaybackManager.next()
    override fun seekToNextMediaItem() = PlaybackManager.next()
    override fun seekToPrevious() = PlaybackManager.previous()
    override fun seekToPreviousMediaItem() = PlaybackManager.previous()
}
