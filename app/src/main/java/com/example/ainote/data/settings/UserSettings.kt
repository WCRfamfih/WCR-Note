package com.example.ainote.data.settings

data class UserSettings(
    val apiProvider: String = "Fake",
    val apiKey: String = "",
    val autoCompletionEnabled: Boolean = true,
    val completionDelayMs: Long = 700,
    val maxCompletionLength: Int = 30,
    val useFullNoteContext: Boolean = false
)
