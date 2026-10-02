package app.ove.studio.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ove.studio.engine.OveClient
import app.ove.studio.engine.RationalValue
import app.ove.studio.editor.EditorViewModel
import app.ove.studio.project.ProjectEntry
import app.ove.studio.project.ProjectRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Empty(val ready: Boolean) : HomeUiState
    data class Ready(val projects: List<ProjectRow>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

data class ProjectRow(val entry: ProjectEntry, val engineDirExists: Boolean)

class HomeViewModel(
    private val registry: ProjectRegistry,
    private val client: OveClient,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state

    init { refresh() }

    /**
     * Recompute rows from disk in one shot (no intermediate Loading flash).
     * Cheap local I/O; called on init AND every time this screen re-enters
     * composition (returning from the editor), so the list can never show a
     * stale view.
     */
    fun refresh() {
        val rows = registry.list().map {
            ProjectRow(it, registry.dirFor(it).isDirectory)
        }
        _state.value = if (rows.isEmpty()) HomeUiState.Empty(ready = true) else HomeUiState.Ready(rows)
    }

    /**
     * v0.1.2 fix for the "Engine folder missing" dead-end:
     * the ENGINE project is created FIRST — while the user is still on this
     * screen — and the registry row is only saved after the engine confirms.
     * Navigation to the editor then happens with the folder already on disk
     * (new=false ⇒ the editor OPENS, it never races a second create), and an
     * interrupted create can no longer leave a registry row without a folder.
     * Engine errors surface in the dialog; nothing is persisted on failure.
     */
    fun createProject(name: String, onCreated: (ProjectEntry) -> Unit, onFailed: (String) -> Unit) {
        viewModelScope.launch {
            val dirName = registry.nextDirName(name)
            val dir = registry.dirForDirName(dirName)
            val result = runCatching {
                client.createProject(
                    dir.absolutePath,
                    RationalValue(EditorViewModel.TICK_NUM, EditorViewModel.TICK_DEN),
                )
            }.getOrNull()
            if (result == null) {
                onFailed("Engine bridge failure — nothing was created. Please try again.")
                return@launch
            }
            if (result.isError) {
                onFailed(result.error?.message ?: "engine rejected the project")
                return@launch
            }
            val entry = registry.register(name, dirName)
            refresh()
            onCreated(entry)
        }
    }

    fun deleteProject(entry: ProjectEntry) {
        registry.remove(entry)
        refresh()
    }

    fun markOpened(entry: ProjectEntry) = registry.touch(entry)

    fun dirFor(entry: ProjectEntry) = registry.dirFor(entry)

    class Factory(
        private val context: Context,
        private val client: OveClient,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(ProjectRegistry(context), client) as T
    }
}
