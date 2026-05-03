package com.example.ainote.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ainote.data.repository.AiRepository
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.AccentColorPreset
import com.example.ainote.data.settings.AiProviderPreset
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
    fun updateShowMarkdownMarkers(value: Boolean) = launch { dataStore.updateShowMarkdownMarkers(value) }
    fun updateShowCompletionErrorToast(value: Boolean) = launch { dataStore.updateShowCompletionErrorToast(value) }
    fun updateDocumentDirectoryUri(value: String) = launch {
        dataStore.updateDocumentDirectoryUri(value)
        if (value.isBlank()) {
            _documentStatus.value = null
        } else {
            val imported = noteRepository?.importBackupsFromDirectory(value) ?: 0
            _documentStatus.value = "已选择目录，导入 $imported 篇备份笔记。"
        }
    }
    fun updateNoteSortField(value: NoteSortField) = launch { dataStore.updateNoteSortField(value) }
    fun updateNoteSortDirection(value: NoteSortDirection) = launch { dataStore.updateNoteSortDirection(value) }

    fun testConnection() {
        _testStatus.value = "\u6b63\u5728\u6d4b\u8bd5\u8fde\u63a5..."
        viewModelScope.launch {
            val result = aiRepository.testConnection()
            _testStatus.value = result.fold(
                onSuccess = { "\u8fde\u63a5\u6210\u529f\uff1a$it" },
                onFailure = { "\u8fde\u63a5\u5931\u8d25\uff1a${it.message ?: "\u672a\u77e5\u9519\u8bef"}" }
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
