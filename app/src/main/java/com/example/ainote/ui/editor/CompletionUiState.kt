package com.example.ainote.ui.editor

import com.example.ainote.domain.model.AiActionRequest

data class CompletionUiState(
    val suggestion: String? = null,
    val loading: Boolean = false,
    val errorMessage: String? = null
)

data class ManualAiUiState(
    val loading: Boolean = false,
    val result: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val actionLabel: String? = null,
    val retryRequest: AiActionRequest? = null,
    val replaceSelection: Boolean = false
)
