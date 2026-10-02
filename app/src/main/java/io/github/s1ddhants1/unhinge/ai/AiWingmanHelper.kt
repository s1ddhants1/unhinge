package io.github.s1ddhants1.unhinge.ai

import android.content.Context
import io.github.s1ddhants1.unhinge.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * AI wingman helper coordinating opener generation, streaming, and in-memory caching.
 */
object AiWingmanHelper {
    private val _status = MutableStateFlow<WingmanStatus>(WingmanStatus.Idle)
    val status: StateFlow<WingmanStatus> = _status.asStateFlow()

    private val _hasActiveSuggestions = MutableStateFlow(false)
    val hasActiveSuggestions: StateFlow<Boolean> = _hasActiveSuggestions.asStateFlow()

    private val _manualTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val manualTrigger: SharedFlow<Unit> = _manualTrigger.asSharedFlow()

    private val _clearSuggestionsTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val clearSuggestionsTrigger: SharedFlow<Unit> = _clearSuggestionsTrigger.asSharedFlow()

    private var generationJob: kotlinx.coroutines.Job? = null
    private var isCompositionActive = true

    // In-memory cache to prevent duplicate model queries during the active session
    private val replyCache = ConcurrentHashMap<String, List<String>>()

    fun setCompositionActive(active: Boolean) {
        isCompositionActive = active
    }

    fun triggerManualGeneration() {
        _manualTrigger.tryEmit(Unit)
    }

    fun triggerClearSuggestions() {
        _clearSuggestionsTrigger.tryEmit(Unit)
        _hasActiveSuggestions.value = false
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        if (_status.value is WingmanStatus.Generating) {
            _status.value = WingmanStatus.Idle
        }
    }

    private fun getCacheKey(
        text: String,
        profileBlock: String,
        systemPrompt: String,
        temperature: Float,
        topP: Float,
        maxTokens: Int,
    ): String {
        return "${text.hashCode()}_${profileBlock.hashCode()}_${systemPrompt.hashCode()}_${temperature}_${topP}_${maxTokens}"
    }

