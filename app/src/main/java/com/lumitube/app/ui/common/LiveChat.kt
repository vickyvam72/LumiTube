package com.lumitube.app.ui.common

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.data.ChatMessage
import com.lumitube.app.data.Repo
import com.lumitube.app.data.Session
import com.lumitube.app.ui.music.Avatar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Baris "Live chat" di bawah video (pengganti kartu komentar saat siaran langsung). */
@Composable
fun LiveChatCard(colors: PanelColors, onClick: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).background(colors.chip).clickable(onClick = onClick).padding(12.dp)
    ) {
        Text("Live chat", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(30)
            Box(
                Modifier.padding(start = 10.dp).weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(colors.bg.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(chatHint(), color = colors.sub, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

private fun chatHint(): String =
    if (Session.loggedIn) "Chat secara publik sebagai ${Session.account?.name ?: "kamu"}..." else "Login untuk ikut chat"

/**
 * Panel live chat seperti YouTube: pesan terbaru di bawah, otomatis bergulir,
 * diperbarui terus selama panel terbuka. Video tetap tampil di atasnya.
 */
@Composable
fun LiveChatPanel(token: String, videoId: String, colors: PanelColors, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember(token) { mutableStateListOf<ChatMessage>() }
    var sendParams by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val state = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }

    // Pesan baru yang belum ditampilkan; dialirkan satu per satu supaya terasa real-time
    // (YouTube mengirim pesan berkelompok tiap beberapa detik).
    val pending = remember(token) { ArrayDeque<ChatMessage>() }
    var pollMs by remember { mutableStateOf(1500L) }
    val seen = remember(token) { HashSet<String>() }

    LaunchedEffect(token) {
        var cont: String? = token
        var first = true
        var failures = 0
        while (true) {
            val c = cont
            if (c == null) {
                // Token habis: ambil token baru dari halaman video.
                cont = try { Repo.watch(videoId).liveChatToken } catch (_: Exception) { null }
                if (cont == null) { delay(3000) }
                continue
            }
            try {
                val page = Repo.liveChat(c)
                error = null
                failures = 0
                if (page.sendParams != null) sendParams = page.sendParams
                if (page.notice != null || first) notice = page.notice
                val fresh = page.messages.filter { seen.add(it.id) }
                if (first) {
                    messages.addAll(fresh.takeLast(80))
                    first = false
                } else {
                    synchronized(pending) { pending.addAll(fresh) }
                }
                cont = page.continuation ?: cont
                pollMs = page.timeoutMs.coerceIn(1000, 2500)
                delay(pollMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                if (failures >= 2) {
                    // Token kedaluwarsa/ditolak: minta token baru.
                    cont = try { Repo.watch(videoId).liveChatToken ?: c } catch (_: Exception) { c }
                    first = messages.isEmpty()
                }
                if (failures >= 4 && messages.isEmpty()) error = "Live chat gagal dimuat, mencoba lagi…"
                delay(1500)
            }
        }
    }
    // Alirkan pesan tertunda merata sepanjang jeda polling.
    LaunchedEffect(token) {
        while (true) {
            val n = synchronized(pending) { pending.size }
            if (n == 0) {
                delay(150)
                continue
            }
            val m = synchronized(pending) { pending.removeFirst() }
            messages.removeAll { it.id.startsWith("local") && it.text == m.text }
            messages.add(m)
            if (messages.size > 300) messages.removeRange(0, messages.size - 300)
            delay((pollMs / (n + 1)).coerceIn(40, 700))
        }
    }
    // Ikuti pesan terbaru selama pengguna tidak sedang menggulir ke atas.
    LaunchedEffect(messages.size) {
        if (follow && messages.isNotEmpty()) state.scrollToItem(messages.size - 1)
    }
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            follow = last >= messages.size - 2
        }
    }

    BackHandler { onClose() }
    val drag = rememberSheetDragState(onClose)
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.68f)
                .sheetContainer(drag)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(colors.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding().imePadding()
        ) {
            Box(Modifier.fillMaxWidth().sheetHandle(drag).padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(colors.sub.copy(alpha = 0.4f)))
            }
            Row(Modifier.fillMaxWidth().sheetHandle(drag).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Live chat", color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Pesan terpopuler", color = colors.sub, fontSize = 13.sp)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Tutup", tint = colors.text) }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.sub.copy(alpha = 0.25f)))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                    items(messages, key = { it.id }) { m -> ChatRow(m, colors) }
                }
                if (messages.isEmpty()) {
                    Text(error ?: "Memuat live chat…", color = colors.sub, modifier = Modifier.align(Alignment.Center))
                }
                if (!follow && messages.isNotEmpty()) {
                    Text(
                        "↓ Pesan terbaru", color = Color.White, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                            .clip(RoundedCornerShape(16.dp)).background(Color(0xCC3EA6FF))
                            .clickable { follow = true; scope.launch { state.scrollToItem(messages.size - 1) } }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            notice?.let {
                Text(
                    it, color = colors.sub, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp)).background(colors.chip).padding(12.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.sub.copy(alpha = 0.25f)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    enabled = Session.loggedIn && sendParams != null,
                    textStyle = androidx.compose.ui.text.TextStyle(color = colors.text, fontSize = 15.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    maxLines = 3,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(colors.chip)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                if (Session.loggedIn && sendParams == null) "Chat tidak tersedia" else "Chat...",
                                color = colors.sub, fontSize = 15.sp,
                            )
                        }
                        inner()
                    },
                )
                if (draft.isNotBlank()) {
                    IconButton(enabled = !sending, onClick = {
                        val params = sendParams ?: return@IconButton
                        sending = true
                        val text = draft.trim()
                        scope.launch {
                            try {
                                Repo.sendLiveChat(params, text)
                                draft = ""
                                follow = true
                                // Tampilkan langsung pesan sendiri (seperti YouTube).
                                val id = "local" + System.currentTimeMillis()
                                messages.add(ChatMessage(id, Session.account?.handle ?: Session.account?.name ?: "Kamu", Session.account?.photo, text))
                            } catch (_: Exception) {
                                Toast.makeText(context, "Pesan tidak terkirim", Toast.LENGTH_SHORT).show()
                            } finally {
                                sending = false
                            }
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = colors.accent) }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(m: ChatMessage, colors: PanelColors) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (m.paid != null) Modifier.background(Color(0x33FFC107)) else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Img(m.photo, Modifier.size(28.dp).clip(CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = if (m.member) Color(0xFF2BA640) else colors.sub, fontWeight = FontWeight.Medium)) {
                    append(m.author)
                }
                m.paid?.let { withStyle(SpanStyle(color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)) { append("  $it") } }
                append("  ")
                withStyle(SpanStyle(color = colors.text)) { append(m.text) }
            },
            fontSize = 14.sp, lineHeight = 19.sp,
        )
    }
}
