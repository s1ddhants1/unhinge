package io.github.s1ddhants1.unhinge.ai

import android.content.Context
import io.github.s1ddhants1.unhinge.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * AI wingman helper coordinating prompt reply generation, streaming, and in-memory caching.
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

    val LanguageCodeToName = mapOf(
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh" to "Chinese",
        "ar" to "Arabic",
        "hi" to "Hindi",
        "bn" to "Bengali",
        "pa" to "Punjabi",
        "tr" to "Turkish",
        "vi" to "Vietnamese",
        "th" to "Thai",
        "id" to "Indonesian",
        "pl" to "Polish",
        "nl" to "Dutch",
        "sv" to "Swedish",
        "uk" to "Ukrainian"
    )

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

    private fun getCacheKey(text: String, mode: String, targetLanguage: String): String {
        return "${text.hashCode()}_${mode}_${targetLanguage}"
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

    fun generateReplies(
        prompts: List<PromptEntry>,
        targetLanguage: String = "English",
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        scope: CoroutineScope,
        context: Context,
        provider: String = "OpenRouter",
        deeplApiKey: String = "",
        deeplFormality: String = "default",
        useStreaming: Boolean = true,
        candidateId: String = "",
        database: Any? = null,
        systemPrompt: String = "",
    ) {
        generationJob?.cancel()
        _status.value = WingmanStatus.Generating

        // Clear existing suggestions to indicate regeneration
        prompts.forEach { it.suggestedReplyFlow.value = null }

        generationJob = scope.launch(Dispatchers.IO) {
            try {
                val effectiveApiKey = if (provider == "DeepL") deeplApiKey else apiKey
                if (effectiveApiKey.isBlank()) {
                    _status.value = WingmanStatus.Error(context.getString(R.string.ai_error_api_key_required))
                    return@launch
                }

                val effectiveProvider = if (effectiveApiKey.startsWith("AQ.") || effectiveApiKey.startsWith("AIzaSy")) {
                    "Gemini"
                } else {
                    provider
                }

                val effectiveBaseUrl = when {
                    effectiveProvider.equals("Gemini", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                        "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
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

                val effectiveModel = if (effectiveProvider.equals("Gemini", ignoreCase = true) && (model.isBlank() || model.contains("gemini-2.5-flash-lite"))) {
                    "gemini-flash-lite-latest"
                } else {
                    model
                }

                Timber.d("generateReplies: provider=$effectiveProvider, baseUrl=$effectiveBaseUrl, model=$effectiveModel, keyLen=${effectiveApiKey.length}")

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

                val cacheKey = getCacheKey(fullText, mode, targetLanguage)
                val cachedReplies = replyCache[cacheKey]
                if (cachedReplies != null && cachedReplies.size >= nonEmptyEntries.size) {
                    nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                        if (idx < cachedReplies.size) {
                            prompts[originalIndex].suggestedReplyFlow.value = cachedReplies[idx]
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

                if (targetLanguage.isBlank()) {
                    _status.value = WingmanStatus.Error(context.getString(R.string.ai_error_language_required))
                    return@launch
                }

                val fullLanguageName = LanguageCodeToName[targetLanguage]
                    ?: try {
                        Locale.forLanguageTag(targetLanguage).displayLanguage.takeIf { it.isNotBlank() && it != targetLanguage }
                    } catch (e: Exception) {
                        null
                    }
                    ?: targetLanguage

                val result = if (provider == "DeepL") {
                    Timber.d("Using DeepL for translation")
                    DeepLService.translate(
                        text = fullText,
                        targetLanguage = targetLanguage,
                        apiKey = deeplApiKey,
                        formality = deeplFormality,
                    )
                } else if (useStreaming && provider != "Custom") {
                    Timber.d("Using streaming for wingman generation with provider: $provider")
                    var generatedLines: List<String>? = null
                    var hasError = false
                    var errorMessage = ""
                    val contentAccumulator = StringBuilder()

                    OpenRouterStreamingService.streamTranslation(
                        text = fullText,
                        targetLanguage = fullLanguageName,
                        apiKey = effectiveApiKey,
                        baseUrl = effectiveBaseUrl,
                        model = effectiveModel,
                        mode = mode,
                        customSystemPrompt = systemPrompt,
                    ).collect { chunk ->
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
                                Timber.d("Streaming complete with ${chunk.translatedLines.size} lines")
                                generatedLines = chunk.translatedLines
                            }

                            is OpenRouterStreamingService.StreamChunk.Error -> {
                                Timber.e("Streaming error: ${chunk.message}")
                                hasError = true
                                errorMessage = chunk.message
                            }
                        }
                    }

                    if (hasError) {
                        Result.failure(Exception(errorMessage))
                    } else if (generatedLines != null) {
                        Result.success(generatedLines)
                    } else {
                        Result.failure(Exception("No wingman response received"))
                    }
                } else {
                    Timber.d("Using non-streaming for wingman generation")
                    OpenRouterService.translate(
                        text = fullText,
                        targetLanguage = fullLanguageName,
                        apiKey = effectiveApiKey,
                        baseUrl = effectiveBaseUrl,
                        model = effectiveModel,
                        mode = mode,
                        customSystemPrompt = systemPrompt,
                    )
                }

                result.onSuccess { replies ->
                    if (!isCompositionActive) return@onSuccess

                    val cacheKey = getCacheKey(fullText, mode, targetLanguage)
                    replyCache[cacheKey] = replies

                    val expectedCount = nonEmptyEntries.size
                    when {
                        replies.size >= expectedCount -> {
                            nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                                prompts[originalIndex].suggestedReplyFlow.value = replies[idx]
                            }
                            _hasActiveSuggestions.value = true
                            _status.value = WingmanStatus.Success
                        }

                        replies.isNotEmpty() -> {
                            replies.forEachIndexed { idx, reply ->
                                if (idx < nonEmptyEntries.size) {
                                    val originalIndex = nonEmptyEntries[idx].first
                                    prompts[originalIndex].suggestedReplyFlow.value = reply
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
                    val errorMessage = e.message ?: context.getString(R.string.ai_error_translation_failed)
                    _status.value = WingmanStatus.Error(errorMessage)
                }
            }
        }
    }

    sealed class WingmanStatus {
        data object Idle : WingmanStatus()
        data object Generating : WingmanStatus()
        data object Success : WingmanStatus()
        data class Error(val message: String) : WingmanStatus()
    }
}
