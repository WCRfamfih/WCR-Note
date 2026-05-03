package com.example.ainote.data.settings

data class AiProviderPreset(
    val label: String,
    val provider: String,
    val baseUrl: String,
    val model: String
) {
    companion object {
        val Fake = AiProviderPreset(
            label = "Fake",
            provider = "Fake",
            baseUrl = "https://api.openai.com/v1/chat/completions",
            model = "gpt-4o-mini"
        )

        val OpenAi = AiProviderPreset(
            label = "OpenAI",
            provider = "OpenAI",
            baseUrl = "https://api.openai.com/v1/chat/completions",
            model = "gpt-4o-mini"
        )

        val DeepSeek = AiProviderPreset(
            label = "DeepSeek",
            provider = "DeepSeek",
            baseUrl = "https://api.deepseek.com/chat/completions",
            model = "deepseek-chat"
        )

        val Qwen = AiProviderPreset(
            label = "Qwen",
            provider = "Qwen",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            model = "qwen-plus"
        )

        val All = listOf(Fake, OpenAi, DeepSeek, Qwen)
    }
}
