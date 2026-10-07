package io.github.s1ddhants1.unhinge.ai

enum class LlmProtocol(val wireId: String) {
    OpenAiChatCompletions("openai-chat-completions"),
    OpenAiResponses("openai-responses"),
    AnthropicMessages("anthropic-messages"),
    GoogleGemini("google-gemini"),
    GoogleOpenAi("google-openai");

    fun defaultEndpoint(): String =
        when (this) {
            GoogleOpenAi -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
            OpenAiResponses -> ZenResponsesDefaultBaseUrl
            AnthropicMessages -> ZenMessagesBaseUrl
            GoogleGemini -> ZenGeminiBase
            OpenAiChatCompletions -> OpenRouterDefaultBaseUrl
        }

    companion object {
        val Default = OpenAiChatCompletions

        fun fromWire(value: String?): LlmProtocol =
            entries.firstOrNull { it.wireId == value?.trim() } ?: Default

        fun infer(provider: String, apiKey: String): LlmProtocol =
            when {
                provider.equals("Zen", ignoreCase = true) -> OpenAiChatCompletions
                provider.equals("Gemini", ignoreCase = true) -> GoogleOpenAi
                apiKey.trim().startsWith("AIzaSy") -> GoogleOpenAi
                else -> OpenAiChatCompletions
            }
    }
}
