package io.github.s1ddhants1.unhinge.ai

import kotlinx.coroutines.flow.Flow

/**
 * Common polymorphic interface for LLM wire-protocol clients.
 * Enables uniform dispatch across OpenAI Chat, OpenAI Responses, Anthropic Messages, and Google Gemini.
 */
interface LlmClient {
    /**
     * Executes non-streaming opener generation.
     */
    suspend fun generate(request: WingmanLlmRequest): Result<List<String>>

    /**
     * Executes streaming opener generation with SSE chunks.
     */
    fun streamGeneration(request: WingmanLlmRequest): Flow<OpenRouterStreamingService.StreamChunk>

    /**
     * Executes streaming single-turn chat completion (Ask AI / match chat reply).
     */
    fun streamChat(request: ChatLlmRequest): Flow<OpenRouterStreamingService.ChatStreamChunk>
}
