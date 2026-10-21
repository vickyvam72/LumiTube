package com.lumitube.app.ui.music

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.data.Session
import com.lumitube.app.data.lyrics.LyricsRepo
import com.lumitube.app.player.PlaybackManager
import com.lumitube.app.ui.common.AppearanceSettings
import com.lumitube.app.ui.common.LinkSettings
import com.lumitube.app.ui.common.MusicPanelColors
import com.lumitube.app.ui.common.SettingHeader
import com.lumitube.app.ui.common.SettingSwitch
import com.lumitube.app.ui.common.SettingsPage
import com.lumitube.app.ui.common.Text
import com.lumitube.app.ui.common.YtRed

/** Setelan YT Music Lumi dalam satu layar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicSettingsPanel(onClose: () -> Unit) {
    val colors = MusicPanelColors
    var pip by remember { mutableStateOf(Session.lyricsPip) }
    var closePip by remember { mutableStateOf(Session.closeOnPipClose) }
    val enabled = remember {
        mutableStateMapOf<String, Boolean>().apply {
            LyricsRepo.providers.forEach { put(it.name, Session.isLyricsProviderEnabled(it.name)) }
        }
    }
    SettingsPage("Setelan", colors, onClose) {
        SettingHeader("Tampilan", colors)
        AppearanceSettings(colors)
        SettingHeader("Lirik & pemutaran", colors)
        SettingSwitch(
            "PiP lirik saat diminimalkan", "Lirik tetap tampil di jendela kecil saat aplikasi diminimalkan", pip, colors,
        ) {
            pip = it
            Session.lyricsPip = it
        }
        SettingSwitch(
            "Tutup aplikasi saat PiP ditutup", "Menutup jendela kecil juga menghentikan musik", closePip, colors,
        ) {
            closePip = it
            Session.closeOnPipClose = it
        }
        Text(
            "Penyedia lirik (dicari bersamaan, yang sinkron per kata diutamakan)",
            color = colors.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LyricsRepo.providers.map { it.name }.forEach { name ->
                val on = enabled[name] == true
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (on) YtRed.copy(alpha = 0.18f) else Color.Transparent)
                        .border(1.dp, if (on) YtRed else colors.sub.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable {
                            enabled[name] = !on
                            Session.setLyricsProviderEnabled(name, !on)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (on) Icon(Icons.Filled.Check, null, tint = YtRed, modifier = Modifier.size(16.dp).padding(end = 2.dp))
                    androidx.compose.material3.Text(name, color = colors.text, fontSize = 13.sp)
                }
            }
        }
        Box(
            Modifier.padding(start = 20.dp, top = 2.dp, bottom = 2.dp).clip(RoundedCornerShape(18.dp))
                .border(1.dp, colors.accent, RoundedCornerShape(18.dp))
                .clickable {
                    PlaybackManager.loadLyrics(force = true)
                    onClose()
                }.padding(horizontal = 14.dp, vertical = 7.dp),
        ) { Text("Cari ulang lirik lagu ini", color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        SettingHeader("Link", colors)
        LinkSettings(colors)
    }
}
