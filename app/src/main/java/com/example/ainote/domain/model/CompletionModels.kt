package com.example.ainote.domain.model

enum class WritingMode {
    Normal
}

data class CompletionRequest(
    val beforeCursor: String,
    val afterCursor: String,
    val noteTitle: String?,
    val writingMode: WritingMode = WritingMode.Normal,
    val maxLength: Int,
    val language: String = "zh"
)

data class CompletionResult(
    val text: String,
    val confidence: Float? = null,
    val provider: String,
    val latencyMs: Long
)
