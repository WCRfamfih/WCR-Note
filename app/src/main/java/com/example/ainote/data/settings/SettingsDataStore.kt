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
            completionDelayMs = prefs[Keys.CompletionDelayMs] ?: 700,
            maxCompletionLength = prefs[Keys.MaxCompletionLength] ?: 30,
            useFullNoteContext = prefs[Keys.UseFullNoteContext] ?: false,
            themeMode = ThemeMode.from(prefs[Keys.ThemeMode] ?: ThemeMode.System.name),
            editorTextSizeSp = prefs[Keys.EditorTextSizeSp] ?: 18
        )
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
    suspend fun updateCompletionDelayMs(value: Long) = updateLong(Keys.CompletionDelayMs, value)
    suspend fun updateMaxCompletionLength(value: Int) = updateInt(Keys.MaxCompletionLength, value)
    suspend fun updateUseFullNoteContext(value: Boolean) = updateBoolean(Keys.UseFullNoteContext, value)
    suspend fun updateThemeMode(value: ThemeMode) = updateString(Keys.ThemeMode, value.name)
    suspend fun updateEditorTextSizeSp(value: Int) = updateInt(Keys.EditorTextSizeSp, value)

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
        val CompletionDelayMs = longPreferencesKey("completion_delay_ms")
        val MaxCompletionLength = intPreferencesKey("max_completion_length")
        val UseFullNoteContext = booleanPreferencesKey("use_full_note_context")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val EditorTextSizeSp = intPreferencesKey("editor_text_size_sp")
    }
}
