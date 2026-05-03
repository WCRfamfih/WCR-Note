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
import com.example.ainote.domain.model.AiActionRequest
import com.example.ainote.domain.model.AiActionType
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
    val manualAi: ManualAiUiState = ManualAiUiState(),
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
    private val aiRepository = aiRepository

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
                completion = CompletionUiState(),
                manualAi = it.manualAi.copy(result = null, statusMessage = null, errorMessage = null)
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

    fun runManualAction(actionType: AiActionType) {
        completionJob?.cancel()
        viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            val state = _uiState.value
            val selectedText = state.content.selectedTextOrNull()
            val request = AiActionRequest(
                actionType = actionType,
                noteTitle = state.title.ifBlank { null },
                content = state.content.text,
                selectedText = selectedText,
                maxLength = when (actionType) {
                    AiActionType.ContinueWriting -> 120
                    AiActionType.Expand -> 220
                    AiActionType.Formal -> 180
                    AiActionType.Concise -> 100
                    AiActionType.Todo -> 180
                    AiActionType.Summarize -> 160
                    AiActionType.GenerateTitle -> 24
                }.coerceAtMost(settings.maxCompletionLength.coerceAtLeast(24) * 4)
            )
            executeManualAction(request, selectedText)
        }
    }

    fun retryManualAction() {
        val request = _uiState.value.manualAi.retryRequest ?: return
        completionJob?.cancel()
        viewModelScope.launch {
            executeManualAction(
                request = request,
                selectedText = request.selectedText
            )
        }
    }

    private suspend fun executeManualAction(
        request: AiActionRequest,
        selectedText: String?
    ) {
        val actionType = request.actionType
        _uiState.update {
            it.copy(
                completion = CompletionUiState(),
                manualAi = ManualAiUiState(loading = true, actionLabel = actionType.label)
            )
        }
        runCatching { aiRepository.runAction(request) }
            .onSuccess { result ->
                if (actionType == AiActionType.GenerateTitle) {
                    _uiState.update {
                        it.copy(
                            title = result.text,
                            manualAi = ManualAiUiState(result = result.text, actionLabel = actionType.label)
                        )
                    }
                    scheduleSave()
                } else {
                    _uiState.update {
                        it.copy(
                            manualAi = ManualAiUiState(
                                result = result.text.takeIf(String::isNotBlank),
                                actionLabel = actionType.label,
                                replaceSelection = shouldReplaceSelection(actionType, selectedText)
                            )
                        )
                    }
                }
            }
            .onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        manualAi = ManualAiUiState(
                            actionLabel = actionType.label,
                            errorMessage = "\u0041\u0049 \u64cd\u4f5c\u5931\u8d25\uff1a${formatErrorMessage(error)}",
                            retryRequest = request
                        )
                    )
                }
            }
    }

    fun acceptManualAiResult() {
        val state = _uiState.value
        val result = state.manualAi.result ?: return
        if (state.manualAi.actionLabel == AiActionType.GenerateTitle.label) {
            dismissManualAiResult()
            return
        }
        val current = state.content
        val selection = current.selection
        val start = if (state.manualAi.replaceSelection) selection.min else selection.start
        val end = if (state.manualAi.replaceSelection) selection.max else selection.start
        val insertText = if (start > 0 && current.text.getOrNull(start - 1)?.isWhitespace() == false) {
            "\n$result"
        } else {
            result
        }
        val nextText = current.text.substring(0, start) + insertText + current.text.substring(end)
        updateContent(TextFieldValue(nextText, selection = TextRange(start + insertText.length)))
        dismissManualAiResult()
    }

    fun dismissManualAiResult() {
        _uiState.update { it.copy(manualAi = ManualAiUiState()) }
    }

    fun markManualAiResultCopied() {
        _uiState.update { state ->
            state.copy(
                manualAi = state.manualAi.copy(
                    statusMessage = "\u5df2\u590d\u5236\u5230\u526a\u8d34\u677f\u3002",
                    errorMessage = null
                )
            )
        }
    }

    fun dismissManualAiStatus() {
        _uiState.update { it.copy(manualAi = it.manualAi.copy(statusMessage = null, errorMessage = null, loading = false)) }
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
            runCatching { requestCompletion(request, force = force) }
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

    private fun TextFieldValue.selectedTextOrNull(): String? {
        if (selection.collapsed) return null
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(0, text.length)
        return text.substring(start, end).takeIf { it.isNotBlank() }
    }

    private fun shouldReplaceSelection(actionType: AiActionType, selectedText: String?): Boolean {
        if (selectedText == null) return false
        return actionType in setOf(
            AiActionType.Expand,
            AiActionType.Formal,
            AiActionType.Concise,
            AiActionType.Todo
        )
    }

    private fun formatErrorMessage(it: Throwable): String {
        return it.message?.takeIf(String::isNotBlank) ?: "\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002"
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
