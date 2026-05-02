package com.example.ainote.data.repository

import com.example.ainote.data.remote.AiCompletionService
import com.example.ainote.domain.model.AiActionRequest
import com.example.ainote.domain.model.AiActionResult
import com.example.ainote.domain.model.CompletionRequest
import com.example.ainote.domain.model.CompletionResult

class AiRepository(private val service: AiCompletionService) {
    suspend fun completeText(request: CompletionRequest): CompletionResult {
        val result = service.completeText(request)
        return result.copy(text = filterCompletion(result.text, request.maxLength))
    }

    suspend fun runAction(request: AiActionRequest): AiActionResult {
        val result = service.runAction(request)
        return result.copy(text = filterCompletion(result.text, request.maxLength))
    }

    private fun filterCompletion(text: String, maxLength: Int): String {
        return text
            .trim()
            .trim('"', '\'', '“', '”')
            .removePrefix("补全文字：")
            .removePrefix("建议：")
            .trim()
            .take(maxLength)
    }
}
