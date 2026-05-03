package com.example.ainote.domain.usecase

import com.example.ainote.domain.model.CompletionRequest
import com.example.ainote.domain.model.WritingMode

class BuildCompletionContextUseCase {
    operator fun invoke(
        content: String,
        cursor: Int,
        title: String,
        maxLength: Int,
        useFullNoteContext: Boolean,
        beforeLineCount: Int = 5,
        afterLineCount: Int = 2
    ): CompletionRequest {
        val safeCursor = cursor.coerceIn(0, content.length)
        val before = content.take(safeCursor)
        val after = content.drop(safeCursor)
        val context = if (useFullNoteContext) {
            before to after
        } else {
            buildLineBoundedContext(content, safeCursor, beforeLineCount, afterLineCount)
        }
        return CompletionRequest(
            beforeCursor = context.first,
            afterCursor = context.second,
            noteTitle = title.ifBlank { null },
            writingMode = WritingMode.Normal,
            maxLength = maxLength,
            language = detectLanguage(context.first + context.second)
        )
    }

    private fun buildLineBoundedContext(
        content: String,
        cursor: Int,
        beforeLineCount: Int,
        afterLineCount: Int
    ): Pair<String, String> {
        val lineStarts = mutableListOf(0)
        content.forEachIndexed { index, char ->
            if (char == '\n' && index + 1 <= content.length) lineStarts += index + 1
        }
        val currentLineIndex = lineStarts.indexOfLast { it <= cursor }.coerceAtLeast(0)
        val currentLineEnd = content.indexOf('\n', cursor).let { if (it == -1) content.length else it }
        val beforeStartLine = (currentLineIndex - beforeLineCount.coerceAtLeast(0)).coerceAtLeast(0)
        val afterEndLine = (currentLineIndex + afterLineCount.coerceAtLeast(0)).coerceAtMost(lineStarts.lastIndex)
        val beforeStart = lineStarts[beforeStartLine]
        val afterEnd = if (afterEndLine + 1 < lineStarts.size) {
            (lineStarts[afterEndLine + 1] - 1).coerceAtLeast(currentLineEnd)
        } else {
            content.length
        }
        return content.substring(beforeStart, cursor) to content.substring(cursor, afterEnd)
    }

    private fun detectLanguage(text: String): String {
        return if (text.any { it in '\u4e00'..'\u9fff' }) "zh" else "auto"
    }
}
