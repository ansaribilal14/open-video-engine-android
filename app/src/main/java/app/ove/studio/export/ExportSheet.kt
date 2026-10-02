package app.ove.studio.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ove.studio.editor.EditorState
import app.ove.studio.editor.EditorViewModel
import app.ove.studio.editor.ExportState
import app.ove.studio.ui.theme.Spacing
import java.io.File

/**
 * Export sheet — REAL routes only (docs/PRODUCT_SPEC.md §3 J3):
 * MP4 composite (the certified re-encode path), WAV mixdown when audio
 * exists. Progress is honest indeterminate; the engine has no cancellation
 * so no cancel control is shown.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    state: EditorState,
    exportState: ExportState,
    viewModel: EditorViewModel,
    onDismiss: () -> Unit,
) {
    val shape = state.shape
    val hasAudio = shape?.assets?.firstOrNull()?.probe?.hasAudio == true
    val exporting = exportState is ExportState.Running

    ModalBottomSheet(onDismissRequest = { if (!exporting) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md.dp)
                .padding(bottom = Spacing.xl.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            Text("Export", style = MaterialTheme.typography.titleMedium)

            when (val s = exportState) {
                is ExportState.Running -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        "The engine is rendering and encoding. " +
                            "Cancellation is not supported by the engine, so no cancel is offered.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is ExportState.Success -> {
                    Text(
                        if (s.verified) "Export verified" else "Export finished (hash mismatch)",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (s.verified) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.error,
                    )
                    Text("File: ${File(s.path).name}", style = MaterialTheme.typography.bodyMedium)
                    Text("Size: ${s.size} bytes", style = MaterialTheme.typography.bodyMedium)
                    Text("SHA-256 (app-computed): ${s.appSha256}", style = MaterialTheme.typography.labelSmall)
                    if (!s.verified) {
                        Text(
                            "Engine reported ${s.engineSha256} — investigate before trusting this artifact.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
                        Button(onClick = { viewModel.dismissExport() }) { Text("Done") }
                    }
                }
                is ExportState.Failed -> {
                    Text("Export failed", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                    Text("${s.kind}: ${s.message}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.sm.dp))
                    TextButton(onClick = { viewModel.dismissExport() }) { Text("Close") }
                }
                ExportState.Idle -> {
                    if (shape?.multi_source_limit == true) {
                        Text(
                            "Engine v1 note: composites render the first imported source (ADR-017).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { viewModel.exportComposite() },
                        enabled = !state.engineBusy && (shape?.totalClips ?: 0) > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("MP4 — composite re-encode (MPEG4 + AAC)") }
                    if (hasAudio) {
                        OutlinedButton(
                            onClick = { viewModel.exportWav() },
                            enabled = !state.engineBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("WAV — timeline audio mixdown") }
                    } else {
                        Text(
                            "No audio stream in the imported sources — WAV mixdown unavailable.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
