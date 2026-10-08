package io.github.s1ddhants1.unhinge.ai

import io.github.s1ddhants1.unhinge.Consts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val responsesJson = Json { ignoreUnknownKeys = true }

object OpenAiResponsesService {
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generate(
        text: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        maxRetries: Int = 3,
        customSystemPrompt: String = "",
        profileBlock: String = "",
        avoidReplies: List<String> = emptyList(),
        temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
        topP: Float = Consts.DEFAULT_AI_TOP_P,
        reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
        promptTemplate: String? = null,
        directionalStimulus: String = "",
        promptCount: Int? = null,
    ): Result<List<String>> =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext Result.failure(Exception("Input text is empty"))
            val expectedCount = promptCount ?: text.lines().size
            val safeModel = ZenRouter.normalizeModelId(model)
            val effectiveBaseUrl = baseUrl.ifBlank { ZenResponsesDefaultBaseUrl }
            val isZen = ZenRouter.isZenUrl(effectiveBaseUrl)
            val isFree = isZen && ZenRouter.isFreeModel(safeModel)
            repeat(maxRetries) { attempt ->
                try {
                    val sessionId = ZenRouter.generateSessionId()
                    val body =
                        buildResponsesRequest(
                            instructions = WingmanPrompts.openerSystemPrompt(expectedCount, customSystemPrompt),
                            input = WingmanPrompts.openerUserPrompt(
                                text = text,
                                avoidReplies = avoidReplies,
                                profileBlock = profileBlock,
                                template = promptTemplate,
                                directionalStimulus = directionalStimulus,
                                promptCount = expectedCount,
                            ),
                            model = safeModel,
                            temperature = temperature,
                            topP = topP,
                            stream = isFree,
                            reasoningEffort = reasoningEffort,
                            baseUrl = effectiveBaseUrl,
                            sessionId = sessionId,
                        )
                    val request = buildPost(effectiveBaseUrl, apiKey, body.toString(), safeModel, sessionId)
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val responseBody = response.body.string()
                            val error = friendlyGenerationError(responseBody, response.code, response.message, baseUrl = effectiveBaseUrl, model = safeModel)
                            if (isTransientHttpCode(response.code) && attempt < maxRetries - 1) throw Exception(error)
                            return@withContext Result.failure(Exception(error))
                        }
                        val contentType = response.header("Content-Type").orEmpty()
                        var incompleteReason: String? = null
                        val content = if (contentType.contains("text/event-stream") || isFree) {
                            val sb = StringBuilder()
                            response.body.byteStream().bufferedReader().use { reader ->
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (!line.startsWith("data: ")) continue
                                    val data = line.removePrefix("data: ").trim()
                                    if (data.isEmpty() || data == "[DONE]") continue
                                    val element = runCatching { responsesJson.parseToJsonElement(data) }.getOrNull() ?: continue
                                    val delta = extractResponsesDelta(element)
                                    if (!delta.isNullOrEmpty()) {
                                        sb.append(delta)
                                    } else {
                                        extractIncompleteReason(element)?.let { incompleteReason = it }
                                    }
                                }
                            }
                            sb.toString()
                        } else {
                            val responseBody = response.body.string()
                            extractResponsesText(responseBody).orEmpty()
                        }
                        if (content.isBlank()) {
                            val errMsg = if (incompleteReason == "max_output_tokens") {
                                "Model token limit reached during reasoning"
                            } else {
                                "Model returned an empty response"
                            }
                            return@withContext Result.failure(Exception(errMsg))
                        }
                        return@withContext parseGeneratedContent(content, expectedCount)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (attempt == maxRetries - 1) return@withContext Result.failure(error)
                    delay(1000L * (1 shl attempt))
                }
            }
            Result.failure(Exception("Max retries exceeded"))
        }

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
        reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
        promptTemplate: String? = null,
        directionalStimulus: String = "",
        promptCount: Int? = null,
    ): Flow<OpenRouterStreamingService.StreamChunk> =
        flow {
            if (text.isBlank()) {
                emit(OpenRouterStreamingService.StreamChunk.Error("Input text is empty"))
                return@flow
            }
            val expectedCount = promptCount ?: text.lines().size
            val safeModel = ZenRouter.normalizeModelId(model)
            val effectiveBaseUrl = baseUrl.ifBlank { ZenResponsesDefaultBaseUrl }
            var lastError = "Max retries exceeded"
            for (attempt in 0 until maxRetries) {
                try {
                    val sessionId = ZenRouter.generateSessionId()
                    val body =
                        buildResponsesRequest(
                            instructions = WingmanPrompts.openerSystemPrompt(expectedCount, customSystemPrompt),
                            input = WingmanPrompts.openerUserPrompt(
                                text = text,
                                avoidReplies = avoidReplies,
                                profileBlock = profileBlock,
                                template = promptTemplate,
                                directionalStimulus = directionalStimulus,
                                promptCount = expectedCount,
                            ),
                            model = safeModel,
                            temperature = temperature,
                            topP = topP,
                            stream = true,
                            reasoningEffort = reasoningEffort,
                            baseUrl = effectiveBaseUrl,
                            sessionId = sessionId,
                        )
                    val request = buildPost(effectiveBaseUrl, apiKey, body.toString(), safeModel, sessionId)
                    var httpFailed = false
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val rawBody = response.body.string()
                            lastError = friendlyGenerationError(rawBody, response.code, response.message, baseUrl = effectiveBaseUrl, model = safeModel)
                            if (isTransientHttpCode(response.code) && attempt < maxRetries - 1) {
                                httpFailed = true
                                return@use
                            }
                            emit(OpenRouterStreamingService.StreamChunk.Error(lastError))
                            return@flow
                        }
                        val content = StringBuilder()
                        var incompleteReason: String? = null
                        response.body.byteStream().bufferedReader().use { reader ->
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (!line.startsWith("data: ")) continue
                                val data = line.removePrefix("data: ").trim()
                                if (data.isEmpty() || data == "[DONE]") continue
                                val element = runCatching { responsesJson.parseToJsonElement(data) }.getOrNull() ?: continue
                                val delta = extractResponsesDelta(element)
                                if (!delta.isNullOrEmpty()) {
                                    content.append(delta)
                                    emit(OpenRouterStreamingService.StreamChunk.Content(delta))
                                } else {
                                    extractIncompleteReason(element)?.let { incompleteReason = it }
                                }
                            }
                        }
                        val rawContent = content.toString()
                        if (rawContent.isBlank()) {
                            val errMsg = if (incompleteReason == "max_output_tokens") {
                                "Model token limit reached during reasoning"
                            } else {
                                "Model returned an empty response. Retrying with non-streaming..."
                            }
                            emit(OpenRouterStreamingService.StreamChunk.Error(errMsg))
                        } else {
                            parseGeneratedContent(rawContent, expectedCount)
                                .onSuccess { emit(OpenRouterStreamingService.StreamChunk.Complete(it)) }
                                .onFailure { emit(OpenRouterStreamingService.StreamChunk.Error(it.message ?: "Parsing failed")) }
                        }
                    }
                    if (httpFailed) {
                        delay(1000L * (1 shl attempt))
                        continue
                    }
                    return@flow
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    lastError = error.message ?: "Unknown error"
                    if (attempt < maxRetries - 1) {
                        delay(1000L * (1 shl attempt))
                        continue
                    }
                    emit(OpenRouterStreamingService.StreamChunk.Error(lastError))
                    return@flow
                }
            }
            emit(OpenRouterStreamingService.StreamChunk.Error(lastError))
        }.flowOn(Dispatchers.IO)

    fun streamChat(
        systemPrompt: String,
        userPrompt: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
        topP: Float = Consts.DEFAULT_AI_TOP_P,
        maxRetries: Int = 3,
        reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
    ): Flow<OpenRouterStreamingService.ChatStreamChunk> =
        flow {
            if (userPrompt.isBlank()) {
                emit(OpenRouterStreamingService.ChatStreamChunk.Error("Input prompt is empty"))
                return@flow
            }
            val effectiveModel = ZenRouter.normalizeModelId(model).ifBlank { Consts.ZEN_DEFAULT_MODEL }
            val effectiveBaseUrl = baseUrl.ifBlank { ZenResponsesDefaultBaseUrl }
            var lastError = "Max retries exceeded"
            for (attempt in 0 until maxRetries) {
                try {
                    val sessionId = ZenRouter.generateSessionId()
                    val body =
                        buildResponsesRequest(
                            instructions = systemPrompt,
                            input = userPrompt,
                            model = effectiveModel,
                            temperature = temperature,
                            topP = topP,
                            stream = true,
                            reasoningEffort = reasoningEffort,
                            baseUrl = effectiveBaseUrl,
                            sessionId = sessionId,
                        )
                    val request = buildPost(effectiveBaseUrl, apiKey, body.toString(), effectiveModel, sessionId)
                    var httpFailed = false
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val rawBody = response.body.string()
                            lastError = friendlyGenerationError(rawBody, response.code, response.message, baseUrl = effectiveBaseUrl, model = effectiveModel)
                            if (isTransientHttpCode(response.code) && attempt < maxRetries - 1) {
                                httpFailed = true
                                return@use
                            }
                            emit(OpenRouterStreamingService.ChatStreamChunk.Error(lastError))
                            return@flow
                        }

                        val content = StringBuilder()
                        response.body.byteStream().bufferedReader().use { reader ->
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (!line.startsWith("data: ")) continue
                                val data = line.removePrefix("data: ").trim()
                                if (data.isEmpty() || data == "[DONE]") continue
                                val delta = runCatching { extractResponsesDelta(responsesJson.parseToJsonElement(data)) }.getOrNull()
                                if (!delta.isNullOrEmpty()) {
                                    content.append(delta)
                                    emit(OpenRouterStreamingService.ChatStreamChunk.Content(delta))
                                }
                            }
                        }
                        emit(OpenRouterStreamingService.ChatStreamChunk.Complete(content.toString()))
                    }
                    if (httpFailed) {
                        delay(1000L * (1 shl attempt))
                        continue
                    }
                    return@flow
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    lastError = error.message ?: "Unknown error"
                    if (attempt < maxRetries - 1) {
                        delay(1000L * (1 shl attempt))
                        continue
                    }
                    emit(OpenRouterStreamingService.ChatStreamChunk.Error(lastError))
                    return@flow
                }
            }
            emit(OpenRouterStreamingService.ChatStreamChunk.Error(lastError))
        }.flowOn(Dispatchers.IO)

    private fun buildPost(
        url: String,
        apiKey: String,
        json: String,
        model: String = "",
        sessionId: String = ZenRouter.generateSessionId(),
    ): Request =
        Request
            .Builder()
            .url(url)
            .apply {
                if (ZenRouter.isZenUrl(url)) {
                    ZenRouter.injectZenHeaders(this, apiKey, model, sessionId)
                } else {
                    if (apiKey.isNotBlank()) {
                        addHeader("Authorization", "Bearer ${apiKey.trim()}")
                    }
                    addHeader("HTTP-Referer", "https://github.com/s1ddhants1/unhinge")
                    addHeader("X-Title", "Unhinge")
                }
            }.addHeader("Content-Type", "application/json")
            .post(json.toRequestBody(jsonMediaType))
            .build()
}

