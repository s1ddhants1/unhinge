package io.github.s1ddhants1.unhinge.ai

import io.github.s1ddhants1.unhinge.Consts
import kotlinx.coroutines.flow.Flow

/**
 * Backward-compatible facade delegating to the unified [OpenAiChatClient].
 */
object OpenRouterStreamingService {

    fun streamGeneration(
        text: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        customSystemPrompt: String = "",
        profileBlock: String = "",
        avoidReplies: List<String> = emptyList(),
        temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
        topP: Float = Consts.DEFAULT_AI_TOP_P,
        maxRetries: Int = 3,
        structured: Boolean = true,
        reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
        promptTemplate: String? = null,
        directionalStimulus: String = "",
        promptCount: Int? = null,
    ): Flow<StreamChunk> =
        OpenAiChatClient.streamGeneration(
            WingmanLlmRequest(
                text = text,
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                customSystemPrompt = customSystemPrompt,
                profileBlock = profileBlock,
                avoidReplies = avoidReplies,
                temperature = temperature,
                topP = topP,
                maxRetries = maxRetries,
                structured = structured,
                reasoningEffort = reasoningEffort,
                promptTemplate = promptTemplate,
                directionalStimulus = directionalStimulus,
                promptCount = promptCount,
            )
        )

    fun streamChat(
        userPrompt: String,
        systemPrompt: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
        topP: Float = Consts.DEFAULT_AI_TOP_P,
        maxRetries: Int = 3,
        reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
    ): Flow<ChatStreamChunk> =
        OpenAiChatClient.streamChat(
            ChatLlmRequest(
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                temperature = temperature,
                topP = topP,
                reasoningEffort = reasoningEffort,
                maxRetries = maxRetries,
            )
        )

    sealed interface StreamChunk {
        data class Content(val text: String) : StreamChunk
        data class Complete(val generatedLines: List<String>) : StreamChunk
        data class Error(val message: String) : StreamChunk
    }

    sealed interface ChatStreamChunk {
        data class Content(val text: String) : ChatStreamChunk
        data class Complete(val fullText: String) : ChatStreamChunk
        data class Error(val message: String) : ChatStreamChunk
    }
}
