package com.example.ainote.data.remote

import com.example.ainote.domain.model.CompletionRequest
import com.example.ainote.domain.model.CompletionResult

interface AiCompletionService {
    suspend fun completeText(request: CompletionRequest): CompletionResult
}
