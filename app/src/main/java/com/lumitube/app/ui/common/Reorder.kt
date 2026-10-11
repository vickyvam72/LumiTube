package com.lumitube.app.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Menyusun ulang item LazyColumn dengan menyeret tombol ≡ (tanpa library tambahan).
 * [canDrop] membatasi target ke item yang boleh dipindah, [onMove] menukar posisi data,
 * [onEnd] dipanggil sekali setelah jari dilepas (untuk menyimpan ke server).
 */
class ReorderState(
    val listState: LazyListState,
    private val canDrop: (Any) -> Boolean,
    private val onMove: (from: Any, to: Any) -> Unit,
    private val onEnd: (key: Any) -> Unit,
) {
    var dragging by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    /** Posisi baru yang ditunggu setelah tukar (tata letak belum diperbarui). */
    private var pendingIndex: Int? = null

    fun start(key: Any) {
        dragging = key
        offset = 0f
        pendingIndex = null
    }

    fun drag(dy: Float) {
        val key = dragging ?: return
        offset += dy
        val info = listState.layoutInfo
        val items = info.visibleItemsInfo
        val cur = items.firstOrNull { it.key == key } ?: return
        // Tunggu sampai LazyColumn selesai memindahkan item sebelum menukar lagi.
        pendingIndex?.let { p ->
            if (cur.index != p) return
            pendingIndex = null
        }
        // Gulir otomatis saat diseret ke tepi atas/bawah.
        val top = cur.offset + offset
        val bottom = top + cur.size
        val edge = 120f
        val scroll = when {
            top < info.viewportStartOffset + edge -> -24f
            bottom > info.viewportEndOffset - edge -> 24f
            else -> 0f
        }
        if (scroll != 0f) {
            val used = listState.dispatchRawDelta(scroll)
            offset += used
        }
        val center = cur.offset + cur.size / 2f + offset
        val target = items.firstOrNull {
            it.key != key && canDrop(it.key) && center >= it.offset && center <= it.offset + it.size
        } ?: return
        // Item yang diseret pindah ke posisi target: koreksi geseran supaya tetap di bawah jari.
        val newOffset = if (target.offset > cur.offset) target.offset + target.size - cur.size else target.offset
        onMove(key, target.key)
        offset -= (newOffset - cur.offset).toFloat()
        pendingIndex = target.index
    }

    fun end() {
        val key = dragging ?: return
        dragging = null
        offset = 0f
        pendingIndex = null
        onEnd(key)
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    canDrop: (Any) -> Boolean,
    onMove: (Any, Any) -> Unit,
    onEnd: (Any) -> Unit,
): ReorderState {
    val c by rememberUpdatedState(canDrop)
    val m by rememberUpdatedState(onMove)
    val e by rememberUpdatedState(onEnd)
    return remember(listState) { ReorderState(listState, { c(it) }, { a, b -> m(a, b) }, { e(it) }) }
}

/** Modifier untuk baris yang bisa diseret: ikut bergeser & tampil di atas yang lain. */
fun Modifier.reorderItem(state: ReorderState, key: Any): Modifier =
    this.zIndex(if (state.dragging == key) 1f else 0f)
        .graphicsLayer {
            if (state.dragging == key) {
                translationY = state.offset
                shadowElevation = 12f
            }
        }

/** Tombol ≡ untuk menahan dan menggeser. */
@Composable
fun DragHandle(state: ReorderState, key: Any, tint: Color) {
    Box(
        Modifier.size(width = 36.dp, height = 56.dp).pointerInput(key) {
            detectDragGestures(
                onDragStart = { state.start(key) },
                onDragEnd = { state.end() },
                onDragCancel = { state.end() },
            ) { change, amount ->
                change.consume()
                state.drag(amount.y)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.DragHandle, "Geser untuk mengubah urutan", tint = tint)
    }
}
