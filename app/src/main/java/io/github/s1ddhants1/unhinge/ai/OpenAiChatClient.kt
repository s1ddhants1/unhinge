package io.github.s1ddhants1.unhinge.ai

import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.ai.OpenRouterStreamingService.ChatStreamChunk
import io.github.s1ddhants1.unhinge.ai.OpenRouterStreamingService.StreamChunk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Unified client for OpenAI Chat Completions wire protocol (OpenRouter, OpenAI, Zen, Perplexity, xAI, Mistral).
 * Unifies non-streaming generation, streaming opener generation, and single-turn chat streaming.
 */
object OpenAiChatClient : LlmClient {

    override suspend fun generate(request: WingmanLlmRequest): Result<List<String>> =
        withContext(Dispatchers.IO) {
            if (request.text.isBlank()) return@withContext Result.failure(Exception("Input text is empty"))

            val expectedCount = request.effectivePromptCount
            val safeModel = sanitizeModelId(request.model)
            var useStructured = request.structured

            repeat(request.maxRetries) { attempt ->
                try {
                    val body = buildGenerationRequest(
                        text = request.text,
                        model = safeModel,
                        customSystemPrompt = request.customSystemPrompt,
                        profileBlock = request.profileBlock,
                        baseUrl = request.baseUrl.ifBlank { OpenRouterDefaultBaseUrl },
                        avoidReplies = request.avoidReplies,
                        temperature = request.temperature,
                        topP = request.topP,
                        structured = useStructured,
                        reasoningEffort = request.reasoningEffort,
                        promptTemplate = request.promptTemplate,
                        directionalStimulus = request.directionalStimulus,
                        promptCount = expectedCount,
                    )
                    val targetUrl = ZenRouter.normalizeBaseUrl(request.baseUrl.ifBlank { OpenRouterDefaultBaseUrl })
                    val req = Request.Builder()
                        .url(targetUrl)
                        .apply {
                            if (ZenRouter.isZenUrl(targetUrl)) {
                                ZenRouter.injectZenHeaders(this, request.apiKey, safeModel)
                            } else {
                                if (request.apiKey.isNotBlank()) {
                                    addHeader("Authorization", "Bearer ${request.apiKey.trim()}")
                                }
                                addHeader("HTTP-Referer", "https://github.com/s1ddhants1/unhinge")
                                addHeader("X-Title", "Unhinge")
                            }
                        }
                        .addHeader("Content-Type", "application/json")
                        .post(body.toString().toRequestBody(AiHttpClient.jsonMediaType))
                        .build()

                    AiHttpClient.client.newCall(req).execute().use { response ->
                        val responseBody = response.body.string()
                        if (!response.isSuccessful) {
                            ZenRouter.onGatewayResponse(response.code, targetUrl)
                            val structuredRejection = isStructuredOutputError(responseBody, response.code)
                            if (useStructured && structuredRejection) {
                                useStructured = false
                            }
                            val error = friendlyGenerationError(
                                responseBody,
                                response.code,
                                response.message,
                                baseUrl = targetUrl,
                                model = safeModel
                            )
                            val retryable = isTransientHttpCode(response.code) ||
                                (structuredRejection && attempt < request.maxRetries - 1)
                            if (retryable) throw Exception(error)
                            return@withContext Result.failure(Exception(error))
                        }

                        val content = AiHttpClient.json
                            .parseToJsonElement(responseBody)
                            .jsonObject["choices"]
                            ?.jsonArray
                            ?.getOrNull(0)
                            ?.jsonObject
                            ?.get("message")
                            ?.jsonObject
                            ?.get("content")
                            ?.jsonPrimitive
                            ?.contentOrNull
                            .orEmpty()
                        return@withContext parseGeneratedContent(content, expectedCount)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (attempt == request.maxRetries - 1) return@withContext Result.failure(error)
                    delay(1000L * (1 shl attempt))
                }
            }
            Result.failure(Exception("Max retries exceeded"))
        }

    override fun streamGeneration(request: WingmanLlmRequest): Flow<StreamChunk> =
        flow<StreamChunk> {
            if (request.text.isBlank()) {
                emit(StreamChunk.Error("Input text is empty"))
                return@flow
            }

            val expectedCount = request.effectivePromptCount
            val safeModel = sanitizeModelId(request.model)
            var useStructured = request.structured
            var lastError = "Max retries exceeded"

            for (attempt in 0 until request.maxRetries) {
                try {
                    val body = buildGenerationRequest(
                        text = request.text,
                        model = safeModel,
                        customSystemPrompt = request.customSystemPrompt,
                        profileBlock = request.profileBlock,
                        baseUrl = request.baseUrl.ifBlank { OpenRouterDefaultBaseUrl },
                        stream = true,
                        avoidReplies = request.avoidReplies,
                        temperature = request.temperature,
                        topP = request.topP,
                        structured = useStructured,
                        reasoningEffort = request.reasoningEffort,
                        promptTemplate = request.promptTemplate,
                        directionalStimulus = request.directionalStimulus,
                        promptCount = expectedCount,
                    )
                    val targetUrl = ZenRouter.normalizeBaseUrl(request.baseUrl.ifBlank { OpenRouterDefaultBaseUrl })
                    val req = Request.Builder()
                        .url(targetUrl)
                        .apply {
                            if (ZenRouter.isZenUrl(targetUrl)) {
                                ZenRouter.injectZenHeaders(this, request.apiKey, safeModel)
                            } else {
                                if (request.apiKey.isNotBlank()) {
                                    addHeader("Authorization", "Bearer ${request.apiKey.trim()}")
                                }
                                addHeader("HTTP-Referer", "https://github.com/s1ddhants1/unhinge")
                                addHeader("X-Title", "Unhinge")
                            }
                        }
                        .addHeader("Content-Type", "application/json")
                        .post(body.toString().toRequestBody(AiHttpClient.jsonMediaType))
                        .build()

                    var httpFailed = false
                    AiHttpClient.client.newCall(req).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errBody = response.body.string()
                            ZenRouter.onGatewayResponse(response.code, targetUrl)
                            val structuredRejection = isStructuredOutputError(errBody, response.code)
                            if (useStructured && structuredRejection) {
                                useStructured = false
                            }
                            lastError = friendlyGenerationError(
                                errBody,
                                response.code,
                                response.message,
                                baseUrl = targetUrl,
                                model = safeModel
                            )
                            val retryable = isTransientHttpCode(response.code) ||
                                (structuredRejection && attempt < request.maxRetries - 1)
                            if (retryable) {
                                httpFailed = true
                                return@use
                            }
                            emit(StreamChunk.Error(lastError))
                            return@flow
                        }

                        val source = response.body.source()
                        val content = StringBuilder()

                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data: ")) continue
                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") break

                            val delta = runCatching {
                                AiHttpClient.json
                                    .parseToJsonElement(data)
                                    .jsonObject["choices"]
                                    ?.jsonArray
                                    ?.getOrNull(0)
                                    ?.jsonObject
                                    ?.get("delta")
                                    ?.jsonObject
                                    ?.get("content")
                                    ?.jsonPrimitive
                                    ?.contentOrNull
                            }.getOrNull()

                            if (!delta.isNullOrEmpty()) {
                                content.append(delta)
                                emit(StreamChunk.Content(delta))
                            }
                        }

                        parseGeneratedContent(content.toString(), expectedCount)
                            .onSuccess { emit(StreamChunk.Complete(it)) }
                            .onFailure { emit(StreamChunk.Error(it.message ?: "Parsing failed")) }
                    }
                    if (httpFailed) {
                        delay(1000L * (1 shl attempt))
                        continue
                    }
                    return@flow
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.e(error, "Streaming generation failed")
                    lastError = error.message ?: "Unknown error"
                    if (attempt < request.maxRetries - 1 && AiHttpClient.isTransientFailure(error)) {
                        delay(1000L * (1 shl attempt))
                        continue
                    }
                    emit(StreamChunk.Error(lastError))
                    return@flow
                }
            }
            emit(StreamChunk.Error(lastError))
        }.flowOn(Dispatchers.IO)

    override fun streamChat(request: ChatLlmRequest): Flow<ChatStreamChunk> =
        flow<ChatStreamChunk> {
            if (request.userPrompt.isBlank()) {
                emit(ChatStreamChunk.Error("Input prompt is empty"))
                return@flow
            }

            val safeModel = sanitizeModelId(request.model)
            var lastError = "Max retries exceeded"

            for (attempt in 0 until request.maxRetries) {
                try {
                    val body = buildChatStreamRequest(
                        userPrompt = request.userPrompt,
                        systemPrompt = request.systemPrompt,
                        model = safeModel,
                        baseUrl = request.baseUrl.ifBlank { OpenRouterDefaultBaseUrl },
                        temperature = request.temperature,
                        topP = request.topP,
                        reasoningEffort = request.reasoningEffort,
                    )
                    val targetUrl = ZenRouter.normalizeBaseUrl(request.baseUrl.ifBlank { OpenRouterDefaultBaseUrl })
                    val req = Request.Builder()
                        .url(targetUrl)
                        .apply {
                            if (ZenRouter.isZenUrl(targetUrl)) {
                                ZenRouter.injectZenHeaders(this, request.apiKey, safeModel)
                            } else {
                                if (request.apiKey.isNotBlank()) {
                                    addHeader("Authorization", "Bearer ${request.apiKey.trim()}")
                                }
                                addHeader("HTTP-Referer", "https://github.com/s1ddhants1/unhinge")
                                addHeader("X-Title", "Unhinge")
                            }
                        }
                        .addHeader("Content-Type", "application/json")
                        .post(body.toString().toRequestBody(AiHttpClient.jsonMediaType))
                        .build()

                    var httpFailed = false
                    AiHttpClient.client.newCall(req).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errBody = response.body.string()
                            ZenRouter.onGatewayResponse(response.code, targetUrl)
                            lastError = friendlyGenerationError(
                                errBody,
                                response.code,
                                response.message,
                                baseUrl = targetUrl,
                                model = safeModel
                            )
                            if (isTransientHttpCode(response.code)) {
                                httpFailed = true
                                return@use
                            }
                            emit(ChatStreamChunk.Error(lastError))
                            return@flow
                        }

                        val source = response.body.source()
                        val content = StringBuilder()

                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data: ")) continue
                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") break

                            val delta = runCatching {
                                AiHttpClient.json
                                    .parseToJsonElement(data)
                                    .jsonObject["choices"]
                                    ?.jsonArray
                                    ?.getOrNull(0)
                                    ?.jsonObject
                                    ?.get("delta")
                                    ?.jsonObject
                                    ?.get("content")
                                    ?.jsonPrimitive
                                    ?.contentOrNull
                            }.getOrNull()

                            if (!delta.isNullOrEmpty()) {
                                content.append(delta)
                                emit(ChatStreamChunk.Content(delta))
                            }
                        }

                        emit(ChatStreamChunk.Complete(content.toString()))
                    }
                    if (httpFailed) {
                        delay(1000L * (1 shl attempt))
                        continue
                    }
                    return@flow
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.e(error, "Streaming chat failed")
                    lastError = error.message ?: "Unknown error"
                    if (attempt < request.maxRetries - 1 && AiHttpClient.isTransientFailure(error)) {
                        delay(1000L * (1 shl attempt))
                        continue
                    }
                    emit(ChatStreamChunk.Error(lastError))
                    return@flow
                }
            }
            emit(ChatStreamChunk.Error(lastError))
        }.flowOn(Dispatchers.IO)
}

