package io.github.s1ddhants1.unhinge.ai

/**
 * Registry mapping wire protocols to their corresponding polymorphic [LlmClient] implementations.
 * Eliminates repetitive `when (protocol)` dispatch blocks across the codebase.
 */
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
