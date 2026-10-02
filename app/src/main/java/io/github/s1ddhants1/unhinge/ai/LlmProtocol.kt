package io.github.s1ddhants1.unhinge.ai

/**
 * LLM API wire protocol.
 *
 * Brand (the settings provider label) only decides endpoint/model defaults; the
 * protocol determines request behavior. Ported from Zafiro's `LlmProtocol`: explicit
 * resolution in one place replaces API-key prefix sniffing scattered across callers.
 */
enum class LlmProtocol(val wireId: String) {
    OpenAiChatCompletions("openai-chat-completions"),
    OpenAiResponses("openai-responses"),
    AnthropicMessages("anthropic-messages"),
    GoogleGemini("google-gemini"),
    GoogleOpenAi("google-openai");

    /** Default endpoint served when no explicit base URL is configured. */
    fun defaultEndpoint(): String =
        when (this) {
            GoogleOpenAi -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
            OpenAiResponses -> io.github.s1ddhants1.unhinge.Consts.ZEN_DEFAULT_BASE_URL
            AnthropicMessages -> ZenMessagesBaseUrl
            GoogleGemini -> ZenGeminiBase
            OpenAiChatCompletions -> OpenRouterDefaultBaseUrl
        }

    companion object {
        val Default = OpenAiChatCompletions

        fun fromWire(value: String?): LlmProtocol =
            entries.firstOrNull { it.wireId == value?.trim() } ?: Default

        /**
         * Brand → protocol. A pasted Gemini key (`AIzaSy…`) still routes to Google's
         * OpenAI-compatible endpoint even when the brand selector lags behind.
         * Zen resolves per-model in [ZenRouter]; the brand default is Responses.
         */
        fun infer(provider: String, apiKey: String): LlmProtocol =
            when {
                provider.equals("Zen", ignoreCase = true) -> OpenAiResponses
                provider.equals("Gemini", ignoreCase = true) -> GoogleOpenAi
                apiKey.trim().startsWith("AIzaSy") -> GoogleOpenAi
                else -> OpenAiChatCompletions
            }
    }
}
