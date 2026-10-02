package app.ove.studio.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ove.studio.editor.EditorState
import app.ove.studio.editor.EditorViewModel
import app.ove.studio.engine.RationalValue
import app.ove.studio.ui.theme.Spacing
import app.ove.studio.util.TimeCode

/**
 * Transport: frame-step controls, play/pause (stepped real rendering), and
 * the exact time readout. One row, 48dp, flush under the preview.
 */
@Composable
fun TransportBar(state: EditorState, viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    val frame = RationalValue(1, 24)
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = Spacing.sm.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                val back = state.playhead.sub(frame)
                viewModel.setPlayhead(if (back.num < 0) RationalValue.ZERO else back)
            },
        ) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Step one frame back")
        }
        IconButton(onClick = { viewModel.togglePlay() }) {
            if (state.playing) {
                Icon(Icons.Filled.Pause, contentDescription = "Pause")
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
            }
        }
        IconButton(
            onClick = { viewModel.setPlayhead(state.playhead.add(frame)) },
        ) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Step one frame forward")
        }
        Text(
            text = TimeCode.format(state.playhead),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.sm.dp),
        )
        Text(
            text = TimeCode.format(state.shape?.timeline_span ?: RationalValue.ZERO),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Spacing.sm.dp),
        )
    }
}
