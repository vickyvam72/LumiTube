package com.lumitube.app.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/** Kualitas siaran live yang bisa direkam (dari playlist HLS utama). */
data class LiveVariant(val height: Int, val bandwidth: Long, val url: String) {
    val label: String get() = if (height > 0) "${height}p" else "${bandwidth / 1000} kbps"
}

/**
 * Rekam siaran live yang sedang berjalan (HLS, potongan MPEG-TS):
 * - dari awal live (selama YouTube masih menyimpan bagian awalnya), atau
 * - mulai dari detik ini.
 * Rekaman berlanjut sampai live selesai atau pengguna menekan "Berhenti & simpan",
 * lalu digabung menjadi MP4 dan disimpan ke folder Download/LumiTube.
 */
object LiveDownloader {
    private const val ACTION_STOP = "com.lumitube.app.STOP_LIVE"
    private const val CHANNEL = "downloads"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.lumitube.app.CrashLog.handler)
    private lateinit var ctx: Context

    /** Rekaman yang sedang berjalan: key → permintaan berhenti. */
    private val stopRequested = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private var nextId = 2700

    fun init(context: Context) {
        ctx = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.getStringExtra("key")?.let { stop(it) }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, IntentFilter(ACTION_STOP), Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                ctx.registerReceiver(receiver, IntentFilter(ACTION_STOP))
            }
        } catch (_: Exception) {
        }
    }

    fun isRecording(key: String) = jobs.containsKey(key)

    /** Hentikan rekaman lalu simpan bagian yang sudah terekam. */
    fun stop(key: String) {
        stopRequested[key] = true
    }

    private fun get(url: String, ua: String): String {
        val req = Request.Builder().url(url).header("User-Agent", ua).build()
        return InnerTube.http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string() ?: throw IOException("Kosong")
        }
    }

    /** Pilihan kualitas dari playlist HLS utama siaran live. */
    suspend fun variants(videoId: String): List<LiveVariant> = withContext(Dispatchers.IO) {
        val s = StreamResolver.resolve(videoId, audioOnly = false)
        val hls = s.hlsUrl ?: throw IOException("Live ini tidak bisa direkam")
        val master = get(hls, s.userAgent.ifEmpty { InnerTube.DESKTOP_UA })
        val lines = master.lines()
        val out = mutableListOf<LiveVariant>()
        for (i in lines.indices) {
            val l = lines[i]
            if (!l.startsWith("#EXT-X-STREAM-INF")) continue
            val url = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: continue
            val bw = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0
            val h = Regex("RESOLUTION=\\d+x(\\d+)").find(l)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            out.add(LiveVariant(h, bw, resolve(hls, url.trim())))
        }
        if (out.isEmpty() && master.contains("#EXTINF")) out.add(LiveVariant(0, 0, hls))
        out.groupBy { it.height }.map { (_, l) -> l.maxByOrNull { it.bandwidth }!! }
            .filter { it.height <= 1080 }
            .sortedByDescending { it.height }
    }

    private fun resolve(base: String, url: String): String =
        if (url.startsWith("http")) url else java.net.URI(base).resolve(url).toString()

    private data class Media(val seq: Long, val segments: List<String>, val ended: Boolean, val target: Int, val avg: Double)

    private fun media(url: String, ua: String): Media {
        val text = get(url, ua)
        val lines = text.lines()
        val seq = lines.firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }?.substringAfter(':')?.trim()?.toLongOrNull() ?: 0
        val target = lines.firstOrNull { it.startsWith("#EXT-X-TARGETDURATION:") }?.substringAfter(':')?.trim()?.toIntOrNull() ?: 5
        val segs = lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { resolve(url, it.trim()) }
        val durs = lines.filter { it.startsWith("#EXTINF:") }.mapNotNull { it.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() }
        return Media(seq, segs, text.contains("#EXT-X-ENDLIST"), target, durs.average().takeIf { !it.isNaN() } ?: target.toDouble())
    }

    private val sqRegex = Regex("/sq/(\\d+)/")

    /** URL potongan ke-[sq] (YouTube menaruh nomor potongan di path: /sq/123/). */
    private fun segmentUrl(template: String, sq: Long): String? =
        if (sqRegex.containsMatchIn(template)) template.replace(sqRegex, "/sq/$sq/") else null

    private fun download(url: String, ua: String, out: java.io.OutputStream): Long {
        val req = Request.Builder().url(url).header("User-Agent", ua).build()
        InnerTube.http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val input = r.body?.byteStream() ?: throw IOException("Kosong")
            return input.copyTo(out, 64 * 1024)
        }
    }

    private fun exists(url: String, ua: String): Boolean = try {
        val req = Request.Builder().url(url).header("User-Agent", ua).header("Range", "bytes=0-187").build()
        InnerTube.http.newCall(req).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }

    fun start(item: Item, variant: LiveVariant, fromStart: Boolean) {
        val key = "${item.id}:live:${variant.label}"
        if (jobs.containsKey(key)) return
        stopRequested.remove(key)
        val id = nextId++
        jobs[key] = scope.launch {
            try {
                record(item, variant, fromStart, key, id)
            } finally {
                jobs.remove(key)
                stopRequested.remove(key)
                Downloader.active.remove(key)
            }
        }
    }

    private suspend fun record(item: Item, variant: LiveVariant, fromStart: Boolean, key: String, id: Int) {
        val ua = InnerTube.DESKTOP_UA
        val dir = File(ctx.cacheDir, "dl").apply { mkdirs() }
        val ts = File(dir, "live_$id.ts")
        val mp4 = File(dir, "live_$id.mp4")
        var bytes = 0L
        var seconds = 0.0
        val startedAt = System.currentTimeMillis()
        var lastNotify = 0L
        val modeLabel = if (fromStart) "Dari awal live" else "Mulai sekarang"
        fun report(status: String) {
            Downloader.active[key] = ActiveDownload(key, item.title, "Live ${variant.label}", 0f, status, live = true)
            val now = System.currentTimeMillis()
            if (now - lastNotify > 1500) {
                lastNotify = now
                notifyRecording(id, key, item.title, status)
            }
        }
        report(com.lumitube.app.tr("Menyiapkan rekaman…"))
        try {
            var playlist = media(variant.url, ua)
            val template = playlist.segments.firstOrNull() ?: throw IOException("Live belum punya potongan")
            // Nomor potongan berikutnya yang akan diunduh.
            var next = playlist.seq
            if (fromStart && segmentUrl(template, 0) != null) {
                // Cari potongan paling awal yang masih disimpan YouTube (biasanya sampai beberapa jam ke belakang).
                next = if (exists(segmentUrl(template, 0)!!, ua)) 0 else {
                    var lo = 0L
                    var hi = playlist.seq
                    while (lo < hi) {
                        val mid = (lo + hi) / 2
                        if (exists(segmentUrl(template, mid)!!, ua)) hi = mid else lo = mid + 1
                    }
                    lo
                }
            }
            var idle = 0
            ts.outputStream().buffered(256 * 1024).use { out ->
                while (stopRequested[key] != true) {
                    val head = playlist.seq + playlist.segments.size // potongan pertama yang belum ada
                    var progressed = false
                    while (next < head && stopRequested[key] != true) {
                        val url = segmentUrl(template, next)
                            ?: playlist.segments.getOrNull((next - playlist.seq).toInt())
                        if (url == null) { next = playlist.seq; continue }
                        try {
                            bytes += download(url, ua, out)
                            seconds += playlist.avg
                            progressed = true
                        } catch (e: IOException) {
                            // Potongan sudah hilang dari server: lewati.
                            if (e.message?.startsWith("HTTP 4") != true) throw e
                        }
                        next++
                        report(com.lumitube.app.tr("Merekam • %s • %s", com.lumitube.app.ui.common.formatTime((seconds * 1000).toLong()), formatBytes(bytes)))
                    }
                    if (playlist.ended) break
                    if (!progressed) idle++ else idle = 0
                    // Tidak ada potongan baru cukup lama: live kemungkinan sudah selesai.
                    if (idle > 24) break
                    delay((playlist.target * 1000L).coerceIn(1000, 6000))
                    playlist = try {
                        media(variant.url, ua)
                    } catch (e: IOException) {
                        // Playlist hilang (403/404) = siaran sudah berakhir.
                        if (e.message?.startsWith("HTTP 4") == true) break
                        delay(2000)
                        continue
                    }
                    // Tanpa nomor di URL: ikuti jendela playlist apa adanya.
                    if (segmentUrl(template, 0) == null && next < playlist.seq) next = playlist.seq
                }
            }
            if (bytes == 0L) throw IOException(com.lumitube.app.tr("Belum ada yang terekam"))
            report(com.lumitube.app.tr("Menyimpan…"))
            val name = safeName("${item.title} (Live ${variant.label}).mp4")
            val (file, mime, outName) = try {
                remux(ts, mp4)
                Triple(mp4, "video/mp4", name)
            } catch (_: Exception) {
                // Gagal digabung ke MP4: simpan apa adanya (.ts tetap bisa diputar).
                Triple(ts, "video/mp2t", name.removeSuffix(".mp4") + ".ts")
            }
            val uri = Downloader.saveFile(file, outName, mime)
            val rec = DownloadRecord(
                id = "$id-${System.currentTimeMillis()}",
                videoId = item.id,
                title = item.title,
                author = item.channelName ?: item.subtitle.substringBefore(" • "),
                thumbnail = item.thumbnail,
                uri = uri,
                label = "Live ${variant.label} • $modeLabel",
                audioOnly = false,
                size = file.length(),
                time = startedAt,
            )
            withContext(Dispatchers.Main) { Downloader.addRecord(rec) }
            finish(id, item.title, com.lumitube.app.tr("Rekaman live tersimpan • %s", formatBytes(file.length())))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            finish(id, item.title, com.lumitube.app.tr("Gagal merekam: %s", e.message ?: ""))
        } finally {
            ts.delete()
            mp4.delete()
        }
    }

    /** Gabungkan potongan MPEG-TS menjadi satu file MP4 (tanpa encode ulang). */
    private fun remux(input: File, out: File) {
        val ex = MediaExtractor()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            ex.setDataSource(input.path)
            val map = HashMap<Int, Int>()
            for (t in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(t)
                val mime = f.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    ex.selectTrack(t)
                    map[t] = muxer.addTrack(f)
                }
            }
            if (map.isEmpty()) throw IOException("Tidak ada trek")
            muxer.start()
            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var base = -1L
            val last = HashMap<Int, Long>()
            while (true) {
                buffer.clear()
                val size = ex.readSampleData(buffer, 0)
                if (size < 0) break
                val track = ex.sampleTrackIndex
                val dst = map[track]
                if (dst != null) {
                    if (base < 0) base = ex.sampleTime
                    var t = (ex.sampleTime - base).coerceAtLeast(0)
                    // Waktu harus terus naik per trek.
                    val prev = last[dst] ?: -1
                    if (t <= prev) t = prev + 1
                    last[dst] = t
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = t
                    info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    muxer.writeSampleData(dst, buffer, info)
                }
                ex.advance()
            }
            muxer.stop()
        } finally {
            ex.release()
            try {
                muxer.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun safeName(n: String) = n.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(150)

    private fun formatBytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(java.util.Locale.US, "%.2f GB", b / (1024.0 * 1024 * 1024))
        b >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", b / (1024.0 * 1024))
        else -> "${b / 1024} KB"
    }

    private fun notifyRecording(id: Int, key: String, title: String, text: String) {
        try {
            val stop = PendingIntent.getBroadcast(
                ctx, id, Intent(ACTION_STOP).setPackage(ctx.packageName).putExtra("key", key),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .addAction(0, com.lumitube.app.tr("Berhenti & simpan"), stop)
                .build()
            ctx.getSystemService(NotificationManager::class.java)?.notify(id, n)
        } catch (_: Exception) {
        }
    }

    private suspend fun finish(id: Int, title: String, text: String) {
        try {
            ctx.getSystemService(NotificationManager::class.java)?.cancel(id)
        } catch (_: Exception) {
        }
        delay(400)
        try {
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setTimeoutAfter(10_000)
                .build()
            ctx.getSystemService(NotificationManager::class.java)?.notify(id, n)
        } catch (_: Exception) {
        }
    }
}
