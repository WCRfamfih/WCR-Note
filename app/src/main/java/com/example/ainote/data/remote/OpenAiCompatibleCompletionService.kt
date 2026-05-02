package com.example.ainote.data.remote

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
            systemPrompt = completionSystemPrompt(request.maxLength),
            userPrompt = buildCompletionPrompt(request),
            maxTokens = request.maxLength.coerceAtLeast(16) * 2,
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
        val apiKey = settings.apiKey.trim()
        val url = settings.apiBaseUrl.trim()
        val model = settings.apiModel.trim()
        require(apiKey.isNotBlank()) { "API Key is empty." }
        require(url.isNotBlank()) { "API base URL is empty." }
        require(model.isNotBlank()) { "API model is empty." }

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

        val httpRequest = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("AI API request failed: HTTP ${response.code}")
            }
            parseChatCompletion(body)
        }
    }

    private fun parseChatCompletion(body: String): String {
        val json = JSONObject(body)
        val choices = json.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""
        val message = choices.optJSONObject(0)?.optJSONObject("message")
        return message?.optString("content").orEmpty()
    }

    private fun completionSystemPrompt(maxLength: Int): String {
        return "\u4f60\u662f\u4e00\u4e2a\u4e2d\u6587\u5199\u4f5c\u8865\u5168\u52a9\u624b\u3002\u53ea\u8f93\u51fa\u53ef\u4ee5\u81ea\u7136\u63a5\u5728\u5149\u6807\u4f4d\u7f6e\u540e\u7684\u8865\u5168\u6587\u5b57\uff0c\u4e0d\u8981\u89e3\u91ca\uff0c\u4e0d\u8981\u91cd\u590d\u5df2\u6709\u5185\u5bb9\uff0c\u957f\u5ea6\u4e0d\u8d85\u8fc7 ${maxLength} \u4e2a\u5b57\u3002"
    }

    private fun buildCompletionPrompt(request: CompletionRequest): String {
        return """
            笔记标题：
            ${request.noteTitle.orEmpty()}

            光标前内容：
            ${request.beforeCursor}

            光标后内容：
            ${request.afterCursor}

            请输出补全文字：
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
            笔记标题：
            ${request.noteTitle.orEmpty()}

            需要处理的内容：
            $target
        """.trimIndent()
    }
}
