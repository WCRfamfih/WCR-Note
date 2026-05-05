package com.example.ainote.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.ainote.data.debug.AiDebugLogStore
import com.example.ainote.data.repository.AiRepository
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.data.settings.UserSettings
import com.example.ainote.domain.model.AiActionRequest
import com.example.ainote.domain.model.AiActionType
import com.example.ainote.domain.model.CompletionRequest
import com.example.ainote.domain.model.KnowledgeExtractionLaunchArgs
import com.example.ainote.domain.model.KnowledgeScopeSummary
import com.example.ainote.domain.model.Note
import com.example.ainote.domain.model.NoteContentType
import com.example.ainote.domain.usecase.BuildCompletionContextUseCase
import com.example.ainote.domain.usecase.RequestCompletionUseCase
import com.example.ainote.ui.components.normalizeMarkdownMarkers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteEditorUiState(
    val noteId: Long,
    val contentType: NoteContentType = NoteContentType.Note,
    val createdAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val title: String = "",
    val content: TextFieldValue = TextFieldValue(""),
    val wordCount: Int = 0,
    val completion: CompletionUiState = CompletionUiState(),
    val manualAi: ManualAiUiState = ManualAiUiState(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isLoaded: Boolean = false,
    val showKnowledgeButton: Boolean = contentType == NoteContentType.Note,
    val knowledgeScopeSummary: KnowledgeScopeSummary = KnowledgeScopeSummary(),
    val isGlobalKnowledge: Boolean = false,
    val knowledgeOverflowPrompt: KnowledgeOverflowPrompt? = null,
    val showMarathonButton: Boolean = false,
    val marathonActive: Boolean = false,
    val marathonProgress: Float = 0f,
    val marathonRemainingMs: Long = 0L,
    val hideUndoRedo: Boolean = false,
    val hideAiButton: Boolean = false,
    val disableManualAiCompletion: Boolean = false
)

data class KnowledgeOverflowPrompt(
    val matchCount: Int,
    val limit: Int
)

class NoteEditorViewModel(
    private val noteId: Long,
    private val contentType: NoteContentType,
    private val noteRepository: NoteRepository,
    aiRepository: AiRepository,
    private val settingsDataStore: SettingsDataStore,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val buildCompletionContext = BuildCompletionContextUseCase()
    private val requestCompletion = RequestCompletionUseCase(aiRepository)
    private val aiRepository = aiRepository

    private val _uiState = MutableStateFlow(NoteEditorUiState(noteId = noteId, contentType = contentType))
    val uiState: StateFlow<NoteEditorUiState> = _uiState

    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var saveJob: Job? = null
    private var completionJob: Job? = null
    private var marathonTickerJob: Job? = null
    private var initialized = false
    private var showMarkdownMarkers = false
    private var settingsSnapshot = UserSettings()
    private var pendingKnowledgeAction: PendingKnowledgeAction? = null

    init {
        viewModelScope.launch {
            noteRepository.observeNote(noteId).collect { note ->
                if (!initialized && note != null) {
                    initialized = true
                    applyNote(note)
                }
            }
        }
        viewModelScope.launch {
            settingsDataStore.settings.collect { settings ->
                settingsSnapshot = settings
                showMarkdownMarkers = settings.showMarkdownMarkers
                if (!settings.experimentalMarathonEnabled && savedMarathonActive()) {
                    clearMarathonState()
                }
                syncMarathonUi()
            }
        }
        if (contentType == NoteContentType.Note) {
            viewModelScope.launch {
                noteRepository.observeKnowledgeScopeSummary(noteId).collect { summary ->
                    _uiState.update { it.copy(knowledgeScopeSummary = summary) }
                }
            }
        }
        restoreMarathonTickerIfNeeded()
    }

    fun updateTitle(value: String) {
        discardCompletionPreview()
        _uiState.update { it.copy(title = value, completion = CompletionUiState()) }
        scheduleSave()
    }

    fun toggleGlobalKnowledge(value: Boolean) {
        if (_uiState.value.contentType != NoteContentType.Knowledge) return
        discardCompletionPreview()
        _uiState.update { it.copy(isGlobalKnowledge = value) }
        scheduleSave()
    }

    fun startMarathon() {
        if (!settingsSnapshot.experimentalMarathonEnabled || savedMarathonActive()) return
        discardCompletionPreview()
        val baseContent = _uiState.value.content.text
        val durationMs = (settingsSnapshot.marathonDurationMinutes * 60_000f).toLong().coerceAtLeast(1_000L)
        savedStateHandle[KEY_MARATHON_ACTIVE] = true
        savedStateHandle[KEY_MARATHON_END_AT] = System.currentTimeMillis() + durationMs
        savedStateHandle[KEY_MARATHON_DURATION_MS] = durationMs
        savedStateHandle[KEY_MARATHON_LOCKED_PREFIX] = baseContent
        if (settingsSnapshot.marathonDisableAi) {
            completionJob?.cancel()
            _uiState.update {
                it.copy(
                    completion = CompletionUiState(),
                    manualAi = ManualAiUiState()
                )
            }
        }
        startMarathonTicker()
        syncMarathonUi()
    }

    fun stopMarathon() {
        clearMarathonState()
        syncMarathonUi()
    }

    fun updateContent(value: TextFieldValue) {
        val currentState = _uiState.value
        val adjustedValue = removeCompletionPreviewFromValue(value)
        val normalizedValue = if (showMarkdownMarkers) adjustedValue else adjustedValue.normalizeMarkdownSelection()
        val current = currentState.content.withoutPreview(currentState.completion.previewRange)
        val effectiveValue = if (currentState.marathonActive) {
            enforceMarathonEdit(current, normalizedValue)
        } else {
            normalizedValue
        }
        val contentChanged = current.text != effectiveValue.text
        if (current != effectiveValue) {
            undoStack.addLast(current)
            if (undoStack.size > 100) undoStack.removeFirst()
            redoStack.clear()
        }
        _uiState.update {
            it.copy(
                content = effectiveValue,
                wordCount = effectiveValue.text.length,
                completion = CompletionUiState(),
                manualAi = it.manualAi.copy(result = null, statusMessage = null, errorMessage = null),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        scheduleSave()
        scheduleCompletion(contentChanged = contentChanged)
    }

    fun undo() {
        if (_uiState.value.marathonActive) return
        discardCompletionPreview()
        val current = _uiState.value.content
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current)
        _uiState.update {
            it.copy(
                content = previous,
                wordCount = previous.text.length,
                completion = CompletionUiState(),
                manualAi = it.manualAi.copy(result = null, statusMessage = null, errorMessage = null),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun redo() {
        if (_uiState.value.marathonActive) return
        discardCompletionPreview()
        val current = _uiState.value.content
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current)
        _uiState.update {
            it.copy(
                content = next,
                wordCount = next.text.length,
                completion = CompletionUiState(),
                manualAi = it.manualAi.copy(result = null, statusMessage = null, errorMessage = null),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun acceptCompletion() {
        val state = _uiState.value
        val range = state.completion.previewRange ?: return
        val previous = state.content.withoutPreview(range)
        undoStack.addLast(previous)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        _uiState.update {
            it.copy(
                completion = CompletionUiState(),
                content = it.content.copy(selection = TextRange(range.max)),
                wordCount = it.content.text.length,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        scheduleSave()
    }

    fun dismissCompletion() {
        completionJob?.cancel()
        discardCompletionPreview()
    }

    fun requestCompletionNow() {
        if (_uiState.value.disableManualAiCompletion) return
        scheduleCompletion(force = true)
    }

    fun retryCompletion() {
        if (_uiState.value.disableManualAiCompletion) return
        dismissCompletion()
        scheduleCompletion(force = true)
    }

    fun applyMarkdownFormat(action: MarkdownFormatAction) {
        discardCompletionPreview()
        if (action == MarkdownFormatAction.ManualCompletion && _uiState.value.disableManualAiCompletion) return
        if (
            contentType == NoteContentType.Knowledge &&
            action !in setOf(MarkdownFormatAction.ManualCompletion, MarkdownFormatAction.Outdent, MarkdownFormatAction.Indent)
        ) {
            return
        }
        val current = _uiState.value.content
        val next = when (action) {
            MarkdownFormatAction.ManualCompletion -> {
                requestCompletionNow()
                return
            }
            MarkdownFormatAction.Outdent -> transformSelectedLines(current, ::outdentLine)
            MarkdownFormatAction.Indent -> transformSelectedLines(current) { "    $it" }
            MarkdownFormatAction.Heading1 -> toggleHeading(current, 1)
            MarkdownFormatAction.Heading2 -> toggleHeading(current, 2)
            MarkdownFormatAction.Heading3 -> toggleHeading(current, 3)
            MarkdownFormatAction.Bold -> toggleInlineWrapper(current, "**", "**")
            MarkdownFormatAction.Italic -> toggleInlineWrapper(current, "*", "*")
            MarkdownFormatAction.Strike -> toggleInlineWrapper(current, "~~", "~~")
            MarkdownFormatAction.Underline -> toggleInlineWrapper(current, "<u>", "</u>")
        }
        updateContent(next)
    }

    fun runManualAction(actionType: AiActionType) {
        if (_uiState.value.hideAiButton) return
        discardCompletionPreview()
        completionJob?.cancel()
        viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            val state = _uiState.value
            val selectedText = state.content.selectedTextOrNull()
            val request = AiActionRequest(
                noteId = state.noteId,
                actionType = actionType,
                noteTitle = state.title.ifBlank { null },
                content = state.content.text,
                selectedText = selectedText,
                maxLength = when (actionType) {
                    AiActionType.ContinueWriting -> 120
                    AiActionType.ExtractToKnowledge -> 400
                    AiActionType.Expand -> 220
                    AiActionType.Formal -> 180
                    AiActionType.Concise -> 100
                    AiActionType.Todo -> 180
                    AiActionType.Summarize -> 160
                    AiActionType.GenerateTitle -> 24
                }.coerceAtMost(settings.maxCompletionLength.coerceAtLeast(24) * 4),
                disableKnowledgeInjection = actionType == AiActionType.ExtractToKnowledge
            )
            val context = selectedText ?: state.content.text
            val resolvedRequest = prepareManualActionRequest(request, context)
            if (resolvedRequest != null) {
                executeManualAction(resolvedRequest, selectedText)
            }
        }
    }

    fun buildKnowledgeExtractionLaunch(
        settings: UserSettings,
        forceFullDocument: Boolean = false
    ): KnowledgeExtractionLaunchArgs {
        discardCompletionPreview()
        val state = _uiState.value
        return KnowledgeExtractionLaunchArgs(
            noteId = state.noteId,
            contentType = state.contentType,
            material = buildCompletionContext.buildMaterial(
                content = state.content.text,
                selectionStart = state.content.selection.start,
                selectionEnd = state.content.selection.end,
                useFullNoteContext = forceFullDocument || settings.useFullNoteContext,
                beforeLineCount = settings.completionBeforeLineCount,
                afterLineCount = settings.completionAfterLineCount
            )
        )
    }

    fun retryManualAction() {
        discardCompletionPreview()
        val request = _uiState.value.manualAi.retryRequest ?: return
        completionJob?.cancel()
        viewModelScope.launch {
            val context = request.selectedText ?: request.content
            val resolvedRequest = prepareManualActionRequest(request, context)
            if (resolvedRequest != null) {
                executeManualAction(resolvedRequest, resolvedRequest.selectedText)
            }
        }
    }

    fun confirmKnowledgeOverflow() {
        val pending = pendingKnowledgeAction ?: return
        pendingKnowledgeAction = null
        _uiState.update { it.copy(knowledgeOverflowPrompt = null) }
        viewModelScope.launch {
            when (pending) {
                is PendingKnowledgeAction.Completion -> {
                    val resolvedRequest = applyKnowledgeContext(
                        request = pending.request,
                        context = pending.context,
                        includeAllMatches = true
                    )
                    executeCompletionRequest(resolvedRequest, pending.force)
                }

                is PendingKnowledgeAction.Manual -> {
                    val resolvedRequest = applyKnowledgeContext(
                        request = pending.request,
                        context = pending.context,
                        includeAllMatches = true
                    )
                    executeManualAction(resolvedRequest, pending.selectedText)
                }
            }
        }
    }

    fun dismissKnowledgeOverflow() {
        pendingKnowledgeAction = null
        _uiState.update { it.copy(knowledgeOverflowPrompt = null) }
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
                            errorMessage = "AI 操作失败：${formatErrorMessage(error)}",
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
                    statusMessage = "已复制到剪贴板。",
                    errorMessage = null
                )
            )
        }
    }

    fun dismissManualAiStatus() {
        _uiState.update {
            it.copy(manualAi = it.manualAi.copy(statusMessage = null, errorMessage = null, loading = false))
        }
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
            contentType = note.contentType,
            createdAt = note.createdAt,
            pinned = note.pinned,
            title = note.title,
            content = TextFieldValue(note.content, selection = TextRange(note.content.length)),
            wordCount = note.content.length,
            isLoaded = true,
            showKnowledgeButton = note.contentType == NoteContentType.Note,
            knowledgeScopeSummary = _uiState.value.knowledgeScopeSummary,
            isGlobalKnowledge = note.isGlobalKnowledge,
            showMarathonButton = settingsSnapshot.experimentalMarathonEnabled,
            marathonActive = savedMarathonActive()
        )
        syncMarathonUi()
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
            content = state.content.withoutPreview(state.completion.previewRange).text,
            createdAt = state.createdAt,
            pinned = state.pinned,
            contentType = state.contentType,
            isGlobalKnowledge = state.isGlobalKnowledge
        )
    }

    private fun scheduleCompletion(force: Boolean = false, contentChanged: Boolean = true) {
        completionJob?.cancel()
        completionJob = viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            if (!canRequestCompletion(settings, force, contentChanged)) {
                if (force) {
                    AiDebugLogStore.add("Completion skipped", "Manual completion skipped because the cursor is not in a completable position.")
                    _uiState.update {
                        it.copy(completion = CompletionUiState(errorMessage = "请把光标放在可补全的位置后再试。"))
                    }
                }
                return@launch
            }
            if (!force) delay(settings.completionDelayMs)
            val state = _uiState.value
            val cursor = state.content.selection.start
            val request = buildCompletionContext(
                content = state.content.text,
                cursor = cursor,
                title = state.title,
                maxLength = settings.maxCompletionLength,
                useFullNoteContext = settings.useFullNoteContext,
                beforeLineCount = settings.completionBeforeLineCount,
                afterLineCount = settings.completionAfterLineCount
            ).copy(noteId = state.noteId)
            val context = request.beforeCursor + request.afterCursor
            val resolvedRequest = prepareCompletionRequest(request, context, force) ?: return@launch
            executeCompletionRequest(resolvedRequest, force)
        }
    }

    private suspend fun executeCompletionRequest(request: CompletionRequest, force: Boolean) {
        _uiState.update { it.copy(completion = CompletionUiState(loading = true)) }
        runCatching { requestCompletion(request, force = force) }
            .onSuccess { result ->
                val suggestion = result.text.takeIf(String::isNotBlank)
                _uiState.update {
                    if (suggestion == null) {
                        AiDebugLogStore.add("Completion empty", "AI returned no usable completion text after filtering.")
                        it.copy(completion = CompletionUiState(errorMessage = "AI 没有返回可用的补全文字，请重试。"))
                    } else {
                        it.withCompletionPreview(suggestion)
                    }
                }
            }
            .onFailure { error ->
                if (error is CancellationException) {
                    AiDebugLogStore.add("Completion cancelled", error.message ?: "Coroutine was cancelled.")
                    _uiState.update { it.copy(completion = CompletionUiState()) }
                } else {
                    AiDebugLogStore.add("Completion error", formatErrorMessage(error))
                    _uiState.update {
                        it.copy(completion = CompletionUiState(errorMessage = "AI 补全失败：${formatErrorMessage(error)}"))
                    }
                }
            }
    }

    private suspend fun prepareCompletionRequest(
        request: CompletionRequest,
        context: String,
        force: Boolean
    ): CompletionRequest? {
        val resolution = aiRepository.resolveKnowledgeContext(context, request.noteId)
        if (resolution.overflow) {
            pendingKnowledgeAction = PendingKnowledgeAction.Completion(request, context, force)
            _uiState.update {
                it.copy(
                    completion = CompletionUiState(),
                    knowledgeOverflowPrompt = KnowledgeOverflowPrompt(
                        matchCount = resolution.matches.size,
                        limit = resolution.limit
                    )
                )
            }
            return null
        }
        return request.copy(
            relatedKnowledge = resolution.relatedKnowledge,
            disableKnowledgeInjection = resolution.relatedKnowledge.isBlank()
        )
    }

    private suspend fun prepareManualActionRequest(
        request: AiActionRequest,
        context: String
    ): AiActionRequest? {
        if (request.disableKnowledgeInjection) return request
        val resolution = aiRepository.resolveKnowledgeContext(context, request.noteId)
        if (resolution.overflow) {
            pendingKnowledgeAction = PendingKnowledgeAction.Manual(request, context, request.selectedText)
            _uiState.update {
                it.copy(
                    manualAi = ManualAiUiState(actionLabel = request.actionType.label),
                    knowledgeOverflowPrompt = KnowledgeOverflowPrompt(
                        matchCount = resolution.matches.size,
                        limit = resolution.limit
                    )
                )
            }
            return null
        }
        return request.copy(
            relatedKnowledge = resolution.relatedKnowledge,
            disableKnowledgeInjection = resolution.relatedKnowledge.isBlank()
        )
    }

    private suspend fun applyKnowledgeContext(
        request: CompletionRequest,
        context: String,
        includeAllMatches: Boolean
    ): CompletionRequest {
        val resolution = aiRepository.resolveKnowledgeContext(context, request.noteId, includeAllMatches)
        return request.copy(
            relatedKnowledge = resolution.relatedKnowledge,
            disableKnowledgeInjection = resolution.relatedKnowledge.isBlank()
        )
    }

    private suspend fun applyKnowledgeContext(
        request: AiActionRequest,
        context: String,
        includeAllMatches: Boolean
    ): AiActionRequest {
        if (request.disableKnowledgeInjection) return request
        val resolution = aiRepository.resolveKnowledgeContext(context, request.noteId, includeAllMatches)
        return request.copy(
            relatedKnowledge = resolution.relatedKnowledge,
            disableKnowledgeInjection = resolution.relatedKnowledge.isBlank()
        )
    }

    private fun canRequestCompletion(settings: UserSettings, force: Boolean, contentChanged: Boolean): Boolean {
        val state = _uiState.value
        if (state.marathonActive && settings.marathonDisableAi) return false
        val cursor = state.content.selection.start
        if (force) {
            return state.content.selection.collapsed &&
                cursor > 0 &&
                state.content.text.take(cursor).isNotBlank()
        }
        return settings.autoCompletionEnabled &&
            (!settings.autoCompleteOnlyOnContentChange || contentChanged) &&
            state.content.selection.collapsed &&
            cursor > 0 &&
            state.content.text.take(cursor).isNotBlank() &&
            (!settings.skipBlankLineAutoCompletion || !isCurrentLineBlank(state.content.text, cursor)) &&
            (!settings.preferChineseAutoCompletion || containsChinese(state.content.text.take(cursor))) &&
            state.completion.previewRange == null
    }

    private fun discardCompletionPreview() {
        val state = _uiState.value
        val previewRange = state.completion.previewRange ?: return
        val restored = state.content.withoutPreview(previewRange)
        _uiState.update {
            it.copy(
                content = restored,
                wordCount = restored.text.length,
                completion = CompletionUiState()
            )
        }
    }

    private fun removeCompletionPreviewFromValue(value: TextFieldValue): TextFieldValue {
        val previewRange = _uiState.value.completion.previewRange ?: return value
        return value.withoutPreview(previewRange)
    }

    private fun enforceMarathonEdit(
        current: TextFieldValue,
        proposed: TextFieldValue
    ): TextFieldValue {
        if (proposed.text == current.text) return proposed
        val isValid = proposed.text.length >= current.text.length && proposed.text.startsWith(current.text)
        return if (isValid) {
            proposed
        } else {
            current.copy(
                selection = TextRange(
                    start = proposed.selection.start.coerceIn(0, current.text.length),
                    end = proposed.selection.end.coerceIn(0, current.text.length)
                )
            )
        }
    }

    private fun syncMarathonUi() {
        val active = savedMarathonActive()
        val remainingMs = if (active) {
            (savedStateHandle.get<Long>(KEY_MARATHON_END_AT) ?: 0L) - System.currentTimeMillis()
        } else {
            0L
        }
        if (active && remainingMs <= 0L) {
            clearMarathonState()
            syncMarathonUi()
            return
        }
        val totalDurationMs = savedStateHandle.get<Long>(KEY_MARATHON_DURATION_MS) ?: 0L
        val progress = if (active && totalDurationMs > 0L) {
            ((totalDurationMs - remainingMs).toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        _uiState.update {
            it.copy(
                showMarathonButton = settingsSnapshot.experimentalMarathonEnabled,
                marathonActive = active,
                marathonRemainingMs = remainingMs.coerceAtLeast(0L),
                marathonProgress = progress,
                hideUndoRedo = active,
                hideAiButton = active && settingsSnapshot.marathonDisableAi,
                disableManualAiCompletion = active && settingsSnapshot.marathonDisableAi
            )
        }
    }

    private fun restoreMarathonTickerIfNeeded() {
        if (!savedMarathonActive()) return
        startMarathonTicker()
    }

    private fun startMarathonTicker() {
        marathonTickerJob?.cancel()
        marathonTickerJob = viewModelScope.launch {
            while (savedMarathonActive()) {
                syncMarathonUi()
                delay(500)
            }
            syncMarathonUi()
        }
    }

    private fun clearMarathonState() {
        marathonTickerJob?.cancel()
        marathonTickerJob = null
        savedStateHandle[KEY_MARATHON_ACTIVE] = false
        savedStateHandle[KEY_MARATHON_END_AT] = 0L
        savedStateHandle[KEY_MARATHON_DURATION_MS] = 0L
        savedStateHandle[KEY_MARATHON_LOCKED_PREFIX] = ""
    }

    private fun savedMarathonActive(): Boolean {
        return savedStateHandle.get<Boolean>(KEY_MARATHON_ACTIVE) == true
    }

    private fun TextFieldValue.withoutPreview(previewRange: TextRange?): TextFieldValue {
        val range = previewRange ?: return this
        if (range.collapsed) return this
        val start = range.min.coerceIn(0, text.length)
        val end = range.max.coerceIn(start, text.length)
        val nextText = text.removeRange(start, end)
        fun adjust(offset: Int): Int = when {
            offset <= start -> offset
            offset >= end -> offset - (end - start)
            else -> start
        }.coerceIn(0, nextText.length)
        return copy(
            text = nextText,
            selection = TextRange(adjust(selection.start), adjust(selection.end))
        )
    }

    private fun NoteEditorUiState.withCompletionPreview(suggestion: String): NoteEditorUiState {
        val baseContent = content.withoutPreview(completion.previewRange)
        val cursor = baseContent.selection.start.coerceIn(0, baseContent.text.length)
        val nextText = baseContent.text.substring(0, cursor) + suggestion + baseContent.text.substring(cursor)
        val previewRange = TextRange(cursor, cursor + suggestion.length)
        return copy(
            content = TextFieldValue(nextText, selection = TextRange(previewRange.max)),
            wordCount = nextText.length,
            completion = CompletionUiState(suggestion = suggestion, previewRange = previewRange)
        )
    }

    private fun containsChinese(text: String): Boolean {
        return text.any { it in '\u4e00'..'\u9fff' }
    }

    private fun isCurrentLineBlank(text: String, cursor: Int): Boolean {
        val safeCursor = cursor.coerceIn(0, text.length)
        val lineStart = if (safeCursor == 0) 0 else text.lastIndexOf('\n', safeCursor - 1).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', safeCursor).let { if (it == -1) text.length else it }
        return text.substring(lineStart, lineEnd).isBlank()
    }

    private fun transformSelectedLines(
        value: TextFieldValue,
        transform: (String) -> String
    ): TextFieldValue {
        val text = value.text
        val range = targetLineRange(value)
        val block = text.substring(range.first, range.second)
        val transformed = block.split('\n').joinToString("\n", transform = transform)
        return replaceTarget(value, range.first, range.second, transformed)
    }

    private fun targetLineRange(value: TextFieldValue): Pair<Int, Int> {
        val text = value.text
        if (text.isEmpty()) return 0 to 0
        val start = if (value.selection.collapsed) value.selection.start else value.selection.min
        val end = if (value.selection.collapsed) value.selection.start else value.selection.max
        val lineStart = if (start == 0) 0 else text.lastIndexOf('\n', start - 1).let { if (it == -1) 0 else it + 1 }
        val searchFrom = if (!value.selection.collapsed && end > start && text.getOrNull(end - 1) == '\n') {
            (end - 1).coerceAtLeast(lineStart)
        } else {
            end.coerceAtLeast(lineStart)
        }
        val lineEnd = text.indexOf('\n', searchFrom).let { if (it == -1) text.length else it }
        return lineStart to lineEnd
    }

    private fun outdentLine(line: String): String {
        return when {
            line.startsWith("    ") -> line.drop(4)
            line.startsWith("\t") -> line.drop(1)
            line.startsWith("  ") -> line.drop(2)
            line.startsWith(" ") -> line.drop(1)
            else -> line
        }
    }

    private fun toggleHeading(value: TextFieldValue, level: Int): TextFieldValue {
        val text = value.text
        val range = targetLineRange(value)
        val block = text.substring(range.first, range.second)
        val lines = block.split('\n')
        val prefix = "${"#".repeat(level)} "
        val nonBlankLines = lines.filter { it.isNotBlank() }
        val removeHeading = nonBlankLines.isNotEmpty() && nonBlankLines.all { it.startsWith(prefix) }
        val transformed = lines.joinToString("\n") { line ->
            if (line.isBlank()) {
                line
            } else {
                val content = line.replace(Regex("^\\s{0,3}#{1,6}\\s+"), "").trimStart()
                if (removeHeading) content else "$prefix$content"
            }
        }
        return replaceTarget(value, range.first, range.second, transformed)
    }

    private fun toggleInlineWrapper(
        value: TextFieldValue,
        prefix: String,
        suffix: String
    ): TextFieldValue {
        val initialRange = if (value.selection.collapsed) {
            targetWordRange(value, prefix, suffix)
        } else {
            shrinkToVisibleContent(value.text, value.selection.min to value.selection.max)
        }
        val text = value.text
        val range = expandFormattedRange(text, initialRange, prefix, suffix)
        val target = text.substring(range.first, range.second)
        val unwrap = target.startsWith(prefix) && target.endsWith(suffix) && target.length >= prefix.length + suffix.length
        val transformed = if (unwrap) {
            target.removePrefix(prefix).removeSuffix(suffix)
        } else {
            "$prefix$target$suffix"
        }
        val nextText = text.replaceRange(range.first, range.second, transformed)
        val selection = if (unwrap) {
            TextRange(range.first, range.first + transformed.length)
        } else {
            TextRange(range.first + prefix.length, range.first + transformed.length - suffix.length)
        }
        return value.copy(text = nextText, selection = selection)
    }

    private fun targetWordRange(value: TextFieldValue, prefix: String, suffix: String): Pair<Int, Int> {
        val text = value.text
        val cursor = value.selection.start.coerceIn(0, text.length)
        if (text.isEmpty()) return 0 to 0
        val before = if (cursor > 0) text[cursor - 1] else null
        val after = text.getOrNull(cursor)
        val insideWord = before?.isWhitespace() == false || after?.isWhitespace() == false
        if (insideWord) {
            var start = cursor
            while (start > 0 && !text[start - 1].isWhitespace()) start--
            var end = cursor
            while (end < text.length && !text[end].isWhitespace()) end++
            return shrinkToVisibleContent(text, expandFormattedRange(text, start to end, prefix, suffix))
        }
        return shrinkToVisibleContent(text, targetLineRange(value))
    }

    private fun shrinkToVisibleContent(text: String, range: Pair<Int, Int>): Pair<Int, Int> {
        var start = range.first.coerceIn(0, text.length)
        var end = range.second.coerceIn(start, text.length)
        var changed: Boolean
        do {
            changed = false
            val lineHeadingEnd = headingMarkerEndAtLineStart(text, start)
            if (lineHeadingEnd != null && lineHeadingEnd <= end) {
                start = lineHeadingEnd
                changed = true
            }
            listOf("**", "~~", "*", "<u>").forEach { marker ->
                if (text.startsWith(marker, start) && start + marker.length <= end) {
                    start += marker.length
                    changed = true
                }
            }
            listOf("**", "~~", "*", "</u>").forEach { marker ->
                if (end - marker.length >= start && text.substring(end - marker.length, end) == marker) {
                    end -= marker.length
                    changed = true
                }
            }
        } while (changed && start <= end)
        return start to end
    }

    private fun headingMarkerEndAtLineStart(text: String, start: Int): Int? {
        if (start > 0 && text.getOrNull(start - 1) != '\n') return null
        var index = start
        var count = 0
        while (index < text.length && text[index] == '#' && count < 6) {
            index++
            count++
        }
        if (count == 0 || text.getOrNull(index)?.isWhitespace() != true) return null
        return index + 1
    }

    private fun expandFormattedRange(
        text: String,
        range: Pair<Int, Int>,
        prefix: String,
        suffix: String
    ): Pair<Int, Int> {
        val start = range.first
        val end = range.second
        val wrappedStart = start - prefix.length
        val wrappedEnd = end + suffix.length
        return if (
            wrappedStart >= 0 &&
            wrappedEnd <= text.length &&
            text.substring(wrappedStart, start) == prefix &&
            text.substring(end, wrappedEnd) == suffix
        ) {
            wrappedStart to wrappedEnd
        } else {
            range
        }
    }

    private fun replaceTarget(
        value: TextFieldValue,
        start: Int,
        end: Int,
        replacement: String,
        selectReplacement: Boolean = !value.selection.collapsed
    ): TextFieldValue {
        val nextText = value.text.replaceRange(start, end, replacement)
        val nextSelection = if (selectReplacement) {
            TextRange(start, start + replacement.length)
        } else {
            TextRange(start + replacement.length)
        }
        return value.copy(text = nextText, selection = nextSelection)
    }

    private fun TextFieldValue.normalizeMarkdownSelection(): TextFieldValue {
        val normalizedText = normalizeMarkdownMarkers(text)
        if (normalizedText == text) return this
        return copy(
            text = normalizedText,
            selection = TextRange(
                selection.start.coerceIn(0, normalizedText.length),
                selection.end.coerceIn(0, normalizedText.length)
            )
        )
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

    private fun formatErrorMessage(error: Throwable): String {
        return error.message?.takeIf(String::isNotBlank) ?: "请稍后重试。"
    }

    override fun onCleared() {
        marathonTickerJob?.cancel()
        saveJob?.cancel()
        completionJob?.cancel()
        super.onCleared()
    }

    private sealed interface PendingKnowledgeAction {
        data class Completion(
            val request: CompletionRequest,
            val context: String,
            val force: Boolean
        ) : PendingKnowledgeAction

        data class Manual(
            val request: AiActionRequest,
            val context: String,
            val selectedText: String?
        ) : PendingKnowledgeAction
    }

    class Factory(
        private val noteId: Long,
        private val contentType: NoteContentType,
        private val noteRepository: NoteRepository,
        private val aiRepository: AiRepository,
        private val settingsDataStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras: androidx.lifecycle.viewmodel.CreationExtras
        ): T {
            return NoteEditorViewModel(
                noteId = noteId,
                contentType = contentType,
                noteRepository = noteRepository,
                aiRepository = aiRepository,
                settingsDataStore = settingsDataStore,
                savedStateHandle = extras.createSavedStateHandle()
            ) as T
        }
    }

    private companion object {
        const val KEY_MARATHON_ACTIVE = "marathon_active"
        const val KEY_MARATHON_END_AT = "marathon_end_at"
        const val KEY_MARATHON_DURATION_MS = "marathon_duration_ms"
        const val KEY_MARATHON_LOCKED_PREFIX = "marathon_locked_prefix"
    }
}
