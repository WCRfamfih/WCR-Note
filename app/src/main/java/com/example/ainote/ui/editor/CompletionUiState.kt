package com.example.ainote.ui.editor

data class CompletionUiState(
    val suggestion: String? = null,
    val loading: Boolean = false
)

data class ManualAiUiState(
    val loading: Boolean = false,
    val result: String? = null,
    val actionLabel: String? = null,
    val replaceSelection: Boolean = false
)
