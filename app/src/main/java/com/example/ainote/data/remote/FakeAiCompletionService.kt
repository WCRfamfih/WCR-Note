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
            request.beforeCursor.endsWith("\uff0c") || request.beforeCursor.endsWith(",") -> "\u63a5\u4e0b\u6765\u5148\u628a\u6700\u91cd\u8981\u7684\u4e8b\u9879\u5217\u6e05\u695a\u3002"
            request.beforeCursor.contains("\u4f1a\u8bae") -> "\u5e76\u540c\u6b65\u786e\u8ba4\u8d1f\u8d23\u4eba\u548c\u622a\u6b62\u65f6\u95f4\u3002"
            request.beforeCursor.contains("\u4eca\u5929") -> "\u5148\u5b8c\u6210\u5f53\u524d\u6700\u5173\u952e\u7684\u4e00\u4ef6\u4e8b\u3002"
            else -> "\u7ee7\u7eed\u8865\u5145\u4e0b\u4e00\u6b65\u7684\u5177\u4f53\u60f3\u6cd5\u3002"
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
            AiActionType.Expand -> expand(source)
            AiActionType.Formal -> formal(source)
            AiActionType.Concise -> concise(source)
            AiActionType.Todo -> todo(source)
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
            content.contains("\u4f1a\u8bae") -> "\u4e0b\u4e00\u6b65\u53ef\u4ee5\u628a\u4f1a\u8bae\u7ed3\u8bba\u62c6\u6210\u5f85\u529e\uff0c\u5e76\u6807\u660e\u6bcf\u9879\u4efb\u52a1\u7684\u8d1f\u8d23\u4eba\u3002"
            content.contains("\u8ba1\u5212") -> "\u5148\u4ece\u6700\u5bb9\u6613\u843d\u5730\u7684\u4e8b\u9879\u5f00\u59cb\u63a8\u8fdb\uff0c\u518d\u6839\u636e\u7ed3\u679c\u8c03\u6574\u540e\u7eed\u5b89\u6392\u3002"
            else -> "\u63a5\u4e0b\u6765\u53ef\u4ee5\u7ee7\u7eed\u8865\u5145\u80cc\u666f\u3001\u76ee\u6807\u548c\u4e0b\u4e00\u6b65\u884c\u52a8\uff0c\u8ba9\u8fd9\u6761\u7b14\u8bb0\u66f4\u5b8c\u6574\u3002"
        }
    }

    private fun expand(content: String): String {
        val base = firstMeaningfulLine(content)
        return if (base.isBlank()) {
            "\u53ef\u4ee5\u5148\u8865\u5145\u80cc\u666f\u3001\u76ee\u6807\u548c\u5177\u4f53\u884c\u52a8\uff0c\u8ba9\u5185\u5bb9\u66f4\u6e05\u695a\u3002"
        } else {
            "$base\n\n\u8fd9\u4ef6\u4e8b\u9700\u8981\u5148\u660e\u786e\u76ee\u6807\uff0c\u518d\u62c6\u5206\u6210\u53ef\u6267\u884c\u7684\u6b65\u9aa4\u3002\u5982\u679c\u6d89\u53ca\u591a\u4e2a\u4eba\uff0c\u8fd8\u5e94\u8bb0\u5f55\u8d1f\u8d23\u4eba\u548c\u622a\u6b62\u65f6\u95f4\u3002"
        }
    }

    private fun formal(content: String): String {
        val compact = content.trim().ifBlank { "\u8bf7\u8865\u5145\u9700\u8981\u6539\u5199\u7684\u5185\u5bb9\u3002" }
        return "\u5efa\u8bae\u8868\u8ff0\uff1a$compact\u3002\u540e\u7eed\u5c06\u6309\u7167\u660e\u786e\u7684\u76ee\u6807\u3001\u65f6\u95f4\u8282\u70b9\u548c\u8d23\u4efb\u5206\u5de5\u7ee7\u7eed\u63a8\u8fdb\u3002"
    }

    private fun concise(content: String): String {
        val first = firstMeaningfulLine(content)
        return if (first.isBlank()) "\u6682\u65e0\u53ef\u7b80\u5316\u5185\u5bb9\u3002" else first.take(40)
    }

    private fun todo(content: String): String {
        val lines = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(5)
            .toList()
        if (lines.isEmpty()) return "- [ ] \u8865\u5145\u9700\u8981\u5b8c\u6210\u7684\u4efb\u52a1"
        return lines.joinToString("\n") { "- [ ] ${it.take(36)}" }
    }

    private fun summarize(content: String): String {
        val compact = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString("\uff1b")
        return if (compact.isBlank()) "\u5f53\u524d\u7b14\u8bb0\u5185\u5bb9\u8f83\u5c11\uff0c\u6682\u65f6\u65e0\u6cd5\u603b\u7ed3\u3002" else "\u603b\u7ed3\uff1a$compact"
    }

    private fun generateTitle(content: String): String {
        val firstLine = firstMeaningfulLine(content)
        return when {
            firstLine.isNotBlank() -> firstLine.take(18)
            content.contains("\u4f1a\u8bae") -> "\u4f1a\u8bae\u8bb0\u5f55"
            content.contains("\u8ba1\u5212") -> "\u884c\u52a8\u8ba1\u5212"
            else -> "\u65b0\u7684\u7b14\u8bb0"
        }
    }

    private fun firstMeaningfulLine(content: String): String {
        return content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }
}
