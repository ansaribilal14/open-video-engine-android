package app.ove.studio.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ove.studio.engine.OveClient
import app.ove.studio.engine.OveShape
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

class HomeViewModel(private val registry: ProjectRegistry) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state

    init { refresh() }

    fun refresh() {
        _state.value = HomeUiState.Loading
        val rows = registry.list().map {
            ProjectRow(it, registry.dirFor(it).isDirectory)
        }
        _state.value = if (rows.isEmpty()) HomeUiState.Empty(ready = true) else HomeUiState.Ready(rows)
    }

    fun createProject(name: String, onCreated: (ProjectEntry) -> Unit) {
        viewModelScope.launch {
            val entry = registry.create(name)
            // The engine project is created when the editor opens; a failure
            // there is surfaced on the editor screen (recovery state).
            onCreated(entry)
        }
    }

    fun deleteProject(entry: ProjectEntry) {
        registry.remove(entry)
        refresh()
    }

    fun markOpened(entry: ProjectEntry) = registry.touch(entry)

    fun dirFor(entry: ProjectEntry) = registry.dirFor(entry)

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(ProjectRegistry(context)) as T
    }
}
