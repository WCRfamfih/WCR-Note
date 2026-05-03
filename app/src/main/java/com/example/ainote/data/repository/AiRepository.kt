package com.example.ainote.data.repository

import com.example.ainote.data.remote.FakeAiCompletionService
import com.example.ainote.data.remote.OpenAiCompatibleCompletionService
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.data.settings.UserSettings
import com.example.ainote.domain.model.AiActionRequest
import com.example.ainote.domain.model.AiActionResult
import com.example.ainote.domain.model.CompletionRequest
import com.example.ainote.domain.model.CompletionResult
import kotlinx.coroutines.flow.first

class AiRepository(
    private val settingsDataStore: SettingsDataStore,
    private val fakeService: FakeAiCompletionService = FakeAiCompletionService(),
    private val openAiService: OpenAiCompatibleCompletionService = OpenAiCompatibleCompletionService()
) {
    private var lastAutomaticCompletionAt: Long = 0L

    suspend fun completeText(request: CompletionRequest): CompletionResult {
        return completeText(request, enforceThrottle = true)
    }

    suspend fun completeTextNow(request: CompletionRequest): CompletionResult {
        return completeText(request, enforceThrottle = false)
    }

    private suspend fun completeText(request: CompletionRequest, enforceThrottle: Boolean): CompletionResult {
        val settings = settingsDataStore.settings.first()
        if (enforceThrottle && !settings.shouldUseFake()) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastAutomaticCompletionAt
            if (elapsed < MIN_REAL_API_COMPLETION_INTERVAL_MS) {
                throw AiCompletionThrottledException
            }
            lastAutomaticCompletionAt = now
        }
        val result = if (settings.shouldUseFake()) {
            fakeService.completeText(request)
        } else {
            openAiService.completeText(request, settings)
        }
        return result.copy(text = filterCompletion(result.text, request.maxLength))
    }

    suspend fun runAction(request: AiActionRequest): AiActionResult {
        val settings = settingsDataStore.settings.first()
        val result = if (settings.shouldUseFake()) {
            fakeService.runAction(request)
        } else {
            openAiService.runAction(request, settings)
        }
        return result.copy(text = filterCompletion(result.text, request.maxLength))
    }

    suspend fun testConnection(): Result<String> {
        val settings = settingsDataStore.settings.first()
        return runCatching {
            if (settings.shouldUseFake()) {
                "Fake OK"
            } else {
                val result = openAiService.completeText(
                    request = CompletionRequest(
                        beforeCursor = "\u4eca\u5929\u9700\u8981",
                        afterCursor = "",
                        noteTitle = "\u8fde\u63a5\u6d4b\u8bd5",
                        maxLength = 12
                    ),
                    settings = settings
                )
                result.text.ifBlank { "OK" }
            }
        }
    }

    private fun UserSettings.shouldUseFake(): Boolean {
        return apiProvider.equals("Fake", ignoreCase = true) || apiKey.isBlank()
    }

    private fun filterCompletion(text: String, maxLength: Int): String {
        return text
            .trim()
            .trim('"', '\'', '\u201c', '\u201d')
            .removePrefix("\u8865\u5168\u6587\u5b57\uff1a")
            .removePrefix("\u5efa\u8bae\uff1a")
            .trim()
            .take(maxLength)
    }

    private companion object {
        const val MIN_REAL_API_COMPLETION_INTERVAL_MS = 1_500L
    }
}

object AiCompletionThrottledException : Exception("Completion request throttled.")
