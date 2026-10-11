package com.lumitube.app.ui.common

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Panel dengan garis di atas bisa diseret ke bawah untuk ditutup,
 * seperti bottom sheet YouTube. Berlaku saat menyeret garis/judul,
 * dan saat daftar di dalamnya sudah di paling atas lalu ditarik ke bawah.
 */
class SheetDragState internal constructor(
    private val scope: CoroutineScope,
    private val closeDistance: Float,
    private val onClose: () -> Unit,
) {
    var offset by mutableFloatStateOf(0f)
        private set
    private var anim: Job? = null
    private var closing = false

    fun dragBy(delta: Float) {
        if (closing) return
        anim?.cancel()
        offset = (offset + delta).coerceAtLeast(0f)
    }

    fun release(velocityY: Float) {
        if (closing) return
        anim?.cancel()
        if (offset > closeDistance || (velocityY > 1400f && offset > 0f)) {
            closing = true
            anim = scope.launch {
                animate(offset, offset + 2400f, animationSpec = tween(180)) { v, _ -> offset = v }
                onClose()
            }
        } else if (offset > 0f) {
            anim = scope.launch {
                animate(offset, 0f, animationSpec = tween(160)) { v, _ -> offset = v }
            }
        }
    }

    val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Sheet sedang turun dan pengguna menggeser ke atas: naikkan sheet dulu.
            if (available.y < 0 && offset > 0f) {
                val used = maxOf(available.y, -offset)
                dragBy(used)
                return Offset(0f, used)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // Daftar sudah di paling atas, sisa tarikan ke bawah menggeser sheet.
            if (available.y > 0 && source == NestedScrollSource.UserInput) {
                dragBy(available.y)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset > 0f) {
                release(available.y)
                return available
            }
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberSheetDragState(onClose: () -> Unit): SheetDragState {
    val scope = rememberCoroutineScope()
    val close by rememberUpdatedState(onClose)
    val distance = with(LocalDensity.current) { 110.dp.toPx() }
    return remember { SheetDragState(scope, distance) { close() } }
}

/** Untuk wadah sheet: ikut turun saat diseret, dan menerima tarikan dari daftar di dalamnya. */
fun Modifier.sheetContainer(state: SheetDragState): Modifier =
    this.offset { IntOffset(0, state.offset.roundToInt()) }.nestedScroll(state.nestedScroll)

/** Untuk area garis + judul: seret ke bawah untuk menutup. */
@Composable
fun Modifier.sheetHandle(state: SheetDragState): Modifier = this.draggable(
    state = rememberDraggableState { state.dragBy(it) },
    orientation = Orientation.Vertical,
    onDragStopped = { v -> state.release(v) },
)
