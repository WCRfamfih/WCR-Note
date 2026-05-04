package com.example.ainote.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore by preferencesDataStore(name = "user_settings")

class SettingsDataStore(private val context: Context) {
    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        val legacy = legacyPresetFromPrefs(prefs)
        val presets = readAiServicePresets(prefs, legacy)
        val defaultUsageId = if (presets.any { it.id == legacy.id } && !legacy.shouldUseFake()) legacy.id else "fake"
        UserSettings(
            apiProvider = prefs[Keys.ApiProvider] ?: "Fake",
            apiKey = prefs[Keys.ApiKey] ?: "",
            apiBaseUrl = prefs[Keys.ApiBaseUrl] ?: "https://api.openai.com/v1/chat/completions",
            apiModel = prefs[Keys.ApiModel] ?: "gpt-4o-mini",
            aiServicePresets = presets,
            autoCompletionPresetId = prefs[Keys.AutoCompletionPresetId] ?: defaultUsageId,
            manualCompletionPresetId = prefs[Keys.ManualCompletionPresetId] ?: defaultUsageId,
            aiToolPresetId = prefs[Keys.AiToolPresetId] ?: defaultUsageId,
            autoCompletionEnabled = prefs[Keys.AutoCompletionEnabled] ?: true,
            preferChineseAutoCompletion = prefs[Keys.PreferChineseAutoCompletion] ?: true,
            skipBlankLineAutoCompletion = prefs[Keys.SkipBlankLineAutoCompletion] ?: true,
            autoCompleteOnlyOnContentChange = prefs[Keys.AutoCompleteOnlyOnContentChange] ?: true,
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
            knowledgeBaseEnabled = prefs[Keys.KnowledgeBaseEnabled] ?: false,
            documentDirectoryUri = prefs[Keys.DocumentDirectoryUri] ?: "",
            noteSortField = NoteSortField.from(prefs[Keys.NoteSortField] ?: NoteSortField.Time.name),
            noteSortDirection = NoteSortDirection.from(prefs[Keys.NoteSortDirection] ?: NoteSortDirection.Descending.name)
        )
    }

    val folders: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        readFolderList(prefs[Keys.Folders])
    }

    val knowledgeFolders: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        readFolderList(prefs[Keys.KnowledgeFolders])
    }

    private fun readFolderList(raw: String?): List<String> {
        return raw
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

    suspend fun updateAiServicePresets(value: List<AiServicePreset>) {
        context.settingsDataStore.edit { prefs ->
            val normalized = value.ifEmpty { defaultAiServicePresets() }.distinctBy { it.id }
            prefs[Keys.AiServicePresetsJson] = normalized.toJson()
            val ids = normalized.map { it.id }.toSet()
            if (prefs[Keys.AutoCompletionPresetId] !in ids) prefs[Keys.AutoCompletionPresetId] = normalized.first().id
            if (prefs[Keys.ManualCompletionPresetId] !in ids) prefs[Keys.ManualCompletionPresetId] = normalized.first().id
            if (prefs[Keys.AiToolPresetId] !in ids) prefs[Keys.AiToolPresetId] = normalized.first().id
        }
    }

    suspend fun updateAutoCompletionPresetId(value: String) = updateString(Keys.AutoCompletionPresetId, value)
    suspend fun updateManualCompletionPresetId(value: String) = updateString(Keys.ManualCompletionPresetId, value)
    suspend fun updateAiToolPresetId(value: String) = updateString(Keys.AiToolPresetId, value)

    suspend fun updateAutoCompletionEnabled(value: Boolean) = updateBoolean(Keys.AutoCompletionEnabled, value)
    suspend fun updatePreferChineseAutoCompletion(value: Boolean) = updateBoolean(Keys.PreferChineseAutoCompletion, value)
    suspend fun updateSkipBlankLineAutoCompletion(value: Boolean) = updateBoolean(Keys.SkipBlankLineAutoCompletion, value)
    suspend fun updateAutoCompleteOnlyOnContentChange(value: Boolean) = updateBoolean(Keys.AutoCompleteOnlyOnContentChange, value)
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
    suspend fun updateKnowledgeBaseEnabled(value: Boolean) = updateBoolean(Keys.KnowledgeBaseEnabled, value)
    suspend fun updateDocumentDirectoryUri(value: String) = updateString(Keys.DocumentDirectoryUri, value)
    suspend fun updateNoteSortField(value: NoteSortField) = updateString(Keys.NoteSortField, value.name)
    suspend fun updateNoteSortDirection(value: NoteSortDirection) = updateString(Keys.NoteSortDirection, value.name)
    suspend fun addFolder(value: String) {
        addFolder(Keys.Folders, value)
    }

    suspend fun addKnowledgeFolder(value: String) {
        addFolder(Keys.KnowledgeFolders, value)
    }

    suspend fun removeFolder(value: String) {
        removeFolder(Keys.Folders, value)
    }

    suspend fun removeKnowledgeFolder(value: String) {
        removeFolder(Keys.KnowledgeFolders, value)
    }

    private suspend fun addFolder(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        val folderName = value.trim().replace('\n', ' ')
        if (folderName.isBlank()) return
        context.settingsDataStore.edit { prefs ->
            val current = readFolderList(prefs[key])
            prefs[key] = (current + folderName).distinct().joinToString("\n")
        }
    }

    private suspend fun removeFolder(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        val folderName = value.trim()
        context.settingsDataStore.edit { prefs ->
            val current = readFolderList(prefs[key]).filter { it != folderName }
            prefs[key] = current.distinct().joinToString("\n")
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

    private fun legacyPresetFromPrefs(prefs: Preferences): AiServicePreset {
        return AiServicePreset(
            id = "legacy",
            label = (prefs[Keys.ApiProvider] ?: "Current").ifBlank { "Current" },
            provider = prefs[Keys.ApiProvider] ?: "Fake",
            baseUrl = prefs[Keys.ApiBaseUrl] ?: "https://api.openai.com/v1/chat/completions",
            model = prefs[Keys.ApiModel] ?: "gpt-4o-mini",
            apiKey = prefs[Keys.ApiKey] ?: ""
        )
    }

    private fun readAiServicePresets(prefs: Preferences, legacy: AiServicePreset): List<AiServicePreset> {
        val stored = prefs[Keys.AiServicePresetsJson]
        val parsed = stored?.let { json ->
            runCatching {
                val array = JSONArray(json)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val id = item.optString("id")
                        if (id.isNotBlank()) {
                            add(
                                AiServicePreset(
                                    id = id,
                                    label = item.optString("label").ifBlank { item.optString("provider").ifBlank { id } },
                                    provider = item.optString("provider").ifBlank { "OpenAI" },
                                    baseUrl = item.optString("baseUrl"),
                                    model = item.optString("model"),
                                    apiKey = item.optString("apiKey")
                                )
                            )
                        }
                    }
                }
            }.getOrNull()
        }.orEmpty()
        if (parsed.isNotEmpty()) return parsed.distinctBy { it.id }

        val defaults = defaultAiServicePresets()
        return if (legacy.shouldUseFake()) defaults else (listOf(legacy) + defaults).distinctBy { it.id }
    }

    private fun List<AiServicePreset>.toJson(): String {
        val array = JSONArray()
        forEach { preset ->
            array.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("label", preset.label)
                    .put("provider", preset.provider)
                    .put("baseUrl", preset.baseUrl)
                    .put("model", preset.model)
                    .put("apiKey", preset.apiKey)
            )
        }
        return array.toString()
    }

    private object Keys {
        val ApiProvider = stringPreferencesKey("api_provider")
        val ApiKey = stringPreferencesKey("api_key")
        val ApiBaseUrl = stringPreferencesKey("api_base_url")
        val ApiModel = stringPreferencesKey("api_model")
        val AiServicePresetsJson = stringPreferencesKey("ai_service_presets_json")
        val AutoCompletionPresetId = stringPreferencesKey("auto_completion_preset_id")
        val ManualCompletionPresetId = stringPreferencesKey("manual_completion_preset_id")
        val AiToolPresetId = stringPreferencesKey("ai_tool_preset_id")
        val AutoCompletionEnabled = booleanPreferencesKey("auto_completion_enabled")
        val PreferChineseAutoCompletion = booleanPreferencesKey("prefer_chinese_auto_completion")
        val SkipBlankLineAutoCompletion = booleanPreferencesKey("skip_blank_line_auto_completion")
        val AutoCompleteOnlyOnContentChange = booleanPreferencesKey("auto_complete_only_on_content_change")
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
        val KnowledgeBaseEnabled = booleanPreferencesKey("knowledge_base_enabled")
        val DocumentDirectoryUri = stringPreferencesKey("document_directory_uri")
        val Folders = stringPreferencesKey("folders")
        val KnowledgeFolders = stringPreferencesKey("knowledge_folders")
        val NoteSortField = stringPreferencesKey("note_sort_field")
        val NoteSortDirection = stringPreferencesKey("note_sort_direction")
    }
}