internal fun buildResponsesRequest(
    instructions: String,
    input: String,
    model: String,
    temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
    topP: Float = Consts.DEFAULT_AI_TOP_P,
    stream: Boolean = false,
    reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
    baseUrl: String = "",
    sessionId: String = ZenRouter.generateSessionId(),
): JsonObject {
    val safeModel = ZenRouter.normalizeModelId(model)
    val sanitizedEffort = ModelReasoningCatalog.sanitizeReasoningEffort(reasoningEffort, safeModel)
    val effectiveBaseUrl = baseUrl.ifBlank { ZenResponsesDefaultBaseUrl }
    val isZen = ZenRouter.isZenUrl(effectiveBaseUrl)
    val isFree = isZen && ZenRouter.isFreeModel(safeModel)

    return buildJsonObject {
        if (safeModel.isNotBlank()) put("model", safeModel)
        if (instructions.isNotBlank()) put("instructions", instructions)
        put("input", input)
        put("temperature", temperature.toDouble())
        put("top_p", topP.toDouble())
        if (sanitizedEffort != null && sanitizedEffort != "on" && sanitizedEffort != "off") {
            put("reasoning", buildJsonObject {
                put("effort", sanitizedEffort)
            })
        }
        if (isFree) {
            put("tools", ZenRouter.DefaultZenResponsesTools)
            put("prompt_cache_key", sessionId)
            put("include", buildJsonArray { add("reasoning.encrypted_content") })
        }
        put("store", false)
        if (stream || isFree) put("stream", true)
    }
}

