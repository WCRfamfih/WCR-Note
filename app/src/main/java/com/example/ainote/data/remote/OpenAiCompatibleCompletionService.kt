package com.example.ainote.data.remote

import com.example.ainote.data.debug.AiDebugLogStore
import com.example.ainote.data.settings.UserSettings
import com.example.ainote.domain.model.AiActionRequest
import com.example.ainote.domain.model.AiActionResult
import com.example.ainote.domain.model.AiActionType
import com.example.ainote.domain.model.CompletionRequest
import com.example.ainote.domain.model.CompletionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class OpenAiCompatibleCompletionService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    suspend fun completeText(request: CompletionRequest, settings: UserSettings): CompletionResult {
        val startedAt = System.currentTimeMillis()
        val text = requestChatCompletion(
            settings = settings,
            systemPrompt = completionSystemPrompt(request),
            userPrompt = buildCompletionPrompt(request),
            maxTokens = completionMaxTokens(request.maxLength),
            temperature = 0.3
        )
        return CompletionResult(
            text = text,
            confidence = null,
            provider = settings.apiProvider,
            latencyMs = System.currentTimeMillis() - startedAt
        )
    }

    suspend fun runAction(request: AiActionRequest, settings: UserSettings): AiActionResult {
        val startedAt = System.currentTimeMillis()
        val text = requestChatCompletion(
            settings = settings,
            systemPrompt = actionSystemPrompt(request.actionType),
            userPrompt = buildActionPrompt(request),
            maxTokens = request.maxLength.coerceAtLeast(48) * 2,
            temperature = 0.4
        )
        return AiActionResult(
            text = text,
            provider = settings.apiProvider,
            latencyMs = System.currentTimeMillis() - startedAt
        )
    }

    private suspend fun requestChatCompletion(
        settings: UserSettings,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double
    ): String = withContext(Dispatchers.IO) {
        runCatching {
            executeChatCompletion(settings, systemPrompt, userPrompt, maxTokens, temperature)
        }.getOrElse { throw it.toAiApiException() }
    }

    private fun executeChatCompletion(
        settings: UserSettings,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double
    ): String {
        val apiKey = settings.apiKey.trim()
        val url = normalizeChatCompletionsUrl(settings.apiBaseUrl.trim())
        val model = settings.apiModel.trim()
        if (apiKey.isBlank()) throw AiApiException("\u0041\u0050\u0049 \u004b\u0065\u0079 \u4e3a\u7a7a\uff0c\u8bf7\u5148\u5728\u8bbe\u7f6e\u9875\u586b\u5199\u3002")
        if (url.isBlank()) throw AiApiException("\u0041\u0050\u0049 \u5730\u5740\u4e3a\u7a7a\uff0c\u8bf7\u586b\u5199\u5b8c\u6574\u7684 chat/completions \u5730\u5740\u3002")
        if (model.isBlank()) throw AiApiException("\u6a21\u578b\u540d\u4e3a\u7a7a\uff0c\u8bf7\u586b\u5199\u670d\u52a1\u5546\u652f\u6301\u7684\u6a21\u578b\u3002")

        val payload = JSONObject()
            .put("model", model)
            .put("temperature", temperature)
            .put("max_tokens", maxTokens)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt))
            )

        AiDebugLogStore.add(
            title = "HTTP request",
            detail = """
                provider=${settings.apiProvider}
                model=$model
                url=$url
                maxTokens=$maxTokens
                temperature=$temperature

                system:
                $systemPrompt

                user:
                $userPrompt
            """.trimIndent()
        )

        val httpRequest = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            AiDebugLogStore.add(
                title = "HTTP response ${response.code}",
                detail = body.ifBlank { "<empty body>" }
            )
            if (!response.isSuccessful) {
                throw AiApiException(formatHttpError(response.code, body))
            }
            val parsed = parseChatCompletion(body)
            AiDebugLogStore.add(
                title = "Parsed completion",
                detail = parsed.ifBlank { "<empty parsed text>" }
            )
            return parsed
        }
    }

    private fun Throwable.toAiApiException(): Throwable {
        if (this is AiApiException) return this
        return when (this) {
            is UnknownHostException -> AiApiException("\u65e0\u6cd5\u89e3\u6790 API \u57df\u540d\uff0c\u8bf7\u68c0\u67e5\u6a21\u62df\u5668\u7f51\u7edc\u3001DNS \u6216\u4ee3\u7406\u8bbe\u7f6e\u3002", this)
            is SocketTimeoutException -> AiApiException("\u0041\u0049 \u8bf7\u6c42\u8d85\u65f6\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u6216\u68c0\u67e5\u7f51\u7edc\u3002", this)
            is IOException -> AiApiException("\u7f51\u7edc\u8bf7\u6c42\u5931\u8d25\uff1a${message.orEmpty()}", this)
            else -> this
        }
    }

    private fun formatHttpError(code: Int, body: String): String {
        val apiMessage = parseErrorMessage(body)
        val hint = when (code) {
            400 -> "\u8bf7\u6c42\u53c2\u6570\u6709\u8bef\uff0c\u8bf7\u68c0\u67e5 Model \u548c API Base URL\u3002"
            401 -> "\u8ba4\u8bc1\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5 API Key\u3002"
            402 -> "\u8d26\u6237\u4f59\u989d\u4e0d\u8db3\u6216\u8ba1\u8d39\u72b6\u6001\u5f02\u5e38\u3002"
            403 -> "\u6ca1\u6709\u6743\u9650\u8bbf\u95ee\u8be5\u6a21\u578b\u6216 API\u3002"
            404 -> "\u63a5\u53e3\u5730\u5740\u6216\u6a21\u578b\u4e0d\u5b58\u5728\u3002"
            429 -> "\u8bf7\u6c42\u8fc7\u4e8e\u9891\u7e41\u6216\u89e6\u53d1\u9650\u6d41\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002"
            in 500..599 -> "\u670d\u52a1\u5546\u670d\u52a1\u5f02\u5e38\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002"
            else -> "\u0041\u0049 \u670d\u52a1\u8fd4\u56de\u9519\u8bef\u3002"
        }
        return if (apiMessage.isBlank()) "HTTP $code: $hint" else "HTTP $code: $hint $apiMessage"
    }

    private fun parseErrorMessage(body: String): String {
        return runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
    }

    private fun parseChatCompletion(body: String): String {
        val json = runCatching { JSONObject(body) }.getOrElse {
            throw AiApiException(
                "API 杩斿洖鐨勪笉鏄?JSON锛岃妫€鏌?API Base URL 鏄惁鏄畬鏁寸殑 chat/completions 鎺ュ彛銆傝繑鍥炲唴瀹癸細${body.previewForError()}"
            )
        }
        val choices = json.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""
        val choice = choices.optJSONObject(0)
        val message = choice?.optJSONObject("message")
        val content = message?.optString("content").orEmpty()
        if (content.isBlank()) {
            AiDebugLogStore.add(
                title = "Empty content detail",
                detail = """
                    finishReason=${choice?.optString("finish_reason").orEmpty()}
                    reasoningContent:
                    ${message?.optString("reasoning_content").orEmpty().ifBlank { "<empty reasoning_content>" }}
                """.trimIndent()
            )
        }
        return content
    }

    private fun completionMaxTokens(maxLength: Int): Int {
        return (maxLength.coerceAtLeast(30) * 8).coerceAtLeast(256)
    }

    private fun normalizeChatCompletionsUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        val trimmed = rawUrl.trimEnd('/')
        val path = runCatching { URI(trimmed).path.orEmpty().trimEnd('/') }.getOrDefault("")
        return when {
            path.endsWith("/chat/completions") -> trimmed
            path.isBlank() -> "$trimmed/v1/chat/completions"
            path == "/v1" -> "$trimmed/chat/completions"
            else -> rawUrl
        }
    }

    private fun String.previewForError(): String {
        return replace('\n', ' ')
            .replace('\r', ' ')
            .take(180)
    }

    private fun completionSystemPrompt(request: CompletionRequest): String {
        val languageRule = if (request.language == "zh") {
            "Use Simplified Chinese."
        } else {
            "Continue in the same language and style as the text before the cursor."
        }
        return "You are a multilingual writing completion assistant. " +
            "Output only text that can naturally continue after the cursor. " +
            "Do not explain, do not quote, and do not repeat existing content. " +
            "$languageRule Keep the completion within ${request.maxLength} characters."
    }

    private fun buildCompletionPrompt(request: CompletionRequest): String {
        return """
            Note title:
            ${request.noteTitle.orEmpty()}

            Text before cursor:
            ${request.beforeCursor}

            Text after cursor:
            ${request.afterCursor}

            ${request.relatedKnowledge.takeIf { it.isNotBlank() } ?: ""}

            Completion:
        """.trimIndent()
    }

    private fun actionSystemPrompt(actionType: AiActionType): String {
        return when (actionType) {
            AiActionType.ContinueWriting -> "\u4f60\u662f\u5199\u4f5c\u52a9\u624b\u3002\u6839\u636e\u7b14\u8bb0\u7eed\u5199 1 \u5230 3 \u53e5\uff0c\u4fdd\u6301\u539f\u6709\u8bed\u6c14\uff0c\u4e0d\u8981\u89e3\u91ca\u3002"
            AiActionType.Expand -> "\u4f60\u662f\u5199\u4f5c\u52a9\u624b\u3002\u5c06\u5185\u5bb9\u6269\u5199\u5f97\u66f4\u5b8c\u6574\uff0c\u4fdd\u7559\u539f\u610f\uff0c\u4e0d\u6dfb\u52a0\u65e0\u6839\u636e\u4e8b\u5b9e\u3002"
            AiActionType.Formal -> "\u4f60\u662f\u5199\u4f5c\u52a9\u624b\u3002\u5c06\u5185\u5bb9\u6539\u5199\u5f97\u66f4\u6b63\u5f0f\uff0c\u4fdd\u7559\u539f\u610f\uff0c\u4e0d\u8981\u89e3\u91ca\u3002"
            AiActionType.Concise -> "\u4f60\u662f\u5199\u4f5c\u52a9\u624b\u3002\u5c06\u5185\u5bb9\u6539\u5199\u5f97\u66f4\u7b80\u6d01\uff0c\u4fdd\u7559\u6838\u5fc3\u610f\u601d\uff0c\u4e0d\u8981\u89e3\u91ca\u3002"
            AiActionType.Todo -> "\u4f60\u662f\u5199\u4f5c\u52a9\u624b\u3002\u5c06\u5185\u5bb9\u6574\u7406\u6210\u5f85\u529e\u5217\u8868\uff0c\u6bcf\u9879\u4ee5 - [ ] \u5f00\u5934\uff0c\u4e0d\u6dfb\u52a0\u65b0\u4efb\u52a1\u3002"
            AiActionType.Summarize -> "\u4f60\u662f\u5199\u4f5c\u52a9\u624b\u3002\u603b\u7ed3\u7b14\u8bb0\u4e3b\u8981\u5185\u5bb9\uff0c\u8f93\u51fa\u7b80\u77ed\u4e2d\u6587\u6458\u8981\uff0c\u4e0d\u8981\u89e3\u91ca\u3002"
            AiActionType.GenerateTitle -> "\u4f60\u662f\u5199\u4f5c\u52a9\u624b\u3002\u6839\u636e\u7b14\u8bb0\u751f\u6210\u4e00\u4e2a\u77ed\u6807\u9898\uff0c\u4e0d\u8d85\u8fc7 18 \u4e2a\u5b57\uff0c\u4e0d\u8981\u52a0\u5f15\u53f7\u3002"
        }
    }

    private fun buildActionPrompt(request: AiActionRequest): String {
        val target = request.selectedText?.takeIf { it.isNotBlank() } ?: request.content
        return """
            Note title:
            ${request.noteTitle.orEmpty()}

            Content to process:
            $target

            ${request.relatedKnowledge.takeIf { it.isNotBlank() } ?: ""}
        """.trimIndent()
    }}
