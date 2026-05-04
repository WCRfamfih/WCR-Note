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
    val language: String = "zh",
    val relatedKnowledge: String = ""
)

data class CompletionResult(
    val text: String,
    val confidence: Float? = null,
    val provider: String,
    val latencyMs: Long
)

enum class AiActionType(val label: String) {
    ContinueWriting("\u7ee7\u7eed\u5199"),
    Expand("\u6269\u5199"),
    Formal("\u6539\u5f97\u66f4\u6b63\u5f0f"),
    Concise("\u6539\u5f97\u66f4\u7b80\u6d01"),
    Todo("\u6574\u7406\u6210\u5f85\u529e"),
    Summarize("\u603b\u7ed3"),
    GenerateTitle("\u751f\u6210\u6807\u9898")
}

data class AiActionRequest(
    val actionType: AiActionType,
    val noteTitle: String?,
    val content: String,
    val selectedText: String? = null,
    val maxLength: Int,
    val language: String = "zh",
    val relatedKnowledge: String = ""
)

data class AiActionResult(
    val text: String,
    val provider: String,
    val latencyMs: Long
)
