package com.lumitube.app.ui.common

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.player.PlaybackManager
import kotlinx.coroutines.delay
import java.util.Calendar

/** Teks status timer tidur, mis. "12:34 lagi" atau "Sampai video selesai". */
@Composable
fun sleepTimerLabel(): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(PlaybackManager.sleepAt) {
        while (PlaybackManager.sleepAt != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val at = PlaybackManager.sleepAt
    return when {
        PlaybackManager.sleepEndOfItem -> "Sampai selesai"
        at != null -> {
            val left = ((at - now) / 1000).coerceAtLeast(0)
            "%d:%02d lagi".format(left / 60, left % 60)
        }
        else -> "Nonaktif"
    }
}

/**
 * Timer tidur: aplikasi ditutup otomatis setelah waktu tertentu, pada jam tertentu,
 * atau setelah video/lagu yang sedang diputar selesai.
 */
@Composable
fun SleepTimerDialog(endLabel: String = "Sampai video selesai", onDismiss: () -> Unit) {
    val context = LocalContext.current
    val status = sleepTimerLabel()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timer tidur") },
        text = {
            Column {
                Text("Status: $status", fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                val active = PlaybackManager.sleepAt != null || PlaybackManager.sleepEndOfItem
                Option("Nonaktif", !active) { PlaybackManager.cancelSleep(); onDismiss() }
                for (m in listOf(3, 5, 10, 15, 30, 60)) {
                    Option("$m menit", false) { PlaybackManager.setSleepMinutes(m); onDismiss() }
                }
                Option(endLabel, PlaybackManager.sleepEndOfItem) { PlaybackManager.setSleepEndOfItem(); onDismiss() }
                Option("Pilih jam…", false) {
                    val c = Calendar.getInstance()
                    TimePickerDialog(context, { _, h, min ->
                        val t = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, h)
                            set(Calendar.MINUTE, min)
                            set(Calendar.SECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                        }
                        PlaybackManager.setSleepAtTime(t.timeInMillis)
                        android.widget.Toast.makeText(
                            context, "Aplikasi akan ditutup pukul %02d.%02d".format(h, min), android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                    onDismiss()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, null, modifier = Modifier.size(20.dp))
    }
}
