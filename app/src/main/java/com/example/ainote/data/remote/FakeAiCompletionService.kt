package com.example.ainote.data.remote

import com.example.ainote.domain.model.AiActionRequest
import com.example.ainote.domain.model.AiActionResult
import com.example.ainote.domain.model.AiActionType
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

    override suspend fun runAction(request: AiActionRequest): AiActionResult {
        val startedAt = System.currentTimeMillis()
        delay(650)
        val source = request.selectedText?.takeIf { it.isNotBlank() } ?: request.content
        val text = when (request.actionType) {
            AiActionType.ContinueWriting -> continueWriting(source)
            AiActionType.Summarize -> summarize(source)
            AiActionType.GenerateTitle -> generateTitle(source)
        }.trim().take(request.maxLength)
        return AiActionResult(
            text = text,
            provider = "Fake",
            latencyMs = System.currentTimeMillis() - startedAt
        )
    }

    private fun continueWriting(content: String): String {
        return when {
            content.contains("会议") -> "下一步可以把会议结论拆成待办，并标明每项任务的负责人。"
            content.contains("计划") -> "先从最容易落地的事项开始推进，再根据结果调整后续安排。"
            else -> "接下来可以继续补充背景、目标和下一步行动，让这条笔记更完整。"
        }
    }

    private fun summarize(content: String): String {
        val compact = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString("；")
        return if (compact.isBlank()) "当前笔记内容较少，暂时无法总结。" else "总结：$compact"
    }

    private fun generateTitle(content: String): String {
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return when {
            firstLine.isNotBlank() -> firstLine.take(18)
            content.contains("会议") -> "会议记录"
            content.contains("计划") -> "行动计划"
            else -> "新的笔记"
        }
    }
}
