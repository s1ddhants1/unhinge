package io.github.s1ddhants1.unhinge.ai

import android.content.Context
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.R
import io.github.s1ddhants1.unhinge.model.ChatMessageRecord
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * High-level coordinator orchestrating dating prompt and match chat reply generation.
 * Manages request lifecycles, streaming state transitions, in-memory caching, and fallback policies.
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

    private var generationJob: Job? = null
    private var isCompositionActive = true

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

    fun isApiKeyRequired(provider: String, model: String): Boolean =
        LlmProtocolRouter.isApiKeyRequired(provider, model)

    fun resolveEffectiveEndpoint(
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
    ): Triple<LlmProtocol, String, String> =
        LlmProtocolRouter.resolveEffectiveEndpoint(provider, apiKey, baseUrl, model)

    fun streamCustomChat(
        userPrompt: String,
        systemPrompt: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        provider: String,
        temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
        topP: Float = Consts.DEFAULT_AI_TOP_P,
        reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
    ): Flow<OpenRouterStreamingService.ChatStreamChunk> {
        val (protocol, effectiveBaseUrl, effectiveModel) =
            resolveEffectiveEndpoint(provider, apiKey, baseUrl, model)
        val client = LlmClientRegistry.getClient(protocol)
        return client.streamChat(
            ChatLlmRequest(
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                apiKey = apiKey,
                baseUrl = effectiveBaseUrl,
                model = effectiveModel,
                temperature = temperature,
                topP = topP,
                reasoningEffort = reasoningEffort,
            ),
        )
    }

    fun steerOpener(
        entry: PromptEntry,
        currentOpener: String,
        steerInstruction: String,
        profileBlock: String = "",
        apiKey: String,
        baseUrl: String,
        model: String,
        scope: CoroutineScope,
        context: Context,
        prefs: PreferencesManager? = null,
        provider: String = "OpenRouter",
        temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
        topP: Float = Consts.DEFAULT_AI_TOP_P,
        reasoningEffort: String = prefs?.aiReasoningEffort ?: Consts.DEFAULT_AI_REASONING_EFFORT,
        onComplete: ((String) -> Unit)? = null,
    ) {
        val userPrompt = WingmanPrompts.steerOpenerPrompt(
            profileBlock = profileBlock,
            promptText = entry.text,
            currentOpener = currentOpener,
            steerInstruction = steerInstruction,
        )
        val systemPrompt = WingmanPrompts.openerSystemPrompt(
            lineCount = 1,
            custom = prefs?.aiCustomSystemPrompt.orEmpty(),
            prefs = prefs,
            context = context,
        )

        entry.isSteeringFlow.value = true
        entry.addFollowUpUserMessage(steerInstruction)
        entry.addFollowUpAssistantMessage("")

        scope.launch(Dispatchers.IO) {
            val contentAccumulator = StringBuilder()
            try {
                streamCustomChat(
                    userPrompt = userPrompt,
                    systemPrompt = systemPrompt,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    provider = provider,
                    temperature = temperature,
                    topP = topP,
                    reasoningEffort = reasoningEffort,
                ).collect { chunk ->
                    when (chunk) {
                        is OpenRouterStreamingService.ChatStreamChunk.Content -> {
                            contentAccumulator.append(chunk.text)
                            entry.updateLastAssistantMessage(contentAccumulator.toString())
                        }
                        is OpenRouterStreamingService.ChatStreamChunk.Complete -> {
                            val finalReply = (chunk.fullText.ifBlank { contentAccumulator.toString() })
                                .trim()
                                .removeSurrounding("\"")
                            entry.updateLastAssistantMessage(finalReply)
                            entry.addReply(finalReply, selectNew = true)
                            entry.isSteeringFlow.value = false
                            onComplete?.invoke(finalReply)
                        }
                        is OpenRouterStreamingService.ChatStreamChunk.Error -> {
                            entry.updateLastAssistantMessage("Error: ${chunk.message}")
                            entry.isSteeringFlow.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                entry.updateLastAssistantMessage("Error: ${e.message ?: "Failed to steer opener"}")
                entry.isSteeringFlow.value = false
            }
        }
    }

    private fun safeErrorString(context: Context, id: Int, fallback: String): String =
        try {
            context.getString(id)
        } catch (_: Throwable) {
            fallback
        }


    fun generateReplies(
        prompts: List<PromptEntry>,
        apiKey: String,
        baseUrl: String,
        model: String,
        scope: CoroutineScope,
        context: Context,
        prefs: PreferencesManager? = null,
        provider: String = "OpenRouter",
        useStreaming: Boolean = true,
        systemPrompt: String = "",
        profileBlock: String = "",
        forceRefresh: Boolean = false,
        temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
        topP: Float = Consts.DEFAULT_AI_TOP_P,
        reasoningEffort: String = prefs?.aiReasoningEffort ?: Consts.DEFAULT_AI_REASONING_EFFORT,
        directionalStimulus: String = "",
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
                        safeErrorString(context, R.string.ai_error_zen_paid_key_required, "An API key is required for paid Zen models. Switch to a free model or set an API key in Settings.")
                    } else {
                        safeErrorString(context, R.string.ai_error_api_key_required, "API key is required")
                    }
                    _status.value = WingmanStatus.Error(err)
                    return@launch
                }

                val (protocol, effectiveBaseUrl, effectiveModel) =
                    resolveEffectiveEndpoint(provider, apiKey, baseUrl, model)

                Timber.d("generateReplies: protocol=${protocol.wireId}, baseUrl=$effectiveBaseUrl, model=$effectiveModel, keyLen=${apiKey.length}, forceRefresh=$forceRefresh, vibe=$directionalStimulus")

                if (prompts.isEmpty()) {
                    _status.value = WingmanStatus.Error(safeErrorString(context, R.string.ai_error_no_prompts, "No prompts available"))
                    return@launch
                }

                val nonEmptyEntries = prompts.mapIndexedNotNull { index, entry ->
                    if (entry.text.isNotBlank()) index to entry else null
                }

                if (nonEmptyEntries.isEmpty()) {
                    _status.value = WingmanStatus.Error(safeErrorString(context, R.string.ai_error_prompts_empty, "Prompts are empty"))
                    return@launch
                }

                val fullText = nonEmptyEntries.joinToString("\n") { it.second.text }
                val cacheKey = AiWingmanCache.createKey(fullText, profileBlock, systemPrompt, temperature, topP, directionalStimulus)

                if (!forceRefresh) {
                    val cachedReplies = AiWingmanCache.get(cacheKey)
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
                    AiWingmanCache.remove(cacheKey)
                }

                val avoidReplies = if (prompts.size == 1) prompts[0].repliesFlow.value else emptyList()
                val promptTemplate = prefs?.aiOpenerPromptTemplate?.takeIf { it.isNotBlank() }

                val llmRequest = WingmanLlmRequest(
                    text = fullText,
                    apiKey = apiKey,
                    baseUrl = effectiveBaseUrl,
                    model = effectiveModel,
                    customSystemPrompt = systemPrompt,
                    profileBlock = profileBlock,
                    avoidReplies = avoidReplies,
                    temperature = temperature,
                    topP = topP,
                    reasoningEffort = reasoningEffort,
                    promptTemplate = promptTemplate,
                    directionalStimulus = directionalStimulus,
                    promptCount = nonEmptyEntries.size,
                )

                val client = LlmClientRegistry.getClient(protocol)
                val result = if (useStreaming && provider != "Custom") {
                    Timber.d("Using streaming for wingman generation with provider: $provider")
                    var generatedLines: List<String>? = null
                    var hasError = false
                    var errorMessage = ""
                    val contentAccumulator = StringBuilder()

                    client.streamGeneration(llmRequest).collect { chunk ->
                        when (chunk) {
                            is OpenRouterStreamingService.StreamChunk.Content -> {
                                contentAccumulator.append(chunk.text)
                                val partialResult = tryParsePartialReplies(contentAccumulator.toString(), nonEmptyEntries.size)
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
                        val isFreeTierRestriction = isFreeTierError(errorMessage) && effectiveModel != Consts.ZEN_DEFAULT_MODEL
                        if (isFreeTierRestriction) {
                            fallbackToSpaceBunny(context, prefs)
                            val fallbackReq = llmRequest.copy(
                                baseUrl = Consts.ZEN_DEFAULT_BASE_URL,
                                model = Consts.ZEN_DEFAULT_MODEL,
                            )
                            OpenAiChatClient.generate(fallbackReq)
                        } else if (isFallbackWorthy(errorMessage)) {
                            Timber.d("Streaming failed transiently, falling back to non-streaming")
                            client.generate(llmRequest)
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
                    val initialResult = client.generate(llmRequest)
                    if (initialResult.isFailure) {
                        val errMsg = initialResult.exceptionOrNull()?.message.orEmpty()
                        if (isFreeTierError(errMsg) && effectiveModel != Consts.ZEN_DEFAULT_MODEL) {
                            fallbackToSpaceBunny(context, prefs)
                            val fallbackReq = llmRequest.copy(
                                baseUrl = Consts.ZEN_DEFAULT_BASE_URL,
                                model = Consts.ZEN_DEFAULT_MODEL,
                            )
                            OpenAiChatClient.generate(fallbackReq)
                        } else {
                            initialResult
                        }
                    } else {
                        initialResult
                    }
                }

                result.onSuccess { replies ->
                    if (!isCompositionActive) return@onSuccess
                    if (!forceRefresh) {
                        AiWingmanCache.put(cacheKey, replies)
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
                    val errorMessage = error.message ?: safeErrorString(context, R.string.ai_error_unknown, "An unknown error occurred")
                    _status.value = WingmanStatus.Error(errorMessage)
                }
            } catch (e: Exception) {
                if (e !is CancellationException && isCompositionActive) {
                    val errorMessage = e.message ?: safeErrorString(context, R.string.ai_error_generation_failed, "Generation failed")
                    _status.value = WingmanStatus.Error(errorMessage)
                }
            } finally {
                prompts.forEach { it.isGeneratingFlow.value = false }
            }
        }
    }

    fun formatConversationHistory(matchName: String, messages: List<ChatMessageRecord>): String {
        if (messages.isEmpty()) return "No messages sent yet. (Fresh match!)"
        val displayName = matchName.ifBlank { "Match" }
        return messages.takeLast(15).joinToString("\n") { msg ->
            val sender = if (msg.sentBySubject) displayName else "You"
            val content = when {
                msg.isVoiceNote && msg.voiceTranscript.isNotBlank() -> "[Voice note: \"${msg.voiceTranscript}\"]"
                msg.isVoiceNote -> "[Sent a voice note]"
                else -> msg.body
            }
            "$sender: $content"
        }
    }

    fun generateChatReplies(
        matchName: String,
        profileBlock: String,
        conversationHistory: List<ChatMessageRecord>,
        prompts: List<PromptEntry>,
        apiKey: String,
        baseUrl: String,
        model: String,
        scope: CoroutineScope,
        context: Context,
        prefs: PreferencesManager? = null,
        provider: String = "OpenRouter",
        useStreaming: Boolean = true,
        forceRefresh: Boolean = false,
        temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
        topP: Float = Consts.DEFAULT_AI_TOP_P,
        reasoningEffort: String = prefs?.aiReasoningEffort ?: Consts.DEFAULT_AI_REASONING_EFFORT,
        directionalStimulus: String = "",
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
                        safeErrorString(context, R.string.ai_error_zen_paid_key_required, "An API key is required for paid Zen models. Switch to a free model or set an API key in Settings.")
                    } else {
                        safeErrorString(context, R.string.ai_error_api_key_required, "API key is required")
                    }
                    _status.value = WingmanStatus.Error(err)
                    return@launch
                }

                val (protocol, effectiveBaseUrl, effectiveModel) =
                    resolveEffectiveEndpoint(provider, apiKey, baseUrl, model)

                val historyText = formatConversationHistory(matchName, conversationHistory)
                val cacheKey = AiWingmanCache.createKey("chat_${matchName}_${historyText.hashCode()}", profileBlock, directionalStimulus, temperature, topP)

                if (!forceRefresh) {
                    val cachedReplies = AiWingmanCache.get(cacheKey)
                    if (cachedReplies != null && cachedReplies.size >= prompts.size) {
                        prompts.forEachIndexed { idx, entry ->
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
                    AiWingmanCache.remove(cacheKey)
                }

                val avoidReplies = prompts.flatMap { it.repliesFlow.value }
                val systemPrompt = WingmanPrompts.chatReplySystemPrompt(
                    custom = prefs?.aiCustomSystemPrompt.orEmpty(),
                    prefs = prefs,
                    context = context,
                )
                val userPrompt = WingmanPrompts.chatReplyUserPrompt(
                    matchName = matchName,
                    conversationHistoryText = historyText,
                    profileBlock = profileBlock,
                    avoidReplies = avoidReplies,
                    prefs = prefs,
                    context = context,
                    directionalStimulus = directionalStimulus,
                    replyCount = prompts.size,
                )

                val contentAccumulator = StringBuilder()
                var hasError = false
                var errorMessage = ""

                streamCustomChat(
                    userPrompt = userPrompt,
                    systemPrompt = systemPrompt,
                    apiKey = apiKey,
                    baseUrl = effectiveBaseUrl,
                    model = effectiveModel,
                    provider = provider,
                    temperature = temperature,
                    topP = topP,
                    reasoningEffort = reasoningEffort,
                ).collect { chunk ->
                    when (chunk) {
                        is OpenRouterStreamingService.ChatStreamChunk.Content -> {
                            contentAccumulator.append(chunk.text)
                            val partialResult = tryParsePartialReplies(contentAccumulator.toString(), prompts.size)
                            if (partialResult.isNotEmpty()) {
                                partialResult.forEachIndexed { idx, reply ->
                                    if (idx < prompts.size && reply.isNotBlank()) {
                                        prompts[idx].suggestedReplyFlow.value = reply
                                    }
                                }
                                _status.value = WingmanStatus.Generating
                            }
                        }
                        is OpenRouterStreamingService.ChatStreamChunk.Complete -> {
                            // Handled after collection
                        }
                        is OpenRouterStreamingService.ChatStreamChunk.Error -> {
                            hasError = true
                            errorMessage = chunk.message
                        }
                    }
                }

                val finalFullText = contentAccumulator.toString()
                val parsedReplies = tryParsePartialReplies(finalFullText, prompts.size)

                if (parsedReplies.isNotEmpty() && !hasError) {
                    parsedReplies.forEachIndexed { idx, reply ->
                        if (idx < prompts.size) {
                            prompts[idx].addReply(reply, selectNew = true)
                        }
                    }
                    AiWingmanCache.put(cacheKey, parsedReplies)
                    _hasActiveSuggestions.value = true
                    _status.value = WingmanStatus.Success
                    delay(3000)
                    if (_status.value is WingmanStatus.Success && isCompositionActive) {
                        _status.value = WingmanStatus.Idle
                    }
                } else if (hasError) {
                    if (!isCompositionActive) return@launch
                    _status.value = WingmanStatus.Error(errorMessage)
                } else {
                    _status.value = WingmanStatus.Error("Failed to parse reply suggestions")
                }
            } catch (e: Exception) {
                if (e !is CancellationException && isCompositionActive) {
                    val msg = e.message ?: safeErrorString(context, R.string.ai_error_generation_failed, "Generation failed")
                    _status.value = WingmanStatus.Error(msg)
                }
            } finally {
                prompts.forEach { it.isGeneratingFlow.value = false }
            }
        }
    }

    private fun isFreeTierError(msg: String): Boolean =
        msg.contains("FreeTierError", ignoreCase = true) ||
            msg.contains("within OpenCode", ignoreCase = true) ||
            msg.contains("free-tier restriction", ignoreCase = true)

    private fun fallbackToSpaceBunny(context: Context, prefs: PreferencesManager?) {
        Timber.d("Model restricted on free tier, falling back to space-bunny-free")
        runCatching {
            prefs?.let { p ->
                p.openRouterModel = Consts.ZEN_DEFAULT_MODEL
                p.saveToFallbackStorageAsync(context)
            }
            val sp = context.getSharedPreferences(Consts.PREFS_SETTINGS, Context.MODE_PRIVATE)
            sp.edit().putString(Consts.PREF_OPENROUTER_MODEL, Consts.ZEN_DEFAULT_MODEL).apply()
        }
    }

    private fun isFallbackWorthy(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("503") ||
            lower.contains("502") ||
            lower.contains("529") ||
            lower.contains("temporarily unavailable") ||
            lower.contains("no available model provider") ||
            lower.contains("no endpoints") ||
            lower.contains("empty response") ||
            lower.contains("parse") ||
            lower.contains("freetiererror") ||
            lower.contains("free-tier restriction") ||
            lower.contains("within opencode")
    }

    private fun tryParsePartialReplies(content: String, expectedLines: Int): List<String> {
        val trimmed = content.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.contains("\"lines\"")) {
            val results = mutableListOf<String>()
            var inArray = false
            var inString = false
            var escape = false
            val cur = StringBuilder()
            for (c in trimmed) {
                if (!inArray) {
                    if (c == '[') inArray = true
                    continue
                }
                if (c == ']' && !inString) {
                    inArray = false
                    break
                }
                if (!inString) {
                    if (c == '"') {
                        inString = true
                        cur.clear()
                    }
                } else {
                    if (escape) {
                        cur.append(c)
                        escape = false
                    } else if (c == '\\') {
                        escape = true
                    } else if (c == '"') {
                        inString = false
                        results.add(cur.toString())
                        cur.clear()
                    } else {
                        cur.append(c)
                    }
                }
            }
            if (inString && cur.isNotEmpty()) {
                results.add(cur.toString())
            }
            if (results.isNotEmpty()) {
                return results
            }
        }
        return content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                line.replace(Regex("^\\d+\\.\\s*"), "")
                    .replace(Regex("^Line\\s+\\d+:\\s*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("^-\\s*"), "")
            }
    }

    sealed class WingmanStatus {
        data object Idle : WingmanStatus()
        data object Generating : WingmanStatus()
        data object Success : WingmanStatus()
        data class Error(val message: String) : WingmanStatus()
    }
}
