package com.example.ainote.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ainote.data.repository.AiRepository
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.data.settings.UserSettings
import com.example.ainote.domain.model.Note
import com.example.ainote.domain.usecase.BuildCompletionContextUseCase
import com.example.ainote.domain.usecase.RequestCompletionUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteEditorUiState(
    val noteId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val title: String = "",
    val content: TextFieldValue = TextFieldValue(""),
    val wordCount: Int = 0,
    val completion: CompletionUiState = CompletionUiState(),
    val isLoaded: Boolean = false
)

class NoteEditorViewModel(
    private val noteId: Long,
    private val noteRepository: NoteRepository,
    aiRepository: AiRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    private val buildCompletionContext = BuildCompletionContextUseCase()
    private val requestCompletion = RequestCompletionUseCase(aiRepository)

    private val _uiState = MutableStateFlow(NoteEditorUiState(noteId = noteId))
    val uiState: StateFlow<NoteEditorUiState> = _uiState

    private var saveJob: Job? = null
    private var completionJob: Job? = null
    private var initialized = false

    init {
        viewModelScope.launch {
            noteRepository.observeNote(noteId).collect { note ->
                if (!initialized && note != null) {
                    initialized = true
                    applyNote(note)
                }
            }
        }
    }

    fun updateTitle(value: String) {
        _uiState.update { it.copy(title = value, completion = CompletionUiState()) }
        scheduleSave()
    }

    fun updateContent(value: TextFieldValue) {
        val oldText = _uiState.value.content.text
        _uiState.update {
            it.copy(
                content = value,
                wordCount = value.text.length,
                completion = CompletionUiState()
            )
        }
        scheduleSave()
        if (value.text.length >= oldText.length) {
            scheduleCompletion()
        } else {
            completionJob?.cancel()
        }
    }

    fun acceptCompletion() {
        val suggestion = _uiState.value.completion.suggestion ?: return
        val current = _uiState.value.content
        val cursor = current.selection.start.coerceIn(0, current.text.length)
        val nextText = current.text.substring(0, cursor) + suggestion + current.text.substring(cursor)
        updateContent(TextFieldValue(nextText, selection = TextRange(cursor + suggestion.length)))
    }

    fun dismissCompletion() {
        completionJob?.cancel()
        _uiState.update { it.copy(completion = CompletionUiState()) }
    }

    fun requestCompletionNow() {
        scheduleCompletion(force = true)
    }

    fun saveNow(onSaved: () -> Unit = {}) {
        saveJob?.cancel()
        viewModelScope.launch {
            saveCurrentState()
            onSaved()
        }
    }

    private fun applyNote(note: Note) {
        _uiState.value = NoteEditorUiState(
            noteId = note.id,
            createdAt = note.createdAt,
            pinned = note.pinned,
            title = note.title,
            content = TextFieldValue(note.content, selection = TextRange(note.content.length)),
            wordCount = note.content.length,
            isLoaded = true
        )
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            saveCurrentState()
        }
    }

    private suspend fun saveCurrentState() {
        val state = _uiState.value
        noteRepository.saveNote(
            id = state.noteId,
            title = state.title,
            content = state.content.text,
            createdAt = state.createdAt,
            pinned = state.pinned
        )
    }

    private fun scheduleCompletion(force: Boolean = false) {
        completionJob?.cancel()
        completionJob = viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            if (!canRequestCompletion(settings, force)) return@launch
            if (!force) delay(settings.completionDelayMs)
            val state = _uiState.value
            val cursor = state.content.selection.start
            val request = buildCompletionContext(
                content = state.content.text,
                cursor = cursor,
                title = state.title,
                maxLength = settings.maxCompletionLength,
                useFullNoteContext = settings.useFullNoteContext
            )
            _uiState.update { it.copy(completion = CompletionUiState(loading = true)) }
            runCatching { requestCompletion(request) }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(completion = CompletionUiState(suggestion = result.text.takeIf(String::isNotBlank)))
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(completion = CompletionUiState()) }
                }
        }
    }

    private fun canRequestCompletion(settings: UserSettings, force: Boolean): Boolean {
        val state = _uiState.value
        val cursor = state.content.selection.start
        return (settings.autoCompletionEnabled || force) &&
            state.content.selection.collapsed &&
            cursor > 0 &&
            state.content.text.take(cursor).trim().length >= 5 &&
            state.completion.suggestion == null
    }

    class Factory(
        private val noteId: Long,
        private val noteRepository: NoteRepository,
        private val aiRepository: AiRepository,
        private val settingsDataStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NoteEditorViewModel(noteId, noteRepository, aiRepository, settingsDataStore) as T
        }
    }
}
