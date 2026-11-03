package io.github.s1ddhants1.unhinge.ai

object LlmClientRegistry {
    fun getClient(protocol: LlmProtocol): LlmClient =
        when (protocol) {
            LlmProtocol.OpenAiResponses -> OpenAiResponsesService
            LlmProtocol.AnthropicMessages -> AnthropicMessagesService
            LlmProtocol.GoogleGemini -> GoogleGeminiService
            LlmProtocol.OpenAiChatCompletions,
            LlmProtocol.GoogleOpenAi -> OpenAiChatClient
        }
}
