package app.ove.studio.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ove.studio.editor.EditorState
import app.ove.studio.editor.EditorViewModel
import app.ove.studio.engine.RationalValue
import app.ove.studio.ui.theme.Spacing
import app.ove.studio.util.TimeCode

/**
 * Transport: current timecode chip · frame-step + play (stepped real
 * rendering) · total-span chip. The play control is the accent circle —
 * the one button that always works here is the real engine.
 */
@Composable
fun TransportBar(state: EditorState, viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    val frame = RationalValue(1, 24)
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = Spacing.sm.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeChip(
            text = TimeCode.format(state.playhead),
            emphasize = true,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    val back = state.playhead.sub(frame)
                    viewModel.setPlayhead(if (back.num < 0) RationalValue.ZERO else back)
                },
            ) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Step one frame back")
            }
            FilledIconButton(
                onClick = { viewModel.togglePlay() },
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.playing) "Pause" else "Play",
                )
            }
            IconButton(
                onClick = { viewModel.setPlayhead(state.playhead.add(frame)) },
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Step one frame forward")
            }
        }
        TimeChip(
            text = TimeCode.format(state.shape?.timeline_span ?: RationalValue.ZERO),
            emphasize = false,
        )
    }
}

@Composable
private fun TimeChip(text: String, emphasize: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasize) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
