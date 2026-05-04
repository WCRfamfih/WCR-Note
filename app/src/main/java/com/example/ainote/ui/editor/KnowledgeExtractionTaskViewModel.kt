package com.example.ainote.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ainote.data.repository.AiRepository
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.domain.model.KnowledgeExtractionDraft
import com.example.ainote.domain.model.KnowledgeExtractionRequest
import com.example.ainote.domain.model.KnowledgeTargetSummary
import com.example.ainote.domain.model.Note
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KnowledgeExtractionTaskUiState(
    val sourceMaterial: String = "",
    val instruction: String = "",
    val selectedTarget: KnowledgeTargetSummary? = null,
    val recentTargets: List<KnowledgeTargetSummary> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<KnowledgeTargetSummary> = emptyList(),
    val sending: Boolean = false,
    val draft: KnowledgeExtractionDraft? = null,
    val completed: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class KnowledgeExtractionTaskViewModel(
    private val noteId: Long,
    initialMaterial: String,
    private val noteRepository: NoteRepository,
    private val aiRepository: AiRepository
) : ViewModel() {
    private val instruction = MutableStateFlow("")
    private val searchQuery = MutableStateFlow("")
    private val selectedTarget = MutableStateFlow<KnowledgeTargetSummary?>(null)
    private val taskState = MutableStateFlow(
        KnowledgeExtractionTaskUiState(sourceMaterial = initialMaterial.trim())
    )

    private val recentTargetsFlow: Flow<List<KnowledgeTargetSummary>> =
        noteRepository.observeRecentKnowledgeEntries().map { notes ->
            notes.map { note -> note.toTargetSummary() }
        }

    private val searchResultsFlow: Flow<List<KnowledgeTargetSummary>> =
        searchQuery.flatMapLatest { query: String ->
            if (query.isBlank()) {
                MutableStateFlow(emptyList())
            } else {
                noteRepository.searchKnowledgeEntries(query).map { notes ->
                    notes.map { note -> note.toTargetSummary() }
                }
            }
        }

    private val basePresentationState: Flow<KnowledgeExtractionTaskUiState> = combine(
        taskState,
        instruction
    ) { state, instructionValue ->
        state.copy(instruction = instructionValue)
    }

    private val presentationState: Flow<KnowledgeExtractionTaskUiState> = combine(
        basePresentationState,
        selectedTarget
    ) { state, selectedTargetValue ->
        state.copy(selectedTarget = selectedTargetValue)
    }

    private val taskPresentationState: Flow<KnowledgeExtractionTaskUiState> = combine(
        presentationState,
        recentTargetsFlow
    ) { state, recentTargets ->
        state.copy(recentTargets = recentTargets)
    }

    private val searchPresentationState: Flow<KnowledgeExtractionTaskUiState> = combine(
        taskPresentationState,
        searchQuery
    ) { state, searchQueryValue ->
        state.copy(searchQuery = searchQueryValue)
    }

    val uiState: StateFlow<KnowledgeExtractionTaskUiState> = combine(
        searchPresentationState,
        searchResultsFlow
    ) { state, searchResults ->
        state.copy(searchResults = searchResults)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), taskState.value)

    init {
        if (initialMaterial.isBlank()) {
            viewModelScope.launch {
                val note = noteRepository.observeNote(noteId).first()
                val fallbackMaterial = note?.content?.trim().orEmpty()
                if (fallbackMaterial.isNotBlank()) {
                    taskState.update { it.copy(sourceMaterial = fallbackMaterial) }
                }
            }
        }
    }

    fun updateInstruction(value: String) {
        if (taskState.value.completed) return
        instruction.value = value
        clearDraftState()
    }

    fun updateSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun selectTarget(target: KnowledgeTargetSummary?) {
        if (taskState.value.completed) return
        selectedTarget.value = target
        clearDraftState()
    }

    fun send() {
        val state = uiState.value
        if (state.completed || state.sending) return
        val trimmedInstruction = state.instruction.trim()
        if (trimmedInstruction.isBlank()) {
            taskState.update { it.copy(errorMessage = "\u8bf7\u5148\u8f93\u5165\u6307\u4ee4\u3002") }
            return
        }
        if (state.sourceMaterial.isBlank()) {
            taskState.update { it.copy(errorMessage = "\u6ca1\u6709\u53ef\u7528\u7684\u6587\u672c\u6750\u6599\u3002") }
            return
        }
        val request = KnowledgeExtractionRequest(
            noteId = noteId,
            material = state.sourceMaterial,
            instruction = trimmedInstruction,
            targetKnowledgeId = state.selectedTarget?.id
        )
        taskState.update { it.copy(sending = true, errorMessage = null, statusMessage = null, draft = null) }
        viewModelScope.launch {
            runCatching { aiRepository.extractKnowledge(request) }
                .onSuccess { draft ->
                    taskState.update {
                        it.copy(
                            sending = false,
                            draft = draft,
                            errorMessage = null,
                            statusMessage = "\u5df2\u751f\u6210\u77e5\u8bc6\u8349\u6848\u3002"
                        )
                    }
                }
                .onFailure { error ->
                    taskState.update {
                        it.copy(
                            sending = false,
                            draft = null,
                            errorMessage = error.message ?: "\u77e5\u8bc6\u63d0\u53d6\u5931\u8d25\u3002"
                        )
                    }
                }
        }
    }

    fun retry() {
        if (taskState.value.completed) return
        send()
    }

    fun confirm() {
        val state = uiState.value
        val draft = state.draft ?: return
        if (state.completed) return
        taskState.update { it.copy(sending = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                if (draft.targetKnowledgeId == null) {
                    noteRepository.createKnowledge(draft.title, draft.content)
                } else {
                    noteRepository.overwriteKnowledge(draft.targetKnowledgeId, draft.title, draft.content)
                }
            }.onSuccess {
                taskState.update {
                    it.copy(
                        sending = false,
                        completed = true,
                        statusMessage = "\u4efb\u52a1\u5df2\u5b8c\u6210\u3002",
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                taskState.update {
                    it.copy(
                        sending = false,
                        errorMessage = error.message ?: "\u4fdd\u5b58\u77e5\u8bc6\u5931\u8d25\u3002"
                    )
                }
            }
        }
    }

    fun dismissError() {
        taskState.update { it.copy(errorMessage = null) }
    }

    private fun clearDraftState() {
        taskState.update {
            if (it.completed) {
                it
            } else {
                it.copy(draft = null, errorMessage = null, statusMessage = null)
            }
        }
    }

    private fun Note.toTargetSummary(): KnowledgeTargetSummary {
        return KnowledgeTargetSummary(
            id = id,
            title = displayTitle,
            folderName = folderName,
            updatedAt = updatedAt
        )
    }

    class Factory(
        private val noteId: Long,
        private val initialMaterial: String,
        private val noteRepository: NoteRepository,
        private val aiRepository: AiRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return KnowledgeExtractionTaskViewModel(noteId, initialMaterial, noteRepository, aiRepository) as T
        }
    }
}
