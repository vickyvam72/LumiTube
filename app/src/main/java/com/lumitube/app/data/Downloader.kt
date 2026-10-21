package com.lumitube.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.min

data class DownloadOption(
    val label: String,
    val detail: String,
    val video: Format?,
    val audio: Format,
    val ext: String,
    val mime: String,
    val size: Long,
    val userAgent: String = "",
    val headers: Map<String, String> = emptyMap(),
    /** Ukuran potongan maksimum (0 = 9 MB). */
    val chunk: Long = 0,
) {
    val audioOnly: Boolean get() = video == null
}

data class DownloadRecord(
    val id: String,
    val videoId: String,
    val title: String,
    val author: String,
    val thumbnail: String?,
    val uri: String,
    val label: String,
    val audioOnly: Boolean,
    val size: Long,
    val time: Long,
)

/** Playlist yang di-download lewat "Download semua" (isinya tetap disimpan per lagu/video). */
data class DownloadedPlaylist(
    val id: String,
    val title: String,
    val author: String,
    val videoIds: List<String>,
    val audioOnly: Boolean,
    val time: Long,
)

data class ActiveDownload(val key: String, val title: String, val label: String, val progress: Float, val status: String, val live: Boolean = false)

/**
 * Download video/audio dengan berbagai kualitas.
 * Video YouTube kualitas tinggi terpisah antara gambar & suara, jadi keduanya diunduh
 * lalu digabung (MediaMuxer) menjadi satu file MP4.
 */
object Downloader {