internal fun extractResponsesText(rawBody: String): String? =
    runCatching { extractResponsesText(responsesJson.parseToJsonElement(rawBody)) }.getOrNull()

internal fun extractResponsesText(element: kotlinx.serialization.json.JsonElement): String? {
    val root = element as? JsonObject ?: return null

    root["output_text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }

    val output = root["output"] as? JsonArray
    if (output != null) {
        val sb = StringBuilder()
        output.forEach { item ->
            val content = (item as? JsonObject)?.get("content") as? JsonArray ?: return@forEach
            content.forEach { part ->
                val text = (part as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                if (!text.isNullOrEmpty()) sb.append(text)
            }
        }
        if (sb.isNotEmpty()) return sb.toString()
    }

    return root["choices"]?.jsonArray
        ?.getOrNull(0)?.jsonObject
        ?.get("message")?.jsonObject
        ?.get("content")?.jsonPrimitive?.contentOrNull
}

internal fun extractResponsesDelta(element: kotlinx.serialization.json.JsonElement): String? {
    val obj = element as? JsonObject ?: return null
    obj["delta"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { return it }

    obj["text"]?.let { nested ->
        when (nested) {
            is kotlinx.serialization.json.JsonPrimitive -> nested.contentOrNull?.takeIf { it.isNotEmpty() }?.let { return it }
            is JsonObject -> nested["delta"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { return it }
            else -> null
        }
    }
    return null
}

internal fun extractIncompleteReason(element: kotlinx.serialization.json.JsonElement): String? {
    val obj = element as? JsonObject ?: return null
    if (obj["type"]?.jsonPrimitive?.contentOrNull == "response.incomplete") {
        val details = obj["response"]?.jsonObject?.get("incomplete_details")?.jsonObject
        return details?.get("reason")?.jsonPrimitive?.contentOrNull ?: "incomplete"
    }
    return null
}
