package com.example.ainote.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ainote.data.repository.AiRepository
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.AccentColorPreset
import com.example.ainote.data.settings.AiProviderPreset
import com.example.ainote.data.settings.AiServicePreset
import com.example.ainote.data.settings.EditorFontPreset
import com.example.ainote.data.settings.NoteSortDirection
import com.example.ainote.data.settings.NoteSortField
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.data.settings.ThemeMode
import com.example.ainote.data.settings.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val dataStore: SettingsDataStore,
    private val aiRepository: AiRepository,
    private val noteRepository: NoteRepository? = null
) : ViewModel() {
    val settings: StateFlow<UserSettings> = dataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _testStatus = MutableStateFlow<String?>(null)
    val testStatus: StateFlow<String?> = _testStatus

    private val _documentStatus = MutableStateFlow<String?>(null)
    val documentStatus: StateFlow<String?> = _documentStatus

    fun updateApiProvider(value: String) = launch { dataStore.updateApiProvider(value) }
    fun updateApiKey(value: String) = launch { dataStore.updateApiKey(value) }
    fun updateApiBaseUrl(value: String) = launch { dataStore.updateApiBaseUrl(value) }
    fun updateApiModel(value: String) = launch { dataStore.updateApiModel(value) }

    fun applyProviderPreset(preset: AiProviderPreset) = launch {
        dataStore.applyProviderPreset(preset)
        _testStatus.value = null
    }

    fun updateAiServicePreset(preset: AiServicePreset) = launch {
        val current = settings.value.aiServicePresets
        dataStore.updateAiServicePresets(current.map { if (it.id == preset.id) preset else it })
        _testStatus.value = null
    }

    fun addAiServicePreset(preset: AiServicePreset) = launch {
        dataStore.updateAiServicePresets(settings.value.aiServicePresets + preset)
        _testStatus.value = null
    }

    fun removeAiServicePreset(id: String) = launch {
        val current = settings.value.aiServicePresets
        if (current.size <= 1) return@launch
        dataStore.updateAiServicePresets(current.filterNot { it.id == id })
        _testStatus.value = null
    }

    fun updateAutoCompletionPresetId(value: String) = launch { dataStore.updateAutoCompletionPresetId(value) }
    fun updateManualCompletionPresetId(value: String) = launch { dataStore.updateManualCompletionPresetId(value) }
    fun updateAiToolPresetId(value: String) = launch { dataStore.updateAiToolPresetId(value) }

    fun updateAutoCompletionEnabled(value: Boolean) = launch { dataStore.updateAutoCompletionEnabled(value) }
    fun updatePreferChineseAutoCompletion(value: Boolean) = launch { dataStore.updatePreferChineseAutoCompletion(value) }
    fun updateSkipBlankLineAutoCompletion(value: Boolean) = launch { dataStore.updateSkipBlankLineAutoCompletion(value) }
    fun updateAutoCompleteOnlyOnContentChange(value: Boolean) = launch { dataStore.updateAutoCompleteOnlyOnContentChange(value) }
    fun updateCompletionDelayMs(value: Long) = launch { dataStore.updateCompletionDelayMs(value) }
    fun updateMaxCompletionLength(value: Int) = launch { dataStore.updateMaxCompletionLength(value) }
    fun updateUseFullNoteContext(value: Boolean) = launch { dataStore.updateUseFullNoteContext(value) }
    fun updateCompletionBeforeLineCount(value: Int) = launch { dataStore.updateCompletionBeforeLineCount(value) }
    fun updateCompletionAfterLineCount(value: Int) = launch { dataStore.updateCompletionAfterLineCount(value) }
    fun updateThemeMode(value: ThemeMode) = launch { dataStore.updateThemeMode(value) }
    fun updateAccentColorPreset(value: AccentColorPreset) = launch { dataStore.updateAccentColorPreset(value) }
    fun updateEditorTextSizeSp(value: Int) = launch { dataStore.updateEditorTextSizeSp(value) }
    fun updateEditorLineSpacingPercent(value: Int) = launch { dataStore.updateEditorLineSpacingPercent(value) }
    fun updateEditorLetterSpacingTenthSp(value: Int) = launch { dataStore.updateEditorLetterSpacingTenthSp(value) }
    fun updateEditorFontPreset(value: EditorFontPreset) = launch { dataStore.updateEditorFontPreset(value) }
    fun updateCustomEditorFont(uri: String, label: String) = launch { dataStore.updateCustomEditorFont(uri, label) }
    fun clearCustomEditorFont() = launch { dataStore.clearCustomEditorFont() }
    fun updateShowMarkdownMarkers(value: Boolean) = launch { dataStore.updateShowMarkdownMarkers(value) }
    fun updateShowCompletionErrorToast(value: Boolean) = launch { dataStore.updateShowCompletionErrorToast(value) }
    fun updateKnowledgeBaseEnabled(value: Boolean) = launch { dataStore.updateKnowledgeBaseEnabled(value) }
    fun updateKnowledgeSendLimit(value: Int) = launch { dataStore.updateKnowledgeSendLimit(value) }
    fun updateNoteSortField(value: NoteSortField) = launch { dataStore.updateNoteSortField(value) }
    fun updateNoteSortDirection(value: NoteSortDirection) = launch { dataStore.updateNoteSortDirection(value) }

    fun updateDocumentDirectoryUri(value: String) = launch {
        dataStore.updateDocumentDirectoryUri(value)
        if (value.isBlank()) {
            _documentStatus.value = null
        } else {
            val imported = noteRepository?.importBackupsFromDirectory(value) ?: 0
            _documentStatus.value = "已选择目录，并导入 $imported 篇备份笔记。"
        }
    }

    fun testConnection(presetId: String? = null) {
        _testStatus.value = "正在测试连接..."
        viewModelScope.launch {
            val result = aiRepository.testConnection(presetId)
            _testStatus.value = result.fold(
                onSuccess = { "连接成功：$it" },
                onFailure = { "连接失败：${it.message ?: "未知错误"}" }
            )
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    class Factory(
        private val dataStore: SettingsDataStore,
        private val aiRepository: AiRepository,
        private val noteRepository: NoteRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(dataStore, aiRepository, noteRepository) as T
        }
    }
}
