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

data class ActiveDownload(val key: String, val title: String, val label: String, val progress: Float, val status: String)

/**
 * Download video/audio dengan berbagai kualitas.
 * Video YouTube kualitas tinggi terpisah antara gambar & suara, jadi keduanya diunduh
 * lalu digabung (MediaMuxer) menjadi satu file MP4.
 */
object Downloader {

    private lateinit var ctx: Context
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val CHANNEL = "downloads"
    private var notifId = 2000

    val active = mutableStateMapOf<String, ActiveDownload>()
    val records = mutableStateListOf<DownloadRecord>()

    fun init(context: Context) {
        com.lumitube.app.data.lyrics.LyricsStore.dir = context.filesDir
        ctx = context.applicationContext
        prefs = ctx.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        records.clear()
        records.addAll(load())
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(NotificationChannel(CHANNEL, "Download", NotificationManager.IMPORTANCE_LOW))
        }
    }

    // ---------------- Pilihan kualitas ----------------

    suspend fun options(videoId: String): List<DownloadOption> {
        val s = StreamResolver.resolve(videoId, audioOnly = false)
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
    fun backfillLyrics() {
        val fetch = lyricsFetcher ?: return
        scope.launch {
            for (rec in records.toList()) {
                if (com.lumitube.app.data.lyrics.LyricsStore.has(rec.videoId)) continue
                try {
                    val item = Item(ItemType.SONG, rec.videoId, rec.title, rec.author, rec.thumbnail, channelName = rec.author)
                    fetch(item)?.let { com.lumitube.app.data.lyrics.LyricsStore.save(rec.videoId, it) }
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Sudah pernah di-download (video atau audio)? */
    fun isDownloaded(videoId: String, audioOnly: Boolean) = records.any { it.videoId == videoId && it.audioOnly == audioOnly }

    /**
     * Download semua isi playlist satu per satu. Video memakai kualitas setelan (MP4),
     * lagu memakai audio M4A terbaik (beserta liriknya). Yang sudah ada dilewati.
     */
    fun downloadAll(items: List<Item>, audioOnly: Boolean) {
        val todo = items.filter { !isDownloaded(it.id, audioOnly) }.distinctBy { it.id }
        if (todo.isEmpty()) return
        scope.launch {
            todo.forEachIndexed { n, item ->
                val waitKey = "batch:${item.id}"
                active[waitKey] = ActiveDownload(waitKey, item.title, "${n + 1}/${todo.size}", 0f, "Menyiapkan…")
                try {
                    val opts = options(item.id)
                    val opt = if (audioOnly) {
                        opts.firstOrNull { it.audioOnly && it.ext == "m4a" } ?: opts.firstOrNull { it.audioOnly }
                    } else {
                        val max = Session.maxQuality
                        opts.filter { it.video != null }.firstOrNull { (it.video?.quality ?: 0) <= max }
                            ?: opts.lastOrNull { it.video != null }
                    }
                    active.remove(waitKey)
                    if (opt != null) perform(item, opt)
                } catch (_: Exception) {
                    active.remove(waitKey)
                }
            }
        }
    }

    private suspend fun perform(item: Item, option: DownloadOption) {
        val key = "${item.id}:${option.label}:${option.detail}"
        if (active.containsKey(key)) return
        val id = notifId++
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
                    val p = if (total > 0) done.toFloat() / total else 0f
                    active[key] = ActiveDownload(key, item.title, option.label, p, "${(p * 100).toInt()}%")
                    notify(id, item.title, option.label, (p * 100).toInt(), false)
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
                    label = "${option.label} • ${option.detail}",
                    audioOnly = option.audioOnly,
                    size = finalFile.length(),
                    time = System.currentTimeMillis(),
                )
                // Lagu (YT Music Lumi): simpan juga liriknya supaya tampil saat offline.
                if (!com.lumitube.app.data.lyrics.LyricsStore.has(item.id)) {
                    try {
                        lyricsFetcher?.invoke(item)?.let { com.lumitube.app.data.lyrics.LyricsStore.save(item.id, it) }
                    } catch (_: Exception) {
                    }
                }
                withContext(Dispatchers.Main) {
                    records.add(0, rec)
                    save()
                }
                notify(id, item.title, "Selesai • ${option.label}", 100, true)
            } catch (e: Exception) {
                notify(id, item.title, "Gagal: ${e.message}", 0, true)
            } finally {
                vFile.delete()
                aFile.delete()
                outFile.delete()
                active.remove(key)
            }
        }
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
            while (pos < total) {
                val end = min(pos + chunk, total) - 1
                val req = Request.Builder().url(url).media()
                    .header("Range", "bytes=$pos-$end")
                    .build()
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

    private fun notify(id: Int, title: String, text: String, progress: Int, done: Boolean) {
        try {
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(if (done) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(!done)
                .apply { if (!done) setProgress(100, progress, progress == 0) }
                .build()
            ctx.getSystemService(NotificationManager::class.java)?.notify(id, n)
        } catch (_: Exception) {
        }
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
