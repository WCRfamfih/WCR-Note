package com.example.ainote.domain.usecase

import com.example.ainote.data.repository.AiRepository
import com.example.ainote.domain.model.CompletionRequest

class RequestCompletionUseCase(private val aiRepository: AiRepository) {
    suspend operator fun invoke(request: CompletionRequest, force: Boolean = false) =
        if (force) aiRepository.completeTextNow(request) else aiRepository.completeText(request)
}
