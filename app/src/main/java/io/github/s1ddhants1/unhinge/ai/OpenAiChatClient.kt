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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

object OpenAiChatClient : LlmClient {

    private fun buildPost(
        targetUrl: String,
        apiKey: String,
        model: String,
        bodyJson: String,
    ): Request =
        Request.Builder()
            .url(targetUrl)
            .apply {
                if (ZenRouter.isZenUrl(targetUrl)) {
                    ZenRouter.injectZenHeaders(this, apiKey, model)
                } else {
                    if (apiKey.isNotBlank()) {
                        addHeader("Authorization", "Bearer ${apiKey.trim()}")
                    }
                    addHeader("HTTP-Referer", "https://github.com/s1ddhants1/unhinge")
                    addHeader("X-Title", "Unhinge")
                }
            }
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(AiHttpClient.jsonMediaType))
            .build()

    private fun extractChatDelta(data: String): String? =
        runCatching {
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

    private inline fun readChatSseDeltas(
        response: Response,
        onDelta: (String) -> Unit,
    ): String {
        val source = response.body.source()
        val content = StringBuilder()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data: ")) continue
            val data = line.removePrefix("data: ").trim()
            if (data == "[DONE]") break
            val delta = extractChatDelta(data)
            if (!delta.isNullOrEmpty()) {
                content.append(delta)
                onDelta(delta)
            }
        }
        return content.toString()
    }

    override suspend fun generate(request: WingmanLlmRequest): Result<List<String>> =
        withContext(Dispatchers.IO) {
            if (request.text.isBlank()) return@withContext Result.failure(Exception("Input text is empty"))

            val expectedCount = request.effectivePromptCount
            val targetUrl = ZenRouter.normalizeBaseUrl(request.baseUrl.ifBlank { OpenRouterDefaultBaseUrl })
            val isZen = ZenRouter.isZenUrl(targetUrl)
            val safeModel = if (isZen) ZenRouter.normalizeModelId(request.model) else sanitizeModelId(request.model)
            var useStructured = request.structured

            repeat(request.maxRetries) { attempt ->
                try {
                    val body = buildGenerationRequest(
                        text = request.text,
                        model = safeModel,
                        customSystemPrompt = request.customSystemPrompt,
                        profileBlock = request.profileBlock,
                        baseUrl = targetUrl,
                        stream = isZen && ZenRouter.isFreeModel(safeModel),
                        avoidReplies = request.avoidReplies,
                        temperature = request.temperature,
                        topP = request.topP,
                        structured = useStructured,
                        reasoningEffort = request.reasoningEffort,
                        promptTemplate = request.promptTemplate,
                        directionalStimulus = request.directionalStimulus,
                        promptCount = expectedCount,
                    )
                    val req = buildPost(targetUrl, request.apiKey, safeModel, body.toString())

                    AiHttpClient.clientFor(targetUrl).newCall(req).execute().use { response ->
                        if (!response.isSuccessful) {
                            val responseBody = response.body.string()
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

                        val isStreamResponse = body["stream"]?.jsonPrimitive?.booleanOrNull == true ||
                            response.header("Content-Type")?.contains("text/event-stream", ignoreCase = true) == true
                        val content = if (isStreamResponse) {
                            readChatSseDeltas(response) {}
                        } else {
                            val responseBody = response.body.string()
                            AiHttpClient.json
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
                        }
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
            val targetUrl = ZenRouter.normalizeBaseUrl(request.baseUrl.ifBlank { OpenRouterDefaultBaseUrl })
            val isZen = ZenRouter.isZenUrl(targetUrl)
            val safeModel = if (isZen) ZenRouter.normalizeModelId(request.model) else sanitizeModelId(request.model)
            var useStructured = request.structured
            var lastError = "Max retries exceeded"

            for (attempt in 0 until request.maxRetries) {
                try {
                    val body = buildGenerationRequest(
                        text = request.text,
                        model = safeModel,
                        customSystemPrompt = request.customSystemPrompt,
                        profileBlock = request.profileBlock,
                        baseUrl = targetUrl,
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
                    val req = buildPost(targetUrl, request.apiKey, safeModel, body.toString())

                    var httpFailed = false
                    AiHttpClient.clientFor(targetUrl).newCall(req).execute().use { response ->
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

                        val content = readChatSseDeltas(response) { delta ->
                            emit(StreamChunk.Content(delta))
                        }

                        parseGeneratedContent(content, expectedCount)
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

            val targetUrl = ZenRouter.normalizeBaseUrl(request.baseUrl.ifBlank { OpenRouterDefaultBaseUrl })
            val isZen = ZenRouter.isZenUrl(targetUrl)
            val safeModel = if (isZen) ZenRouter.normalizeModelId(request.model) else sanitizeModelId(request.model)
            var lastError = "Max retries exceeded"

            for (attempt in 0 until request.maxRetries) {
                try {
                    val body = buildChatStreamRequest(
                        userPrompt = request.userPrompt,
                        systemPrompt = request.systemPrompt,
                        model = safeModel,
                        baseUrl = targetUrl,
                        temperature = request.temperature,
                        topP = request.topP,
                        reasoningEffort = request.reasoningEffort,
                    )
                    val req = buildPost(targetUrl, request.apiKey, safeModel, body.toString())

                    var httpFailed = false
                    AiHttpClient.clientFor(targetUrl).newCall(req).execute().use { response ->
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

                        val content = readChatSseDeltas(response) { delta ->
                            emit(ChatStreamChunk.Content(delta))
                        }

                        emit(ChatStreamChunk.Complete(content))
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
    val isZen = ZenRouter.isZenUrl(baseUrl)
    val safeModel = if (isZen) ZenRouter.normalizeModelId(model) else sanitizeModelId(model)
    val effectiveSystem = if (isZen) ZenRouter.withZenEnvironment(systemPrompt) else systemPrompt
    put(
        "messages",
        buildJsonArray {
            if (effectiveSystem.isNotBlank()) {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", effectiveSystem)
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
    if (safeModel.isNotBlank()) put("model", safeModel)
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
