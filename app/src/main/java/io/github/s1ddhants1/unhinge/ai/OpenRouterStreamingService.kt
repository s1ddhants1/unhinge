package io.github.s1ddhants1.unhinge.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.github.s1ddhants1.unhinge.Consts
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object OpenRouterStreamingService {
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }

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
        structured: Boolean = true,
    ): Flow<StreamChunk> =
        flow {
            if (text.isBlank()) {
                emit(StreamChunk.Error("Input text is empty"))
                return@flow
            }

            val safeModel = sanitizeModelId(model)
            var useStructured = structured
            var lastError = "Max retries exceeded"

            for (attempt in 0 until maxRetries) {
                try {
                    val body =
                        buildGenerationRequest(
                            text = text,
                            model = safeModel,
                            customSystemPrompt = customSystemPrompt,
                            profileBlock = profileBlock,
                            baseUrl = baseUrl.ifBlank { OpenRouterDefaultBaseUrl },
                            stream = true,
                            avoidReplies = avoidReplies,
                            temperature = temperature,
                            topP = topP,
                            maxTokens = maxTokens,
                            structured = useStructured,
                        )
                    val request =
                        Request
                            .Builder()
                            .url(baseUrl.ifBlank { OpenRouterDefaultBaseUrl })
                            .apply {
                                if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer ${apiKey.trim()}")
                            }.addHeader("Content-Type", "application/json")
                            .addHeader("HTTP-Referer", "https://github.com/s1ddhants1/unhinge")
                            .addHeader("X-Title", "Unhinge")
                            .post(body.toString().toRequestBody(jsonMediaType))
                            .build()

                    Timber.d("streamGeneration: url=${request.url}, model=$safeModel, keyLen=${apiKey.length}")
                    var httpFailed = false
                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body
                        Timber.d("streamGeneration response: code=${response.code}, msg=${response.message}")
                        if (!response.isSuccessful) {
                            val rawBody = responseBody.string()
                            Timber.e("streamGeneration failed (HTTP ${response.code}): $rawBody")
                            val structuredRejection = isStructuredOutputError(rawBody, response.code)
                            if (useStructured && structuredRejection) {
                                useStructured = false
                            }
                            lastError = friendlyGenerationError(rawBody, response.code, response.message)
                            val retryable = isTransientHttpCode(response.code) ||
                                (structuredRejection && attempt < maxRetries - 1)
                            if (retryable) {
                                httpFailed = true
                                return@use
                            }
                            emit(StreamChunk.Error(lastError))
                            return@flow
                        }

                        val content = StringBuilder()
                        responseBody.byteStream().bufferedReader().use { reader ->
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (!line.startsWith("data: ")) continue
                                val data = line.removePrefix("data: ")
                                if (data == "[DONE]") break

                                runCatching {
                                    json
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
                                }.getOrNull()?.let { chunk ->
                                    content.append(chunk)
                                    emit(StreamChunk.Content(chunk))
                                }
                            }
                        }

                        parseGeneratedContent(content.toString(), text.lines().size)
                            .onSuccess { emit(StreamChunk.Complete(it)) }
                            .onFailure { emit(StreamChunk.Error(it.message ?: "Parsing failed")) }
                    }
                    if (httpFailed) {
                        kotlinx.coroutines.delay(1000L * (1 shl attempt))
                        continue
                    }
                    return@flow
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.e(error, "Streaming generation failed")
                    lastError = error.message ?: "Unknown error"
                    if (attempt < maxRetries - 1 && isTransientFailure(error)) {
                        kotlinx.coroutines.delay(1000L * (1 shl attempt))
                        continue
                    }
                    emit(StreamChunk.Error(lastError))
                    return@flow
                }
            }
            emit(StreamChunk.Error(lastError))
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
    ): Flow<ChatStreamChunk> =
        flow {
            if (userPrompt.isBlank()) {
                emit(ChatStreamChunk.Error("Input prompt is empty"))
                return@flow
            }

            val effectiveModel = sanitizeModelId(model).ifBlank { Consts.OPENROUTER_DEFAULT_MODEL }
            var lastError = "Max retries exceeded"
            for (attempt in 0 until maxRetries) {
                try {
                    val body = buildJsonObject {
                        put("model", JsonPrimitive(effectiveModel))
                        put("temperature", JsonPrimitive(temperature))
                        put("top_p", JsonPrimitive(topP))
                        put("max_tokens", JsonPrimitive(maxTokens))
                        put("messages", buildJsonArray {
                            if (systemPrompt.isNotBlank()) {
                                add(buildJsonObject {
                                    put("role", JsonPrimitive("system"))
                                    put("content", JsonPrimitive(systemPrompt))
                                })
                            }
                            add(buildJsonObject {
                                put("role", JsonPrimitive("user"))
                                put("content", JsonPrimitive(userPrompt))
                            })
                        })
                        put("stream", JsonPrimitive(true))
                    }

                    val targetUrl = baseUrl.ifBlank { OpenRouterDefaultBaseUrl }
                    val request =
                        Request
                            .Builder()
                            .url(targetUrl)
                            .apply {
                                if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer ${apiKey.trim()}")
                            }.addHeader("Content-Type", "application/json")
                            .addHeader("HTTP-Referer", "https://github.com/s1ddhants1/unhinge")
                            .addHeader("X-Title", "Unhinge")
                            .post(body.toString().toRequestBody(jsonMediaType))
                            .build()

                    var httpFailed = false
                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body
                        if (!response.isSuccessful) {
                            val rawBody = responseBody.string()
                            lastError = friendlyGenerationError(rawBody, response.code, response.message)
                            if (isTransientHttpCode(response.code) && attempt < maxRetries - 1) {
                                httpFailed = true
                                return@use
                            }
                            emit(ChatStreamChunk.Error(lastError))
                            return@flow
                        }

                    val content = StringBuilder()
                    responseBody.byteStream().bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (!line.startsWith("data: ")) continue
                            val data = line.removePrefix("data: ")
                            if (data == "[DONE]") break

                            runCatching {
                                json
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
                            }.getOrNull()?.let { chunk ->
                                content.append(chunk)
                                emit(ChatStreamChunk.Content(chunk))
                            }
                        }
                    }

                    emit(ChatStreamChunk.Complete(content.toString()))
                    }
                    if (httpFailed) {
                        kotlinx.coroutines.delay(1000L * (1 shl attempt))
                        continue
                    }
                    return@flow
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.e(error, "Streaming chat failed")
                    lastError = error.message ?: "Unknown error"
                    if (attempt < maxRetries - 1 && isTransientFailure(error)) {
                        kotlinx.coroutines.delay(1000L * (1 shl attempt))
                        continue
                    }
                    emit(ChatStreamChunk.Error(lastError))
                    return@flow
                }
            }
            emit(ChatStreamChunk.Error(lastError))
        }.flowOn(Dispatchers.IO)

    private fun isTransientFailure(error: Exception): Boolean {
        val message = error.message.orEmpty()
        return message.contains("HTTP 500", ignoreCase = true) ||
            message.contains("HTTP 502", ignoreCase = true) ||
            message.contains("HTTP 503", ignoreCase = true) ||
            message.contains("HTTP 529", ignoreCase = true) ||
            message.contains("temporarily unavailable", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("Unable to resolve host", ignoreCase = true)
    }

    sealed interface StreamChunk {
        data class Content(
            val text: String,
        ) : StreamChunk

        data class Complete(
            val generatedLines: List<String>,
        ) : StreamChunk

        data class Error(
            val message: String,
        ) : StreamChunk
    }

    sealed interface ChatStreamChunk {
        data class Content(
            val text: String,
        ) : ChatStreamChunk

        data class Complete(
            val fullText: String,
        ) : ChatStreamChunk

        data class Error(
            val message: String,
        ) : ChatStreamChunk
    }
}
