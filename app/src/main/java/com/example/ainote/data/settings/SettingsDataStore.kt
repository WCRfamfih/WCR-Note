package com.example.ainote.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "user_settings")

class SettingsDataStore(private val context: Context) {
    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            apiProvider = prefs[Keys.ApiProvider] ?: "Fake",
            apiKey = prefs[Keys.ApiKey] ?: "",
            apiBaseUrl = prefs[Keys.ApiBaseUrl] ?: "https://api.openai.com/v1/chat/completions",
            apiModel = prefs[Keys.ApiModel] ?: "gpt-4o-mini",
            autoCompletionEnabled = prefs[Keys.AutoCompletionEnabled] ?: true,
            preferChineseAutoCompletion = prefs[Keys.PreferChineseAutoCompletion] ?: true,
            skipBlankLineAutoCompletion = prefs[Keys.SkipBlankLineAutoCompletion] ?: true,
            completionDelayMs = prefs[Keys.CompletionDelayMs] ?: 700,
            maxCompletionLength = prefs[Keys.MaxCompletionLength] ?: 30,
            useFullNoteContext = prefs[Keys.UseFullNoteContext] ?: false,
            completionBeforeLineCount = prefs[Keys.CompletionBeforeLineCount] ?: 5,
            completionAfterLineCount = prefs[Keys.CompletionAfterLineCount] ?: 2,
            themeMode = ThemeMode.from(prefs[Keys.ThemeMode] ?: ThemeMode.System.name),
            accentColorPreset = AccentColorPreset.from(prefs[Keys.AccentColorPreset] ?: AccentColorPreset.Violet.name),
            editorTextSizeSp = prefs[Keys.EditorTextSizeSp] ?: 18,
            showMarkdownMarkers = prefs[Keys.ShowMarkdownMarkers] ?: false,
            showCompletionErrorToast = prefs[Keys.ShowCompletionErrorToast] ?: true,
            documentDirectoryUri = prefs[Keys.DocumentDirectoryUri] ?: "",
            noteSortField = NoteSortField.from(prefs[Keys.NoteSortField] ?: NoteSortField.Time.name),
            noteSortDirection = NoteSortDirection.from(prefs[Keys.NoteSortDirection] ?: NoteSortDirection.Descending.name)
        )
    }

    val folders: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.Folders]
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()
    }

    suspend fun updateApiProvider(value: String) = updateString(Keys.ApiProvider, value)
    suspend fun updateApiKey(value: String) = updateString(Keys.ApiKey, value)
    suspend fun updateApiBaseUrl(value: String) = updateString(Keys.ApiBaseUrl, value)
    suspend fun updateApiModel(value: String) = updateString(Keys.ApiModel, value)
    suspend fun applyProviderPreset(preset: AiProviderPreset) {
        context.settingsDataStore.edit {
            it[Keys.ApiProvider] = preset.provider
            it[Keys.ApiBaseUrl] = preset.baseUrl
            it[Keys.ApiModel] = preset.model
        }
    }

    suspend fun updateAutoCompletionEnabled(value: Boolean) = updateBoolean(Keys.AutoCompletionEnabled, value)
    suspend fun updatePreferChineseAutoCompletion(value: Boolean) = updateBoolean(Keys.PreferChineseAutoCompletion, value)
    suspend fun updateSkipBlankLineAutoCompletion(value: Boolean) = updateBoolean(Keys.SkipBlankLineAutoCompletion, value)
    suspend fun updateCompletionDelayMs(value: Long) = updateLong(Keys.CompletionDelayMs, value)
    suspend fun updateMaxCompletionLength(value: Int) = updateInt(Keys.MaxCompletionLength, value)
    suspend fun updateUseFullNoteContext(value: Boolean) = updateBoolean(Keys.UseFullNoteContext, value)
    suspend fun updateCompletionBeforeLineCount(value: Int) = updateInt(Keys.CompletionBeforeLineCount, value)
    suspend fun updateCompletionAfterLineCount(value: Int) = updateInt(Keys.CompletionAfterLineCount, value)
    suspend fun updateThemeMode(value: ThemeMode) = updateString(Keys.ThemeMode, value.name)
    suspend fun updateAccentColorPreset(value: AccentColorPreset) = updateString(Keys.AccentColorPreset, value.name)
    suspend fun updateEditorTextSizeSp(value: Int) = updateInt(Keys.EditorTextSizeSp, value)
    suspend fun updateShowMarkdownMarkers(value: Boolean) = updateBoolean(Keys.ShowMarkdownMarkers, value)
    suspend fun updateShowCompletionErrorToast(value: Boolean) = updateBoolean(Keys.ShowCompletionErrorToast, value)
    suspend fun updateDocumentDirectoryUri(value: String) = updateString(Keys.DocumentDirectoryUri, value)
    suspend fun updateNoteSortField(value: NoteSortField) = updateString(Keys.NoteSortField, value.name)
    suspend fun updateNoteSortDirection(value: NoteSortDirection) = updateString(Keys.NoteSortDirection, value.name)
    suspend fun addFolder(value: String) {
        val folderName = value.trim().replace('\n', ' ')
        if (folderName.isBlank()) return
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.Folders]
                ?.split('\n')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            prefs[Keys.Folders] = (current + folderName).distinct().joinToString("\n")
        }
    }

    suspend fun removeFolder(value: String) {
        val folderName = value.trim()
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.Folders]
                ?.split('\n')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() && it != folderName }
                .orEmpty()
            prefs[Keys.Folders] = current.distinct().joinToString("\n")
        }
    }

    private suspend fun updateString(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private suspend fun updateBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private suspend fun updateLong(key: androidx.datastore.preferences.core.Preferences.Key<Long>, value: Long) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private suspend fun updateInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Int) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private object Keys {
        val ApiProvider = stringPreferencesKey("api_provider")
        val ApiKey = stringPreferencesKey("api_key")
        val ApiBaseUrl = stringPreferencesKey("api_base_url")
        val ApiModel = stringPreferencesKey("api_model")
        val AutoCompletionEnabled = booleanPreferencesKey("auto_completion_enabled")
        val PreferChineseAutoCompletion = booleanPreferencesKey("prefer_chinese_auto_completion")
        val SkipBlankLineAutoCompletion = booleanPreferencesKey("skip_blank_line_auto_completion")
        val CompletionDelayMs = longPreferencesKey("completion_delay_ms")
        val MaxCompletionLength = intPreferencesKey("max_completion_length")
        val UseFullNoteContext = booleanPreferencesKey("use_full_note_context")
        val CompletionBeforeLineCount = intPreferencesKey("completion_before_line_count")
        val CompletionAfterLineCount = intPreferencesKey("completion_after_line_count")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val AccentColorPreset = stringPreferencesKey("accent_color_preset")
        val EditorTextSizeSp = intPreferencesKey("editor_text_size_sp")
        val ShowMarkdownMarkers = booleanPreferencesKey("show_markdown_markers")
        val ShowCompletionErrorToast = booleanPreferencesKey("show_completion_error_toast")
        val DocumentDirectoryUri = stringPreferencesKey("document_directory_uri")
        val Folders = stringPreferencesKey("folders")
        val NoteSortField = stringPreferencesKey("note_sort_field")
        val NoteSortDirection = stringPreferencesKey("note_sort_direction")
    }
}
