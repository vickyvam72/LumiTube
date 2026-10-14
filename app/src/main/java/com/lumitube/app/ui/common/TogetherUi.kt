package com.lumitube.app.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.CrossApp
import com.lumitube.app.player.Together

private val togetherName: String get() = if (CrossApp.isMusic) "Listen Together" else "Watch Together"

/** Tombol di bilah atas (sebelah tombol download). Merah saat sedang di dalam room. */
@Composable
fun TogetherButton(tint: Color) {
    IconButton(onClick = { Together.showSheet = true }) {
        Icon(
            Icons.Filled.Groups, togetherName,
            tint = if (Together.roomCode != null) YtRed else tint,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** Jendela Watch/Listen Together: buat room, gabung dengan kode, bagikan, keluar. */
@Composable
fun TogetherHost() {
    if (!Together.showSheet) return
    val ctx = LocalContext.current
    val code = Together.roomCode
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { Together.showSheet = false },
        icon = { Icon(Icons.Filled.Groups, null) },
        title = { Text(togetherName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (code == null) {
                    Text(
                        if (CrossApp.isMusic) "Dengarkan lagu yang sama bersama teman secara bersamaan. Buat room lalu bagikan kodenya, atau masukkan kode dari temanmu."
                        else "Tonton video yang sama bersama teman secara bersamaan. Buat room lalu bagikan kodenya, atau masukkan kode dari temanmu.",
                        fontSize = 14.sp,
                    )
                    Button(onClick = { Together.create(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Buat room") }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.uppercase().take(6) },
                        label = { Text("Kode room") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = {
                            if (!Together.join(ctx, input)) Toast.makeText(ctx, "Kode room harus 6 karakter", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Gabung") }
                } else {
                    Text("Kode room", fontSize = 13.sp)
                    Text(
                        code.chunked(3).joinToString(" "), fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, letterSpacing = 4.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Text(
                        if (Together.isHost) "Kamu host. Teman di room akan mengikuti video, posisi, putar, dan jeda dari HP-mu."
                        else "Kamu tamu. Pemutaran mengikuti host secara otomatis.",
                        fontSize = 14.sp,
                    )
                    Text(
                        "Peserta: ${Together.members} · " + if (Together.connected) "Tersambung" else "Menyambungkan…",
                        fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { shareRoom(ctx, code) }, modifier = Modifier.weight(1f)) { Text("Bagikan") }
                        OutlinedButton(onClick = { copyRoom(ctx, code) }, modifier = Modifier.weight(1f)) { Text("Salin kode") }
                    }
                    OutlinedButton(
                        onClick = { Together.leave(); Together.showSheet = false },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) { Text(if (Together.isHost) "Tutup room" else "Keluar room", color = YtRed) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { Together.showSheet = false }) { Text("Tutup") } },
    )
}

private fun shareRoom(ctx: Context, code: String) {
    val app = if (CrossApp.isMusic) "YT Music Lumi" else "YouTube Lumi"
    val verb = if (CrossApp.isMusic) "dengerin lagu" else "nonton"
    val text = "Ayo $verb bareng di $app! Buka $togetherName, lalu masukkan kode room: $code"
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(intent, "Bagikan kode room").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun copyRoom(ctx: Context, code: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Kode room", code))
    Toast.makeText(ctx, "Kode disalin", Toast.LENGTH_SHORT).show()
}
