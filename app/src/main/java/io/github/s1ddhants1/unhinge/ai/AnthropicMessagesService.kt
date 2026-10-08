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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val anthropicJson = Json { ignoreUnknownKeys = true }

object AnthropicMessagesService {
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
            val safeModel = sanitizeModelId(model)
            repeat(maxRetries) { attempt ->
                try {
                    val body =
                        buildMessagesRequest(
                            system = WingmanPrompts.openerSystemPrompt(expectedCount, customSystemPrompt),
                            user = WingmanPrompts.openerUserPrompt(
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
                            stream = false,
                            reasoningEffort = reasoningEffort,
                        )
                    val request = buildPost(baseUrl.ifBlank { ZenMessagesBaseUrl }, apiKey, body.toString(), safeModel)
                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body.string()
                        if (!response.isSuccessful) {
                            val error = friendlyGenerationError(responseBody, response.code, response.message, baseUrl = baseUrl, model = safeModel)
                            if (isTransientHttpCode(response.code) && attempt < maxRetries - 1) throw Exception(error)
                            return@withContext Result.failure(Exception(error))
                        }
                        val content = extractMessagesText(responseBody).orEmpty()
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
            val safeModel = sanitizeModelId(model)
            var lastError = "Max retries exceeded"
            for (attempt in 0 until maxRetries) {
                try {
                    val body =
                        buildMessagesRequest(
                            system = WingmanPrompts.openerSystemPrompt(expectedCount, customSystemPrompt),
                            user = WingmanPrompts.openerUserPrompt(
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
                        )
                    val request = buildPost(baseUrl.ifBlank { ZenMessagesBaseUrl }, apiKey, body.toString(), safeModel)
                    var httpFailed = false
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val rawBody = response.body.string()
                            lastError = friendlyGenerationError(rawBody, response.code, response.message, baseUrl = baseUrl, model = safeModel)
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
                                val delta = runCatching { extractMessagesDelta(anthropicJson.parseToJsonElement(data)) }.getOrNull()
                                if (!delta.isNullOrEmpty()) {
                                    content.append(delta)
                                    emit(OpenRouterStreamingService.StreamChunk.Content(delta))
                                }
                            }
                        }
                        parseGeneratedContent(content.toString(), expectedCount)
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
        maxRetries: Int = 3,
        reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
    ): Flow<OpenRouterStreamingService.ChatStreamChunk> =
        flow {
            if (userPrompt.isBlank()) {
                emit(OpenRouterStreamingService.ChatStreamChunk.Error("Input prompt is empty"))
                return@flow
            }
            var lastError = "Max retries exceeded"
            for (attempt in 0 until maxRetries) {
                try {
                    val body =
                        buildMessagesRequest(
                            system = systemPrompt,
                            user = userPrompt,
                            model = sanitizeModelId(model).ifBlank { Consts.ZEN_DEFAULT_MODEL },
                            temperature = temperature,
                            topP = topP,
                            stream = true,
                            reasoningEffort = reasoningEffort,
                        )
                    val request = buildPost(baseUrl.ifBlank { ZenMessagesBaseUrl }, apiKey, body.toString(), sanitizeModelId(model).ifBlank { Consts.ZEN_DEFAULT_MODEL })
                    var httpFailed = false
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val rawBody = response.body.string()
                            lastError = friendlyGenerationError(rawBody, response.code, response.message, baseUrl = baseUrl, model = sanitizeModelId(model))
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
                                val delta = runCatching { extractMessagesDelta(anthropicJson.parseToJsonElement(data)) }.getOrNull()
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

    private fun buildPost(url: String, apiKey: String, json: String, model: String = ""): Request =
        Request
            .Builder()
            .url(url)
            .apply {
                if (ZenRouter.isZenUrl(url)) {
                    ZenRouter.injectZenHeaders(this, apiKey, model)
                    if (!ZenRouter.isFreeModel(model) && apiKey.isNotBlank()) {
                        addHeader("x-api-key", apiKey.trim())
                    }
                } else {
                    if (apiKey.isNotBlank()) {
                        addHeader("Authorization", "Bearer ${apiKey.trim()}")
                        addHeader("x-api-key", apiKey.trim())
                    }
                    addHeader("HTTP-Referer", "https://github.com/s1ddhants1/unhinge")
                    addHeader("X-Title", "Unhinge")
                }
            }.addHeader("Content-Type", "application/json")
            .addHeader("anthropic-version", "2023-06-01")
            .post(json.toRequestBody(jsonMediaType))
            .build()
}

internal fun buildMessagesRequest(
    system: String,
    user: String,
    model: String,
    temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
    topP: Float = Consts.DEFAULT_AI_TOP_P,
    stream: Boolean = false,
    reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
): JsonObject =
    buildJsonObject {
        val safeModel = sanitizeModelId(model)
        if (safeModel.isNotBlank()) put("model", safeModel)
        if (system.isNotBlank()) put("system", system)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", user)
            })
        })
        val budgetTokens = ModelReasoningCatalog.resolveBudgetTokens(reasoningEffort, safeModel)
        if (budgetTokens != null) {
            put("thinking", buildJsonObject {
                put("type", "enabled")
                put("budget_tokens", budgetTokens)
            })

            put("max_tokens", budgetTokens + 1024)
            put("temperature", 1.0)
        } else {

            put("max_tokens", 4096)
            put("temperature", temperature.toDouble())
            put("top_p", topP.toDouble())
        }
        if (stream) put("stream", true)
    }

internal fun extractMessagesText(rawBody: String): String? =
    runCatching { extractMessagesText(anthropicJson.parseToJsonElement(rawBody)) }.getOrNull()

internal fun extractMessagesText(element: kotlinx.serialization.json.JsonElement): String? {
    val root = element as? JsonObject ?: return null
    val content = root["content"] as? JsonArray ?: return null
    val sb = StringBuilder()
    content.forEach { block ->
        val text = (block as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
        if (!text.isNullOrEmpty()) sb.append(text)
    }
    return sb.toString().takeIf { it.isNotEmpty() }
}

internal fun extractMessagesDelta(element: kotlinx.serialization.json.JsonElement): String? {
    val obj = element as? JsonObject ?: return null

    (obj["delta"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
        ?.takeIf { it.isNotEmpty() }?.let { return it }
    return null
}
