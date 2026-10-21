package com.lumitube.app.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.CrossApp
import com.lumitube.app.I18n
import com.lumitube.app.tr

/**
 * Halaman Setelan satu layar (tanpa scroll): bila isinya lebih tinggi dari layar
 * (misalnya huruf HP dibesarkan), semuanya diperkecil sampai pas.
 */
@Composable
fun SettingsPage(title: String, colors: PanelColors, onBack: () -> Unit, insets: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    BackHandler { onBack() }
    Column(
        Modifier.fillMaxSize().background(colors.bg)
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
            .then(if (insets) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
    ) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = colors.text) }
            Text(title, color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            FitToScreen {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { content() }
            }
        }
    }
}

@Composable
fun SettingHeader(text: String, colors: PanelColors) {
    Text(
        text, color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, colors: PanelColors, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = colors.text, fontSize = 15.sp)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = colors.sub, fontSize = 12.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = YtRed,
                uncheckedThumbColor = colors.sub, uncheckedTrackColor = colors.chip, uncheckedBorderColor = colors.sub,
            ),
        )
    }
}

/** Pilihan berbentuk tombol bersebelahan (mis. Terang | Gelap). */
@Composable
fun <T> SettingSegment(title: String, options: List<Pair<T, String>>, selected: T, colors: PanelColors, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(title, color = colors.text, fontSize = 15.sp)
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(colors.chip),
        ) {
            options.forEach { (value, label) ->
                val on = value == selected
                Box(
                    Modifier.weight(1f).padding(3.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (on) YtRed else Color.Transparent)
                        .clickable { onSelect(value) }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (on) Color.White else colors.text, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}

/** Baris yang membuka sesuatu (mis. pilih bahasa). */
@Composable
fun SettingAction(title: String, value: String?, colors: PanelColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Tampilan: tema terang/gelap dan warna biru Lumi. */
@Composable
fun AppearanceSettings(colors: PanelColors) {
    SettingSegment(
        "Tema", listOf(false to "Terang", true to "Gelap"), LumiStyle.dark, colors,
    ) { LumiStyle.updateDark(it) }
    SettingSwitch(
        "Warna biru Lumi", "Tampilan dominan biru seperti logo Lumi, termasuk garis putar dan Shorts",
        LumiStyle.blue, colors,
    ) { LumiStyle.updateBlue(it) }
}

@Composable
fun LinkSettings(colors: PanelColors) {
    val ctx = LocalContext.current
    var links by remember { mutableStateOf(CrossApp.linksEnabled(ctx)) }
    SettingSwitch(
        if (CrossApp.isMusic) "Buka link YouTube Music di aplikasi ini" else "Buka link YouTube di aplikasi ini",
        null, links, colors,
    ) {
        links = it
        CrossApp.setLinksEnabled(ctx, it)
        if (it) CrossApp.startLinkSetup(ctx)
    }
    if (links) {
        Box(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp).clip(RoundedCornerShape(18.dp))
                .border(1.dp, colors.accent, RoundedCornerShape(18.dp))
                .clickable { CrossApp.startLinkSetup(ctx) }.padding(horizontal = 14.dp, vertical = 7.dp),
        ) { Text("Atur ulang link", color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
    }
}

/** Panduan "Atur ulang link" langkah demi langkah (tidak terpotong seperti Toast). */
@Composable
fun LinkSetupGuide() {
    val step = CrossApp.guideStep
    if (step == 0) return
    val ctx = LocalContext.current
    val original = CrossApp.originalName
    val own = CrossApp.ownName
    val (title, lines, button) = if (step == 1) {
        Triple(
            tr("Langkah 1 dari 2: Lepaskan link dari %s", original),
            listOf(
                tr("Halaman pengaturan aplikasi %s akan terbuka.", original),
                tr("Ketuk \"Buka secara default\" (di beberapa HP: \"Setel sebagai default\")."),
                tr("Matikan \"Buka link yang didukung\"."),
                tr("Tekan kembali sampai masuk lagi ke %s.", own),
            ),
            tr("Buka pengaturan %s", original),
        )
    } else {
        Triple(
            tr("Langkah 2 dari 2: Sambungkan link ke %s", own),
            listOf(
                tr("Halaman pengaturan aplikasi %s akan terbuka.", own),
                tr("Ketuk \"Buka secara default\" (di beberapa HP: \"Setel sebagai default\")."),
                tr("Aktifkan \"Buka link yang didukung\"."),
                tr("Ketuk \"+ Tambahkan link\", centang semua link yang ada, lalu ketuk \"Tambahkan\"."),
                tr("Tekan kembali. Sekarang link YouTube langsung terbuka di %s.", own),
            ),
            tr("Buka pengaturan %s", own),
        )
    }
    AlertDialog(
        onDismissRequest = { CrossApp.guideStep = 0 },
        title = { androidx.compose.material3.Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEachIndexed { i, line ->
                    Row {
                        Box(
                            Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(YtRed),
                            contentAlignment = Alignment.Center,
                        ) { androidx.compose.material3.Text("${i + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        androidx.compose.material3.Text(line, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { CrossApp.continueGuide(ctx) }) {
                androidx.compose.material3.Text(button, fontWeight = FontWeight.Bold, color = YtRed, overflow = TextOverflow.Ellipsis, maxLines = 1)
            }
        },
        dismissButton = { TextButton(onClick = { CrossApp.guideStep = 0 }) { Text("Nanti saja") } },
    )
}