    /**
     * Parses progressive streaming response text into separate prompt suggestions.
     */
    private fun tryParsePartialReplies(content: String, expectedLines: Int): List<String> {
        return content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                line.replace(Regex("^\\d+\\.\\s*"), "")
                    .replace(Regex("^Line\\s+\\d+:\\s*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("^-\\s*"), "")
            }
    }

    fun isApiKeyRequired(provider: String, model: String): Boolean {
        if (provider.equals("Zen", ignoreCase = true)) {
            return !ZenRouter.isFreeModel(model)
        }
        return true
    }

    fun resolveEffectiveEndpoint(
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
    ): Triple<LlmProtocol, String, String> {
        if (provider.equals("Zen", ignoreCase = true)) {
            val trimmed = baseUrl.trim()
            // Only a genuine third-party proxy overrides per-model routing. URLs left behind by
            // another provider (Zen URLs, or an OpenRouter default predating the provider switch)
            // must not hijack the request, mirroring the non-Zen `openrouter.ai` swap below.
            val isCustomProxy =
                trimmed.isNotBlank() &&
                    !ZenRouter.isZenUrl(trimmed) &&
                    !trimmed.contains("openrouter.ai", ignoreCase = true)
            if (isCustomProxy) {
                val protocol = when {
                    trimmed.endsWith("/responses") -> LlmProtocol.OpenAiResponses
                    trimmed.endsWith("/messages") -> LlmProtocol.AnthropicMessages
                    trimmed.contains("/models/") -> LlmProtocol.GoogleGemini
                    else -> LlmProtocol.OpenAiChatCompletions
                }
                val resolvedModel = sanitizeModelId(model).ifBlank { io.github.s1ddhants1.unhinge.Consts.ZEN_DEFAULT_MODEL }
                return Triple(protocol, trimmed, resolvedModel)
            }
            return ZenRouter.resolve(model)
        }
        val protocol = LlmProtocol.infer(provider, apiKey)
        val effectiveProvider = if (protocol == LlmProtocol.GoogleOpenAi) "Gemini" else provider

        val effectiveBaseUrl = when {
            effectiveProvider.equals("Gemini", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                LlmProtocol.GoogleOpenAi.defaultEndpoint()
            effectiveProvider.equals("OpenAI", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                "https://api.openai.com/v1/chat/completions"
            effectiveProvider.equals("Claude", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                "https://api.anthropic.com/v1/messages"
            effectiveProvider.equals("Perplexity", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                "https://api.perplexity.ai/chat/completions"
            effectiveProvider.equals("XAi", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                "https://api.x.ai/v1/chat/completions"
            effectiveProvider.equals("Mistral", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                "https://api.mistral.ai/v1/chat/completions"
            effectiveProvider.equals("Inception", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                "https://api.inceptionlabs.ai/v1/chat/completions"
            else -> baseUrl.ifBlank { OpenRouterDefaultBaseUrl }
        }

        val sanitizedModel = sanitizeModelId(model)
        val effectiveModel = if (effectiveProvider.equals("Gemini", ignoreCase = true) && (sanitizedModel.isBlank() || sanitizedModel.contains("gemini-2.5-flash-lite"))) {
            "gemini-flash-lite-latest"
        } else if (sanitizedModel.isBlank() || sanitizedModel == io.github.s1ddhants1.unhinge.Consts.LEGACY_OPENROUTER_DEFAULT_MODEL) {
            // One-way migration: pre-Oct-2026 stored default no longer ships in the
            // model list and 503s on OpenRouter; move to the current default.
            io.github.s1ddhants1.unhinge.Consts.OPENROUTER_DEFAULT_MODEL
        } else {
            sanitizedModel
        }

        return Triple(protocol, effectiveBaseUrl, effectiveModel)
    }

    fun streamCustomChat(
        userPrompt: String,
        systemPrompt: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        provider: String,
        temperature: Float = io.github.s1ddhants1.unhinge.Consts.DEFAULT_AI_TEMPERATURE,
        topP: Float = io.github.s1ddhants1.unhinge.Consts.DEFAULT_AI_TOP_P,
        maxTokens: Int = io.github.s1ddhants1.unhinge.Consts.DEFAULT_AI_MAX_TOKENS,
    ): Flow<OpenRouterStreamingService.ChatStreamChunk> {
        val (protocol, effectiveBaseUrl, effectiveModel) = resolveEffectiveEndpoint(provider, apiKey, baseUrl, model)
        return when (protocol) {
            LlmProtocol.OpenAiResponses ->
                OpenAiResponsesService.streamChat(
                    systemPrompt, userPrompt, apiKey, effectiveBaseUrl, effectiveModel, temperature, topP, maxTokens,
                )
            LlmProtocol.AnthropicMessages ->
                AnthropicMessagesService.streamChat(
                    systemPrompt, userPrompt, apiKey, effectiveBaseUrl, effectiveModel, temperature, topP, maxTokens,
                )
            LlmProtocol.GoogleGemini ->
                GoogleGeminiService.streamChat(
                    systemPrompt, userPrompt, apiKey, effectiveBaseUrl, effectiveModel, temperature, topP, maxTokens,
                )
            else ->
                OpenRouterStreamingService.streamChat(
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    apiKey = apiKey,
                    baseUrl = effectiveBaseUrl,
                    model = effectiveModel,
                    temperature = temperature,
                    topP = topP,
                    maxTokens = maxTokens,
                )
        }
    }

    fun generateReplies(
        prompts: List<PromptEntry>,
        apiKey: String,
        baseUrl: String,
        model: String,
        scope: CoroutineScope,
        context: Context,
        provider: String = "OpenRouter",
        useStreaming: Boolean = true,
        systemPrompt: String = "",
        profileBlock: String = "",
        forceRefresh: Boolean = false,
        temperature: Float = io.github.s1ddhants1.unhinge.Consts.DEFAULT_AI_TEMPERATURE,
        topP: Float = io.github.s1ddhants1.unhinge.Consts.DEFAULT_AI_TOP_P,
        maxTokens: Int = io.github.s1ddhants1.unhinge.Consts.DEFAULT_AI_MAX_TOKENS,
    ) {
        if (!forceRefresh) {
            generationJob?.cancel()
        }
        _status.value = WingmanStatus.Generating

        prompts.forEach { it.isGeneratingFlow.value = true }
        if (!forceRefresh) {
            prompts.forEach { it.suggestedReplyFlow.value = null }
        }

        generationJob = scope.launch(Dispatchers.IO) {
            try {
                if (isApiKeyRequired(provider, model) && apiKey.isBlank()) {
                    val err = if (provider.equals("Zen", ignoreCase = true)) {
                        context.getString(R.string.ai_error_zen_paid_key_required)
                    } else {
                        context.getString(R.string.ai_error_api_key_required)
                    }
                    _status.value = WingmanStatus.Error(err)
                    return@launch
                }

                val (protocol, effectiveBaseUrl, effectiveModel) =
                    resolveEffectiveEndpoint(provider, apiKey, baseUrl, model)

                Timber.d("generateReplies: protocol=${protocol.wireId}, baseUrl=$effectiveBaseUrl, model=$effectiveModel, keyLen=${apiKey.length}, forceRefresh=$forceRefresh")

                if (prompts.isEmpty()) {
                    _status.value = WingmanStatus.Error(context.getString(R.string.ai_error_no_prompts))
                    return@launch
                }

                val nonEmptyEntries = prompts.mapIndexedNotNull { index, entry ->
                    if (entry.text.isNotBlank()) index to entry else null
                }

                if (nonEmptyEntries.isEmpty()) {
                    _status.value = WingmanStatus.Error(context.getString(R.string.ai_error_prompts_empty))
                    return@launch
                }

                val fullText = nonEmptyEntries.joinToString("\n") { it.second.text }

                val cacheKey = getCacheKey(fullText, profileBlock, systemPrompt, temperature, topP, maxTokens)
                if (!forceRefresh) {
                    val cachedReplies = replyCache[cacheKey]
                    if (cachedReplies != null && cachedReplies.size >= nonEmptyEntries.size) {
                        nonEmptyEntries.forEachIndexed { idx, (_, entry) ->
                            if (idx < cachedReplies.size) {
                                entry.addReply(cachedReplies[idx], selectNew = true)
                            }
                        }
                        _hasActiveSuggestions.value = true
                        _status.value = WingmanStatus.Success

                        delay(3000)
                        if (_status.value is WingmanStatus.Success && isCompositionActive) {
                            _status.value = WingmanStatus.Idle
                        }
                        return@launch
                    }
                } else {
                    replyCache.remove(cacheKey)
                }

                val avoidReplies = if (prompts.size == 1) prompts[0].repliesFlow.value else emptyList()

                val result = if (useStreaming && provider != "Custom") {
                    Timber.d("Using streaming for wingman generation with provider: $provider")
                    var generatedLines: List<String>? = null
                    var hasError = false
                    var errorMessage = ""
                    val contentAccumulator = StringBuilder()

                    streamingGeneration(protocol, fullText, apiKey, effectiveBaseUrl, effectiveModel, systemPrompt, profileBlock, avoidReplies, temperature, topP, maxTokens).collect { chunk ->
                        when (chunk) {
                            is OpenRouterStreamingService.StreamChunk.Content -> {
                                contentAccumulator.append(chunk.text)
                                val partialContent = contentAccumulator.toString()
                                val partialResult = tryParsePartialReplies(partialContent, nonEmptyEntries.size)
                                if (partialResult.isNotEmpty()) {
                                    partialResult.forEachIndexed { idx, reply ->
                                        if (idx < nonEmptyEntries.size && reply.isNotBlank()) {
                                            val originalIndex = nonEmptyEntries[idx].first
                                            prompts[originalIndex].suggestedReplyFlow.value = reply
                                        }
                                    }
                                    _status.value = WingmanStatus.Generating
                                }
                            }

                            is OpenRouterStreamingService.StreamChunk.Complete -> {
                                Timber.d("Streaming complete with ${chunk.generatedLines.size} lines")
                                generatedLines = chunk.generatedLines
                            }

                            is OpenRouterStreamingService.StreamChunk.Error -> {
                                Timber.e("Streaming error: ${chunk.message}")
                                hasError = true
                                errorMessage = chunk.message
                            }
                        }
                    }

                    if (hasError) {
                        if (isFallbackWorthy(errorMessage)) {
                            Timber.d("Streaming failed transiently, falling back to non-streaming")
                            nonStreamingGeneration(
                                protocol = protocol,
                                text = fullText,
                                apiKey = apiKey,
                                baseUrl = effectiveBaseUrl,
                                model = effectiveModel,
                                systemPrompt = systemPrompt,
                                profileBlock = profileBlock,
                                avoidReplies = avoidReplies,
                                temperature = temperature,
                                topP = topP,
                                maxTokens = maxTokens,
                            )
                        } else {
                            Result.failure(Exception(errorMessage))
                        }
                    } else if (generatedLines != null) {
                        Result.success(generatedLines)
                    } else {
                        Result.failure(Exception("No wingman response received"))
                    }
                } else {
                    Timber.d("Using non-streaming for wingman generation")
                    nonStreamingGeneration(
                        protocol = protocol,
                        text = fullText,
                        apiKey = apiKey,
                        baseUrl = effectiveBaseUrl,
                        model = effectiveModel,
                        systemPrompt = systemPrompt,
                        profileBlock = profileBlock,
                        avoidReplies = avoidReplies,
                        temperature = temperature,
                        topP = topP,
                        maxTokens = maxTokens,
                    )
                }

                result.onSuccess { replies ->
                    if (!isCompositionActive) return@onSuccess

                    if (!forceRefresh) {
                        val cacheKey = getCacheKey(fullText, profileBlock, systemPrompt, temperature, topP, maxTokens)
                        replyCache[cacheKey] = replies
                    }

                    val expectedCount = nonEmptyEntries.size
                    when {
                        replies.size >= expectedCount -> {
                            nonEmptyEntries.forEachIndexed { idx, (_, entry) ->
                                entry.addReply(replies[idx], selectNew = true)
                            }
                            _hasActiveSuggestions.value = true
                            _status.value = WingmanStatus.Success
                        }

                        replies.isNotEmpty() -> {
                            replies.forEachIndexed { idx, reply ->
                                if (idx < nonEmptyEntries.size) {
                                    val (_, entry) = nonEmptyEntries[idx]
                                    entry.addReply(reply, selectNew = true)
                                }
                            }
                            _hasActiveSuggestions.value = true
                            _status.value = WingmanStatus.Success
                        }
                    }

                    delay(3000)
                    if (_status.value is WingmanStatus.Success && isCompositionActive) {
                        _status.value = WingmanStatus.Idle
                    }
                }.onFailure { error ->
                    if (!isCompositionActive) return@onFailure
                    val errorMessage = error.message ?: context.getString(R.string.ai_error_unknown)
                    _status.value = WingmanStatus.Error(errorMessage)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException && isCompositionActive) {
                    val errorMessage = e.message ?: context.getString(R.string.ai_error_generation_failed)
                    _status.value = WingmanStatus.Error(errorMessage)
                }
            } finally {
                prompts.forEach { it.isGeneratingFlow.value = false }
            }
        }
    }

    private fun streamingGeneration(
        protocol: LlmProtocol,
        text: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        systemPrompt: String,
        profileBlock: String,
        avoidReplies: List<String>,
        temperature: Float,
        topP: Float,
        maxTokens: Int,
    ): Flow<OpenRouterStreamingService.StreamChunk> =
        when (protocol) {
            LlmProtocol.OpenAiResponses ->
                OpenAiResponsesService.streamGeneration(
                    text = text,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    customSystemPrompt = systemPrompt,
                    profileBlock = profileBlock,
                    avoidReplies = avoidReplies,
                    temperature = temperature,
                    topP = topP,
                    maxTokens = maxTokens,
                )
            LlmProtocol.AnthropicMessages ->
                AnthropicMessagesService.streamGeneration(
                    text = text,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    customSystemPrompt = systemPrompt,
                    profileBlock = profileBlock,
                    avoidReplies = avoidReplies,
                    temperature = temperature,
                    topP = topP,
                    maxTokens = maxTokens,
                )
            LlmProtocol.GoogleGemini ->
                GoogleGeminiService.streamGeneration(
                    text = text,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    customSystemPrompt = systemPrompt,
                    profileBlock = profileBlock,
                    avoidReplies = avoidReplies,
                    temperature = temperature,
                    topP = topP,
                    maxTokens = maxTokens,
                )
            else ->
                OpenRouterStreamingService.streamGeneration(
                    text = text,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    customSystemPrompt = systemPrompt,
                    profileBlock = profileBlock,
                    avoidReplies = avoidReplies,
                    temperature = temperature,
                    topP = topP,
                    maxTokens = maxTokens,
                )
        }

    private suspend fun nonStreamingGeneration(
        protocol: LlmProtocol,
        text: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        systemPrompt: String,
        profileBlock: String,
        avoidReplies: List<String>,
        temperature: Float,
        topP: Float,
        maxTokens: Int,
    ): Result<List<String>> =
        when (protocol) {
            LlmProtocol.OpenAiResponses ->
                OpenAiResponsesService.generate(
                    text = text,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    customSystemPrompt = systemPrompt,
                    profileBlock = profileBlock,
                    avoidReplies = avoidReplies,
                    temperature = temperature,
                    topP = topP,
                    maxTokens = maxTokens,
                )
            LlmProtocol.AnthropicMessages ->
                AnthropicMessagesService.generate(
                    text = text,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    customSystemPrompt = systemPrompt,
                    profileBlock = profileBlock,
                    avoidReplies = avoidReplies,
                    temperature = temperature,
                    topP = topP,
                    maxTokens = maxTokens,
                )
            LlmProtocol.GoogleGemini ->
                GoogleGeminiService.generate(
                    text = text,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    customSystemPrompt = systemPrompt,
                    profileBlock = profileBlock,
                    avoidReplies = avoidReplies,
                    temperature = temperature,
                    topP = topP,
                    maxTokens = maxTokens,
                )
            else ->
                OpenRouterService.generate(
                    text = text,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    customSystemPrompt = systemPrompt,
                    profileBlock = profileBlock,
                    avoidReplies = avoidReplies,
                    temperature = temperature,
                    topP = topP,
                    maxTokens = maxTokens,
                )
        }

    private fun isFallbackWorthy(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("503") ||
            lower.contains("502") ||
            lower.contains("529") ||
            lower.contains("temporarily unavailable") ||
            lower.contains("no available model provider") ||
            lower.contains("no endpoints")
    }

    sealed class WingmanStatus {
        data object Idle : WingmanStatus()
        data object Generating : WingmanStatus()
        data object Success : WingmanStatus()
        data class Error(val message: String) : WingmanStatus()
    }
}
