package com.example.ainote.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
            accentBrightnessOffset = prefs[Keys.AccentBrightnessOffset] ?: 0f,
            accentSaturationFactor = prefs[Keys.AccentSaturationFactor] ?: 1f,
            editorTextSizeSp = prefs[Keys.EditorTextSizeSp] ?: 18,
            editorLineSpacingPercent = prefs[Keys.EditorLineSpacingPercent] ?: 140,
            editorLetterSpacingTenthSp = prefs[Keys.EditorLetterSpacingTenthSp] ?: 0,
            editorPaginationEnabled = prefs[Keys.EditorPaginationEnabled] ?: false,
            editorFontPreset = EditorFontPreset.from(prefs[Keys.EditorFontPreset]),
            customEditorFontUri = prefs[Keys.CustomEditorFontUri] ?: "",
            customEditorFontLabel = prefs[Keys.CustomEditorFontLabel] ?: "",
            showMarkdownMarkers = prefs[Keys.ShowMarkdownMarkers] ?: false,
            showCompletionErrorToast = prefs[Keys.ShowCompletionErrorToast] ?: true,
            knowledgeBaseEnabled = prefs[Keys.KnowledgeBaseEnabled] ?: false,
            knowledgeSendLimit = prefs[Keys.KnowledgeSendLimit] ?: 5,
            experimentalMarathonEnabled = prefs[Keys.ExperimentalMarathonEnabled] ?: false,
            marathonDurationMinutes = prefs[Keys.MarathonDurationMinutes] ?: 25f,
            marathonDisableAi = prefs[Keys.MarathonDisableAi] ?: false,
            recentEditableSettingKeys = readRecentEditableSettingKeys(prefs[Keys.RecentEditableSettings]),
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

    private fun readRecentEditableSettingKeys(raw: String?): List<String> {
        return raw
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(5)
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

    suspend fun updateAutoCompletionEnabled(value: Boolean) = updateTrackedBoolean(Keys.AutoCompletionEnabled, value, EditableSettingKeys.AutoCompletionEnabled)
    suspend fun updatePreferChineseAutoCompletion(value: Boolean) = updateTrackedBoolean(Keys.PreferChineseAutoCompletion, value, EditableSettingKeys.PreferChineseAutoCompletion)
    suspend fun updateSkipBlankLineAutoCompletion(value: Boolean) = updateTrackedBoolean(Keys.SkipBlankLineAutoCompletion, value, EditableSettingKeys.SkipBlankLineAutoCompletion)
    suspend fun updateAutoCompleteOnlyOnContentChange(value: Boolean) = updateTrackedBoolean(Keys.AutoCompleteOnlyOnContentChange, value, EditableSettingKeys.AutoCompleteOnlyOnContentChange)
    suspend fun updateCompletionDelayMs(value: Long) = updateTrackedLong(Keys.CompletionDelayMs, value, EditableSettingKeys.CompletionDelayMs)
    suspend fun updateMaxCompletionLength(value: Int) = updateTrackedInt(Keys.MaxCompletionLength, value, EditableSettingKeys.MaxCompletionLength)
    suspend fun updateUseFullNoteContext(value: Boolean) = updateTrackedBoolean(Keys.UseFullNoteContext, value, EditableSettingKeys.UseFullNoteContext)
    suspend fun updateCompletionBeforeLineCount(value: Int) = updateTrackedInt(Keys.CompletionBeforeLineCount, value, EditableSettingKeys.CompletionBeforeLineCount)
    suspend fun updateCompletionAfterLineCount(value: Int) = updateTrackedInt(Keys.CompletionAfterLineCount, value, EditableSettingKeys.CompletionAfterLineCount)
    suspend fun updateThemeMode(value: ThemeMode) = updateString(Keys.ThemeMode, value.name)
    suspend fun updateAccentColorPreset(value: AccentColorPreset) = updateString(Keys.AccentColorPreset, value.name)
    suspend fun updateAccentBrightnessOffset(value: Float) = updateTrackedFloat(Keys.AccentBrightnessOffset, value.coerceIn(-0.25f, 0.25f), EditableSettingKeys.AccentBrightnessOffset)
    suspend fun updateAccentSaturationFactor(value: Float) = updateTrackedFloat(Keys.AccentSaturationFactor, value.coerceIn(0.5f, 1.5f), EditableSettingKeys.AccentSaturationFactor)
    suspend fun updateEditorTextSizeSp(value: Int) = updateTrackedInt(Keys.EditorTextSizeSp, value, EditableSettingKeys.EditorTextSizeSp)
    suspend fun updateEditorLineSpacingPercent(value: Int) = updateTrackedInt(Keys.EditorLineSpacingPercent, value, EditableSettingKeys.EditorLineSpacingPercent)
    suspend fun updateEditorLetterSpacingTenthSp(value: Int) = updateTrackedInt(Keys.EditorLetterSpacingTenthSp, value, EditableSettingKeys.EditorLetterSpacingTenthSp)
    suspend fun updateEditorPaginationEnabled(value: Boolean) = updateTrackedBoolean(Keys.EditorPaginationEnabled, value, EditableSettingKeys.EditorPaginationEnabled)
    suspend fun updateEditorFontPreset(value: EditorFontPreset) = updateString(Keys.EditorFontPreset, value.name)
    suspend fun updateCustomEditorFont(uri: String, label: String) {
        context.settingsDataStore.edit {
            it[Keys.CustomEditorFontUri] = uri
            it[Keys.CustomEditorFontLabel] = label
            it[Keys.EditorFontPreset] = EditorFontPreset.Custom.name
        }
    }
    suspend fun clearCustomEditorFont() {
        context.settingsDataStore.edit {
            it[Keys.CustomEditorFontUri] = ""
            it[Keys.CustomEditorFontLabel] = ""
            if ((it[Keys.EditorFontPreset] ?: EditorFontPreset.System.name) == EditorFontPreset.Custom.name) {
                it[Keys.EditorFontPreset] = EditorFontPreset.System.name
            }
        }
    }
    suspend fun updateShowMarkdownMarkers(value: Boolean) = updateTrackedBoolean(Keys.ShowMarkdownMarkers, value, EditableSettingKeys.ShowMarkdownMarkers)
    suspend fun updateShowCompletionErrorToast(value: Boolean) = updateTrackedBoolean(Keys.ShowCompletionErrorToast, value, EditableSettingKeys.ShowCompletionErrorToast)
    suspend fun updateKnowledgeBaseEnabled(value: Boolean) = updateTrackedBoolean(Keys.KnowledgeBaseEnabled, value, EditableSettingKeys.KnowledgeBaseEnabled)
    suspend fun updateKnowledgeSendLimit(value: Int) = updateTrackedInt(Keys.KnowledgeSendLimit, value.coerceAtLeast(1), EditableSettingKeys.KnowledgeSendLimit)
    suspend fun updateExperimentalMarathonEnabled(value: Boolean) = updateTrackedBoolean(Keys.ExperimentalMarathonEnabled, value, EditableSettingKeys.ExperimentalMarathonEnabled)
    suspend fun updateMarathonDurationMinutes(value: Float) = updateTrackedFloat(Keys.MarathonDurationMinutes, value.coerceAtLeast(0.1f), EditableSettingKeys.MarathonDurationMinutes)
    suspend fun updateMarathonDisableAi(value: Boolean) = updateTrackedBoolean(Keys.MarathonDisableAi, value, EditableSettingKeys.MarathonDisableAi)
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

    private suspend fun updateTrackedBoolean(
        key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
        value: Boolean,
        settingKey: String
    ) {
        context.settingsDataStore.edit {
            it[key] = value
            it[Keys.RecentEditableSettings] = reorderRecentEditableSettings(it[Keys.RecentEditableSettings], settingKey)
        }
    }

    private suspend fun updateLong(key: androidx.datastore.preferences.core.Preferences.Key<Long>, value: Long) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private suspend fun updateTrackedLong(
        key: androidx.datastore.preferences.core.Preferences.Key<Long>,
        value: Long,
        settingKey: String
    ) {
        context.settingsDataStore.edit {
            it[key] = value
            it[Keys.RecentEditableSettings] = reorderRecentEditableSettings(it[Keys.RecentEditableSettings], settingKey)
        }
    }

    private suspend fun updateInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Int) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private suspend fun updateTrackedFloat(
        key: androidx.datastore.preferences.core.Preferences.Key<Float>,
        value: Float,
        settingKey: String
    ) {
        context.settingsDataStore.edit {
            it[key] = value
            it[Keys.RecentEditableSettings] = reorderRecentEditableSettings(it[Keys.RecentEditableSettings], settingKey)
        }
    }

    private suspend fun updateTrackedInt(
        key: androidx.datastore.preferences.core.Preferences.Key<Int>,
        value: Int,
        settingKey: String
    ) {
        context.settingsDataStore.edit {
            it[key] = value
            it[Keys.RecentEditableSettings] = reorderRecentEditableSettings(it[Keys.RecentEditableSettings], settingKey)
        }
    }

    private fun reorderRecentEditableSettings(raw: String?, settingKey: String): String {
        val existing = readRecentEditableSettingKeys(raw).filterNot { it == settingKey }
        return (listOf(settingKey) + existing).take(5).joinToString("\n")
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
        val AccentBrightnessOffset = floatPreferencesKey("accent_brightness_offset")
        val AccentSaturationFactor = floatPreferencesKey("accent_saturation_factor")
        val EditorTextSizeSp = intPreferencesKey("editor_text_size_sp")
        val EditorLineSpacingPercent = intPreferencesKey("editor_line_spacing_percent")
        val EditorLetterSpacingTenthSp = intPreferencesKey("editor_letter_spacing_tenth_sp")
        val EditorPaginationEnabled = booleanPreferencesKey("editor_pagination_enabled")
        val EditorFontPreset = stringPreferencesKey("editor_font_preset")
        val CustomEditorFontUri = stringPreferencesKey("custom_editor_font_uri")
        val CustomEditorFontLabel = stringPreferencesKey("custom_editor_font_label")
        val ShowMarkdownMarkers = booleanPreferencesKey("show_markdown_markers")
        val ShowCompletionErrorToast = booleanPreferencesKey("show_completion_error_toast")
        val KnowledgeBaseEnabled = booleanPreferencesKey("knowledge_base_enabled")
        val KnowledgeSendLimit = intPreferencesKey("knowledge_send_limit")
        val ExperimentalMarathonEnabled = booleanPreferencesKey("experimental_marathon_enabled")
        val MarathonDurationMinutes = floatPreferencesKey("marathon_duration_minutes")
        val MarathonDisableAi = booleanPreferencesKey("marathon_disable_ai")
        val RecentEditableSettings = stringPreferencesKey("recent_editable_settings")
        val DocumentDirectoryUri = stringPreferencesKey("document_directory_uri")
        val Folders = stringPreferencesKey("folders")
        val KnowledgeFolders = stringPreferencesKey("knowledge_folders")
        val NoteSortField = stringPreferencesKey("note_sort_field")
        val NoteSortDirection = stringPreferencesKey("note_sort_direction")
    }
}
