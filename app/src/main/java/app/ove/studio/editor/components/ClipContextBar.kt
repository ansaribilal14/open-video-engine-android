package app.ove.studio.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ove.studio.editor.EditorState
import app.ove.studio.editor.EditorViewModel
import app.ove.studio.ui.theme.Spacing

/**
 * Clip context bar — appears ONLY when a clip is selected and offers ONLY
 * operations the engine actually supports (no dead controls, ever).
 */
@Composable
fun ClipContextBar(
    state: EditorState,
    viewModel: EditorViewModel,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sel = state.selectedClipId ?: return
    val shape = state.shape ?: return
    val trackCount = shape.tracks.size

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier
                .height(56.dp)
                .padding(horizontal = Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
        ) {
            Text(
                text = "Clip #$sel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.sm.dp),
            )
            TextButton(onClick = { viewModel.splitSelectedAtPlayhead() }) {
                Icon(Icons.Filled.ContentCut, contentDescription = null)
                Text("Split at playhead")
            }
            IconButton(
                onClick = onConfirmDelete,
                modifier = Modifier.padding(start = Spacing.xs.dp),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete clip #$sel",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            if (trackCount > 1) {
                IconButton(onClick = { viewModel.moveSelectedToTrack(prevTrack(shape, sel)) }) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move clip up one track")
                }
                IconButton(onClick = { viewModel.moveSelectedToTrack(nextTrack(shape, sel)) }) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move clip down one track")
                }
            }
        }
    }
}

private fun clipTrack(state: EditorState, clipId: Long): Long =
    state.shape?.tracks?.firstOrNull { t -> t.clips.any { it.id == clipId } }?.id ?: 1

private fun prevTrack(shape: app.ove.studio.engine.OveShape, clipId: Long): Long {
    val cur = shape.tracks.indexOfFirst { t -> t.clips.any { it.id == clipId } }
    val ids = shape.tracks.map { it.id }
    return ids[(cur - 1).coerceAtLeast(0)]
}

private fun nextTrack(shape: app.ove.studio.engine.OveShape, clipId: Long): Long {
    val cur = shape.tracks.indexOfFirst { t -> t.clips.any { it.id == clipId } }
    val ids = shape.tracks.map { it.id }
    return ids[(cur + 1).coerceAtMost(ids.size - 1)]
}
