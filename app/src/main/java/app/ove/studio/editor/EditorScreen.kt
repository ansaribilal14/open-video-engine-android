package app.ove.studio.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ove.studio.editor.components.PreviewPane
import app.ove.studio.editor.components.TimelineView
import app.ove.studio.editor.components.ToolChipBar
import app.ove.studio.editor.components.ToolChipSpec
import app.ove.studio.editor.components.TransportBar
import app.ove.studio.export.ExportSheet
import app.ove.studio.ui.theme.OveTheme
import app.ove.studio.ui.theme.Spacing
import java.io.File

/**
 * The editor workspace: preview → transport → timeline → context bar →
 * CapCut-style bottom action bar (icon + label columns; ONLY operations the
 * engine actually supports — no dead controls, ever). Forced-dark
 * (docs/UI_SYSTEM.md §10).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectName: String,
    projectDir: String,
    needsCreate: Boolean,
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showExport by remember { mutableStateOf(false) }
    var confirmDeleteClip by remember { mutableStateOf(false) }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.stageAndImport(uri)
        }
    }

    LaunchedEffect(projectDir) {
        if (needsCreate) viewModel.createAndOpen(projectDir, projectName)
        else viewModel.open(projectDir, projectName)
    }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    OveTheme(forceDark = true) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                projectName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (state.shape?.multi_source_limit == true) {
                                Text(
                                    "renders first source (engine v1)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        TextButton(onClick = onBack) { Text("Projects") }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Info, contentDescription = "Diagnostics")
                        }
                    },
                )
            },
            bottomBar = {
                // CapCut signature: ONE scrollable tool-chip bar, always
                // present; clip-scoped tools activate when a clip is selected.
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    ToolChipBar(
                        chips = buildList {
                            add(ToolChipSpec(Icons.Filled.Add, "Import", onClick = {
                                pickMedia.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                                )
                            }))
                            if (state.selectedClipId != null) {
                                add(ToolChipSpec(Icons.Filled.ContentCut, "Split", onClick = {
                                    viewModel.splitSelectedAtPlayhead()
                                }))
                                if ((state.shape?.tracks?.size ?: 0) > 1) {
                                    add(ToolChipSpec(Icons.Filled.ArrowUpward, "Track up", onClick = {
                                        viewModel.moveSelectedToTrack(prevTrackOf(state))
                                    }))
                                    add(ToolChipSpec(Icons.Filled.ArrowDownward, "Track down", onClick = {
                                        viewModel.moveSelectedToTrack(nextTrackOf(state))
                                    }))
                                }
                                add(ToolChipSpec(Icons.Filled.Delete, "Delete", onClick = {
                                    confirmDeleteClip = true
                                }, danger = true))
                            }
                            add(ToolChipSpec(Icons.Filled.Undo, "Undo", onClick = { viewModel.undo() }, enabled = state.canUndo))
                            add(ToolChipSpec(Icons.Filled.Redo, "Redo", onClick = { viewModel.redo() }, enabled = state.canRedo))
                            add(ToolChipSpec(Icons.Filled.IosShare, "Export", onClick = { showExport = true }, accent = true))
                        },
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (state.loadError != null) {
                    LoadErrorCard(state.loadError!!, onBack)
                    return@Column
                }
                if (state.engineBusy && state.shape == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (state.importMessage != null) {
                    Column {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            state.importMessage!!,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
                        )
                    }
                }

                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            RoundedCornerShape(14.dp),
                        ),
                ) {
                    PreviewPane(state, Modifier.fillMaxSize())
                }
                TransportBar(state, viewModel)
                TimelineView(state, viewModel)
            }
        }

        if (showExport) {
            ExportSheet(
                state = state,
                exportState = exportState,
                viewModel = viewModel,
                onDismiss = { showExport = false },
            )
        }

        if (confirmDeleteClip) {
            AlertDialog(
                onDismissRequest = { confirmDeleteClip = false },
                title = { Text("Delete clip?") },
                text = { Text("The clip is removed by an exact engine inverse — undo restores it.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDeleteClip = false
                        viewModel.deleteSelected()
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteClip = false }) { Text("Cancel") }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// CapCut-style bottom action bar — icon over label, one action per column.
// ---------------------------------------------------------------------------

@Composable
private fun EditorBottomBar(
    selected: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onImport: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val actions = listOf(
        BottomAction(Icons.Filled.Add, "Import", enabled = true) { onImport() },
        BottomAction(Icons.Filled.ContentCut, "Split", enabled = selected) { onSplit() },
        BottomAction(Icons.Filled.Delete, "Delete", enabled = selected) { onDelete() },
        BottomAction(Icons.Filled.Undo, "Undo", enabled = canUndo) { onUndo() },
        BottomAction(Icons.Filled.Redo, "Redo", enabled = canRedo) { onRedo() },
        BottomAction(Icons.Filled.IosShare, "Export", enabled = true, tint = accent) { onExport() },
    )
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = Spacing.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action ->
                BottomItem(action, Modifier.weight(1f))
            }
        }
    }
}

private data class BottomAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
    val tint: Color? = null,
    val onClick: () -> Unit,
)

@Composable
private fun BottomItem(action: BottomAction, modifier: Modifier = Modifier) {
    val contentColor = action.tint ?: MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = action.enabled, onClick = action.onClick)
            .padding(vertical = Spacing.xs.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            action.icon,
            contentDescription = action.label,
            tint = if (action.enabled) contentColor else contentColor.copy(alpha = 0.35f),
            modifier = Modifier.size(22.dp),
        )
        Text(
            action.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (action.enabled) contentColor else contentColor.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------
// Track-move helpers (contextual tools in the chip bar)
// ---------------------------------------------------------------------------

private fun prevTrackOf(state: app.ove.studio.editor.EditorState): Long {
    val shape = state.shape ?: return 1
    val cur = shape.tracks.indexOfFirst { t -> t.clips.any { it.id == state.selectedClipId } }
    val ids = shape.tracks.map { it.id }
    return ids[(cur - 1).coerceAtLeast(0)]
}

private fun nextTrackOf(state: app.ove.studio.editor.EditorState): Long {
    val shape = state.shape ?: return 1
    val cur = shape.tracks.indexOfFirst { t -> t.clips.any { it.id == state.selectedClipId } }
    val ids = shape.tracks.map { it.id }
    return ids[(cur + 1).coerceAtMost(ids.size - 1)]
}

@Composable
private fun LoadErrorCard(message: String, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(Spacing.md.dp),
    ) {
        Icon(
            Icons.Filled.Warning, contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text("Could not open the engine project", style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onBack) { Text("Back to projects") }
    }
}
