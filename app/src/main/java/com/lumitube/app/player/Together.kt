package com.lumitube.app.player

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumitube.app.CrashLog
import com.lumitube.app.CrossApp
import com.lumitube.app.data.InnerTube
import com.lumitube.app.data.Item
import com.lumitube.app.data.ItemType
import com.lumitube.app.data.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Watch Together (YouTube Lumi) / Listen Together (YT Music Lumi).
 *
 * Setiap HP memutar video/lagu langsung dari YouTube; yang dikirim antar-HP hanya perintah kecil
 * (video apa, posisi, sedang diputar/jeda). Perintah dikirim lewat layanan pub/sub publik ntfy.sh
 * tanpa akun, terenkripsi AES-GCM dengan kunci dari kode room, jadi isinya tidak bisa dibaca orang
 * lain. Pembuat room (host) yang memilih video/lagu; tamu mengikuti. Host bisa mengizinkan
 * anggota ikut memilih: pilihan tamu dikirim ke host, lalu host memutarnya untuk semua.
 */
object Together {
    private const val SERVER = "https://ntfy.sh"
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Kode room aktif (null = tidak di dalam room). */
    var roomCode by mutableStateOf<String?>(null)
        private set
    var isHost by mutableStateOf(false)
        private set
    var members by mutableIntStateOf(1)
        private set
    var connected by mutableStateOf(false)
        private set
    var showSheet by mutableStateOf(false)

    /** Anggota (tamu) juga boleh memilih video/lagu dan memutar/menjeda. Diatur oleh host. */
    var guestControl by mutableStateOf(false)
        private set

    /** Host: izinkan / larang anggota ikut memilih. */
    fun updateGuestControl(on: Boolean) {
        if (!isHost) return
        guestControl = on
        publishState(force = true)
    }

    /** Dipanggil saat tamu harus membuka video baru (YouTube Lumi membuka halaman tonton). */
    var onOpen: ((Item) -> Unit)? = null

    /** true saat Together sendiri yang mengubah pemutaran (mengikuti host), bukan pengguna. */
    @Volatile private var applying = false

