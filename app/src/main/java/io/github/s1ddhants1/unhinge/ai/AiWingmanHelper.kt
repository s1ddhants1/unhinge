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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    fun isApiKeyRequired(provider: String, model: String = ""): Boolean =
        AiProviderPresets.isApiKeyRequired(provider, model)

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
        val systemPrompt = WingmanPrompts.systemPrompt(
            lineCount = 1,
            custom = prefs?.aiCustomSystemPrompt.orEmpty(),
            prefs = prefs,
            context = context,
        )

        entry.isSteeringFlow.value = true
        entry.addFollowUpUserMessage(steerInstruction)
        entry.addFollowUpAssistantMessage("")

        scope.launch(Dispatchers.IO) {
            try {
                val result = AiWingmanClient.requestStructuredAi(
                    provider = provider,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    temperature = temperature.toDouble(),
                    topP = topP.toDouble(),
                    reasoningEffort = reasoningEffort,
                )
                result.fold(
                    onSuccess = { raw ->
                        val lines = AiWingmanClient.parseJsonLinesPayload(raw)
                        val reply = lines?.firstOrNull() ?: raw.trim().removeSurrounding("\"")
                        entry.updateLastAssistantMessage(reply)
                        entry.addReply(reply, selectNew = true)
                        entry.isSteeringFlow.value = false
                        onComplete?.invoke(reply)
                    },
                    onFailure = { error ->
                        val errorMsg = error.aiFailure().userFriendlyMessage(context)
                        entry.updateLastAssistantMessage("Error: $errorMsg")
                        entry.isSteeringFlow.value = false
                    }
                )
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
                    val err = safeErrorString(context, R.string.ai_error_api_key_required, "API key is required")
                    _status.value = WingmanStatus.Error(err)
                    return@launch
                }

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
                val effectiveTemplate = PromptRepository.getEffectiveSystemPromptTemplate(prefs, context)
                val cacheKey = AiWingmanCache.createKey(
                    text = fullText,
                    profileBlock = profileBlock,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    topP = topP,
                    directionalStimulus = directionalStimulus,
                    template = effectiveTemplate,
                )

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
                val effectiveSystemPrompt = WingmanPrompts.systemPrompt(
                    lineCount = nonEmptyEntries.size,
                    custom = systemPrompt.ifBlank { prefs?.aiCustomSystemPrompt.orEmpty() },
                    prefs = prefs,
                    context = context,
                )
                val userPrompt = WingmanPrompts.openerUserPrompt(
                    text = fullText,
                    profileBlock = profileBlock,
                    avoidReplies = avoidReplies,
                    prefs = prefs,
                    context = context,
                    directionalStimulus = directionalStimulus,
                    promptCount = nonEmptyEntries.size,
                )

                val result = AiWingmanClient.requestStructuredAi(
                    provider = provider,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    systemPrompt = effectiveSystemPrompt,
                    userPrompt = userPrompt,
                    temperature = temperature.toDouble(),
                    topP = topP.toDouble(),
                    reasoningEffort = reasoningEffort,
                )

                result.fold(
                    onSuccess = { raw ->
                        if (!isCompositionActive) return@fold
                        val replies = AiWingmanClient.parseJsonLinesPayload(raw)
                            ?: tryParsePartialReplies(raw, nonEmptyEntries.size)
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
                            else -> {
                                _status.value = WingmanStatus.Error("Failed to parse reply suggestions")
                            }
                        }
                        delay(3000)
                        if (_status.value is WingmanStatus.Success && isCompositionActive) {
                            _status.value = WingmanStatus.Idle
                        }
                    },
                    onFailure = { error ->
                        if (!isCompositionActive) return@fold
                        val errorMessage = error.aiFailure().userFriendlyMessage(context)
                        _status.value = WingmanStatus.Error(errorMessage)
                    }
                )
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
                    val err = safeErrorString(context, R.string.ai_error_api_key_required, "API key is required")
                    _status.value = WingmanStatus.Error(err)
                    return@launch
                }

                val historyText = formatConversationHistory(matchName, conversationHistory)
                val systemPrompt = WingmanPrompts.chatReplySystemPrompt(
                    replyCount = prompts.size,
                    custom = prefs?.aiCustomSystemPrompt.orEmpty(),
                    prefs = prefs,
                    context = context,
                )
                val effectiveTemplate = PromptRepository.getEffectiveChatReplyTemplate(prefs, context)
                val cacheKey = AiWingmanCache.createKey(
                    text = "chat_${matchName}_${historyText.hashCode()}",
                    profileBlock = profileBlock,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    topP = topP,
                    directionalStimulus = directionalStimulus,
                    template = effectiveTemplate,
                )

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

                val result = AiWingmanClient.requestStructuredAi(
                    provider = provider,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    temperature = temperature.toDouble(),
                    topP = topP.toDouble(),
                    reasoningEffort = reasoningEffort,
                )

                result.fold(
                    onSuccess = { raw ->
                        if (!isCompositionActive) return@fold
                        val parsedReplies = AiWingmanClient.parseJsonLinesPayload(raw)
                            ?: tryParsePartialReplies(raw, prompts.size)

                        if (parsedReplies.isNotEmpty()) {
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
                        } else {
                            _status.value = WingmanStatus.Error("Failed to parse reply suggestions")
                        }
                    },
                    onFailure = { error ->
                        if (!isCompositionActive) return@fold
                        val errorMessage = error.aiFailure().userFriendlyMessage(context)
                        _status.value = WingmanStatus.Error(errorMessage)
                    }
                )
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
