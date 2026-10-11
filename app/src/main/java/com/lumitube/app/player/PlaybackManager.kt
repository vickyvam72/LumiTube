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
import com.lumitube.app.data.InnerTube
import com.lumitube.app.data.Parser
import com.lumitube.app.data.Stream
import com.lumitube.app.data.StreamResolver
import com.lumitube.app.data.Session
import com.lumitube.app.data.ItemType
import com.lumitube.app.data.lyrics.Lyrics
import com.lumitube.app.data.lyrics.LyricsRepo
import com.lumitube.app.data.lyrics.SongQuery
import androidx.media3.datasource.DefaultDataSource
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
    /** true saat ExoPlayer sedang buffering (menunggu data). */
    var buffering by mutableStateOf(false)
        private set
    /** videoId yang frame pertamanya sudah tampil (untuk menyembunyikan thumbnail). */
    var firstFrameFor by mutableStateOf<String?>(null)
        private set
    var playWhenReady by mutableStateOf(false)
        private set
    /** Daftar subtitle untuk video saat ini; -1 = nonaktif. */
    var captions by mutableStateOf<List<com.lumitube.app.data.Caption>>(emptyList())
        private set
    var captionIndex by mutableIntStateOf(-1)
        private set
    /** Kode bahasa terjemahan otomatis (null = tanpa terjemahan). */
    var captionTranslate by mutableStateOf<String?>(null)
        private set
    var translations by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var captionLoading by mutableStateOf(false)
        private set
    var captionError by mutableStateOf<String?>(null)
    private var captionFile: java.io.File? = null
    /** Dipanggil saat video selesai & autoplay aktif (mis. putar video terkait berikutnya). */
    var onAutoNext: (() -> Unit)? = null
    /** Video terkait berikutnya (untuk tombol next & putar otomatis). */
    var relatedNext by mutableStateOf<Item?>(null)
    /** Jumlah percobaan ulang (ganti klien) untuk item saat ini. */
    private var retries = 0
    private var currentClient = ""
    /** Semua hasil lirik untuk lagu saat ini (terbaik di depan). */
    var lyricsList by mutableStateOf<List<Lyrics>>(emptyList())
        private set
    var lyricsIndex by mutableIntStateOf(0)
    var lyricsLoading by mutableStateOf(false)
        private set
    private var lyricsFor: String? = null
    /** Judul (dinormalkan) & durasi lagu pemilik lirik saat ini. */
    private var lyricsSong: String? = null
    private var lyricsDuration = 0L

    /** "Opalite (Official Music Video)" == "Opalite" -> versi lain dari lagu yang sama. */
    private fun songKey(title: String): String {
        val k = title.lowercase()
            .replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ")
            .replace(Regex("\\b(official|music video|lyric video|lyrics?|audio|video|visualizer|mv)\\b"), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), "")
        return k.ifEmpty { title.lowercase().trim() }
    }
    val lyrics: Lyrics? get() = lyricsList.getOrNull(lyricsIndex)

    /** Resolusi video yang tersedia untuk video saat ini. */
    var qualities by mutableStateOf<List<Int>>(emptyList())
        private set
    var speed by mutableStateOf(1f)
        private set
    private lateinit var appContext: Context
    var repeat by mutableStateOf(RepeatMode.OFF)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null
    private var queueJob: Job? = null

    fun init(context: Context) {
        if (::exo.isInitialized) return
        appContext = context.applicationContext
        exo = ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            // Mulai putar lebih cepat: cukup 0,7 detik data untuk mulai
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 60_000, 500, 1_000)
                    .build()
            )
            .build()
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayWhenReadyChanged(play: Boolean, reason: Int) {
                playWhenReady = play
            }

            override fun onRenderedFirstFrame() {
                firstFrameFor = current?.id
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) onEnded()
                if (state == Player.STATE_READY && !isVideo) loadLyrics()
            }

            override fun onPlayerError(e: PlaybackException) {
                // URL kedaluwarsa / ditolak (HTTP 403 di tengah lagu): ambil URL baru
                // dari klien lain dan lanjutkan dari posisi yang sama, sampai beberapa kali.
                val item = current
                if (item != null && item.localUri == null && retries < 5) {
                    retries++
                    // Gangguan koneksi: coba lagi klien yang sama. Selain itu (403, data rusak/
                    // bukan media, dsb.) URL dari klien ini bermasalah -> ganti klien.
                    val network = e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                    if (!network || retries > 2) {
                        if (!StreamResolver.markRejected(item.id, currentClient)) StreamResolver.resetRejected(item.id)
                    } else {
                        StreamResolver.invalidate(item.id)
                    }
                    load(item, exo.currentPosition.coerceAtLeast(0))
                } else {
                    error = "Gagal memutar: ${e.errorCodeName}"
                }
            }
        })
        // Seek ke keyframe terdekat: maju/mundur jauh lebih cepat (seperti aplikasi YouTube)
        exo.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        player = QueuePlayer(exo)
        startWatchdog()
        // Mesin cipher (WebView) untuk klien web, disiapkan di latar belakang.
        com.lumitube.app.data.Cipher.cacheDir = appContext.cacheDir
        com.lumitube.app.data.Cipher.runner = WebJsRunner(appContext)
        scope.launch {
            kotlinx.coroutines.delay(1500)
            com.lumitube.app.data.Cipher.prewarm()
        }
    }

    /**
     * Penjaga "stuck": jika buffering lebih dari 10 detik tanpa data baru,
     * muat ulang stream dari posisi saat ini.
     */
    private fun startWatchdog() {
        scope.launch {
            var lastBuffered = -1L
            var stuckSince = 0L
            while (true) {
                kotlinx.coroutines.delay(2000)
                val item = current ?: continue
                if (exo.playbackState == Player.STATE_BUFFERING && exo.playWhenReady && !loading && item.localUri == null) {
                    val b = exo.bufferedPosition
                    val now = System.currentTimeMillis()
                    if (b != lastBuffered) {
                        lastBuffered = b
                        stuckSince = now
                    } else if (stuckSince > 0 && now - stuckSince > 10_000) {
                        stuckSince = 0
                        StreamResolver.invalidate(item.id)
                        load(item, exo.currentPosition.coerceAtLeast(0))
                    }
                } else {
                    lastBuffered = -1
                    stuckSince = 0
                }
            }
        }
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
                        // Radio YT Music sering memuat versi lain dari lagu yang sama (mis. video
                        // klip) -> lagu seolah "mulai ulang" setelah selesai. Buang duplikat itu.
                        val key = songKey(item.title)
                        val rest = q.items.filter { it.id != item.id && songKey(it.title) != key }
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

    /** Next untuk mode video: antrean dulu, lalu video terkait (dan siapkan terkait berikutnya). */
    fun nextVideo() {
        if (!isVideo || index + 1 < queue.size) {
            next()
            return
        }
        val n = relatedNext ?: return
        relatedNext = null
        playVideo(n.copy(type = ItemType.VIDEO))
        scope.launch {
            try {
                val w = Repo.watch(n.id)
                if (current?.id == n.id && relatedNext == null) {
                    relatedNext = w.related.firstOrNull { it.type == ItemType.VIDEO }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
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
        relatedNext = null
    }

    fun retry() {
        current?.let { load(it) }
    }

    /** Ambil lirik dari semua penyedia (sekali per lagu). */
    fun loadLyrics(force: Boolean = false) {
        val item = current ?: return
        if (!force && (lyricsFor == item.id || lyricsLoading)) return
        val durMs = exo.duration.takeIf { it > 0 } ?: 0L
        // Lagu yang sama diputar ulang (atau versi lain dengan durasi sama): pakai lirik yang ada.
        if (!force && lyricsList.isNotEmpty() && lyricsSong == songKey(item.title) &&
            (durMs == 0L || lyricsDuration == 0L || kotlin.math.abs(durMs - lyricsDuration) <= 3000)
        ) {
            lyricsFor = item.id
            return
        }
        lyricsFor = item.id
        lyricsSong = songKey(item.title)
        lyricsDuration = durMs
        chosenSource = null
        lyricsLoading = true
        lyricsList = emptyList()
        lyricsIndex = 0
        val dur = (exo.duration.takeIf { it > 0 } ?: 0L) / 1000
        scope.launch {
            try {
                val q = songQuery(item, dur.toInt())
                // Hasil ditampilkan bertahap: begitu ada lirik sinkron, langsung muncul.
                val list = LyricsRepo.fetchAll(q) { partial ->
                    scope.launch { if (current?.id == item.id) applyLyrics(partial) }
                }
                if (current?.id == item.id) applyLyrics(list)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                if (current?.id == item.id) lyricsLoading = false
            }
        }
    }

    private var chosenSource: String? = null

    /**
     * Metadata lagu yang akurat untuk pencarian lirik. Judul/artis dari kartu beranda bisa
     * berisi label tipe ("Lagu"), jadi ambil dari antrean YT Music (artis • album • tahun)
     * dan cadangan dari detail video ("Taylor Swift - Topic").
     */
    private suspend fun songQuery(item: Item, durationSec: Int): SongQuery {
        var artist = item.channelName?.takeIf { it.isNotBlank() && Parser.artistFromSubtitle(it) == it }
        var album: String? = null
        var title = item.title
        try {
            val rich = Repo.musicQueue(item.id).items.firstOrNull { it.id == item.id }
            if (rich != null) {
                val parts = rich.subtitle.split(" • ").map { it.trim() }
                rich.channelName?.takeIf { it.isNotBlank() }?.let { artist = it }
                if (parts.size >= 3) album = parts[1]
                title = rich.title.ifBlank { title }
                // Perbarui tampilan (mini player menampilkan artis yang benar)
                if (current?.id == item.id && artist != null) {
                    val upd = current!!.copy(channelName = artist, subtitle = rich.subtitle.ifBlank { current!!.subtitle })
                    current = upd
                    if (index in queue.indices && queue[index].id == item.id) queue[index] = upd
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        if (artist == null) {
            artist = try {
                StreamResolver.resolve(item.id, true).author?.removeSuffix(" - Topic")
            } catch (_: Exception) {
                null
            }
        }
        return SongQuery(item.id, title, artist ?: Parser.artistFromSubtitle(item.subtitle) ?: "", album, durationSec)
    }

    private fun applyLyrics(list: List<Lyrics>) {
        val keep = chosenSource
        lyricsList = list
        lyricsIndex = keep?.let { name -> list.indexOfFirst { it.source == name }.takeIf { it >= 0 } } ?: 0
    }

    fun nextLyricsSource() {
        if (lyricsList.size > 1) {
            lyricsIndex = (lyricsIndex + 1) % lyricsList.size
            chosenSource = lyricsList[lyricsIndex].source
        }
    }

    /** Ganti kualitas video dan lanjutkan dari posisi yang sama. */
    fun setQuality(height: Int) {
        Session.maxQuality = height
        val item = current ?: return
        if (!isVideo || item.localUri != null) return
        val pos = exo.currentPosition
        load(item, pos)
    }

    /**
     * Pilih subtitle (-1 = nonaktif). Bila [translate] diisi, subtitle diterjemahkan otomatis
     * oleh YouTube ke bahasa tersebut. File VTT diunduh dulu lalu dipasang sebagai trek teks.
     */
    fun setCaption(i: Int, translate: String? = null) {
        val item = current ?: return
        if (i < 0) {
            captionIndex = -1
            captionTranslate = null
            captionFile = null
            if (isVideo && item.localUri == null) load(item, exo.currentPosition)
            return
        }
        val cap = captions.getOrNull(i) ?: return
        captionLoading = true
        captionError = null
        scope.launch {
            try {
                val file = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val body = com.lumitube.app.data.Subtitles.load(item.id, cap, translate)
                    java.io.File(appContext.cacheDir, "cap_${item.id}_${cap.lang}_${translate ?: "o"}.vtt").apply { writeText(body) }
                }
                if (current?.id != item.id) return@launch
                captionIndex = i
                captionTranslate = translate
                captionFile = file
                if (isVideo && item.localUri == null) load(item, exo.currentPosition)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                captionError = "Subtitle tidak bisa dimuat"
            } finally {
                captionLoading = false
            }
        }
    }

    fun seekBy(ms: Long) {
        val d = exo.duration
        var t = exo.currentPosition + ms
        if (d > 0) t = t.coerceAtMost(d - 500)
        exo.seekTo(t.coerceAtLeast(0))
    }

    fun setPlaybackSpeed(value: Float) {
        speed = value
        exo.setPlaybackSpeed(value)
    }

    // ---------------- Internal ----------------

    private fun onEnded() {
        val loop = repeat == RepeatMode.ONE ||
            (isVideo && (Session.loopVideo || current?.type == ItemType.SHORT))
        if (loop) {
            exo.seekTo(0)
            exo.play()
        } else if (isVideo && index + 1 >= queue.size && Session.autoplay && relatedNext != null &&
            current?.type != ItemType.SHORT
        ) {
            nextVideo()
        } else {
            next()
        }
    }

    private fun load(item: Item, startPosition: Long = 0L) {
        if (current?.id != item.id) {
            if (relatedNext?.id == item.id) relatedNext = null
            captions = emptyList()
            translations = emptyList()
            captionIndex = -1
            captionTranslate = null
            captionFile = null
            retries = 0
        }
        loadJob?.cancel()
        current = item
        error = null
        if (lyricsFor != item.id && lyricsSong != songKey(item.title)) {
            lyricsList = emptyList()
            lyricsIndex = 0
        }
        loading = true
        if (firstFrameFor == item.id && startPosition == 0L) firstFrameFor = null
        exo.stop()
        val audioOnly = !isVideo
        loadJob = scope.launch {
            try {
                val local = item.localUri
                if (local != null) {
                    qualities = emptyList()
                    val meta = MediaMetadata.Builder().setTitle(item.title).setArtist(item.channelName ?: "")
                        .apply { item.thumbnail?.let { setArtworkUri(Uri.parse(it)) } }.build()
                    val mi = MediaItem.Builder().setMediaId(item.id).setUri(Uri.parse(local)).setMediaMetadata(meta).build()
                    exo.setMediaSource(
                        ProgressiveMediaSource.Factory(DefaultDataSource.Factory(appContext)).createMediaSource(mi),
                        startPosition
                    )
                } else {
                    val stream = StreamResolver.resolve(item.id, audioOnly)
                    currentClient = stream.client
                    qualities = stream.qualities
                    captions = stream.captions
                    translations = stream.translations
                    queue.getOrNull(index + 1)?.let { if (it.localUri == null) StreamResolver.prefetch(it.id) }
                    exo.setMediaSource(buildSource(item, stream, audioOnly), startPosition)
                }
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

    /**
     * Trek subtitle dari file VTT lokal. Media3 1.4+ mengharuskan subtitle diurai saat ekstraksi
     * (SingleSampleMediaSource menimbulkan ERROR_CODE_FAILED_RUNTIME_CHECK), jadi pakai SubtitleExtractor.
     */
    private fun subtitleSource(file: java.io.File, lang: String, label: String): MediaSource {
        val format = androidx.media3.common.Format.Builder()
            .setSampleMimeType(androidx.media3.common.MimeTypes.TEXT_VTT)
            .setLanguage(lang)
            .setLabel(label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
            .build()
        val parserFactory = androidx.media3.extractor.text.DefaultSubtitleParserFactory()
        val extractors = androidx.media3.extractor.ExtractorsFactory {
            arrayOf(androidx.media3.extractor.text.SubtitleExtractor(parserFactory.create(format), format))
        }
        return ProgressiveMediaSource.Factory(DefaultDataSource.Factory(appContext), extractors)
            .createMediaSource(MediaItem.fromUri(Uri.fromFile(file)))
    }

    private val mediaHttp: okhttp3.OkHttpClient by lazy {
        InnerTube.http.newBuilder()
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun buildSource(item: Item, s: Stream, audioOnly: Boolean): MediaSource {
        // OkHttp: koneksi dipakai ulang antar potongan (tanpa handshake TLS berulang), mendukung HTTP/2.
        val plainHttp = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(mediaHttp)
            .setUserAgent(s.userAgent.ifEmpty { null })
            .setDefaultRequestProperties(s.headers)
        // Progressive (video/audio terpisah) diunduh per potongan agar tidak macet.
        val http = ChunkedDataSource.Factory(plainHttp, s.chunk)
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
                HlsMediaSource.Factory(plainHttp).createMediaSource(mediaItem(hls))
            audioOnly && audioUrl != null ->
                ProgressiveMediaSource.Factory(http).createMediaSource(mediaItem(audioUrl))
            !audioOnly && videoUrl != null && audioUrl != null -> {
                val sources = mutableListOf<MediaSource>(
                    ProgressiveMediaSource.Factory(http).createMediaSource(mediaItem(videoUrl)),
                    ProgressiveMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(audioUrl)),
                )
                val capFile = captionFile
                val cap = s.captions.getOrNull(captionIndex)
                if (capFile != null && cap != null && capFile.exists()) {
                    sources.add(subtitleSource(capFile, captionTranslate ?: cap.lang, cap.name))
                }
                MergingMediaSource(*sources.toTypedArray())
            }
            hls != null -> HlsMediaSource.Factory(plainHttp).createMediaSource(mediaItem(hls))
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

    override fun hasNextMediaItem(): Boolean =
        PlaybackManager.index + 1 < PlaybackManager.queue.size || PlaybackManager.relatedNext != null
    override fun hasPreviousMediaItem(): Boolean = PlaybackManager.index > 0
    override fun seekToNext() = PlaybackManager.nextVideo()
    override fun seekToNextMediaItem() = PlaybackManager.nextVideo()
    override fun seekToPrevious() = PlaybackManager.previous()
    override fun seekToPreviousMediaItem() = PlaybackManager.previous()
}
