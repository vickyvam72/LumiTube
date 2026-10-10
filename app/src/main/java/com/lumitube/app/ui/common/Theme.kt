package com.lumitube.app.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

val LocalYt = staticCompositionLocalOf { YtLight }

val YtRed = Color(0xFFFF0033)

object MusicColors {
    val bg = Color(0xFF030303)
    val top = Color(0xFF1D1934)
    val card = Color(0xFF212121)
    val chip = Color(0x1AFFFFFF)
    val text = Color(0xFFFFFFFF)
    val sub = Color(0xB3FFFFFF)
    val nav = Color(0xFF0C0C0C)
    val mini = Color(0xFF212121)
}

@Composable
fun LumiTheme(dark: Boolean, content: @Composable () -> Unit) {
    val yt = if (dark) YtDark else YtLight
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
