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

/**
 * OpenAI Responses API client (`POST /v1/responses`) for providers like
 * OpenCode Zen that serve reasoning models (`muse-spark-1.3*`) only on this
 * wire protocol. Chat-completions payloads 400 on these endpoints, hence a
 * dedicated client instead of reusing [OpenRouterService].
 *
 * Wire notes: `instructions` carries the system prompt, `input` the user
 * prompt, `max_output_tokens` replaces `max_tokens`, `store=false` avoids
 * server-side retention. Streaming SSE deltas arrive as
 * `response.output_text.delta` events with a `delta` string field.
 */
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
        maxTokens: Int = Consts.DEFAULT_AI_MAX_TOKENS,
    ): Result<List<String>> =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext Result.failure(Exception("Input text is empty"))
            val safeModel = sanitizeModelId(model)
            repeat(maxRetries) { attempt ->
                try {
                    val body =
                        buildResponsesRequest(
                            instructions = WingmanPrompts.openerSystemPrompt(text.lines().size, customSystemPrompt),
                            input = WingmanPrompts.openerUserPrompt(text, avoidReplies, profileBlock),
                            model = safeModel,
                            temperature = temperature,
                            topP = topP,
                            maxTokens = maxTokens,
                            stream = false,
                        )
                    val request = buildPost(baseUrl.ifBlank { ZenResponsesDefaultBaseUrl }, apiKey, body.toString())
                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body.string()
                        if (!response.isSuccessful) {
                            val error = friendlyGenerationError(responseBody, response.code, response.message)
                            if (isTransientHttpCode(response.code) && attempt < maxRetries - 1) throw Exception(error)
                            return@withContext Result.failure(Exception(error))
                        }
                        val content = extractResponsesText(responseBody).orEmpty()
                        return@withContext parseGeneratedContent(content, text.lines().size)
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
        maxTokens: Int = Consts.DEFAULT_AI_MAX_TOKENS,
        maxRetries: Int = 3,
    ): Flow<OpenRouterStreamingService.StreamChunk> =
        flow {
            if (text.isBlank()) {
                emit(OpenRouterStreamingService.StreamChunk.Error("Input text is empty"))
                return@flow
            }
            val safeModel = sanitizeModelId(model)
            var lastError = "Max retries exceeded"
            for (attempt in 0 until maxRetries) {
                try {
                    val body =
                        buildResponsesRequest(
                            instructions = WingmanPrompts.openerSystemPrompt(text.lines().size, customSystemPrompt),
                            input = WingmanPrompts.openerUserPrompt(text, avoidReplies, profileBlock),
                            model = safeModel,
                            temperature = temperature,
                            topP = topP,
                            maxTokens = maxTokens,
                            stream = true,
                        )
                    val request = buildPost(baseUrl.ifBlank { ZenResponsesDefaultBaseUrl }, apiKey, body.toString())
                    var httpFailed = false
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val rawBody = response.body.string()
                            lastError = friendlyGenerationError(rawBody, response.code, response.message)
                            if (isTransientHttpCode(response.code) && attempt < maxRetries - 1) {
                                httpFailed = true
                                return@use
                            }
                            emit(OpenRouterStreamingService.StreamChunk.Error(lastError))
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
                                    emit(OpenRouterStreamingService.StreamChunk.Content(delta))
                                }
                                // Terminal full payload (response.completed) carries no delta;
                                // accumulator already holds the text, so skip without emitting.
                            }
                        }
                        parseGeneratedContent(content.toString(), text.lines().size)
                            .onSuccess { emit(OpenRouterStreamingService.StreamChunk.Complete(it)) }
                            .onFailure { emit(OpenRouterStreamingService.StreamChunk.Error(it.message ?: "Parsing failed")) }
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
        maxTokens: Int = Consts.DEFAULT_AI_MAX_TOKENS,
        maxRetries: Int = 3,
    ): Flow<OpenRouterStreamingService.ChatStreamChunk> =
        flow {
            if (userPrompt.isBlank()) {
                emit(OpenRouterStreamingService.ChatStreamChunk.Error("Input prompt is empty"))
                return@flow
            }
            val effectiveModel = sanitizeModelId(model).ifBlank { Consts.ZEN_DEFAULT_MODEL }
            var lastError = "Max retries exceeded"
            for (attempt in 0 until maxRetries) {
                try {
                    val body =
                        buildResponsesRequest(
                            instructions = systemPrompt,
                            input = userPrompt,
                            model = effectiveModel,
                            temperature = temperature,
                            topP = topP,
                            maxTokens = maxTokens,
                            stream = true,
                        )
                    val request = buildPost(baseUrl.ifBlank { ZenResponsesDefaultBaseUrl }, apiKey, body.toString())
                    var httpFailed = false
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val rawBody = response.body.string()
                            lastError = friendlyGenerationError(rawBody, response.code, response.message)
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

    private fun buildPost(url: String, apiKey: String, json: String): Request =
        Request
            .Builder()
            .url(url)
            .apply {
                if (ZenRouter.isZenUrl(url)) {
                    ZenRouter.injectZenHeaders(this, apiKey)
                } else if (apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${apiKey.trim()}")
                }
            }.addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/s1ddhants1/unhinge")
            .addHeader("X-Title", "Unhinge")
            .post(json.toRequestBody(jsonMediaType))
            .build()
}

internal fun buildResponsesRequest(
    instructions: String,
    input: String,
    model: String,
    temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
    topP: Float = Consts.DEFAULT_AI_TOP_P,
    maxTokens: Int = Consts.DEFAULT_AI_MAX_TOKENS,
    stream: Boolean = false,
): JsonObject {
    val safeModel = sanitizeModelId(model)
    return buildJsonObject {
        if (safeModel.isNotBlank()) put("model", safeModel)
        if (instructions.isNotBlank()) put("instructions", instructions)
        put("input", input)
        put("temperature", temperature.toDouble())
        put("top_p", topP.toDouble())
        put("max_output_tokens", maxTokens)
        put("store", false)
        if (stream) put("stream", true)
    }
}

/** Non-streaming Responses payload → plain assistant text. Handles output/output_text/chat fallbacks. */
internal fun extractResponsesText(rawBody: String): String? =
    runCatching { extractResponsesText(responsesJson.parseToJsonElement(rawBody)) }.getOrNull()

internal fun extractResponsesText(element: kotlinx.serialization.json.JsonElement): String? {
    val root = element as? JsonObject ?: return null
    // Convenience field some gateways return.
    root["output_text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
    // Canonical Responses shape: output[].content[].text
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
    // Gateway-translated chat shape fallback.
    return root["choices"]?.jsonArray
        ?.getOrNull(0)?.jsonObject
        ?.get("message")?.jsonObject
        ?.get("content")?.jsonPrimitive?.contentOrNull
}

/** Streaming SSE `data:` JSON → text delta, or null for lifecycle/completed events. */
internal fun extractResponsesDelta(element: kotlinx.serialization.json.JsonElement): String? {
    val obj = element as? JsonObject ?: return null
    obj["delta"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { return it }
    // Some gateways nest under text.delta or output_text.delta.
    obj["text"]?.let { nested ->
        when (nested) {
            is kotlinx.serialization.json.JsonPrimitive -> nested.contentOrNull?.takeIf { it.isNotEmpty() }?.let { return it }
            is JsonObject -> nested["delta"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { return it }
            else -> null
        }
    }
    return null
}