internal fun buildChatStreamRequest(
    userPrompt: String,
    systemPrompt: String,
    model: String,
    baseUrl: String,
    temperature: Float,
    topP: Float,
    reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
) = buildJsonObject {
    put(
        "messages",
        buildJsonArray {
            if (systemPrompt.isNotBlank()) {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    },
                )
            }
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                },
            )
        },
    )
    val safeModel = sanitizeModelId(model)
    if (safeModel.isNotBlank()) put("model", safeModel)
    val isZen = ZenRouter.isZenUrl(baseUrl)
    val sanitizedEffort = ModelReasoningCatalog.sanitizeReasoningEffort(reasoningEffort, safeModel, if (isZen) "Zen" else "")
    if (sanitizedEffort != null) {
        if (sanitizedEffort == "on" || sanitizedEffort == "off") {
            if (baseUrl.contains("openrouter.ai", ignoreCase = true)) {
                put("reasoning", buildJsonObject { put("enabled", sanitizedEffort == "on") })
            }
        } else {
            put("reasoning_effort", sanitizedEffort)
            if (baseUrl.contains("openrouter.ai", ignoreCase = true)) {
                put("reasoning", buildJsonObject { put("effort", sanitizedEffort) })
            }
        }
    }
    if (isZen && ZenRouter.isFreeModel(safeModel)) {
        put("tools", ZenRouter.DefaultZenChatTools as JsonElement)
    }
    put("stream", true)
    if (isZen) {
        put("stream_options", buildJsonObject { put("include_usage", true) })
    }
}
