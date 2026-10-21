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
    /** Subtitle digambar sendiri di atas video, jadi menyalakan/mematikan CC tidak memuat ulang video. */
    // ---------------- Tahan untuk 2x ----------------

    private var holdJob: Job? = null
    private var holdWasPlaying = false

    /** 1 = putar 2x lebih cepat, -1 = mundur dengan kecepatan 2x. */
    fun startHoldSeek(direction: Int) {
        holdJob?.cancel()
        holdWasPlaying = exo.playWhenReady
        if (direction > 0) {
            exo.setPlaybackSpeed(speed * 2f)
            exo.play()
        } else {
            // ExoPlayer tidak bisa diputar mundur: lompat mundur sedikit-sedikit dengan cepat.
            exo.pause()
            exo.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
            holdJob = scope.launch {
                while (true) {
                    if (exo.playbackState != Player.STATE_BUFFERING) {
                        val t = (exo.currentPosition - 500).coerceAtLeast(0)
                        exo.seekTo(t)
                        if (t == 0L) break
                    }
                    kotlinx.coroutines.delay(250)
                }
            }
        }
    }

    fun stopHoldSeek() {
        holdJob?.cancel()
        holdJob = null
        exo.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        exo.setPlaybackSpeed(speed)
        if (holdWasPlaying) exo.play()
        holdWasPlaying = false
    }

    // ---------------- Timer tidur ----------------

    /** Waktu (epoch ms) aplikasi akan ditutup, atau null. */
    var sleepAt by mutableStateOf<Long?>(null)
        private set
    /** Tutup setelah video/lagu yang sedang diputar selesai. */
    var sleepEndOfItem by mutableStateOf(false)
        private set
    /** Bertambah saat timer habis; MainActivity menutup aplikasi. */
    var closeRequested by mutableIntStateOf(0)
        private set

    /** Tombol Tutup di notifikasi ditekan: jendela PiP (bila ada) ikut ditutup. */
    var pipCloseRequested by mutableIntStateOf(0)

    /** Dipanggil MainActivity setelah menutup aplikasi, supaya bisa dibuka lagi. */
    fun closeHandled() {
        closeRequested = 0
    }
    private var sleepJob: Job? = null

    fun setSleepMinutes(minutes: Int) = setSleepAtTime(System.currentTimeMillis() + minutes * 60_000L)

    fun setSleepAtTime(at: Long) {
        cancelSleep()
        sleepAt = at
        sleepJob = scope.launch {
            while (true) {
                val left = at - System.currentTimeMillis()
                if (left <= 0) break
                kotlinx.coroutines.delay(minOf(left, 15_000L))
            }
            sleepNow()
        }
    }

    fun setSleepEndOfItem() {
        cancelSleep()
        sleepEndOfItem = true
    }

    fun cancelSleep() {
        sleepJob?.cancel()
        sleepJob = null
        sleepAt = null
        sleepEndOfItem = false
    }

    private fun sleepNow() {
        cancelSleep()
        stop()
        closeRequested++
    }

    /** Gambar pratinjau timeline untuk video saat ini. */
    var storyboard by mutableStateOf<com.lumitube.app.data.Storyboard?>(null)
        private set
    /** Bab video (diisi halaman tonton). */
    var chapters by mutableStateOf<List<Pair<Long, String>>>(emptyList())

    /** Video yang diputar adalah siaran langsung. */
    var isLiveStream by mutableStateOf(false)
        private set

    /** Lompat ke bagian terkini siaran langsung. */
    fun goLive() {
        exo.seekToDefaultPosition()
        exo.play()
    }

    var subtitleCues by mutableStateOf<List<com.lumitube.app.data.SubCue>>(emptyList())
        private set
    /** Dipanggil saat video selesai & autoplay aktif (mis. putar video terkait berikutnya). */
    var onAutoNext: (() -> Unit)? = null
    /** Video terkait berikutnya (untuk tombol next & putar otomatis). */
    var relatedNext by mutableStateOf<Item?>(null)
    private var netRetries = 0

    private fun isNetworkError(e: Throwable?): Boolean {
        var t = e
        while (t != null) {
            if (t is java.net.UnknownHostException || t is java.net.SocketTimeoutException ||
                t is java.net.ConnectException || t is java.net.NoRouteToHostException
            ) return true
            val m = t.message ?: ""
            if (m.contains("hostname", true) || m.contains("resolve host", true)) return true
            t = t.cause
        }
        return false
    }

    /** Jumlah percobaan ulang (ganti klien) untuk item saat ini. */
    private var retries = 0
    private var currentClient = ""
    private var currentNote = ""
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

    /** Antrean berasal dari playlist/album (bukan radio satu lagu): ulangi-semua berlaku. */
    var playlistQueue by mutableStateOf(false)
        private set

    /**
     * Tombol ulangi. Playlist: Mati → Ulangi playlist → Ulangi 1 lagu → Mati.
     * Satu lagu / radio: Mati → Ulangi 1 lagu → Mati (ikon ulangi-semua tidak dipakai).
     */
    fun cycleRepeat() {
        repeat = if (playlistQueue) {
            when (repeat) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
        } else {
            if (repeat == RepeatMode.ONE) RepeatMode.OFF else RepeatMode.ONE
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + com.lumitube.app.CrashLog.handler)
    private lateinit var trackSelector: androidx.media3.exoplayer.trackselection.DefaultTrackSelector

    /** Batas kualitas untuk stream adaptif (live HLS) sesuai setelan kualitas. */
    private fun applyQualityCap() {
        if (!::trackSelector.isInitialized) return
        val q = Session.maxQuality
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setViewportSize(Int.MAX_VALUE, Int.MAX_VALUE, false)
                .setMaxVideoSize(if (q >= 1080) 1920 else if (q >= 720) 1280 else if (q >= 480) 854 else 640, q.coerceAtLeast(360))
        )
    }
    private var loadJob: Job? = null
    private var queueJob: Job? = null

    fun init(context: Context) {
        if (::exo.isInitialized) return
        appContext = context.applicationContext
        trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context.applicationContext)
        applyQualityCap()
        exo = ExoPlayer.Builder(context.applicationContext)
            .setTrackSelector(trackSelector)
            // Perkiraan awal kecepatan internet tinggi: live (HLS) langsung mulai di kualitas setelan
            // (mis. 720p), bukan dari resolusi terendah lalu naik pelan-pelan.
            .setBandwidthMeter(
                androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context.applicationContext)
                    .setInitialBitrateEstimate(8_000_000L)
                    .build()
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            // Layar mati: tahan CPU + Wi-Fi tetap aktif selama memutar. Tanpa ini Android
            // menidurkan jaringan, data berhenti mengalir, dan lagu berhenti sendiri.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Mulai putar lebih cepat: cukup 0,7 detik data untuk mulai
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    // Mulai cepat (0,5 dtk), tapi setelah sempat buffering tunggu 2,5 dtk supaya tidak
                    // putus-putus; simpan cadangan 30-60 dtk di depan (seperti NewPipe/YouTube).
                    .setBufferDurationsMs(30_000, 120_000, 500, 2_500)
                    .setPrioritizeTimeOverSizeThresholds(true)
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
                if (state == Player.STATE_READY) registerHistory()
            }

            override fun onPlayerError(e: PlaybackException) {
                // URL kedaluwarsa / ditolak (HTTP 403 di tengah lagu): ambil URL baru
                // dari klien lain dan lanjutkan dari posisi yang sama, sampai beberapa kali.
                val item = current
                // Live dijeda terlalu lama (tertinggal dari jendela siaran): langsung lompat ke bagian terkini,
                // bukan layar hitam.
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exo.seekToDefaultPosition()
                    exo.prepare()
                    exo.play()
                    return
                }
                if (item != null && item.localUri == null && isNetworkError(e.cause) && netRetries < 4) {
                    // Internet putus sesaat: tunggu sebentar lalu lanjut dari posisi yang sama.
                    netRetries++
                    val pos = exo.currentPosition.coerceAtLeast(0)
                    scope.launch {
                        kotlinx.coroutines.delay(2000L * netRetries)
                        if (current?.id == item.id) load(item, pos)
                    }
                    return
                }
                if (item != null && item.localUri == null && retries < 5) {
                    retries++
                    // Gangguan koneksi: coba lagi klien yang sama. Selain itu (403, data rusak/
                    // bukan media, dsb.) URL dari klien ini bermasalah -> ganti klien.
                    val network = e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                    if (!network || retries > 2) {
                        val http = (e.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                        StreamResolver.notePlaybackError(item.id, currentClient.substringBefore('/'), (http?.let { "HTTP $it" } ?: e.errorCodeName) + if (currentNote.isNotEmpty()) ", $currentNote" else "")
                        if (!StreamResolver.markRejected(item.id, currentClient)) StreamResolver.resetRejected(item.id)
                    } else {
                        StreamResolver.invalidate(item.id)
                    }
                    load(item, exo.currentPosition.coerceAtLeast(0))
                } else {
                    error = com.lumitube.app.tr("Gagal memutar: %s", e.errorCodeName)
                    // Lanjutkan otomatis begitu gangguan selesai (internet kembali / server pulih).
                    if (item != null && item.localUri == null) {
                        resumeFor = item.id
                        resumePos = exo.currentPosition.coerceAtLeast(0)
                    }
                }
            }
        })
        // Seek ke keyframe terdekat: maju/mundur jauh lebih cepat (seperti aplikasi YouTube)
        exo.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        player = QueuePlayer(exo)
        startWatchdog()
        startAutoResume(context)
        // Saat lagu di-download, lirik terbaik ikut disimpan (untuk diputar offline).
        if (com.lumitube.app.CrossApp.isMusic) {
            com.lumitube.app.data.Downloader.lyricsFetcher = { item ->
                val dur = try { StreamResolver.resolve(item.id, true).lengthSeconds.toInt() } catch (_: Exception) { 0 }
                val q = songQuery(item, dur)
                // Ambil lirik yang sudah ketemu walau penyedia lain belum selesai (tidak menunggu yang lambat).
                var best: com.lumitube.app.data.lyrics.Lyrics? = null
                fun pick(list: List<com.lumitube.app.data.lyrics.Lyrics>) =
                    list.firstOrNull { it.synced && it.lines.isNotEmpty() } ?: list.firstOrNull { it.lines.isNotEmpty() }
                val all = kotlinx.coroutines.withTimeoutOrNull(35_000) {
                    com.lumitube.app.data.lyrics.LyricsRepo.fetchAll(q) { partial -> pick(partial)?.let { best = it } }
                }
                all?.let { pick(it) } ?: best
            }
            // Download lama (sebelum fitur ini) dilengkapi liriknya di latar belakang.
            scope.launch {
                kotlinx.coroutines.delay(15_000)
                com.lumitube.app.data.Downloader.backfillLyrics()
            }
        }
        // Mesin cipher (WebView) untuk klien web, disiapkan di latar belakang.
        com.lumitube.app.data.Cipher.cacheDir = appContext.cacheDir
        com.lumitube.app.data.Cipher.runner = WebJsRunner(appContext)
        scope.launch {
            kotlinx.coroutines.delay(200)
            com.lumitube.app.data.Cipher.prewarm()
        }
    }

    /**
     * Penjaga "stuck": jika buffering lebih dari 10 detik tanpa data baru,
     * muat ulang stream dari posisi saat ini.
     */
    // ---------------- Lanjut otomatis setelah gangguan ----------------

    /** Lagu/video yang terhenti karena gangguan dan harus diputar lagi otomatis. */
    private var resumeFor: String? = null
    private var resumePos = 0L
    private var lastResumeTry = 0L

    private fun online(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) {
        true
    }

    /**
     * Begitu internet tersambung lagi (atau gangguan selesai), putar ulang otomatis dari posisi
     * terakhir dan cari lirik yang tadi gagal dimuat. Berlaku untuk video maupun lagu.
     */
    private fun startAutoResume(context: Context) {
        val app = context.applicationContext
        try {
            app.getSystemService(android.net.ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
                object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        scope.launch {
                            kotlinx.coroutines.delay(1500)
                            tryResume(force = true)
                        }
                    }
                }
            )
        } catch (_: Exception) {
        }
        // Cadangan: periksa berkala (mis. gangguan dari server, bukan internet putus).
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000)
                if (resumeFor != null && online(app)) tryResume(force = false)
                // Lirik yang gagal dimuat karena gangguan: cari lagi saat sudah online.
                val cur = current
                if (cur != null && !isVideo && lyricsList.isEmpty() && !lyricsLoading && lyricsFor == null &&
                    exo.playbackState == Player.STATE_READY && online(app)
                ) {
                    loadLyrics()
                }
            }
        }
    }

    private fun tryResume(force: Boolean) {
        val item = current ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastResumeTry < 15_000) return
        val stalled = resumeFor == item.id || (error != null && item.localUri == null) ||
            (exo.playbackState == Player.STATE_IDLE && exo.playWhenReady && item.localUri == null && !loading)
        if (!stalled) {
            // Lagu tetap jalan tapi liriknya tadi gagal: cari lagi.
            if (!isVideo && lyricsList.isEmpty() && !lyricsLoading) {
                lyricsTries.remove(item.id)
                lyricsFor = null
                loadLyrics()
            }
            return
        }
        lastResumeTry = now
        val pos = if (resumeFor == item.id) resumePos else exo.currentPosition.coerceAtLeast(0)
        resumeFor = null
        error = null
        retries = 0
        netRetries = 0
        lyricsTries.remove(item.id)
        if (lyricsList.isEmpty()) lyricsFor = null
        StreamResolver.invalidate(item.id)
        load(item, pos)
        // Lirik dicari otomatis saat lagu siap diputar (STATE_READY).
    }

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
                        // Macet total: klien ini kemungkinan dibatasi -> pakai klien lain.
                        if (!StreamResolver.markRejected(item.id, currentClient)) StreamResolver.resetRejected(item.id)
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
        playlistQueue = items.size > 1
        if (!playlistQueue && repeat == RepeatMode.ALL) repeat = RepeatMode.OFF
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
        playlistQueue = !radio && items.size > 1
        if (!playlistQueue && repeat == RepeatMode.ALL) repeat = RepeatMode.OFF
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
        } else if (repeat == RepeatMode.ALL && playlistQueue && queue.isNotEmpty()) {
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
        // Pemutar berhenti karena gangguan (IDLE): tombol play memuat ulang dari posisi terakhir,
        // bukan diam saja.
        val item = current
        if (!exo.isPlaying && item != null && exo.playbackState == Player.STATE_IDLE && !loading) {
            error = null
            load(item, exo.currentPosition.coerceAtLeast(0))
            return
        }
        if (exo.isPlaying) {
            exo.pause()
        } else {
            // Live tanpa pemutaran ulang: lanjut dari bagian terkini (bukan layar hitam menunggu).
            if (isLiveStream && (exo.duration < 120_000 || !exo.isCurrentMediaItemSeekable)) exo.seekToDefaultPosition()
            exo.play()
        }
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
        // Pencarian yang masih berjalan untuk lagu LAIN tidak boleh menahan lagu ini.
        if (!force && lyricsFor == item.id) return
        lyricsJob?.cancel()
        lyricsLoading = false
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
        // Lirik yang tersimpan bersama download (bisa tampil tanpa internet).
        if (!force) {
            com.lumitube.app.data.lyrics.LyricsStore.load(item.id)?.let { stored ->
                chosenSource = null
                lyricsList = listOf(stored)
                lyricsIndex = 0
                return
            }
        }
        chosenSource = null
        lyricsLoading = true
        lyricsList = emptyList()
        lyricsIndex = 0
        val dur = (exo.duration.takeIf { it > 0 } ?: 0L) / 1000
        val gen = ++lyricsGen
        lyricsJob = scope.launch {
            try {
                val q = songQuery(item, dur.toInt())
                // Hasil ditampilkan bertahap: begitu ada lirik sinkron, langsung muncul.
                // Batas waktu: penyedia yang macet tidak boleh membuat "Mencari lirik…" berputar terus.
                var partialSeen: List<Lyrics> = emptyList()
                val list = kotlinx.coroutines.withTimeoutOrNull(30_000) {
                    LyricsRepo.fetchAll(q) { partial ->
                        partialSeen = partial
                        scope.launch { if (gen == lyricsGen && current?.id == item.id) applyLyrics(partial) }
                    }
                } ?: partialSeen
                if (gen == lyricsGen && current?.id == item.id && list.isNotEmpty()) applyLyrics(list)
                // Lagu yang sudah di-download: simpan lirik terbaik untuk diputar offline.
                val best = list.firstOrNull { it.lines.isNotEmpty() }
                // Simpan juga untuk lagu yang belum di-download: kalau nanti di-download/diputar offline, liriknya sudah ada.
                if (best != null) {
                    kotlinx.coroutines.withContext(Dispatchers.IO) { com.lumitube.app.data.lyrics.LyricsStore.save(item.id, best) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                if (gen == lyricsGen) {
                    lyricsLoading = false
                    // Tidak ketemu (mis. internet putus saat mencari): boleh dicoba lagi sekali
                    // saat lagu ini siap diputar lagi / internet kembali.
                    if (lyricsList.isEmpty() && (lyricsTries[item.id] ?: 0) < 2) {
                        lyricsTries[item.id] = (lyricsTries[item.id] ?: 0) + 1
                        if (lyricsFor == item.id) lyricsFor = null
                    }
                }
            }
        }
    }

    /** Judul asli per video (dari respons pemutar, tanpa terjemahan otomatis). */
    val originalTitles = androidx.compose.runtime.mutableStateMapOf<String, String>()

    private var historyFor: String? = null
    private var historyWarned = false
    private var lastStream: Pair<String, com.lumitube.app.data.Stream>? = null

    /** Masukkan video/lagu yang diputar ke Histori akun YouTube (sekali per pemutaran). */
    private fun registerHistory() {
        val item = current ?: return
        if (item.localUri != null || historyFor == item.id || !com.lumitube.app.data.Session.loggedIn) return
        historyFor = item.id
        scope.launch {
            try {
                val st = lastStream?.takeIf { it.first == item.id }?.second
                val tracking = st?.trackPlayback?.let {
                    com.lumitube.app.data.InnerTube.Tracking(it, st.trackWatchtime, st.trackClient, st.trackVersion, st.lengthSeconds)
                }
                val ok = com.lumitube.app.data.InnerTube.registerWatch(item.id, item.playlistId, music = !isVideo, stream = tracking, short = item.type == ItemType.SHORT)
                if (!ok && !historyWarned) {
                    historyWarned = true
                    com.lumitube.app.ui.common.LumiToast.makeText(
                        appContext,
                        "Video belum tercatat di Histori (${com.lumitube.app.data.InnerTube.lastHistoryResult.take(120)}). Cek apakah Histori tontonan dijeda di akun YouTube.",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
                // Histori yang tersimpan sementara di memori dibuang supaya video ini langsung muncul.
                listOf("hist" + true, "FEhistory", "FEmusic_history").forEach { com.lumitube.app.ui.common.FeedLoader.invalidate(it) }
            } catch (_: Exception) {
            }
        }
    }

    private var lyricsJob: kotlinx.coroutines.Job? = null
    private val lyricsTries = HashMap<String, Int>()
    private var lyricsGen = 0

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
        applyQualityCap()
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
            subtitleCues = emptyList()
            return
        }
        val cap = captions.getOrNull(i) ?: return
        captionLoading = true
        captionError = null
        scope.launch {
            try {
                val cues = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    com.lumitube.app.data.Subtitles.cues(com.lumitube.app.data.Subtitles.load(item.id, cap, translate))
                }
                if (current?.id != item.id) return@launch
                if (cues.isEmpty()) throw java.io.IOException("Subtitle kosong")
                captionIndex = i
                captionTranslate = translate
                subtitleCues = cues
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
        if (sleepEndOfItem) {
            sleepNow()
            return
        }
        val loop = repeat == RepeatMode.ONE ||
            (isVideo && (Session.loopVideo || current?.type == ItemType.SHORT))
        if (loop) {
            exo.seekTo(0)
            exo.play()
        } else if (repeat == RepeatMode.ALL && playlistQueue && index + 1 >= queue.size && queue.isNotEmpty()) {
            // Ulangi playlist: kembali ke item pertama.
            index = 0
            load(queue[0])
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
            subtitleCues = emptyList()
            retries = 0
            netRetries = 0
            storyboard = null
            chapters = emptyList()
        }
        loadJob?.cancel()
        if (current?.id != item.id) historyFor = null
        current = item
        error = null
        if (lyricsFor != item.id && lyricsSong != songKey(item.title)) {
            lyricsList = emptyList()
            lyricsIndex = 0
        }
        if (lyricsFor != item.id) {
            // Hentikan pencarian lirik lagu sebelumnya.
            lyricsJob?.cancel()
            lyricsGen++
            lyricsLoading = false
        }
        loading = true
        if (firstFrameFor == item.id && startPosition == 0L) firstFrameFor = null
        exo.stop()
        val audioOnly = !isVideo
        loadJob = scope.launch {
            try {
                val local = item.localUri
                if (local != null) {
                    isLiveStream = false
                    qualities = emptyList()
                    val meta = MediaMetadata.Builder().setTitle(item.title).setArtist(item.channelName ?: "")
                        .apply { item.thumbnail?.let { setArtworkUri(Uri.parse(it)) } }.build()
                    val mi = MediaItem.Builder().setMediaId(item.id).setUri(Uri.parse(local)).setMediaMetadata(meta).build()
                    exo.setMediaSource(
                        ProgressiveMediaSource.Factory(DefaultDataSource.Factory(appContext)).createMediaSource(mi),
                        startPosition
                    )
                } else {
                    val stream = StreamResolver.resolve(item.id, audioOnly, short = item.type == ItemType.SHORT)
                    lastStream = item.id to stream
                    // Judul asli dari pemutar (bukan judul terjemahan otomatis di daftar YouTube).
                    stream.title?.takeIf { it.isNotBlank() }?.let { originalTitles[item.id] = it }
                    stream.title?.takeIf { it.isNotBlank() && it != item.title }?.let { original ->
                        if (current?.id == item.id) {
                            current = current!!.copy(title = original)
                            if (index in queue.indices && queue[index].id == item.id) queue[index] = queue[index].copy(title = original)
                        }
                    }
                    currentClient = stream.client
                    currentNote = stream.note
                    if (!stream.isLive) {
                        com.lumitube.app.data.Storyboard.parse(stream.storyboardSpec, stream.lengthSeconds * 1000)?.let { storyboard = it }
                    }
                    // Dibuka dari link: judul/artis belum diketahui -> isi dari data stream.
                    if (item.title.isBlank() && current?.id == item.id) {
                        val upd = item.copy(
                            title = stream.title ?: "",
                            channelName = item.channelName ?: stream.author?.removeSuffix(" - Topic"),
                            thumbnail = item.thumbnail ?: "https://i.ytimg.com/vi/${item.id}/hqdefault.jpg",
                        )
                        current = upd
                        if (index in queue.indices && queue[index].id == item.id) queue[index] = upd
                    }
                    qualities = stream.qualities
                    captions = stream.captions
                    translations = stream.translations
                    queue.getOrNull(index + 1)?.let { if (it.localUri == null) prefetchMedia(it.id, video = isVideo) }
                    isLiveStream = stream.isLive
                    if (stream.isLive) {
                        // Live: mulai dari bagian terkini, bukan dari awal siaran.
                        exo.setMediaSource(buildSource(item, stream, audioOnly), true)
                    } else {
                        exo.setMediaSource(buildSource(item, stream, audioOnly), startPosition)
                    }
                }
                exo.prepare()
                exo.play()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "Gagal memutar"
                if (isNetworkError(e) && netRetries < 4) {
                    // Internet sempat putus/DNS gagal ("hostname"): coba lagi otomatis sebentar lagi.
                    netRetries++
                    val delayMs = 1500L * netRetries
                    scope.launch {
                        kotlinx.coroutines.delay(delayMs)
                        if (current?.id == item.id) load(item, startPosition)
                    }
                    return@launch
                }
                error = if (isNetworkError(e)) {
                    "Koneksi internet bermasalah. Periksa internet lalu coba lagi."
                } else if (msg.contains("bot", ignoreCase = true)) {
                    // Identitas pengunjung kemungkinan ditandai: pakai yang baru saat mencoba lagi.
                    StreamResolver.resetIdentity()
                    if (Session.loggedIn) "YouTube sedang membatasi pemutaran. Coba lagi sebentar lagi."
                    else "YouTube meminta konfirmasi bukan bot. Login di tab Anda supaya video bisa diputar."
                } else {
                    // Sertakan alasan tiap cara putar (ringkas) supaya mudah dilaporkan.
                    val detail = StreamResolver.lastFailure
                    if (detail.isNotBlank()) com.lumitube.app.tr(msg) + "\n" + detail.take(700) else msg
                }
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

    /**
     * Cache media di disk. Awal video Shorts berikutnya diunduh lebih dulu ke sini,
     * jadi saat digeser videonya langsung jalan tanpa loading (seperti YouTube asli).
     */
    private val mediaCache by lazy {
        androidx.media3.datasource.cache.SimpleCache(
            java.io.File(appContext.cacheDir, "media"),
            androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(300L * 1024 * 1024),
            androidx.media3.database.StandaloneDatabaseProvider(appContext),
        )
    }

    /** URL googlevideo berubah tiap kali diambil; kunci cache = video + format + ukuran. */
    private val cacheKeys = androidx.media3.datasource.cache.CacheKeyFactory { spec ->
        val u = spec.uri
        val id = try { u.getQueryParameter("id") } catch (_: Exception) { null }
        val itag = try { u.getQueryParameter("itag") } catch (_: Exception) { null }
        val clen = try { u.getQueryParameter("clen") } catch (_: Exception) { null }
        if (id != null && itag != null) "$id-$itag-$clen" else spec.key ?: u.toString()
    }

    private fun plainFactory(s: Stream): androidx.media3.datasource.DataSource.Factory {
        val base = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(mediaHttp)
            .setUserAgent(s.userAgent.ifEmpty { null })
            .setDefaultRequestProperties(s.headers)
        if (s.nMap.isEmpty()) return base
        // Live web: ubah parameter "n" di setiap URL playlist & potongan HLS.
        return androidx.media3.datasource.ResolvingDataSource.Factory(base) { spec ->
            val u = spec.uri.toString()
            val r = StreamResolver.rewriteN(u, s.nMap)
            if (r == u) spec else spec.withUri(android.net.Uri.parse(r))
        }
    }

    /**
     * [write] = true hanya untuk prefetch. Pemutar utama cukup MEMBACA bagian awal yang sudah
     * di-cache lalu lanjut langsung dari internet (tanpa menulis ke disk & tanpa kunci cache),
     * jadi tidak menambah beban saat memutar.
     */
    private fun cachedFactory(s: Stream, write: Boolean = false): androidx.media3.datasource.DataSource.Factory =
        androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(mediaCache)
            .setCacheKeyFactory(cacheKeys)
            .setUpstreamDataSourceFactory(ChunkedDataSource.Factory(plainFactory(s), s.chunk))
            .apply { if (!write) setCacheWriteDataSinkFactory(null) }
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    private val warmed = java.util.Collections.synchronizedSet(HashSet<String>())
    /** Prefetch dijalankan satu per satu supaya tidak berebut internet dengan video yang sedang diputar. */
    private val prefetchDispatcher = Dispatchers.IO.limitedParallelism(1)

    /**
     * Siapkan video berikutnya (Shorts / antrean): ambil URL stream lalu unduh bagian awal
     * video & audio ke cache. [video] = false untuk lagu (audio saja).
     */
    fun prefetchMedia(videoId: String, video: Boolean = true, short: Boolean = false) {
        if (!warmed.add(videoId + video)) return
        scope.launch(prefetchDispatcher) {
            try {
                // Tunggu video yang sedang diputar punya cadangan buffer dulu.
                var waited = 0
                while (waited < 15_000) {
                    val ready = kotlinx.coroutines.withContext(Dispatchers.Main) {
                        exo.playbackState == Player.STATE_READY &&
                            exo.bufferedPosition - exo.currentPosition > 4_000
                    }
                    if (ready) break
                    kotlinx.coroutines.delay(500)
                    waited += 500
                }
                val s = StreamResolver.resolve(videoId, audioOnly = !video, short = short)
                if (s.isLive) return@launch
                val factory = cachedFactory(s, write = true)
                for (url in listOfNotNull(if (video) s.videoUrl else null, s.audioUrl)) {
                    val spec = androidx.media3.datasource.DataSpec.Builder()
                        .setUri(url).setPosition(0).setLength(if (video) 450_000L else 200_000L).build()
                    androidx.media3.datasource.cache.CacheWriter(
                        factory.createDataSource() as androidx.media3.datasource.cache.CacheDataSource,
                        spec, null, null,
                    ).cache()
                }
            } catch (_: Exception) {
                warmed.remove(videoId + video)
            }
        }
    }

    private fun buildSource(item: Item, s: Stream, audioOnly: Boolean): MediaSource {
        // OkHttp: koneksi dipakai ulang antar potongan (tanpa handshake TLS berulang), mendukung HTTP/2.
        val plainHttp = plainFactory(s)
        // Progressive (video/audio terpisah) diunduh per potongan agar tidak macet, lewat cache disk.
        val http = cachedFactory(s)
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
            s.isLive && s.dashUrl != null ->
                androidx.media3.exoplayer.dash.DashMediaSource.Factory(plainHttp).createMediaSource(mediaItem(s.dashUrl))
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
