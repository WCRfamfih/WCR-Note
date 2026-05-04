package com.example.ainote.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.domain.model.Note
import com.example.ainote.domain.model.NoteKnowledgeScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class KnowledgeCardToggleItem(
    val id: Long,
    val title: String,
    val enabled: Boolean,
    val effectiveEnabled: Boolean
)

data class KnowledgeFolderToggleItem(
    val folderName: String,
    val label: String,
    val enabled: Boolean,
    val cards: List<KnowledgeCardToggleItem>
)

data class NoteKnowledgeScopeUiState(
    val folders: List<KnowledgeFolderToggleItem> = emptyList(),
    val enabledFolderCount: Int = 0,
    val totalFolderCount: Int = 0,
    val enabledKnowledgeCount: Int = 0,
    val totalKnowledgeCount: Int = 0
)

class NoteKnowledgeScopeViewModel(
    private val noteId: Long,
    private val noteRepository: NoteRepository,
    settingsDataStore: SettingsDataStore
) : ViewModel() {
    val uiState: StateFlow<NoteKnowledgeScopeUiState> = combine(
        noteRepository.observeKnowledgeEntries(),
        settingsDataStore.knowledgeFolders,
        noteRepository.observeNoteKnowledgeScope(noteId)
    ) { knowledgeEntries, storedFolders, scope ->
        buildUiState(knowledgeEntries, storedFolders, scope)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteKnowledgeScopeUiState())

    fun toggleFolder(folderName: String, enabled: Boolean) {
        val current = uiState.value
        val nextFolders = current.folders.map { folder ->
            if (folder.folderName == folderName) folder.copy(enabled = enabled) else folder
        }
        saveScope(nextFolders)
    }

    fun toggleKnowledge(folderName: String, knowledgeId: Long, enabled: Boolean) {
        val current = uiState.value
        val nextFolders = current.folders.map { folder ->
            if (folder.folderName != folderName) {
                folder
            } else {
                folder.copy(
                    cards = folder.cards.map { card ->
                        if (card.id == knowledgeId) {
                            card.copy(enabled = enabled, effectiveEnabled = enabled && folder.enabled)
                        } else {
                            card
                        }
                    }
                )
            }
        }
        saveScope(nextFolders)
    }

    private fun saveScope(folders: List<KnowledgeFolderToggleItem>) {
        viewModelScope.launch {
            val scope = NoteKnowledgeScope(
                enabledFolderNames = folders.filter { it.enabled }.map { it.folderName }.toSet(),
                disabledFolderNames = folders.filterNot { it.enabled }.map { it.folderName }.toSet(),
                enabledKnowledgeIds = folders.flatMap { folder -> folder.cards.filter { it.enabled }.map { it.id } }.toSet(),
                disabledKnowledgeIds = folders.flatMap { folder -> folder.cards.filterNot { it.enabled }.map { it.id } }.toSet()
            )
            noteRepository.saveNoteKnowledgeScope(noteId, scope)
        }
    }

    private fun buildUiState(
        knowledgeEntries: List<Note>,
        storedFolders: List<String>,
        scope: NoteKnowledgeScope?
    ): NoteKnowledgeScopeUiState {
        val allFolderNames = (storedFolders + knowledgeEntries.map { it.folderName })
            .map { it.trim() }
            .distinct()
            .sortedBy { it.ifBlank { "\uFFFF" } }
        val folders = allFolderNames.map { folderName ->
            val folderEnabled = scope?.let {
                folderName in it.enabledFolderNames && folderName !in it.disabledFolderNames
            } ?: true
            val cards = knowledgeEntries
                .filter { it.folderName == folderName }
                .map { note ->
                    val cardEnabled = scope?.let {
                        note.id in it.enabledKnowledgeIds && note.id !in it.disabledKnowledgeIds
                    } ?: true
                    KnowledgeCardToggleItem(
                        id = note.id,
                        title = note.displayTitle,
                        enabled = cardEnabled,
                        effectiveEnabled = folderEnabled && cardEnabled
                    )
                }
                .sortedBy { it.title.lowercase() }
            KnowledgeFolderToggleItem(
                folderName = folderName,
                label = folderName.ifBlank { "\u672a\u5206\u7c7b" },
                enabled = folderEnabled,
                cards = cards
            )
        }
        return NoteKnowledgeScopeUiState(
            folders = folders,
            enabledFolderCount = folders.count { it.enabled },
            totalFolderCount = folders.size,
            enabledKnowledgeCount = folders.sumOf { folder -> folder.cards.count { it.effectiveEnabled } },
            totalKnowledgeCount = folders.sumOf { it.cards.size }
        )
    }

    class Factory(
        private val noteId: Long,
        private val noteRepository: NoteRepository,
        private val settingsDataStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NoteKnowledgeScopeViewModel(noteId, noteRepository, settingsDataStore) as T
        }
    }
}
