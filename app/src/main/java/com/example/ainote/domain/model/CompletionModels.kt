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

enum class AiActionType(val label: String) {
    ContinueWriting("继续写"),
    Summarize("总结"),
    GenerateTitle("生成标题")
}

data class AiActionRequest(
    val actionType: AiActionType,
    val noteTitle: String?,
    val content: String,
    val selectedText: String? = null,
    val maxLength: Int,
    val language: String = "zh"
)

data class AiActionResult(
    val text: String,
    val provider: String,
    val latencyMs: Long
)
