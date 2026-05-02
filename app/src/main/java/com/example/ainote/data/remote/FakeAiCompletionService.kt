package com.example.ainote.data.remote

import com.example.ainote.domain.model.CompletionRequest
import com.example.ainote.domain.model.CompletionResult
import kotlinx.coroutines.delay

class FakeAiCompletionService : AiCompletionService {
    override suspend fun completeText(request: CompletionRequest): CompletionResult {
        val startedAt = System.currentTimeMillis()
        delay(450)
        val suggestion = when {
            request.beforeCursor.endsWith("，") || request.beforeCursor.endsWith(",") -> "接下来先把最重要的事项列清楚。"
            request.beforeCursor.contains("会议") -> "并同步确认负责人和截止时间。"
            request.beforeCursor.contains("今天") -> "先完成当前最关键的一件事。"
            else -> "继续补充下一步的具体想法。"
        }.take(request.maxLength)
        return CompletionResult(
            text = suggestion,
            confidence = 0.7f,
            provider = "Fake",
            latencyMs = System.currentTimeMillis() - startedAt
        )
    }
}
