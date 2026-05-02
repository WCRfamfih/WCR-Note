package com.example.ainote.data.settings

data class UserSettings(
    val apiProvider: String = "Fake",
    val apiKey: String = "",
    val apiBaseUrl: String = "https://api.openai.com/v1/chat/completions",
    val apiModel: String = "gpt-4o-mini",
    val autoCompletionEnabled: Boolean = true,
    val completionDelayMs: Long = 700,
    val maxCompletionLength: Int = 30,
    val useFullNoteContext: Boolean = false
)
