package com.example.ainote.domain.usecase

import com.example.ainote.domain.model.CompletionRequest
import com.example.ainote.domain.model.WritingMode

class BuildCompletionContextUseCase {
    operator fun invoke(
        content: String,
        cursor: Int,
        title: String,
        maxLength: Int,
        useFullNoteContext: Boolean
    ): CompletionRequest {
        val safeCursor = cursor.coerceIn(0, content.length)
        val before = content.take(safeCursor)
        val after = content.drop(safeCursor)
        return CompletionRequest(
            beforeCursor = if (useFullNoteContext) before else before.takeLast(1000),
            afterCursor = if (useFullNoteContext) after else after.take(300),
            noteTitle = title.ifBlank { null },
            writingMode = WritingMode.Normal,
            maxLength = maxLength,
            language = detectLanguage(before + after)
        )
    }

    private fun detectLanguage(text: String): String {
        return if (text.any { it in '\u4e00'..'\u9fff' }) "zh" else "auto"
    }
}
