package com.lumitube.app.ui.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.data.Session
import com.lumitube.app.data.lyrics.LyricsRepo
import com.lumitube.app.player.PlaybackManager
import com.lumitube.app.ui.common.BottomPanel
import com.lumitube.app.ui.common.MusicPanelColors
import com.lumitube.app.ui.common.YtRed

private val providerInfo = mapOf(
    "BetterLyrics" to "Lirik Apple Music, sinkron per kata",
    "LyricsPlus" to "Sinkron per kata/suku kata (banyak server)",
    "LRCLIB" to "Database terbuka, sinkron per baris",
    "KuGou" to "Koleksi besar, kuat untuk lagu Asia",
    "YouTube Music" to "Lirik bawaan (tidak sinkron)",
)

@Composable
fun MusicSettingsPanel(onClose: () -> Unit) {
    val colors = MusicPanelColors
    var pip by remember { mutableStateOf(Session.lyricsPip) }
    val enabled = remember {
        mutableStateMapOf<String, Boolean>().apply {
            LyricsRepo.providers.forEach { put(it.name, Session.isLyricsProviderEnabled(it.name)) }
        }
    }
    BottomPanel("Setelan Music", colors, onClose, heightFraction = 0.8f) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                ToggleRow(
                    "PiP lirik saat diminimalkan",
                    "Saat musik diputar dan aplikasi diminimalkan, lirik tampil di jendela kecil",
                    pip,
                ) {
                    pip = it
                    Session.lyricsPip = it
                }
            }
            item {
                Text(
                    "Penyedia lirik", color = colors.sub, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
                Text(
                    "Semua penyedia aktif dicari bersamaan; hasil sinkron per kata diutamakan.",
                    color = colors.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(LyricsRepo.providers.map { it.name }) { name ->
                ToggleRow(name, providerInfo[name] ?: "", enabled[name] == true) {
                    enabled[name] = it
                    Session.setLyricsProviderEnabled(name, it)
                }
            }
            item {
                Text(
                    "Cari ulang lirik lagu ini",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().clickable {
                        PlaybackManager.loadLyrics(force = true)
                        onClose()
                    }.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (subtitle.isNotBlank()) Text(subtitle, color = MusicPanelColors.sub, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = YtRed))
    }
}
