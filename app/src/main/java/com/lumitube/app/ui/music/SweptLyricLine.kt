package com.lumitube.app.ui.music

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumitube.app.data.lyrics.LyricLine
import com.lumitube.app.player.PlaybackManager

private val DimWhite = Color(0x59FFFFFF)

/**
 * Jam lirik: posisi pemutaran diperbarui setiap frame layar, dibaca di fase gambar
 * saja sehingga animasi halus tanpa membangun ulang seluruh tampilan.
 */
@Composable
fun rememberLyricClock(): State<Long> {
    val clock: MutableLongState = remember { mutableLongStateOf(PlaybackManager.exo.currentPosition) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            clock.longValue = PlaybackManager.exo.currentPosition
        }
    }
    return clock
}

private fun ease(p: Float): Float = 1f - (1f - p) * (1f - p) * (1f - p)

/** Kotak kata di layout teks (dengan perkiraan bila kata terpotong ke baris berikutnya). */
private fun wordBox(layout: TextLayoutResult, start: Int, end: Int): Rect? {
    val len = layout.layoutInput.text.length
    if (start >= len || end <= start) return null
    val line = layout.getLineForOffset(start)
    val lineEnd = layout.getLineEnd(line, visibleEnd = true)
    val left = layout.getHorizontalPosition(start, true)
    val e = end.coerceAtMost(len)
    val right = if (e >= lineEnd || layout.getLineForOffset(e - 1) != line) {
        layout.getLineRight(line)
    } else {
        layout.getHorizontalPosition(e, true)
    }
    return Rect(left, layout.getLineTop(line), maxOf(right, left), layout.getLineBottom(line))
}

/**
 * Baris lirik bergaya Apple Music (diadaptasi dari teknik BitChord):
 * - salinan redup sebagai dasar,
 * - salinan terang yang "menyapu" huruf demi huruf mengikuti waktu tiap kata,
 * - kata yang dinyanyikan naik sedikit,
 * - nada panjang diberi cahaya (glow).
 */
@Composable
fun SweptLyricLine(
    line: LyricLine,
    clock: State<Long>,
    style: TextStyle,
    active: Boolean,
    modifier: Modifier = Modifier,
    rise: Dp = 3.dp,
    maxLines: Int = Int.MAX_VALUE,
) {
    val words = line.words
    val display = remember(line) {
        words.joinToString("") { it.text }.trimEnd().ifEmpty { line.text }
    }
    // Rentang karakter tiap kata di dalam teks yang ditampilkan
    val ranges = remember(line, display) {
        var c = 0
        words.map { w ->
            val s = c
            c += w.text.length
            s.coerceAtMost(display.length) until c.coerceAtMost(display.length)
        }
    }
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }

    fun ContentDrawScope.drawWords(lit: Boolean, glowOnly: Boolean) {
        val lay = layout
        if (lay == null || words.isEmpty()) {
            if (!glowOnly) drawContent()
            return
        }
        val now = clock.value
        val risePx = rise.toPx()
        val cds = this
        for ((i, w) in words.withIndex()) {
            val r = ranges[i]
            val box = wordBox(lay, r.first, r.last + 1) ?: continue
            val dur = (w.end - w.start).coerceAtLeast(1)
            val p = if (!active) 0f else ((now - w.start).toFloat() / dur).coerceIn(0f, 1f)
            val lift = if (active) risePx * ease(p) else 0f
            when {
                glowOnly -> {
                    // Hanya kata panjang yang sedang dinyanyikan yang bercahaya
                    if (p <= 0f || p >= 1f || dur < 650) continue
                    clipRect(box.left - 2f, box.top - risePx - 4f, box.left + box.width * p + 2f, box.bottom + 4f) {
                        translate(top = -lift) { cds.drawContent() }
                    }
                }
                lit -> {
                    if (p <= 0f) continue
                    val right = box.left + box.width * p
                    clipRect(box.left, box.top - risePx - 4f, right, box.bottom + 4f) {
                        translate(top = -lift) { cds.drawContent() }
                    }
                }
                else -> clipRect(box.left, box.top - risePx - 4f, box.right, box.bottom + 4f) {
                    translate(top = -lift) { cds.drawContent() }
                }
            }
        }
    }

    Box(modifier) {
        // Lapisan dasar (redup); ikut naik per kata agar sejajar dengan lapisan terang.
        Text(
            display,
            style = style.copy(color = DimWhite),
            maxLines = maxLines,
            onTextLayout = { layout = it },
            modifier = if (active) Modifier.drawWithContent { drawWords(lit = false, glowOnly = false) } else Modifier,
        )
        if (active) {
            if (Build.VERSION.SDK_INT >= 31) {
                Text(
                    display,
                    style = style.copy(color = Color.White),
                    maxLines = maxLines,
                    modifier = Modifier
                        .graphicsLayer { alpha = 0.85f }
                        .blur(10.dp, BlurredEdgeTreatment.Unbounded)
                        .drawWithContent { drawWords(lit = true, glowOnly = true) },
                )
            }
            Text(
                display,
                style = style.copy(color = Color.White),
                maxLines = maxLines,
                modifier = Modifier.drawWithContent { drawWords(lit = true, glowOnly = false) },
            )
        }
    }
}
