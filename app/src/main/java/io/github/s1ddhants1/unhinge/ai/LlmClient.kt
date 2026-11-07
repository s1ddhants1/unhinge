package io.github.s1ddhants1.unhinge.ai

import kotlinx.coroutines.flow.Flow

interface LlmClient {
    suspend fun generate(request: WingmanLlmRequest): Result<List<String>>
    fun streamGeneration(request: WingmanLlmRequest): Flow<OpenRouterStreamingService.StreamChunk>
    fun streamChat(request: ChatLlmRequest): Flow<OpenRouterStreamingService.ChatStreamChunk>
}
