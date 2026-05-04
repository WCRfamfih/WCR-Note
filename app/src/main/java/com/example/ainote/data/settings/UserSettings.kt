package com.example.ainote.data.settings

data class AiServicePreset(
    val id: String,
    val label: String,
    val provider: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String = ""
) {
    fun shouldUseFake(): Boolean = provider.equals("Fake", ignoreCase = true) || apiKey.isBlank()
}

enum class AiPresetUsage {
    AutoCompletion,
    ManualCompletion,
    AiTool
}

enum class EditorFontPreset(val label: String) {
    System("\u8ddf\u968f\u7cfb\u7edf"),
    Sans("\u65e0\u886c\u7ebf"),
    Serif("\u886c\u7ebf"),
    Monospace("\u7b49\u5bbd"),
    Cursive("\u624b\u5199");

    companion object {
        fun from(raw: String?): EditorFontPreset {
            return entries.firstOrNull { it.name == raw } ?: System
        }
    }
}

data class UserSettings(
    val apiProvider: String = "Fake",
    val apiKey: String = "",
    val apiBaseUrl: String = "https://api.openai.com/v1/chat/completions",
    val apiModel: String = "gpt-4o-mini",
    val aiServicePresets: List<AiServicePreset> = defaultAiServicePresets(),
    val autoCompletionPresetId: String = "fake",
    val manualCompletionPresetId: String = "fake",
    val aiToolPresetId: String = "fake",
    val autoCompletionEnabled: Boolean = true,
    val preferChineseAutoCompletion: Boolean = true,
    val skipBlankLineAutoCompletion: Boolean = true,
    val autoCompleteOnlyOnContentChange: Boolean = true,
    val completionDelayMs: Long = 700,
    val maxCompletionLength: Int = 30,
    val useFullNoteContext: Boolean = false,
    val completionBeforeLineCount: Int = 5,
    val completionAfterLineCount: Int = 2,
    val themeMode: ThemeMode = ThemeMode.System,
    val accentColorPreset: AccentColorPreset = AccentColorPreset.Violet,
    val editorTextSizeSp: Int = 18,
    val editorLineSpacingPercent: Int = 140,
    val editorLetterSpacingTenthSp: Int = 0,
    val editorFontPreset: EditorFontPreset = EditorFontPreset.System,
    val showMarkdownMarkers: Boolean = false,
    val showCompletionErrorToast: Boolean = true,
    val knowledgeBaseEnabled: Boolean = false,
    val knowledgeSendLimit: Int = 5,
    val documentDirectoryUri: String = "",
    val noteSortField: NoteSortField = NoteSortField.Time,
    val noteSortDirection: NoteSortDirection = NoteSortDirection.Descending
) {
    fun presetForUsage(usage: AiPresetUsage): AiServicePreset {
        val id = when (usage) {
            AiPresetUsage.AutoCompletion -> autoCompletionPresetId
            AiPresetUsage.ManualCompletion -> manualCompletionPresetId
            AiPresetUsage.AiTool -> aiToolPresetId
        }
        return aiServicePresets.firstOrNull { it.id == id }
            ?: aiServicePresets.firstOrNull()
            ?: defaultAiServicePresets().first()
    }

    fun legacyPreset(): AiServicePreset = AiServicePreset(
        id = "legacy",
        label = apiProvider.ifBlank { "Current" },
        provider = apiProvider,
        baseUrl = apiBaseUrl,
        model = apiModel,
        apiKey = apiKey
    )
}

fun defaultAiServicePresets(): List<AiServicePreset> = listOf(
    AiServicePreset(
        id = "fake",
        label = "Fake",
        provider = "Fake",
        baseUrl = "https://api.openai.com/v1/chat/completions",
        model = "gpt-4o-mini"
    ),
    AiServicePreset(
        id = "openai",
        label = "OpenAI",
        provider = "OpenAI",
        baseUrl = "https://api.openai.com/v1/chat/completions",
        model = "gpt-4o-mini"
    ),
    AiServicePreset(
        id = "deepseek",
        label = "DeepSeek",
        provider = "DeepSeek",
        baseUrl = "https://api.deepseek.com/chat/completions",
        model = "deepseek-chat"
    ),
    AiServicePreset(
        id = "qwen",
        label = "Qwen",
        provider = "Qwen",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
        model = "qwen-plus"
    )
)
