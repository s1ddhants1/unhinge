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

    fun streamTranslation(
        text: String,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        customSystemPrompt: String = "",
        avoidReplies: List<String> = emptyList(),
        temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
        topP: Float = Consts.DEFAULT_AI_TOP_P,
        maxTokens: Int = Consts.DEFAULT_AI_MAX_TOKENS,
    ): Flow<StreamChunk> =
        flow {
            if (text.isBlank()) {
                emit(StreamChunk.Error("Input text is empty"))
                return@flow
            }

            try {
                val body =
                    buildTranslationRequest(
                        text = text,
                        targetLanguage = targetLanguage,
                        model = model,
                        mode = mode,
                        customSystemPrompt = customSystemPrompt,
                        baseUrl = baseUrl.ifBlank { OpenRouterDefaultBaseUrl },
                        stream = true,
                        avoidReplies = avoidReplies,
                        temperature = temperature,
                        topP = topP,
                        maxTokens = maxTokens,
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

                Timber.d("streamTranslation: url=${request.url}, model=$model, keyLen=${apiKey.length}")
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body
                    Timber.d("streamTranslation response: code=${response.code}, msg=${response.message}")
                    if (!response.isSuccessful) {
                        val rawBody = responseBody.string()
                        Timber.e("streamTranslation failed (HTTP ${response.code}): $rawBody")
                        emit(
                            StreamChunk.Error(
                                "Translation failed: ${apiErrorMessage(rawBody, response.code, response.message)}",
                            ),
                        )
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

                    parseTranslationContent(content.toString(), text.lines().size)
                        .onSuccess { emit(StreamChunk.Complete(it)) }
                        .onFailure { emit(StreamChunk.Error(it.message ?: "Parsing failed")) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "Streaming translation failed")
                emit(StreamChunk.Error(error.message ?: "Unknown error"))
            }
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
    ): Flow<ChatStreamChunk> =
        flow {
            if (userPrompt.isBlank()) {
                emit(ChatStreamChunk.Error("Input prompt is empty"))
                return@flow
            }

            try {
                val effectiveModel = model.ifBlank { Consts.OPENROUTER_DEFAULT_MODEL }
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

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body
                    if (!response.isSuccessful) {
                        val rawBody = responseBody.string()
                        emit(
                            ChatStreamChunk.Error(
                                "Request failed: ${apiErrorMessage(rawBody, response.code, response.message)}",
                            ),
                        )
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "Streaming chat failed")
                emit(ChatStreamChunk.Error(error.message ?: "Unknown error"))
            }
        }.flowOn(Dispatchers.IO)

    sealed interface StreamChunk {
        data class Content(
            val text: String,
        ) : StreamChunk

        data class Complete(
            val translatedLines: List<String>,
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
