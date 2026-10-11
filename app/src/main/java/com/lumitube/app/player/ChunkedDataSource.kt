package com.lumitube.app.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import kotlin.math.min

/**
 * Server video YouTube memperlambat (bahkan menghentikan) satu koneksi panjang yang
 * meminta seluruh file sekaligus — itulah penyebab video/musik "stuck".
 * DataSource ini memecah permintaan menjadi potongan Range kecil berurutan,
 * seperti yang dilakukan aplikasi YouTube asli, tetapi bagi ExoPlayer tetap terlihat
 * sebagai satu aliran data utuh.
 */
@OptIn(UnstableApi::class)
class ChunkedDataSource(
    private val upstream: DataSource,
    /** Potongan pertama kecil supaya cepat mulai/lanjut setelah seek, lalu membesar. */
    private val firstChunk: Long = 512L * 1024,
    private val maxChunk: Long = 8L * 1024 * 1024,
) : DataSource {
    private var nextChunk = firstChunk

    private var spec: DataSpec? = null
    private var position = 0L
    private var end = -1L // eksklusif, -1 bila tidak diketahui
    private var requested = 0L
    private var readInChunk = 0L
    private var opened = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        nextChunk = firstChunk
        position = dataSpec.position
        end = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.position + dataSpec.length
            else -> totalLength(dataSpec.uri)?.takeIf { it > dataSpec.position } ?: -1L
        }
        if (!openChunk()) return 0
        return if (end >= 0) end - dataSpec.position else C.LENGTH_UNSET.toLong()
    }

    /** URL googlevideo menyertakan ukuran file di parameter "clen". */
    private fun totalLength(uri: Uri): Long? = try {
        uri.getQueryParameter("clen")?.toLongOrNull()
    } catch (_: Exception) {
        null
    }

    private fun openChunk(): Boolean {
        val base = spec ?: return false
        var len = nextChunk
        nextChunk = minOf(nextChunk * 2, maxChunk)
        if (end >= 0) len = min(len, end - position)
        if (len <= 0) return false
        val chunk = base.buildUpon().setPosition(position).setLength(len).build()
        var attempt = 0
        while (true) {
            try {
                upstream.open(chunk)
                break
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                // 416 = sudah melewati akhir file
                if (e.responseCode == 416) return false
                // 403 = URL ditolak: biarkan pemutar mengambil URL baru dari klien lain.
                if (e.responseCode == 403 || e.responseCode == 404 || ++attempt > 2) throw e
                try { upstream.close() } catch (_: Exception) {}
                Thread.sleep(400L * attempt)
            } catch (e: HttpDataSource.HttpDataSourceException) {
                // Gangguan jaringan sesaat antar potongan: coba lagi sebentar.
                if (++attempt > 2) throw e
                try { upstream.close() } catch (_: Exception) {}
                Thread.sleep(400L * attempt)
            }
        }
        requested = len
        readInChunk = 0
        opened = true
        return true
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            if (!opened) return C.RESULT_END_OF_INPUT
            val n = upstream.read(buffer, offset, length)
            if (n == C.RESULT_END_OF_INPUT) {
                val full = readInChunk >= requested
                upstream.close()
                opened = false
                if (!full) return C.RESULT_END_OF_INPUT
                if (end >= 0 && position >= end) return C.RESULT_END_OF_INPUT
                if (!openChunk()) return C.RESULT_END_OF_INPUT
                continue
            }
            position += n
            readInChunk += n
            return n
        }
    }

    override fun getUri(): Uri? = upstream.uri ?: spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        if (opened) {
            opened = false
            upstream.close()
        }
    }

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource())
    }
}