    private lateinit var ctx: Context
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.lumitube.app.CrashLog.handler)
    private const val CHANNEL = "downloads"
    private var notifId = 2000

    val active = mutableStateMapOf<String, ActiveDownload>()
    /** "Download semua" yang sedang berjalan: id → (judul playlist, langkah "7/18"). */
    val batches = mutableStateMapOf<Int, Pair<String, String>>()
    private val stopped = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    /** Playlist (id tanpa "VL") → id "Download semua" yang sedang berjalan. */
    val batchOf = mutableStateMapOf<String, Int>()
    private const val ACTION_STOP_BATCH = "com.lumitube.app.STOP_BATCH"

    /** Hentikan "Download semua": lagu/video yang sudah selesai tetap tersimpan. */
    fun stopBatch(id: Int) {
        stopped.add(id)
    }
    val records = mutableStateListOf<DownloadRecord>()
    val playlists = mutableStateListOf<DownloadedPlaylist>()

    fun init(context: Context) {
        com.lumitube.app.data.lyrics.LyricsStore.dir = context.filesDir
        ctx = context.applicationContext
        prefs = ctx.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        records.clear()
        records.addAll(load())
        playlists.clear()
        playlists.addAll(loadPlaylists())
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(NotificationChannel(CHANNEL, "Download", NotificationManager.IMPORTANCE_LOW))
        }
        // Begitu internet tersambung lagi, lengkapi lirik offline yang belum ada.
        try {
            val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
            cm?.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    scope.launch {
                        kotlinx.coroutines.delay(5_000)
                        backfillLyrics()
                    }
                }
            })
        } catch (_: Exception) {
        }
        LiveDownloader.init(ctx)
        try {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: android.content.Intent?) {
                    intent?.getIntExtra("batch", -1)?.takeIf { it >= 0 }?.let { stopBatch(it) }
                }
            }
            val filter = android.content.IntentFilter(ACTION_STOP_BATCH)
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else ctx.registerReceiver(receiver, filter)
        } catch (_: Exception) {
        }
        // Bersihkan notifikasi download yang menggantung dari sesi sebelumnya.
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            for (i in 2000 until 2800) nm?.cancel(i)
        } catch (_: Exception) {
        }
    }

    // ---------------- Pilihan kualitas ----------------

    suspend fun options(videoId: String): List<DownloadOption> {
        val s = StreamResolver.resolveForDownload(videoId)
        val audios = s.formats.filter { it.isAudio }
        val m4a = audios.filter { it.isMp4 }.maxByOrNull { it.bitrate }
        val opus = audios.filter { it.codec == "opus" }.maxByOrNull { it.bitrate }
        val out = mutableListOf<DownloadOption>()
        if (m4a != null) {
            // MP4 (H.264) bisa digabung dengan audio M4A di semua HP.
            s.formats.filter { !it.isAudio && it.isMp4 && it.codec == "avc1" && it.quality > 0 }
                .groupBy { it.quality }
                .map { (_, list) -> list.maxByOrNull { it.bitrate }!! }
                .sortedByDescending { it.quality }
                .forEach { v ->
                    out.add(
                        DownloadOption(
                            label = "${v.quality}p",
                            detail = "Video MP4",
                            video = v, audio = m4a, ext = "mp4", mime = "video/mp4",
                            size = v.contentLength + m4a.contentLength,
                        )
                    )
                }
            out.add(DownloadOption("Audio ${m4a.bitrate / 1000} kbps", "M4A (AAC)", null, m4a, "m4a", "audio/mp4", m4a.contentLength))
        }
        if (opus != null) {
            out.add(DownloadOption("Audio ${opus.bitrate / 1000} kbps", "Opus (WebM)", null, opus, "webm", "audio/webm", opus.contentLength))
        }
        audios.filter { it.isMp4 && it != m4a }.sortedByDescending { it.bitrate }.forEach { a ->
            out.add(DownloadOption("Audio ${a.bitrate / 1000} kbps", "M4A hemat", null, a, "m4a", "audio/mp4", a.contentLength))
        }
        return out.map { it.copy(userAgent = s.userAgent, headers = s.headers, chunk = s.chunk) }
    }

    // ---------------- Proses download ----------------

    /** Pengambil lirik terbaik untuk lagu (diisi PlaybackManager). Disimpan bersama download audio. */
    var lyricsFetcher: (suspend (Item) -> com.lumitube.app.data.lyrics.Lyrics?)? = null

    fun start(item: Item, option: DownloadOption) {
        scope.launch { perform(item, option) }
    }

    /**
     * Lengkapi lirik untuk download lama yang belum punya lirik tersimpan
     * (dijalankan diam-diam saat ada internet).
     */
    @Volatile private var backfilling = false

    fun backfillLyrics() {
        val fetch = lyricsFetcher ?: return
        if (backfilling) return
        backfilling = true
        scope.launch {
            try {
                // 3 lagu sekaligus supaya cepat lengkap sebelum HP offline lagi.
                val sem = kotlinx.coroutines.sync.Semaphore(3)
                kotlinx.coroutines.coroutineScope {
                    for (rec in records.toList().filter { it.audioOnly }.distinctBy { it.videoId }) {
                        if (com.lumitube.app.data.lyrics.LyricsStore.has(rec.videoId)) continue
                        launch {
                            sem.acquire()
                            try {
                                val item = Item(ItemType.SONG, rec.videoId, rec.title, rec.author, rec.thumbnail, channelName = rec.author)
                                kotlinx.coroutines.withTimeoutOrNull(45_000) { fetch(item) }
                                    ?.let { com.lumitube.app.data.lyrics.LyricsStore.save(rec.videoId, it) }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (_: Exception) {
                            } finally {
                                sem.release()
                            }
                        }
                    }
                }
            } finally {
                backfilling = false
            }
        }
    }

    /** Sudah pernah di-download (video atau audio)? */
    fun isDownloaded(videoId: String, audioOnly: Boolean) = records.any { it.videoId == videoId && it.audioOnly == audioOnly }

    /**
     * Download semua isi playlist satu per satu. Video memakai kualitas setelan (MP4),
     * lagu memakai audio M4A terbaik (beserta liriknya). Yang sudah ada dilewati.
     */
    fun downloadAll(items: List<Item>, audioOnly: Boolean, playlistId: String? = null, playlistTitle: String? = null, playlistAuthor: String? = null) {
        // Catat playlist-nya supaya tampil sebagai satu playlist di halaman Download.
        if (playlistId != null && items.isNotEmpty()) {
            val pl = DownloadedPlaylist(
                playlistId.removePrefix("VL"), playlistTitle ?: "Playlist", playlistAuthor ?: "",
                items.map { it.id }.distinct(), audioOnly, System.currentTimeMillis(),
            )
            playlists.removeAll { it.id == pl.id && it.audioOnly == audioOnly }
            playlists.add(0, pl)
            savePlaylists()
        }
        val todo = items.filter { !isDownloaded(it.id, audioOnly) }.distinctBy { it.id }
        if (todo.isEmpty()) return
        val listTitle = playlistTitle ?: "Playlist"
        scope.launch {
            val batchId = notifId++
            var ok = 0
            var wasStopped = false
            val plKey = playlistId?.removePrefix("VL")
            if (plKey != null) batchOf[plKey] = batchId
            for ((n, item) in todo.withIndex()) {
                if (batchId in stopped) { wasStopped = true; break }
                val waitKey = "batch:${item.id}"
                val step = "${n + 1}/${todo.size}"
                batches[batchId] = listTitle to step
                active[waitKey] = ActiveDownload(waitKey, item.title, step, 0f, "Menyiapkan…")
                notify(batchId, com.lumitube.app.tr("Download playlist %s", step), item.title, 0, false, stopBatch = batchId)
                try {
                    val opts = options(item.id)
                    // YT Music: audio M4A terbaik (lalu Opus), disimpan sebagai lagu.
                    // YouTube: MP4 maksimal 720p (atau kualitas setelan bila lebih rendah).
                    val opt = if (audioOnly) {
                        opts.firstOrNull { it.audioOnly && it.ext == "m4a" } ?: opts.firstOrNull { it.audioOnly }
                            ?: opts.filter { it.video != null }.minByOrNull { it.video?.quality ?: 9999 }
                    } else {
                        val max = minOf(Session.maxQuality, 720)
                        opts.filter { it.video != null }.firstOrNull { (it.video?.quality ?: 0) <= max }
                            ?: opts.lastOrNull { it.video != null }
                    }
                    active.remove(waitKey)
                    var done = opt != null && perform(item, opt, asSong = audioOnly, batch = batchId to step)
                    // Gagal sekali (mis. sinyal putus): coba sekali lagi dengan data stream baru.
                    if (!done && opt != null && batchId !in stopped) {
                        kotlinx.coroutines.delay(1500)
                        StreamResolver.invalidate(item.id)
                        val retry = options(item.id).firstOrNull { it.label == opt.label && it.detail == opt.detail } ?: opt
                        done = perform(item, retry, asSong = audioOnly, batch = batchId to step)
                    }
                    if (done) ok++
                } catch (_: Exception) {
                    active.remove(waitKey)
                }
            }
            if (batchId in stopped) wasStopped = true
            stopped.remove(batchId)
            batches.remove(batchId)
            if (plKey != null && batchOf[plKey] == batchId) batchOf.remove(plKey)
            // Coba lagi lirik yang tadi belum ketemu.
            backfillLyrics()
            val summary = if (audioOnly) com.lumitube.app.tr("%s dari %s lagu tersimpan", ok, todo.size) else com.lumitube.app.tr("%s dari %s video tersimpan", ok, todo.size)
            finish(batchId, com.lumitube.app.tr(if (wasStopped) "Download playlist dihentikan" else "Download playlist selesai"), summary)
        }
    }

    /**
     * @param asSong simpan sebagai lagu (tampil di "Lagu yang didownload") walau file-nya MP4.
     * @param batch notifikasi gabungan untuk "Download semua" (id, "3/19"); null = notifikasi sendiri.
     * @return true bila berhasil.
     */
    private suspend fun perform(item: Item, option: DownloadOption, asSong: Boolean = false, batch: Pair<Int, String>? = null): Boolean {
        val key = "${item.id}:${option.label}:${option.detail}"
        if (active.containsKey(key)) return false
        val id = batch?.first ?: notifId++
        val notifTitle = if (batch != null) com.lumitube.app.tr("Download playlist %s", batch.second) else item.title
        val notifText = if (batch != null) item.title else option.label
        var lastNotify = 0L
        var ok = false
        active[key] = ActiveDownload(key, item.title, option.label, 0f, "Menyiapkan…")
        run {
            val tmpDir = File(ctx.cacheDir, "dl").apply { mkdirs() }
            val vFile = File(tmpDir, "$id.v")
            val aFile = File(tmpDir, "$id.a")
            val outFile = File(tmpDir, "$id.${option.ext}")
            try {
                val total = (option.video?.contentLength ?: 0L) + option.audio.contentLength
                var doneVideo = 0L
                val report: (Long) -> Unit = { done ->
                    // "Hentikan" ditekan: batalkan lagu/video yang sedang diunduh.
                    if (batch != null && batch.first in stopped) throw IllegalStateException("Dihentikan")
                    val p = if (total > 0) done.toFloat() / total else 0f
                    active[key] = ActiveDownload(key, item.title, option.label, p, "${(p * 100).toInt()}%")
                    // Android membuang update notifikasi yang terlalu sering (termasuk update "Selesai"),
                    // sehingga notifikasi bisa menggantung. Batasi maksimal sekali per detik.
                    val now = System.currentTimeMillis()
                    if (now - lastNotify > 1000) {
                        lastNotify = now
                        notify(id, notifTitle, notifText, (p * 100).toInt().coerceAtLeast(1), false, stopBatch = batch?.first)
                    }
                }
                option.video?.let { v ->
                    fetch(v.url, option, v.contentLength, vFile) { report(it) }
                    doneVideo = v.contentLength
                }
                fetch(option.audio.url, option, option.audio.contentLength, aFile) { report(doneVideo + it) }
                val finalFile = if (option.video != null) {
                    active[key] = ActiveDownload(key, item.title, option.label, 1f, "Menggabungkan…")
                    mux(vFile, aFile, outFile)
                    outFile
                } else {
                    aFile
                }
                val name = safeName("${item.title} (${option.label}).${option.ext}")
                val uri = saveToDownloads(finalFile, name, option.mime, option.audioOnly)
                val rec = DownloadRecord(
                    id = "$id-${System.currentTimeMillis()}",
                    videoId = item.id,
                    title = item.title,
                    author = item.channelName ?: item.subtitle.substringBefore(" • "),
                    thumbnail = item.thumbnail,
                    uri = uri,
                    label = if (asSong && !option.audioOnly) "Lagu • MP4 ${option.label}" else "${option.label} • ${option.detail}",
                    audioOnly = option.audioOnly || asSong,
                    size = finalFile.length(),
                    time = System.currentTimeMillis(),
                )
                withContext(Dispatchers.Main) {
                    records.add(0, rec)
                    save()
                }
                if (batch == null) finish(id, item.title, com.lumitube.app.tr("Selesai") + " • ${option.label}")
                // Lagu: lirik untuk offline dicari terpisah (maks. 25 detik) supaya download
                // berikutnya tidak tertahan di 100% menunggu penyedia lirik yang lambat.
                if (!com.lumitube.app.data.lyrics.LyricsStore.has(item.id)) {
                    lyricsFetcher?.let { fetchLyrics ->
                        scope.launch {
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(45_000) { fetchLyrics(item) }
                                    ?.let { com.lumitube.app.data.lyrics.LyricsStore.save(item.id, it) }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
                ok = true
            } catch (e: Exception) {
                if (batch == null) finish(id, item.title, com.lumitube.app.tr("Gagal: %s", e.message))
            } finally {
                vFile.delete()
                aFile.delete()
                outFile.delete()
                active.remove(key)
            }
        }
        return ok
    }

    /** Notifikasi akhir: hapus yang berjalan dulu, lalu tampilkan hasil yang hilang sendiri. */
    private suspend fun finish(id: Int, title: String, text: String) {
        try {
            ctx.getSystemService(NotificationManager::class.java)?.cancel(id)
        } catch (_: Exception) {
        }
        kotlinx.coroutines.delay(400)
        notify(id, title, text, 100, true)
    }

    /** Unduh per potongan 9 MB (YouTube memperlambat unduhan sekaligus tanpa Range). */
    private fun fetch(url: String, opt: DownloadOption, total: Long, out: File, progress: (Long) -> Unit) {
        val chunk = if (opt.chunk > 0) opt.chunk else 9L * 1024 * 1024
        fun Request.Builder.media(): Request.Builder {
            if (opt.userAgent.isNotEmpty()) header("User-Agent", opt.userAgent)
            opt.headers.forEach { (k, v) -> header(k, v) }
            return this
        }
        out.outputStream().use { os ->
            var pos = 0L
            val buf = ByteArray(64 * 1024)
            if (total <= 0) {
                val req = Request.Builder().url(url).media().build()
                InnerTube.http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    val input = r.body?.byteStream() ?: throw IOException("Kosong")
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        pos += n
                        progress(pos)
                    }
                }
                return
            }
            var lastReport = 0L
            var failures = 0
            while (pos < total) {
                val end = min(pos + chunk, total) - 1
                val req = Request.Builder().url(url).media()
                    .header("Range", "bytes=$pos-$end")
                    .build()
                try {
                    InnerTube.http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                        val input = r.body?.byteStream() ?: throw IOException("Kosong")
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                            pos += n
                            if (pos - lastReport > 256 * 1024) {
                                lastReport = pos
                                progress(pos)
                            }
                        }
                    }
                    failures = 0
                } catch (e: IOException) {
                    // Sinyal putus sesaat: lanjutkan dari posisi terakhir (bukan gagal total).
                    if (e.message?.startsWith("HTTP 4") == true || ++failures > 5) throw e
                    Thread.sleep(1500L * failures)
                }
            }
            progress(pos)
        }
    }

    private fun mux(video: File, audio: File, out: File) {
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val extractors = listOf(video, audio).map { f -> MediaExtractor().apply { setDataSource(f.path) } }
        try {
            val tracks = extractors.map { ex ->
                ex.selectTrack(0)
                muxer.addTrack(ex.getTrackFormat(0))
            }
            muxer.start()
            val buffer = ByteBuffer.allocate(8 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            extractors.forEachIndexed { i, ex ->
                while (true) {
                    buffer.clear()
                    val size = ex.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = ex.sampleTime
                    info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    muxer.writeSampleData(tracks[i], buffer, info)
                    ex.advance()
                }
            }
            muxer.stop()
        } finally {
            extractors.forEach { it.release() }
            muxer.release()
        }
    }

    private fun safeName(n: String) = n.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(150)

    /** Simpan file hasil rekaman (mis. live) ke folder Download/LumiTube. */
    fun saveFile(file: File, name: String, mime: String): String = saveToDownloads(file, name, mime, audio = false)

    fun addRecord(rec: DownloadRecord) {
        records.add(0, rec)
        save()
    }

    /** Simpan ke folder Download/LumiTube. */
    private fun saveToDownloads(file: File, name: String, mime: String, audio: Boolean): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LumiTube")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Tidak bisa membuat file")
            resolver.openOutputStream(uri)?.use { os -> file.inputStream().use { it.copyTo(os) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        }
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), if (audio) "Music" else "Video")
        dir.mkdirs()
        val dest = File(dir, name)
        file.copyTo(dest, overwrite = true)
        return Uri.fromFile(dest).toString()
    }

    fun delete(rec: DownloadRecord) {
        try {
            val uri = Uri.parse(rec.uri)
            if (uri.scheme == "file") File(uri.path ?: "").delete() else ctx.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
        records.remove(rec)
        save()
    }

    private fun notify(id: Int, title: String, text: String, progress: Int, done: Boolean, stopBatch: Int? = null) {
        try {
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .apply {
                    if (stopBatch != null && !done) {
                        val pi = android.app.PendingIntent.getBroadcast(
                            ctx, stopBatch,
                            android.content.Intent(ACTION_STOP_BATCH).setPackage(ctx.packageName).putExtra("batch", stopBatch),
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                        )
                        addAction(0, com.lumitube.app.tr("Hentikan"), pi)
                    }
                }
                .setSmallIcon(if (done) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(!done)
                .setAutoCancel(done)
                .apply { if (!done) setProgress(100, progress, progress == 0) else setTimeoutAfter(8000) }
                .build()
            ctx.getSystemService(NotificationManager::class.java)?.notify(id, n)
        } catch (_: Exception) {
        }
    }

    /** Hapus playlist dari daftar Download. [withFiles] = hapus juga lagu/videonya. */
    fun deletePlaylist(pl: DownloadedPlaylist, withFiles: Boolean) {
        playlists.remove(pl)
        savePlaylists()
        if (withFiles) {
            // Lagu yang juga ada di playlist download lain tetap disimpan.
            val keep = playlists.filter { it.audioOnly == pl.audioOnly }.flatMap { it.videoIds }.toSet()
            records.filter { it.audioOnly == pl.audioOnly && it.videoId in pl.videoIds && it.videoId !in keep }.forEach { delete(it) }
        }
    }

    private fun loadPlaylists(): List<DownloadedPlaylist> = try {
        JSONArray(prefs.getString("playlists", "[]")).objects().map { o ->
            val ids = o.optJSONArray("ids")
            DownloadedPlaylist(
                o.optString("id"), o.optString("title"), o.optString("author"),
                (0 until (ids?.length() ?: 0)).map { ids!!.optString(it) },
                o.optBoolean("audioOnly"), o.optLong("time"),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun savePlaylists() {
        val arr = JSONArray()
        playlists.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title).put("author", it.author)
                    .put("ids", JSONArray(it.videoIds)).put("audioOnly", it.audioOnly).put("time", it.time)
            )
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    private fun load(): List<DownloadRecord> = try {
        JSONArray(prefs.getString("list", "[]")).objects().map {
            DownloadRecord(
                it.optString("id"), it.optString("videoId"), it.optString("title"), it.optString("author"),
                it.str("thumbnail"), it.optString("uri"), it.optString("label"), it.optBoolean("audioOnly"),
                it.optLong("size"), it.optLong("time"),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun save() {
        val arr = JSONArray()
        records.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("videoId", it.videoId).put("title", it.title)
                    .put("author", it.author).put("thumbnail", it.thumbnail).put("uri", it.uri)
                    .put("label", it.label).put("audioOnly", it.audioOnly).put("size", it.size).put("time", it.time)
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }
}
