package io.github.s1ddhants1.unhinge.ai

import io.github.s1ddhants1.unhinge.Consts

/**
 * Standardized carrier for dating prompt reply generation requests across all LLM protocols.
 */
data class WingmanLlmRequest(
    val text: String,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val customSystemPrompt: String = "",
    val profileBlock: String = "",
    val avoidReplies: List<String> = emptyList(),
    val temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
    val topP: Float = Consts.DEFAULT_AI_TOP_P,
    val reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
    val promptTemplate: String? = null,
    val directionalStimulus: String = "",
    val promptCount: Int? = null,
    val maxRetries: Int = 3,
    val structured: Boolean = true,
) {
    val effectivePromptCount: Int
        get() = promptCount ?: text.lines().size
}

/**
 * Standardized carrier for freeform chat and profile inquiry requests across all LLM protocols.
 */
data class ChatLlmRequest(
    val userPrompt: String,
    val systemPrompt: String,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
    val topP: Float = Consts.DEFAULT_AI_TOP_P,
    val reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
    val maxRetries: Int = 3,
)

typealias LlmStreamChunk = OpenRouterStreamingService.StreamChunk
typealias ChatStreamChunk = OpenRouterStreamingService.ChatStreamChunk
