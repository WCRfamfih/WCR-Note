package com.example.ainote.data.repository

import com.example.ainote.data.debug.AiDebugLogStore
import com.example.ainote.data.remote.FakeAiCompletionService
import com.example.ainote.data.remote.OpenAiCompatibleCompletionService
import com.example.ainote.data.settings.AiPresetUsage
import com.example.ainote.data.settings.AiServicePreset
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.data.settings.UserSettings
import com.example.ainote.domain.model.AiActionRequest
import com.example.ainote.domain.model.AiActionResult
import com.example.ainote.domain.model.CompletionRequest
import com.example.ainote.domain.model.CompletionResult
import com.example.ainote.domain.model.KnowledgeContextResolution
import com.example.ainote.domain.model.KnowledgeExtractionDraft
import com.example.ainote.domain.model.KnowledgeExtractionRequest
import com.example.ainote.domain.model.Note
import com.example.ainote.domain.model.titleAliases
import kotlinx.coroutines.flow.first

class AiRepository(
    private val settingsDataStore: SettingsDataStore,
    private val noteRepository: NoteRepository? = null,
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
        val enrichedRequest = request.withRelatedKnowledge(settings)
        val preset = settings.presetForUsage(
            if (enforceThrottle) AiPresetUsage.AutoCompletion else AiPresetUsage.ManualCompletion
        )
        AiDebugLogStore.add(
            title = if (enforceThrottle) "Auto completion request" else "Manual completion request",
            detail = """
                preset=${preset.label}
                provider=${preset.provider}
                fake=${preset.shouldUseFake()}
                maxLength=${enrichedRequest.maxLength}
                language=${enrichedRequest.language}
                beforeLength=${enrichedRequest.beforeCursor.length}
                afterLength=${enrichedRequest.afterCursor.length}
                knowledgeLength=${enrichedRequest.relatedKnowledge.length}

                before:
                ${enrichedRequest.beforeCursor}

                after:
                ${enrichedRequest.afterCursor}
            """.trimIndent()
        )
        if (enforceThrottle && !preset.shouldUseFake()) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastAutomaticCompletionAt
            if (elapsed < MIN_REAL_API_COMPLETION_INTERVAL_MS) {
                AiDebugLogStore.add("Completion throttled", "elapsed=${elapsed}ms")
                throw AiCompletionThrottledException
            }
            lastAutomaticCompletionAt = now
        }
        val result = if (preset.shouldUseFake()) {
            fakeService.completeText(enrichedRequest)
        } else {
            openAiService.completeText(enrichedRequest, preset.toUserSettings())
        }
        val filtered = filterCompletion(result.text, enrichedRequest.maxLength)
        AiDebugLogStore.add(
            title = "Completion result",
            detail = """
                provider=${result.provider}
                latencyMs=${result.latencyMs}
                raw:
                ${result.text}

                filtered:
                $filtered
            """.trimIndent()
        )
        return result.copy(text = filtered)
    }

    suspend fun runAction(request: AiActionRequest): AiActionResult {
        val settings = settingsDataStore.settings.first()
        val enrichedRequest = request.withRelatedKnowledge(settings)
        val preset = settings.presetForUsage(AiPresetUsage.AiTool)
        val result = if (preset.shouldUseFake()) {
            fakeService.runAction(enrichedRequest)
        } else {
            openAiService.runAction(enrichedRequest, preset.toUserSettings())
        }
        return result.copy(text = filterCompletion(result.text, enrichedRequest.maxLength))
    }

    suspend fun extractKnowledge(request: KnowledgeExtractionRequest): KnowledgeExtractionDraft {
        val settings = settingsDataStore.settings.first()
        val preset = settings.presetForUsage(AiPresetUsage.AiTool)
        val repository = noteRepository
        val targetKnowledge = when {
            !request.targetKnowledgeTitle.isNullOrBlank() || !request.targetKnowledgeContent.isNullOrBlank() -> {
                Note(
                    id = request.targetKnowledgeId ?: 0L,
                    title = request.targetKnowledgeTitle.orEmpty(),
                    content = request.targetKnowledgeContent.orEmpty(),
                    createdAt = 0L,
                    updatedAt = 0L
                )
            }
            else -> request.targetKnowledgeId?.let { targetId -> repository?.getKnowledgeEntry(targetId) }
        }
        AiDebugLogStore.add(
            title = "Knowledge extraction request",
            detail = """
                preset=${preset.label}
                provider=${preset.provider}
                fake=${preset.shouldUseFake()}
                noteId=${request.noteId}
                targetKnowledgeId=${request.targetKnowledgeId}
                targetKnowledgeTitle=${request.targetKnowledgeTitle.orEmpty()}
                targetKnowledgeContentLength=${request.targetKnowledgeContent?.length ?: 0}
                instruction=${request.instruction}
                materialLength=${request.material.length}
            """.trimIndent()
        )
        return if (preset.shouldUseFake()) {
            fakeService.extractKnowledge(request, targetKnowledge)
        } else {
            openAiService.extractKnowledgeDraft(request, targetKnowledge, preset.toUserSettings())
        }
    }

    suspend fun resolveKnowledgeContext(
        context: String,
        noteId: Long?,
        includeAllMatches: Boolean = false
    ): KnowledgeContextResolution {
        val settings = settingsDataStore.settings.first()
        if (!settings.knowledgeBaseEnabled || context.isBlank()) {
            return KnowledgeContextResolution()
        }
        val matches = matchKnowledgeEntries(context, noteId)
        if (matches.isEmpty()) {
            return KnowledgeContextResolution(limit = settings.knowledgeSendLimit.coerceAtLeast(1))
        }
        val limit = settings.knowledgeSendLimit.coerceAtLeast(1)
        val limitedMatches = if (includeAllMatches) matches else matches.take(limit)
        return KnowledgeContextResolution(
            matches = matches,
            limitedMatches = limitedMatches,
            limit = limit,
            overflow = matches.size > limit,
            relatedKnowledge = buildRelatedKnowledgePayload(limitedMatches)
        )
    }

    suspend fun testConnection(presetId: String? = null): Result<String> {
        val settings = settingsDataStore.settings.first()
        val preset = settings.aiServicePresets.firstOrNull { it.id == presetId }
            ?: settings.presetForUsage(AiPresetUsage.AiTool)
        return runCatching {
            if (preset.shouldUseFake()) {
                "Fake OK"
            } else {
                val result = openAiService.completeText(
                    request = CompletionRequest(
                        beforeCursor = "\u4eca\u5929\u9700\u8981",
                        afterCursor = "",
                        noteTitle = "\u8fde\u63a5\u6d4b\u8bd5",
                        maxLength = 12
                    ),
                    settings = preset.toUserSettings()
                )
                result.text.ifBlank { "OK" }
            }
        }
    }

    private fun AiServicePreset.toUserSettings(): UserSettings {
        return UserSettings(
            apiProvider = provider,
            apiKey = apiKey,
            apiBaseUrl = baseUrl,
            apiModel = model
        )
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

    private suspend fun CompletionRequest.withRelatedKnowledge(settings: UserSettings): CompletionRequest {
        if (!settings.knowledgeBaseEnabled || disableKnowledgeInjection || relatedKnowledge.isNotBlank()) return this
        val context = listOfNotNull(noteTitle, beforeCursor, afterCursor).joinToString("\n")
        val resolution = resolveKnowledgeContext(context, noteId)
        return copy(
            relatedKnowledge = resolution.relatedKnowledge,
            disableKnowledgeInjection = resolution.relatedKnowledge.isBlank()
        )
    }

    private suspend fun AiActionRequest.withRelatedKnowledge(settings: UserSettings): AiActionRequest {
        if (!settings.knowledgeBaseEnabled || disableKnowledgeInjection || relatedKnowledge.isNotBlank()) return this
        val context = listOfNotNull(noteTitle, content, selectedText).joinToString("\n")
        val resolution = resolveKnowledgeContext(context, noteId)
        return copy(
            relatedKnowledge = resolution.relatedKnowledge,
            disableKnowledgeInjection = resolution.relatedKnowledge.isBlank()
        )
    }

    private suspend fun matchKnowledgeEntries(context: String, noteId: Long?): List<Note> {
        val repository = noteRepository ?: return emptyList()
        if (context.isBlank()) return emptyList()
        val entries = repository.getEffectiveKnowledgeEntries(noteId)
        val globalMatches = entries.filter { it.isGlobalKnowledge }
        val aliasMatches = entries
            .asSequence()
            .filterNot { it.isGlobalKnowledge }
            .mapNotNull { note ->
                val firstMatchIndex = note.titleAliases()
                    .map { alias -> context.indexOf(alias).takeIf { it >= 0 } }
                    .filterNotNull()
                    .minOrNull()
                firstMatchIndex?.let { note to it }
            }
            .sortedBy { it.second }
            .map { it.first }
            .toList()
        return (globalMatches + aliasMatches).distinctBy { it.id }
    }

    private fun buildRelatedKnowledgePayload(matches: List<Note>): String {
        if (matches.isEmpty()) return ""
        AiDebugLogStore.add(
            title = "Knowledge context injected",
            detail = matches.joinToString("\n") { it.title }
        )
        return buildString {
            appendLine("\u76f8\u5173\u77e5\u8bc6\uff1a")
            matches.forEachIndexed { index, note ->
                if (index > 0) appendLine()
                appendLine("\u6807\u9898\uff1a${note.title}")
                appendLine("\u6b63\u6587\uff1a")
                appendLine(note.content.take(MAX_KNOWLEDGE_CHARS))
            }
        }.trim()
    }

    private companion object {
        const val MIN_REAL_API_COMPLETION_INTERVAL_MS = 1_500L
        const val MAX_KNOWLEDGE_CHARS = 1_200
    }
}

object AiCompletionThrottledException : Exception("Completion request throttled.")
