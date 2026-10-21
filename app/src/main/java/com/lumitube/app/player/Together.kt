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

    private fun publishState(force: Boolean) {
        val cur = PlaybackManager.current ?: return
        if (cur.type == ItemType.SHORT) return
        val exo = PlaybackManager.exo
        val playing = exo.playWhenReady
        val pos = exo.currentPosition.coerceAtLeast(0)
        val now = System.currentTimeMillis()
        val expected = sentPos + if (sentPlaying) now - sentAt else 0L
        val changed = force || cur.id != sentId || playing != sentPlaying ||
            kotlin.math.abs(pos - expected) > 3000 ||
            (playing && now - sentAt > 90_000)
        if (!changed) return
        sentId = cur.id; sentPlaying = playing; sentPos = pos; sentAt = now
        send(
            JSONObject()
                .put("t", "state")
                .put("id", cur.id)
                .put("kind", if (cur.type == ItemType.SONG) "song" else "video")
                .put("title", cur.title)
                .put("ch", cur.channelName ?: cur.subtitle)
                .put("thumb", cur.thumbnail ?: "")
                .put("pos", pos)
                .put("play", playing)
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
                thumbnail = st.optString("thumb").takeIf { it.isNotEmpty() },
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

    private suspend fun guestLoop() {
        while (scope.isActive && roomCode != null && !isHost) {
            checkGuestChange()
            pending?.let { st ->
                val cur = PlaybackManager.current
                val exo = PlaybackManager.exo
                if (cur?.id == st.optString("id") && exo.duration > 0) {
                    pending = null
                    exo.seekTo(expectedPos(st))
                    if (st.optBoolean("play")) exo.play() else exo.pause()
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
        if (now - lastApplied < 2000) return
        val exo = PlaybackManager.exo
        val hostId = st.optString("id")
        val idDiff = cur.id != hostId
        val playDiff = !idDiff && exo.playWhenReady != st.optBoolean("play")
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
        } else {
            if (now - lastWarnAt > 5000) {
                lastWarnAt = now
                appContext?.let {
                    com.lumitube.app.ui.common.LumiToast.makeText(it, "Hanya pembuat room yang bisa memilih", Toast.LENGTH_SHORT).show()
                }
            }
            applyState(st)
        }
    }

    private fun applyState(st: JSONObject) {
        hostState = st
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
                thumbnail = st.optString("thumb").takeIf { it.isNotEmpty() },
                channelName = st.optString("ch").takeIf { it.isNotEmpty() },
            )
            if (CrossApp.isMusic) PlaybackManager.playMusic(listOf(item), 0, radio = false)
            else PlaybackManager.playVideo(item)
            onOpen?.invoke(item)
            pending = st
            return
        }
        val exo = PlaybackManager.exo
        val target = expectedPos(st)
        if (exo.duration > 0 && kotlin.math.abs(exo.currentPosition - target) > 1500) exo.seekTo(target)
        if (st.optBoolean("play")) { if (!exo.playWhenReady) exo.play() } else if (exo.playWhenReady) exo.pause()
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
