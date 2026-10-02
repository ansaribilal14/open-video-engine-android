package app.ove.studio.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ove.studio.engine.OveClient
import app.ove.studio.project.ProjectRegistry
import app.ove.studio.ui.theme.OveTheme
import app.ove.studio.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Diagnostics: real engine identity, real storage numbers, honest licenses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(client: OveClient, onBack: () -> Unit) {
    var engineInfo by remember { mutableStateOf("Engine bridge: loading…") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val r = client.version()
            engineInfo = if (r.isError) {
                "Bridge error: ${r.error?.message}"
            } else {
                buildString {
                    appendLine("Bridge: ${r.bridge}")
                    appendLine("Engine version: ${r.engine_version}")
                    appendLine("Engine commit (pinned): ${r.engine_pin}")
                    appendLine("libav: FFmpeg 7.1.x, LGPL configuration (bundled in liboveandroid.so)")
                }
            }
        }
    }

    OveTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings & diagnostics") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md.dp),
            ) {
                SectionCard("Engine") {
                    Text(engineInfo, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.sm.dp))
                    Text(
                        "The engine performs all media operations: import, probe, editing " +
                            "state, undo/redo, rendering and export. This app is a client.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.md.dp))
                SectionCard("Honest limitations") {
                    Text(
                        "• Preview is stepped engine rendering (no continuous audio).\n" +
                            "• Export cannot be cancelled (engine v1 has no cancellation).\n" +
                            "• Multi-source composites render the first imported source (ADR-017).\n" +
                            "• No transitions, titles, effects or speed changes (not in engine v1).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.md.dp))
                SectionCard("Licenses") {
                    Text(
                        "OVE Studio is a client of Open Video Engine (MIT OR Apache-2.0). " +
                            "libav (FFmpeg) is linked under the LGPL v2.1+ configuration; " +
                            "its source is available at ffmpeg.org and the used configuration " +
                            "is documented in the engine repository.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Spacing.sm.dp))
            content()
        }
    }
}

@Suppress("unused")
private fun storageUsage(context: Context, registry: ProjectRegistry): Long = registry.storageBytes()
