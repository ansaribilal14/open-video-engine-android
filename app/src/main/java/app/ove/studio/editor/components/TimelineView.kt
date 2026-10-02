package app.ove.studio.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ove.studio.editor.EditorState
import app.ove.studio.editor.EditorViewModel
import app.ove.studio.engine.OveClip
import app.ove.studio.engine.RationalValue
import app.ove.studio.ui.theme.TimelineMetrics
import app.ove.studio.ui.theme.editorClipColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private enum class TrimEdge { LEFT, RIGHT }
private enum class DragMode { SCRUB, TRIM_LEFT, TRIM_RIGHT, MOVE_PENDING }

/** Snap a display-level seconds value back to an exact rational on the
 *  project tick axis (48 kHz) — exact integers, no float round-trips. */
private fun snap(sec: Double): RationalValue {
    val ticks = (sec * 48_000.0).roundToInt().toLong().coerceAtLeast(0)
    return RationalValue(ticks, 48_000)
}

/**
 * The timeline — core product surface. Ruler + tracks + clips + selection +
 * trim handles + playhead on ONE canvas; gestures: tap select, drag-on-ruler
 * scrub, drag on trim handles to resize (exact rational on release),
 * pinch to zoom (anchored at playhead). Pixels are display-only; the engine
 * boundary never sees them.
 */