    /**
     * Tamu di room yang tidak mengizinkan anggota memilih: tidak bisa memilih video/lagu,
     * menggeser durasi, atau memutar/menjeda. Mengembalikan true (dan memberi tahu) bila ditolak.
     */
    fun guestBlocked(): Boolean {
        if (roomCode == null || isHost || guestControl || applying) return false
        if (PlaybackManager.current?.type == ItemType.SHORT) return false
        val now = System.currentTimeMillis()
        if (now - lastWarnAt > 2500) {
            lastWarnAt = now
            appContext?.let {
                com.lumitube.app.ui.common.LumiToast.makeText(it, "Host tidak mengizinkan anggota memilih atau mengatur pemutaran", Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    /** Tamu di room (siapa pun pengaturannya): video/lagu berikutnya ditentukan host. */
    val isGuest: Boolean get() = roomCode != null && !isHost

    private inline fun asSync(block: () -> Unit) {
        applying = true
        try { block() } finally { applying = false }
    }

    /** Gambar cadangan bila host tidak mengirim poster. */
    private fun thumbOf(id: String, thumb: String?) = thumb?.takeIf { it.isNotEmpty() } ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CrashLog.handler)
    private var listenJob: Job? = null
    private var loopJob: Job? = null
    private var appContext: Context? = null
    private val myId = randomCode(10)
    private var topic = ""
    private var key: SecretKeySpec? = null
    private val guests = LinkedHashMap<String, String>()

    // Status terakhir yang dikirim host.
    private var sentId: String? = null
    private var sentPlaying = false
    private var sentPos = 0L
    private var sentAt = 0L

    // Status yang harus diterapkan tamu setelah video baru siap.
    private var pending: JSONObject? = null

    // Tamu: status terakhir dari host, dan kapan terakhir mengirim permintaan / memberi peringatan.
    private var hostState: JSONObject? = null
    private var lastApplied = 0L
    private var lastReqAt = 0L
    private var lastWarnAt = 0L

    private val stream by lazy {
        InnerTube.http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    }

    fun create(context: Context) {
        leave()
        start(context, randomCode(6), host = true)
    }

    fun join(context: Context, code: String): Boolean {
        val c = code.trim().uppercase().filter { it in ALPHABET }
        if (c.length != 6) return false
        leave()
        start(context, c, host = false)
        return true
    }

    fun leave(notify: Boolean = true) {
        val code = roomCode ?: return
        val wasHost = isHost
        val t = topic
        val k = key
        if (notify) scope.launch {
            try {
                publishTo(t, k, JSONObject().put("t", if (wasHost) "end" else "bye"))
            } catch (_: Exception) {
            }
        }
        listenJob?.cancel(); loopJob?.cancel()
        listenJob = null; loopJob = null
        roomCode = null; isHost = false; connected = false; members = 1
        guests.clear(); pending = null; sentId = null
        hostState = null; guestControl = false
        android.util.Log.d("Together", "left $code")
    }

    private fun start(context: Context, code: String, host: Boolean) {
        appContext = context.applicationContext
        roomCode = code
        isHost = host
        members = 1
        topic = "lumitube_" + sha256("topic:$code").toHex().take(24)
        key = SecretKeySpec(sha256("lumitube-together:$code"), "AES")
        listenJob = scope.launch { listenLoop(topic) }
        loopJob = scope.launch { if (host) hostLoop() else guestLoop() }
        if (!host) {
            scope.launch {
                delay(1500) // tunggu langganan tersambung dulu
                send(JSONObject().put("t", "hello").put("name", Session.account?.name ?: "Tamu"))
            }
        }
    }

    // ---------------------------------------------------------------- jaringan

    private suspend fun listenLoop(t: String) {
        var since: String? = null
        while (scope.isActive && topic == t && roomCode != null) {
            try {
                val url = "$SERVER/$t/json" + (since?.let { "?since=$it" } ?: "")
                val req = Request.Builder().url(url).build()
                withContext(Dispatchers.IO) {
                    stream.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
                        withContext(Dispatchers.Main) { connected = true }
                        val src = resp.body?.source() ?: return@use
                        while (!src.exhausted()) {
                            val line = src.readUtf8Line() ?: break
                            if (line.isBlank()) continue
                            val ev = try { JSONObject(line) } catch (_: Exception) { continue }
                            ev.optString("id").takeIf { it.isNotEmpty() }?.let { since = it }
                            if (ev.optString("event") != "message") continue
                            val msg = decrypt(ev.optString("message")) ?: continue
                            withContext(Dispatchers.Main) { handle(msg) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                connected = false
                delay(3000)
            }
        }
    }

    private fun send(msg: JSONObject) {
        val t = topic
        val k = key
        scope.launch {
            try {
                publishTo(t, k, msg)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun publishTo(t: String, k: SecretKeySpec?, msg: JSONObject) {
        if (t.isEmpty() || k == null) return
        msg.put("from", myId)
        val body = encrypt(k, msg.toString())
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url("$SERVER/$t")
                .header("Cache", "no")
                .header("Firebase", "no")
                .post(body.toRequestBody("text/plain".toMediaType()))
                .build()
            InnerTube.http.newCall(req).execute().close()
        }
    }

    // ---------------------------------------------------------------- pesan

    private fun handle(msg: JSONObject) {
        if (msg.optString("from") == myId) return
        val ctx = appContext
        when (msg.optString("t")) {
            "hello" -> if (isHost) {
                guests[msg.optString("from")] = msg.optString("name", "Tamu")
                members = 1 + guests.size
                ctx?.let { com.lumitube.app.ui.common.LumiToast.makeText(it, com.lumitube.app.tr("%s bergabung", msg.optString("name", com.lumitube.app.tr("Tamu"))), Toast.LENGTH_SHORT).show() }
                sentAt = 0L // kirim status sekarang juga
                publishState(force = true)
            }
            "bye" -> if (isHost) {
                guests.remove(msg.optString("from"))?.let { name ->
                    members = 1 + guests.size
                    ctx?.let { com.lumitube.app.ui.common.LumiToast.makeText(it, com.lumitube.app.tr("%s keluar", name), Toast.LENGTH_SHORT).show() }
                    publishState(force = true)
                }
            }
            "end" -> if (!isHost) {
                ctx?.let { com.lumitube.app.ui.common.LumiToast.makeText(it, "Room ditutup oleh host", Toast.LENGTH_SHORT).show() }
                leave(notify = false)
            }
            "state" -> if (!isHost) applyState(msg)
            "req" -> if (isHost && guestControl) applyRequest(msg)
        }
    }

    // ---------------------------------------------------------------- host

    private suspend fun hostLoop() {
        while (scope.isActive && roomCode != null && isHost) {
            publishState(force = false)
            delay(1000)
        }
    }

    private var sentBuf = false
    private var bufSince = 0L

    private fun publishState(force: Boolean) {
        val now = System.currentTimeMillis()
        val cur = PlaybackManager.current
        if (cur == null || cur.type == ItemType.SHORT) {
            // Host belum memutar apa pun: tetap kirim status kosong (izin anggota & jumlah anggota),
            // jadi anggota yang diizinkan bisa memilih lebih dulu.
            if (force || sentId != "" || now - sentAt > 15_000) {
                sentId = ""; sentPlaying = false; sentAt = now
                send(JSONObject().put("t", "state").put("id", "").put("play", false).put("at", now).put("n", members).put("gc", guestControl))
            }
            return
        }
        val exo = PlaybackManager.exo
        // Host sedang memuat / buffering / offline (lebih dari sebentar): anggota ikut berhenti di posisi yang sama.
        val rawBuf = PlaybackManager.loading || exo.playbackState == androidx.media3.common.Player.STATE_BUFFERING ||
            !com.lumitube.app.data.Net.online
        if (!rawBuf) bufSince = 0L else if (bufSince == 0L) bufSince = now
        val buf = rawBuf && now - bufSince > 1200
        val playing = exo.playWhenReady && !buf
        val pos = when {
            PlaybackManager.loading && cur.id != sentId -> 0L
            PlaybackManager.loading -> sentPos
            else -> exo.currentPosition.coerceAtLeast(0)
        }
        val expected = sentPos + if (sentPlaying) now - sentAt else 0L
        val changed = force || cur.id != sentId || playing != sentPlaying || buf != sentBuf ||
            kotlin.math.abs(pos - expected) > 3000 ||
            now - sentAt > 15_000
        if (!changed) return
        sentId = cur.id; sentPlaying = playing; sentPos = pos; sentAt = now; sentBuf = buf
        send(
            JSONObject()
                .put("t", "state")
                .put("id", cur.id)
                .put("kind", if (cur.type == ItemType.SONG) "song" else "video")
                .put("title", cur.title)
                .put("ch", cur.channelName ?: cur.subtitle)
                .put("thumb", thumbOf(cur.id, cur.thumbnail))
                .put("pos", pos)
                .put("play", playing)
                .put("buf", buf)
                .put("at", now)
                .put("n", members)
                .put("gc", guestControl)
        )
    }

    /** Host: anggota memilih video/lagu lain atau memutar/menjeda (bila diizinkan). */
    private fun applyRequest(st: JSONObject) {
        val id = st.optString("id").takeIf { it.isNotEmpty() } ?: return
        val cur = PlaybackManager.current
        if (cur?.id != id) {
            val item = Item(
                type = if (CrossApp.isMusic) ItemType.SONG else ItemType.VIDEO,
                id = id,
                title = st.optString("title"),
                subtitle = st.optString("ch"),
                thumbnail = thumbOf(id, st.optString("thumb")),
                channelName = st.optString("ch").takeIf { it.isNotEmpty() },
            )
            if (CrossApp.isMusic) PlaybackManager.playMusic(listOf(item), 0, radio = false)
            else PlaybackManager.playVideo(item)
            onOpen?.invoke(item)
        } else {
            val exo = PlaybackManager.exo
            if (st.optBoolean("play")) exo.play() else exo.pause()
        }
    }

    // ---------------------------------------------------------------- tamu

    private var lastStateAt = 0L
    private var waitWarned = false

    private suspend fun guestLoop() {
        while (scope.isActive && roomCode != null && !isHost) {
            checkGuestChange()
            pending?.let { st ->
                val cur = PlaybackManager.current
                val exo = PlaybackManager.exo
                if (cur?.id == st.optString("id") && exo.duration > 0 && !PlaybackManager.loading) {
                    pending = null
                    lastApplied = System.currentTimeMillis()
                    exo.seekTo(expectedPos(st))
                    if (st.optBoolean("play")) exo.play() else exo.pause()
                }
            }
            // Host tidak terdengar lagi (aplikasi tertutup / offline): berhenti, jangan jalan sendiri.
            val st = hostState
            if (st != null && st.optBoolean("play") && System.currentTimeMillis() - lastStateAt > 40_000) {
                if (PlaybackManager.exo.playWhenReady) PlaybackManager.exo.pause()
                if (!waitWarned) {
                    waitWarned = true
                    appContext?.let { com.lumitube.app.ui.common.LumiToast.makeText(it, "Menunggu host…", Toast.LENGTH_SHORT).show() }
                }
            }
            delay(400)
        }
    }

    private fun expectedPos(st: JSONObject): Long {
        val pos = st.optLong("pos")
        val drift = if (st.optBoolean("play")) (System.currentTimeMillis() - st.optLong("at")).coerceIn(0, 60_000) else 0L
        return (pos + drift).coerceAtLeast(0)
    }

    /**
     * Tamu mengganti video/lagu atau memutar/menjeda sendiri: kirim ke host bila diizinkan,
     * atau kembalikan ke pilihan host bila tidak.
     */
    private fun checkGuestChange() {
        val st = hostState ?: return
        if (pending != null) return
        val cur = PlaybackManager.current ?: return
        if (cur.type == ItemType.SHORT) return
        val now = System.currentTimeMillis()
        if (now - lastApplied < 2500) return
        val exo = PlaybackManager.exo
        // Sedang memuat / buffering: belum bisa dibandingkan (hindari putar-ulang yang berulang).
        if (PlaybackManager.loading || exo.playbackState == androidx.media3.common.Player.STATE_BUFFERING) return
        val hostId = st.optString("id")
        val idDiff = cur.id != hostId
        val playDiff = !idDiff && hostId.isNotEmpty() && exo.playWhenReady != st.optBoolean("play")
        if (!idDiff && !playDiff) return
        if (guestControl) {
            if (now - lastReqAt < 3000) return
            lastReqAt = now
            send(
                JSONObject()
                    .put("t", "req")
                    .put("id", cur.id)
                    .put("title", cur.title)
                    .put("ch", cur.channelName ?: cur.subtitle)
                    .put("thumb", cur.thumbnail ?: "")
                    .put("pos", exo.currentPosition.coerceAtLeast(0))
                    .put("play", exo.playWhenReady)
            )
        } else if (hostId.isNotEmpty()) {
            // Tidak diizinkan: kembali ke pilihan host (pemberitahuan sudah muncul saat menekan).
            applyState(st)
        }
    }

    private fun applyState(st: JSONObject) {
        hostState = st
        lastStateAt = System.currentTimeMillis()
        waitWarned = false
        lastApplied = System.currentTimeMillis()
        guestControl = st.optBoolean("gc", false)
        members = st.optInt("n", members)
        val id = st.optString("id").takeIf { it.isNotEmpty() } ?: return
        val cur = PlaybackManager.current
        if (cur?.id != id) {
            val item = Item(
                type = if (CrossApp.isMusic) ItemType.SONG else ItemType.VIDEO,
                id = id,
                title = st.optString("title"),
                subtitle = st.optString("ch"),
                thumbnail = thumbOf(id, st.optString("thumb")),
                channelName = st.optString("ch").takeIf { it.isNotEmpty() },
            )
            asSync {
                if (CrossApp.isMusic) PlaybackManager.playMusic(listOf(item), 0, radio = false)
                else PlaybackManager.playVideo(item)
            }
            onOpen?.invoke(item)
            pending = st
            return
        }
        val exo = PlaybackManager.exo
        // Lagu/video yang sama masih dimuat (mis. pindah Lagu ↔ Video): terapkan setelah siap.
        if (PlaybackManager.loading || exo.duration <= 0) {
            pending = st
            return
        }
        val target = expectedPos(st)
        val play = st.optBoolean("play")
        // Host berhenti/buffering: diam di posisi host. Host berjalan: koreksi hanya bila selisih jelas.
        if (kotlin.math.abs(exo.currentPosition - target) > (if (play) 2500 else 1500)) exo.seekTo(target)
        if (play) { if (!exo.playWhenReady) exo.play() } else if (exo.playWhenReady) exo.pause()
    }

    // ---------------------------------------------------------------- util

    private fun randomCode(n: Int): String {
        val r = SecureRandom()
        return (1..n).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }

    private fun sha256(s: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun encrypt(k: SecretKeySpec, plain: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, iv))
        val out = iv + c.doFinal(plain.toByteArray())
        return android.util.Base64.encodeToString(out, android.util.Base64.NO_WRAP)
    }

    private fun decrypt(text: String): JSONObject? = try {
        val k = key ?: throw IllegalStateException()
        val raw = android.util.Base64.decode(text, android.util.Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, raw, 0, 12))
        JSONObject(String(c.doFinal(raw, 12, raw.size - 12)))
    } catch (_: Exception) {
        null
    }
}
