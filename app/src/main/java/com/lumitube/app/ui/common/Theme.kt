package com.lumitube.app.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lumitube.app.data.Session

/** Palet warna yang meniru aplikasi YouTube asli. */
data class YtColors(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val sub: Color,
    val chip: Color,
    val chipSelected: Color,
    val chipSelectedText: Color,
    val divider: Color,
)

val YtLight = YtColors(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF2F2F2),
    text = Color(0xFF0F0F0F),
    sub = Color(0xFF606060),
    chip = Color(0xFFF2F2F2),
    chipSelected = Color(0xFF0F0F0F),
    chipSelectedText = Color(0xFFFFFFFF),
    divider = Color(0x1A000000),
)

val YtDark = YtColors(
    bg = Color(0xFF0F0F0F),
    surface = Color(0xFF212121),
    text = Color(0xFFF1F1F1),
    sub = Color(0xFFAAAAAA),
    chip = Color(0xFF272727),
    chipSelected = Color(0xFFF1F1F1),
    chipSelectedText = Color(0xFF0F0F0F),
    divider = Color(0x33FFFFFF),
)

/** Varian biru Lumi (warna logo: biru muda #22D3EE → biru #2563EB). */
val YtLightBlue = YtColors(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFEAF3FF),
    text = Color(0xFF0B1B33),
    sub = Color(0xFF4A5B75),
    chip = Color(0xFFE6F0FF),
    chipSelected = Color(0xFF2563EB),
    chipSelectedText = Color(0xFFFFFFFF),
    divider = Color(0x1A2563EB),
)

val YtDarkBlue = YtColors(
    bg = Color(0xFF0A1220),
    surface = Color(0xFF14213A),
    text = Color(0xFFEAF2FF),
    sub = Color(0xFF9FB3D1),
    chip = Color(0xFF1A2B47),
    chipSelected = Color(0xFF38BDF8),
    chipSelectedText = Color(0xFF0A1220),
    divider = Color(0x3338BDF8),
)

val LocalYt = staticCompositionLocalOf { YtLight }

/**
 * Tampilan aplikasi: terang/gelap dan warna biru Lumi (opsional, bawaan mati).
 * Bawaan: YouTube Lumi terang, YT Music Lumi gelap.
 */
object LumiStyle {
    var blue by mutableStateOf(false)
        private set
    var dark by mutableStateOf(false)
        private set

    private val music get() = com.lumitube.app.CrossApp.isMusic

    fun load() {
        blue = Session.getString("lumi_blue")?.let { it == "1" } ?: true
        dark = Session.getString("theme_dark")?.let { it == "1" } ?: music
    }

    fun updateBlue(on: Boolean) {
        blue = on
        Session.putString("lumi_blue", if (on) "1" else "0")
    }

    fun updateDark(on: Boolean) {
        dark = on
        Session.putString("theme_dark", if (on) "1" else "0")
    }

    val accentBrush: Brush
        get() = if (blue) Brush.linearGradient(listOf(Color(0xFF22D3EE), Color(0xFF2563EB))) else Brush.linearGradient(listOf(YtRed, YtRed))
}

/** Warna utama (garis putar, Shorts, logo, sakelar …): merah YouTube, atau biru Lumi bila diaktifkan. */
val YtRed: Color
    get() = if (LumiStyle.blue) (if (LumiStyle.dark) Color(0xFF38BDF8) else Color(0xFF2563EB)) else Color(0xFFFF0033)

private data class MusicPalette(
    val bg: Color, val top: Color, val card: Color, val chip: Color,
    val text: Color, val sub: Color, val nav: Color, val mini: Color,
    val panel: Color, val onAccent: Color,
)

private val MusicDark = MusicPalette(
    bg = Color(0xFF030303), top = Color(0xFF1D1934), card = Color(0xFF212121), chip = Color(0x1AFFFFFF),
    text = Color(0xFFFFFFFF), sub = Color(0xB3FFFFFF), nav = Color(0xFF0C0C0C), mini = Color(0xFF212121),
    panel = Color(0xFF1C1B22), onAccent = Color.Black,
)
private val MusicDarkBlue = MusicPalette(
    bg = Color(0xFF050B16), top = Color(0xFF0E2A52), card = Color(0xFF13233C), chip = Color(0x2638BDF8),
    text = Color(0xFFFFFFFF), sub = Color(0xB3E3EEFF), nav = Color(0xFF07101F), mini = Color(0xFF13233C),
    panel = Color(0xFF101C31), onAccent = Color(0xFF050B16),
)
private val MusicLight = MusicPalette(
    bg = Color(0xFFFFFFFF), top = Color(0xFFEDEAF7), card = Color(0xFFF1F1F1), chip = Color(0x14000000),
    text = Color(0xFF0F0F0F), sub = Color(0xFF606060), nav = Color(0xFFFAFAFA), mini = Color(0xFFF1F1F1),
    panel = Color(0xFFFFFFFF), onAccent = Color.White,
)
private val MusicLightBlue = MusicPalette(
    bg = Color(0xFFFFFFFF), top = Color(0xFFDCEBFF), card = Color(0xFFEAF3FF), chip = Color(0x1A2563EB),
    text = Color(0xFF0B1B33), sub = Color(0xFF4A5B75), nav = Color(0xFFF5F9FF), mini = Color(0xFFEAF3FF),
    panel = Color(0xFFFFFFFF), onAccent = Color.White,
)

object MusicColors {
    private val p: MusicPalette
        get() = when {
            LumiStyle.dark && LumiStyle.blue -> MusicDarkBlue
            LumiStyle.dark -> MusicDark
            LumiStyle.blue -> MusicLightBlue
            else -> MusicLight
        }
    val bg get() = p.bg
    val top get() = p.top
    val card get() = p.card
    val chip get() = p.chip
    val text get() = p.text
    val sub get() = p.sub
    val nav get() = p.nav
    val mini get() = p.mini
    val panel get() = p.panel
    /** Tombol utama (mis. "Putar"): putih di tema gelap, gelap di tema terang. */
    val primary get() = if (LumiStyle.blue) YtRed else p.text
    val onPrimary get() = if (LumiStyle.blue) Color.White else p.bg
    val dark get() = LumiStyle.dark
}

@Composable
fun LumiTheme(dark: Boolean, content: @Composable () -> Unit) {
    val yt = when {
        dark && LumiStyle.blue -> YtDarkBlue
        dark -> YtDark
        LumiStyle.blue -> YtLightBlue
        else -> YtLight
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = YtRed,
            background = yt.bg,
            surface = yt.bg,
            onBackground = yt.text,
            onSurface = yt.text,
            surfaceVariant = yt.surface,
            onSurfaceVariant = yt.sub,
        )
    } else {
        lightColorScheme(
            primary = YtRed,
            background = yt.bg,
            surface = yt.bg,
            onBackground = yt.text,
            onSurface = yt.text,
            surfaceVariant = yt.surface,
            onSurfaceVariant = yt.sub,
        )
    }
    CompositionLocalProvider(LocalYt provides yt) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
