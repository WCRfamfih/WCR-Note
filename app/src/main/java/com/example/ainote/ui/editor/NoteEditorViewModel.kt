package com.example.ainote.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ainote.data.debug.AiDebugLogStore
import com.example.ainote.data.repository.AiRepository
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.data.settings.UserSettings
import com.example.ainote.domain.model.AiActionRequest
import com.example.ainote.domain.model.AiActionType
import com.example.ainote.domain.model.KnowledgeExtractionLaunchArgs
import com.example.ainote.domain.model.KnowledgeScopeSummary
import com.example.ainote.domain.model.Note
import com.example.ainote.domain.model.NoteContentType
import com.example.ainote.domain.usecase.BuildCompletionContextUseCase
import com.example.ainote.domain.usecase.RequestCompletionUseCase
import com.example.ainote.ui.components.normalizeMarkdownMarkers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val knowledgeScopeSummary: KnowledgeScopeSummary = KnowledgeScopeSummary()
)

class NoteEditorViewModel(
    private val noteId: Long,
    private val contentType: NoteContentType,
    private val noteRepository: NoteRepository,
    aiRepository: AiRepository,
    private val settingsDataStore: SettingsDataStore
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
    private var initialized = false
    private var showMarkdownMarkers = false

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
                showMarkdownMarkers = settings.showMarkdownMarkers
            }
        }
        if (contentType == NoteContentType.Note) {
            viewModelScope.launch {
                noteRepository.observeKnowledgeScopeSummary(noteId).collect { summary ->
                    _uiState.update { it.copy(knowledgeScopeSummary = summary) }
                }
            }
        }
    }

    fun updateTitle(value: String) {
        _uiState.update { it.copy(title = value, completion = CompletionUiState()) }
        scheduleSave()
    }

    fun updateContent(value: TextFieldValue) {
        val normalizedValue = if (showMarkdownMarkers) value else value.normalizeMarkdownSelection()
        val current = _uiState.value.content
        val contentChanged = current.text != normalizedValue.text
        
        // Record history if content actually changed
        if (current != normalizedValue) {
            undoStack.addLast(current)
            if (undoStack.size > 100) {
                undoStack.removeFirst()
            }
            redoStack.clear()
        }
        
        _uiState.update {
            it.copy(
                content = normalizedValue,
                wordCount = normalizedValue.text.length,
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

    fun retryCompletion() {
        dismissCompletion()
        scheduleCompletion(force = true)
    }

    fun applyMarkdownFormat(action: MarkdownFormatAction) {
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
                }.coerceAtMost(settings.maxCompletionLength.coerceAtLeast(24) * 4)
            )
            executeManualAction(request, selectedText)
        }
    }

    fun buildKnowledgeExtractionLaunch(settings: UserSettings): KnowledgeExtractionLaunchArgs {
        val state = _uiState.value
        return KnowledgeExtractionLaunchArgs(
            noteId = state.noteId,
            contentType = state.contentType,
            material = buildCompletionContext.buildMaterial(
                content = state.content.text,
                selectionStart = state.content.selection.start,
                selectionEnd = state.content.selection.end,
                useFullNoteContext = settings.useFullNoteContext,
                beforeLineCount = settings.completionBeforeLineCount,
                afterLineCount = settings.completionAfterLineCount
            )
        )
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
            contentType = note.contentType,
            createdAt = note.createdAt,
            pinned = note.pinned,
            title = note.title,
            content = TextFieldValue(note.content, selection = TextRange(note.content.length)),
            wordCount = note.content.length,
            isLoaded = true,
            showKnowledgeButton = note.contentType == NoteContentType.Note,
            knowledgeScopeSummary = _uiState.value.knowledgeScopeSummary
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
            pinned = state.pinned,
            contentType = state.contentType
        )
    }

    private fun scheduleCompletion(force: Boolean = false, contentChanged: Boolean = true) {
        completionJob?.cancel()
        completionJob = viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            if (!canRequestCompletion(settings, force, contentChanged)) {
                if (force) {
                    AiDebugLogStore.add("Completion skipped", "Manual completion skipped because the cursor is not after existing body text.")
                    _uiState.update {
                        it.copy(completion = CompletionUiState(errorMessage = "请把光标放在正文已有内容之后再补全。"))
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
            _uiState.update { it.copy(completion = CompletionUiState(loading = true)) }
            runCatching { requestCompletion(request, force = force) }
                .onSuccess { result ->
                    val suggestion = result.text.takeIf(String::isNotBlank)
                    _uiState.update {
                        it.copy(
                            completion = if (suggestion == null) {
                                AiDebugLogStore.add("Completion empty", "AI returned no usable completion text after filtering.")
                                CompletionUiState(errorMessage = "AI 没有返回可用的补全文字，请重试。")
                            } else {
                                CompletionUiState(suggestion = suggestion)
                            }
                        )
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
    }

    private fun canRequestCompletion(settings: UserSettings, force: Boolean, contentChanged: Boolean): Boolean {
        val state = _uiState.value
        val cursor = state.content.selection.start
        if (force) {
            return state.content.selection.collapsed &&
                cursor > 0 &&
                state.content.text.take(cursor).isNotBlank()
        }
        return (settings.autoCompletionEnabled || force) &&
            (!settings.autoCompleteOnlyOnContentChange || contentChanged) &&
            state.content.selection.collapsed &&
            cursor > 0 &&
            state.content.text.take(cursor).isNotBlank() &&
            (!settings.skipBlankLineAutoCompletion || !isCurrentLineBlank(state.content.text, cursor)) &&
            (!settings.preferChineseAutoCompletion || containsChinese(state.content.text.take(cursor))) &&
            state.completion.suggestion == null
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
        val lineStart = if (start == 0) {
            0
        } else {
            text.lastIndexOf('\n', start - 1).let { if (it == -1) 0 else it + 1 }
        }
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
            TextRange(
                range.first + prefix.length,
                range.first + transformed.length - suffix.length
            )
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

    private fun formatErrorMessage(it: Throwable): String {
        return it.message?.takeIf(String::isNotBlank) ?: "\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002"
    }

    class Factory(
        private val noteId: Long,
        private val contentType: NoteContentType,
        private val noteRepository: NoteRepository,
        private val aiRepository: AiRepository,
        private val settingsDataStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NoteEditorViewModel(noteId, contentType, noteRepository, aiRepository, settingsDataStore) as T
        }
    }
}
