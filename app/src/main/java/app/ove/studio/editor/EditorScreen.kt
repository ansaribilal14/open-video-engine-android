package app.ove.studio.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ove.studio.editor.components.ClipContextBar
import app.ove.studio.editor.components.PreviewPane
import app.ove.studio.editor.components.TimelineView
import app.ove.studio.editor.components.TransportBar
import app.ove.studio.export.ExportSheet
import app.ove.studio.ui.theme.OveTheme
import app.ove.studio.ui.theme.Spacing
import java.io.File

/**
 * The editor workspace: preview → transport → timeline → context bar.
 * Forced-dark (docs/UI_SYSTEM.md §10). Import uses the system photo/video
 * picker; staged copies are handed to the engine and deleted afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectName: String,
    projectDir: String,
    isNew: Boolean,
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
        if (isNew) viewModel.createAndOpen(projectDir, projectName)
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
                            Text(projectName, style = MaterialTheme.typography.titleMedium)
                            if (state.shape?.multi_source_limit == true) {
                                Row {
                                    Icon(
                                        Icons.Filled.Info, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                    Text(
                                        "renders first source (engine v1)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        TextButton(onClick = onBack) { Text("Projects") }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = state.canUndo,
                        ) { Icon(Icons.Filled.Undo, contentDescription = "Undo") }
                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = state.canRedo,
                        ) { Icon(Icons.Filled.Redo, contentDescription = "Redo") }
                        IconButton(
                            onClick = {
                                pickMedia.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                                )
                            },
                        ) { Icon(Icons.Filled.Add, contentDescription = "Import video") }
                        IconButton(onClick = { showExport = true }) {
                            Icon(Icons.Filled.IosShare, contentDescription = "Export")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Info, contentDescription = "Diagnostics")
                        }
                    },
                )
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

                Box(Modifier.weight(1f)) {
                    PreviewPane(state, Modifier.fillMaxSize())
                }
                TransportBar(state, viewModel)
                TimelineView(state, viewModel)
                ClipContextBar(
                    state = state,
                    viewModel = viewModel,
                    onConfirmDelete = { confirmDeleteClip = true },
                )
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
