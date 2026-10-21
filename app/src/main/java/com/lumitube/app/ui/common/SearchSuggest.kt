package com.lumitube.app.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.data.Repo
import com.lumitube.app.data.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Daftar di bawah kotak cari, seperti YouTube asli:
 * ikon jam = histori penelusuran, ikon kaca pembesar = saran,
 * panah ↖ di kanan = salin teks ke kotak cari tanpa langsung mencari.
 * Tekan lama baris histori untuk menghapusnya.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchSuggestions(
    text: String,
    textColor: Color,
    subColor: Color,
    onPick: (String) -> Unit,
    onFill: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val q = text.trim()
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(q) {
        if (q.isEmpty()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(120)
        suggestions = try {
            Repo.searchSuggestions(q)
        } catch (_: Exception) {
            emptyList()
        }
    }
    val history = Session.searchHistory
    val past = if (q.isEmpty()) history.take(25) else history.filter { it.contains(q, ignoreCase = true) }.take(4)
    val rows = past.map { it to true } + suggestions.filter { s -> past.none { it.equals(s, ignoreCase = true) } }.map { it to false }
    var confirm by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LazyColumn(modifier.fillMaxSize().imePadding()) {
        items(rows, key = { (if (it.second) "h:" else "s:") + it.first }) { (s, isHistory) ->
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = { onPick(s) },
                        onLongClick = if (isHistory) ({ confirm = s }) else null,
                    )
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isHistory) Icons.Filled.History else Icons.Filled.Search, null,
                    tint = textColor, modifier = Modifier.size(22.dp),
                )
                Text(
                    highlight(s, q),
                    color = textColor, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 18.dp, vertical = 14.dp),
                )
                IconButton(onClick = { onFill(s) }) {
                    Icon(Icons.Filled.NorthWest, "Isi kotak cari", tint = subColor, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    confirm?.let { s ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(s) },
            text = { Text("Hapus dari histori penelusuran?") },
            confirmButton = {
                TextButton(onClick = {
                    Session.removeSearch(s)
                    confirm = null
                    // Ikut dihapus dari histori penelusuran YouTube / YouTube Music asli.
                    scope.launch {
                        try { Repo.removeSearchFromAccount(s, com.lumitube.app.CrossApp.isMusic) } catch (_: Exception) { }
                    }
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } },
        )
    }
}

/** Bagian yang sudah diketik tampil biasa, sisanya tebal (seperti YouTube). */
private fun highlight(s: String, q: String) = buildAnnotatedString {
    if (q.isNotEmpty() && s.startsWith(q, ignoreCase = true)) {
        append(s.substring(0, q.length))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(s.substring(q.length)) }
    } else {
        append(s)
    }
}