@Composable
fun TimelineView(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val currentState by rememberUpdatedState(state)
    val viewModelStable = rememberUpdatedState(viewModel)
    val clipFill = editorClipColor()
    val outlineColor = MaterialTheme.colorScheme.outline
    val selectionColor = MaterialTheme.colorScheme.primary
    val playheadColor = MaterialTheme.colorScheme.error
    val rulerTick = MaterialTheme.colorScheme.onSurfaceVariant
    val laneWell = MaterialTheme.colorScheme.surfaceContainerHigh
    val audioBadge = MaterialTheme.colorScheme.tertiary

    val rulerPx = with(density) { TimelineMetrics.rulerHeight.dp.toPx() }
    val trackPitchPx = with(density) { TimelineMetrics.trackPitch.dp.toPx() }
    val clipMarginPx = with(density) { TimelineMetrics.clipMargin.dp.toPx() }
    val handlePx = with(density) { TimelineMetrics.handleWidth.dp.toPx() }
    val totalHeightDp = TimelineMetrics.rulerHeight + 2 * TimelineMetrics.trackPitch

    Box(
        modifier
            .fillMaxWidth()
            .height(totalHeightDp.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics { contentDescription = "Timeline with tracks, clips and playhead" },
    ) {
        // ---- live gesture context (fresh state without restarting gestures) ----
        fun viewportSecs(widthPx: Float): Double =
            widthPx / currentState.zoomPxPerSecond.toDouble()

        fun originSec(playSec: Double, viewSecs: Double, spanSec: Double): Double {
            val desired = playSec - viewSecs / 3.0
            val maxOrigin = (spanSec - viewSecs).coerceAtLeast(0.0)
            return desired.coerceIn(0.0, maxOrigin)
        }

        fun secToPx(sec: Double, origin: Double, widthPx: Float): Float =
            ((sec - origin) * currentState.zoomPxPerSecond).toFloat()

        fun pxToSec(px: Float, origin: Double): Double =
            origin + (px / currentState.zoomPxPerSecond).toDouble()

        fun clipAt(t: Double, laneY: Float): Pair<Long, Int>? {
            val tracks = currentState.shape?.tracks ?: return null
            tracks.forEachIndexed { index, track ->
                val laneTop = rulerPx + index * trackPitchPx
                if (laneY >= laneTop && laneY <= laneTop + trackPitchPx) {
                    track.clips.forEach { c ->
                        val s = c.start.secondsDouble()
                        if (t >= s && t < s + c.duration.secondsDouble()) return c.id to index
                    }
                }
            }
            return null
        }

        fun trimEdgeOf(selected: OveClip?, index: Int, x: Float, origin: Double, widthPx: Float): TrimEdge? {
            selected ?: return null
            val tracks = currentState.shape?.tracks ?: return null
            if (index < 0 || index >= tracks.size) return null
            val c = tracks[index].clips.firstOrNull { it.id == selected.id } ?: return null
            val startSec = c.start.secondsDouble()
            val durSec = c.duration.secondsDouble()
            val x0 = secToPx(startSec, origin, widthPx)
            val x1 = secToPx(startSec + durSec, origin, widthPx)
            return when {
                abs(x - x0) <= handlePx -> TrimEdge.LEFT
                abs(x - x1) <= handlePx -> TrimEdge.RIGHT
                else -> null
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val playSec = currentState.playhead.secondsDouble()
            val viewSecs = viewportSecs(width)
            val origin = originSec(playSec, viewSecs, currentState.spanSeconds)

            // lane wells
            drawRect(laneWell, Offset(0f, rulerPx), Size(width, height - rulerPx))

            // ruler ticks: 1s minor, 5s major
            val firstTick = max(0, origin.toInt())
            var t = firstTick
            while (t <= origin.toInt() + viewSecs.toInt() + 1) {
                val x = secToPx(t.toDouble(), origin, width)
                if (x >= 0f && x <= width) {
                    val major = t % 5 == 0
                    drawLine(
                        rulerTick.copy(alpha = if (major) 0.9f else 0.35f),
                        Offset(x, rulerPx * (if (major) 0.85f else 0.45f)),
                        Offset(x, rulerPx),
                        strokeWidth = if (major) 2f else 1f,
                    )
                }
                t++
            }

            // tracks + clips (trim preview overrides the selected clip's width)
            currentState.shape?.tracks?.forEachIndexed { index, track ->
                val laneTop = rulerPx + index * trackPitchPx
                if (laneTop > height) return@forEachIndexed
                clipRect(0f, laneTop, width, laneTop + trackPitchPx) {
                    track.clips.forEach { clip ->
                        val selected = currentState.selectedClipId == clip.id
                        val previewDur = if (selected) currentState.trimPreview else null
                        val startSec = clip.start.secondsDouble()
                        val durSec = previewDur?.secondsDouble() ?: clip.duration.secondsDouble()
                        val x = secToPx(startSec, origin, width)
                        val w = max((durSec * currentState.zoomPxPerSecond).toFloat(), 8f)
                        if (x + w < 0f || x > width) return@forEachIndexed
                        val y = laneTop + clipMarginPx
                        val h = trackPitchPx - 2 * clipMarginPx
                        drawRoundRect(
                            color = clipFill,
                            topLeft = Offset(x, y),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(10f, 10f),
                        )
                        // audio presence badge — honest indicator, no fake waveform
                        if (currentState.shape?.assets?.firstOrNull()?.probe?.hasAudio == true) {
                            drawRoundRect(
                                color = audioBadge,
                                topLeft = Offset(x + 6f, y + h - 9f),
                                size = Size(minOf(w - 12f, 20f), 5f),
                                cornerRadius = CornerRadius(2.5f, 2.5f),
                            )
                        }
                        if (selected) {
                            drawRoundRect(
                                color = selectionColor,
                                topLeft = Offset(x - 2f, y - 2f),
                                size = Size(w + 4f, h + 4f),
                                cornerRadius = CornerRadius(11f, 11f),
                                style = Stroke(width = 2.5f),
                            )
                            drawRoundRect(
                                color = selectionColor,
                                topLeft = Offset(x, y),
                                size = Size(handlePx, h),
                                cornerRadius = CornerRadius(9f, 9f),
                            )
                            drawRoundRect(
                                color = selectionColor,
                                topLeft = Offset(x + w - handlePx, y),
                                size = Size(handlePx, h),
                                cornerRadius = CornerRadius(9f, 9f),
                            )
                        }
                    }
                }
            }

            // playhead — highest-contrast element in the timeline
            val px = secToPx(playSec, origin, width)
            if (px >= -2f && px <= width + 2f) {
                drawLine(playheadColor, Offset(px, 0f), Offset(px, height), strokeWidth = 3f)
                drawCircle(playheadColor, radius = 6f, center = Offset(px, rulerPx * 0.5f))
            }
        }

        // ---- gestures (single awaitEachGesture loop: tap / scrub / trim / zoom) ----
        Modifier.pointerInput(viewModel) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val widthPx = size.width.toFloat()
                val playSec = currentState.playhead.secondsDouble()
                val origin = originSec(playSec, viewportSecs(widthPx), currentState.spanSeconds)

                val hit = clipAt(pxToSec(down.position.x, origin), down.position.y)
                val selected = currentState.shape?.clipById(currentState.selectedClipId ?: -1)
                val hitIndex = hit?.second ?: -1
                val edge = if (currentState.selectedClipId != null && hit?.first == currentState.selectedClipId) {
                    trimEdgeOf(selected, hitIndex, down.position.x, origin, widthPx)
                } else null

                var dragMode: DragMode = when {
                    edge == TrimEdge.LEFT -> DragMode.TRIM_LEFT
                    edge == TrimEdge.RIGHT -> DragMode.TRIM_RIGHT
                    down.position.y < rulerPx -> DragMode.SCRUB
                    hit != null && hit.first == state.selectedClipId -> DragMode.MOVE_PENDING
                    else -> DragMode.SCRUB
                }
                var accumulated: Offset = Offset.Zero

                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.isEmpty()) break
                    val zoomChange = event.calculateZoom()
                    if (abs(zoomChange - 1f) > 0.01f && dragMode != DragMode.TRIM_LEFT && dragMode != DragMode.TRIM_RIGHT) {
                        viewModel.setZoom(currentState.zoomPxPerSecond * zoomChange)
                        event.changes.forEach { it.consume() }
                        continue
                    }
                    val change = event.changes.first().positionChange()
                    accumulated += change
                    if (dragMode == DragMode.MOVE_PENDING && abs(accumulated.x) > 12f) {
                        dragMode = DragMode.SCRUB // v0.1.0: drag-selects scrub; move via context bar
                    }
                    when (dragMode) {
                        DragMode.SCRUB -> {
                            val t = pxToSec(event.changes.first().position.x, origin)
                            if (t >= 0) viewModel.setPlayhead(snap(t))
                            event.changes.forEach { it.consume() }
                        }
                        DragMode.TRIM_LEFT -> {
                            val sel = currentState.shape?.clipById(currentState.selectedClipId ?: -1)
                            if (sel != null) {
                                val t = pxToSec(event.changes.first().position.x, origin)
                                val newDur = (sel.start.secondsDouble() + sel.duration.secondsDouble()) - t
                                if (newDur > 0) viewModel.updateTrimPreview(snap(newDur))
                            }
                            event.changes.forEach { it.consume() }
                        }
                        DragMode.TRIM_RIGHT -> {
                            val sel = currentState.shape?.clipById(currentState.selectedClipId ?: -1)
                            if (sel != null) {
                                val t = pxToSec(event.changes.first().position.x, origin)
                                val newDur = t - sel.start.secondsDouble()
                                if (newDur > 0) viewModel.updateTrimPreview(snap(newDur))
                            }
                            event.changes.forEach { it.consume() }
                        }
                        DragMode.MOVE_PENDING -> { /* waits for threshold */ }
                    }
                    if (!event.changes.any { it.pressed }) break
                }

                // commit trims on gesture end (engine resize with exact rational)
                if (dragMode == DragMode.TRIM_LEFT || dragMode == DragMode.TRIM_RIGHT) {
                    viewModel.commitTrim()
                }
            }
        }
    }
}

