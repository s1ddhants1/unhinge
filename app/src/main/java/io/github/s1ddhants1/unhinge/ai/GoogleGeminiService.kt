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

private val geminiJson = Json { ignoreUnknownKeys = true }

object GoogleGeminiService {
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
    ): Result<List<String>> =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext Result.failure(Exception("Input text is empty"))
            val safeModel = sanitizeModelId(model)
            repeat(maxRetries) { attempt ->
                try {
                    val body =
                        buildGeminiRequest(
                            system = WingmanPrompts.openerSystemPrompt(text.lines().size, customSystemPrompt),
                            user = WingmanPrompts.openerUserPrompt(
                                text = text,
                                avoidReplies = avoidReplies,
                                profileBlock = profileBlock,
                                template = promptTemplate,
                                directionalStimulus = directionalStimulus,
                            ),
                            model = safeModel,
                            temperature = temperature,
                            topP = topP,
                            reasoningEffort = reasoningEffort,
                        )
                    val request = buildPost(generateUrl(baseUrl, safeModel), apiKey, body.toString(), safeModel)
                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body.string()
                        if (!response.isSuccessful) {
                            val error = friendlyGenerationError(responseBody, response.code, response.message, baseUrl = baseUrl, model = safeModel)
                            if (isTransientHttpCode(response.code) && attempt < maxRetries - 1) throw Exception(error)
                            return@withContext Result.failure(Exception(error))
                        }
                        val content = extractGeminiText(responseBody).orEmpty()
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
        maxRetries: Int = 3,
        reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
        promptTemplate: String? = null,
        directionalStimulus: String = "",
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
                        buildGeminiRequest(
                            system = WingmanPrompts.openerSystemPrompt(text.lines().size, customSystemPrompt),
                            user = WingmanPrompts.openerUserPrompt(
                                text = text,
                                avoidReplies = avoidReplies,
                                profileBlock = profileBlock,
                                template = promptTemplate,
                                directionalStimulus = directionalStimulus,
                            ),
                            model = safeModel,
                            temperature = temperature,
                            topP = topP,
                            reasoningEffort = reasoningEffort,
                        )
                    val request = buildPost(streamUrl(baseUrl, safeModel), apiKey, body.toString(), safeModel)
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
                                val payload = line.removePrefix("data: ").trim().trimStart('[').trimEnd(',').trimEnd(']')
                                if (payload.isEmpty() || payload == "[DONE]") continue
                                val delta = runCatching {
                                    extractGeminiText(geminiJson.parseToJsonElement(payload))
                                }.getOrNull()
                                if (!delta.isNullOrEmpty()) {

                                    val known = content.toString()
                                    val suffix = if (delta.startsWith(known)) delta.removePrefix(known) else delta
                                    if (suffix.isNotEmpty()) {
                                        content.append(suffix)
                                        emit(OpenRouterStreamingService.StreamChunk.Content(suffix))
                                    } else if (content.isEmpty()) {
                                        content.append(delta)
                                        emit(OpenRouterStreamingService.StreamChunk.Content(delta))
                                    }
                                }
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
                    val safeModel = sanitizeModelId(model)
                    val body = buildGeminiRequest(
                        system = systemPrompt,
                        user = userPrompt,
                        model = safeModel,
                        temperature = temperature,
                        topP = topP,
                        reasoningEffort = reasoningEffort,
                    )
                    val request = buildPost(streamUrl(baseUrl, safeModel), apiKey, body.toString(), safeModel)
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
                                val payload = line.removePrefix("data: ").trim().trimStart('[').trimEnd(',').trimEnd(']')
                                if (payload.isEmpty() || payload == "[DONE]") continue
                                val delta = runCatching {
                                    extractGeminiText(geminiJson.parseToJsonElement(payload))
                                }.getOrNull()
                                if (!delta.isNullOrEmpty()) {
                                    val known = content.toString()
                                    val suffix = if (delta.startsWith(known)) delta.removePrefix(known) else delta
                                    if (suffix.isNotEmpty()) {
                                        content.append(suffix)
                                        emit(OpenRouterStreamingService.ChatStreamChunk.Content(suffix))
                                    }
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

    internal fun generateUrl(baseUrl: String, model: String): String {
        val base = baseUrl.ifBlank { "$ZenGeminiBase/$model" }.trimEnd('/')
        return if (base.endsWith(":generateContent") || base.endsWith(":streamGenerateContent")) {
            base.replace(":streamGenerateContent", ":generateContent")
        } else if (base.matches(Regex(".*/models/[^/]+"))) {
            "$base:generateContent"
        } else {
            "$base/$model:generateContent"
        }
    }

    internal fun streamUrl(baseUrl: String, model: String): String =
        generateUrl(baseUrl, model).replace(":generateContent", ":streamGenerateContent")

    private fun buildPost(url: String, apiKey: String, json: String, model: String = ""): Request =
        Request
            .Builder()
            .url(url)
            .apply {
                if (ZenRouter.isZenUrl(url)) {
                    ZenRouter.injectZenHeaders(this, apiKey, model)
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

internal fun buildGeminiRequest(
    system: String,
    user: String,
    model: String = "",
    temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
    topP: Float = Consts.DEFAULT_AI_TOP_P,
    reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
): JsonObject =
    buildJsonObject {
        if (system.isNotBlank()) {
            put("system_instruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", system) })
                })
            })
        }
        put("contents", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", user) })
                })
            })
        })
        val safeModel = sanitizeModelId(model)
        val sanitizedEffort = ModelReasoningCatalog.sanitizeReasoningEffort(reasoningEffort, safeModel, "Google")
        put("generationConfig", buildJsonObject {
            put("temperature", temperature.toDouble())
            put("topP", topP.toDouble())
            if (sanitizedEffort != null) {
                put("thinkingConfig", buildJsonObject {
                    put("includeThoughts", true)
                    when (sanitizedEffort) {
                        "low", "minimal" -> put("thinkingBudget", 1024)
                        "medium" -> put("thinkingBudget", 4096)
                        "high", "max" -> put("thinkingBudget", 8192)
                    }
                })
            }
        })
    }

internal fun extractGeminiText(rawBody: String): String? =
    runCatching { extractGeminiText(geminiJson.parseToJsonElement(rawBody)) }.getOrNull()

internal fun extractGeminiText(element: kotlinx.serialization.json.JsonElement): String? {
    val root = element as? JsonObject ?: return null
    val candidates = root["candidates"] as? JsonArray ?: return null
    val sb = StringBuilder()
    candidates.forEach { candidate ->
        val content = (candidate as? JsonObject)?.get("content") as? JsonObject ?: return@forEach
        val parts = content["parts"] as? JsonArray ?: return@forEach
        parts.forEach { part ->
            val partObj = part as? JsonObject ?: return@forEach
            if (partObj["thought"]?.jsonPrimitive?.contentOrNull == "true") return@forEach
            val text = partObj["text"]?.jsonPrimitive?.contentOrNull
            if (!text.isNullOrEmpty()) sb.append(text)
        }
    }
    return sb.toString().takeIf { it.isNotEmpty() }
}
