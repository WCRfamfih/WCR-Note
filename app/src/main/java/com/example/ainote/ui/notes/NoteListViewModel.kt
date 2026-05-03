package com.example.ainote.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.NoteSortDirection
import com.example.ainote.data.settings.NoteSortField
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.data.settings.UserSettings
import com.example.ainote.domain.model.Note
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FolderSummary(
    val name: String?,
    val label: String,
    val count: Int,
    val canEdit: Boolean = false
)

data class NoteListUiState(
    val notes: List<Note> = emptyList(),
    val folders: List<FolderSummary> = listOf(FolderSummary(null, "全部", 0)),
    val selectedFolder: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class NoteListViewModel(
    private val repository: NoteRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    val query = MutableStateFlow("")
    private val selectedFolder = MutableStateFlow<String?>(null)

    val uiState: StateFlow<NoteListUiState> = combine(query, selectedFolder) { textQuery, folder ->
        textQuery to folder
    }.flatMapLatest { (textQuery, folder) ->
        combine(
            repository.searchNotes(textQuery, folder),
            repository.observeNotes(),
            settingsDataStore.folders,
            settingsDataStore.settings
        ) { visibleNotes, allNotes, storedFolders, settings ->
            NoteListUiState(
                notes = sortNotes(visibleNotes, settings),
                folders = buildFolderSummaries(allNotes, storedFolders),
                selectedFolder = folder
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteListUiState())

    fun updateQuery(value: String) {
        query.value = value
    }

    fun selectFolder(folderName: String?) {
        selectedFolder.value = folderName
    }

    fun createNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            onCreated(repository.createNote(selectedFolder.value.orEmpty()))
        }
    }

    fun createFolder(name: String) {
        val folderName = name.trim()
        if (folderName.isBlank()) return
        viewModelScope.launch {
            settingsDataStore.addFolder(folderName)
            selectedFolder.value = folderName
        }
    }

    fun renameFolder(oldName: String, newName: String) {
        val trimmedNewName = newName.trim()
        if (oldName.isBlank() || trimmedNewName.isBlank()) return
        viewModelScope.launch {
            repository.renameFolder(oldName, trimmedNewName)
            settingsDataStore.removeFolder(oldName)
            settingsDataStore.addFolder(trimmedNewName)
            selectedFolder.value = trimmedNewName
        }
    }

    fun deleteFolder(folderName: String) {
        if (folderName.isBlank()) return
        viewModelScope.launch {
            settingsDataStore.removeFolder(folderName)
            repository.deleteFolder(folderName)
            selectedFolder.value = null
        }
    }

    fun copyNote(id: Long) {
        viewModelScope.launch {
            repository.copyNote(id)
        }
    }

    fun moveNoteToFolder(id: Long, folderName: String?) {
        viewModelScope.launch {
            repository.moveNoteToFolder(id, folderName.orEmpty())
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    private fun sortNotes(notes: List<Note>, settings: UserSettings): List<Note> {
        val sorted = when (settings.noteSortField) {
            NoteSortField.Name -> notes.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle })
            NoteSortField.Time -> notes.sortedBy { it.updatedAt }
        }
        return when (settings.noteSortDirection) {
            NoteSortDirection.Ascending -> sorted
            NoteSortDirection.Descending -> sorted.reversed()
        }
    }

    private fun buildFolderSummaries(allNotes: List<Note>, storedFolders: List<String>): List<FolderSummary> {
        val usedFolders = allNotes.map { it.folderName.trim() }.filter { it.isNotBlank() }
        val customFolders = (storedFolders + usedFolders).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val summaries = mutableListOf(FolderSummary(null, "全部", allNotes.size))
        summaries += customFolders.map { folder ->
            FolderSummary(folder, folder, allNotes.count { it.folderName == folder }, canEdit = true)
        }
        summaries += FolderSummary("", "未分类", allNotes.count { it.folderName.isBlank() })
        return summaries
    }

    class Factory(
        private val repository: NoteRepository,
        private val settingsDataStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NoteListViewModel(repository, settingsDataStore) as T
        }
    }
}
