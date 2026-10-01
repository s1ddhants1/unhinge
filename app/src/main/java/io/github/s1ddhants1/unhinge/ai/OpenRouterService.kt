package io.github.s1ddhants1.unhinge.ai

import io.github.s1ddhants1.unhinge.Consts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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

const val OpenRouterDefaultBaseUrl = "https://openrouter.ai/api/v1/chat/completions"

private val wingmanJson = Json { ignoreUnknownKeys = true }

object OpenRouterService {
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
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

            repeat(maxRetries) { attempt ->
                try {
                    val body =
                        buildGenerationRequest(
                            text = text,
                            model = model,
                            customSystemPrompt = customSystemPrompt,
                            profileBlock = profileBlock,
                            baseUrl = baseUrl.ifBlank { OpenRouterDefaultBaseUrl },
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

                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body.string()
                        if (!response.isSuccessful) {
                            val error = apiErrorMessage(responseBody, response.code, response.message)
                            if (response.code >= 500) throw Exception(error)
                            return@withContext Result.failure(Exception("Generation failed: $error"))
                        }

                        val content =
                            wingmanJson
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
                        return@withContext parseGeneratedContent(content, text.lines().size)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (attempt == maxRetries - 1) return@withContext Result.failure(error)
                    delay(1000L * (attempt + 1))
                }
            }

            Result.failure(Exception("Max retries exceeded"))
        }
}

internal fun buildGenerationRequest(
    text: String,
    model: String,
    customSystemPrompt: String,
    profileBlock: String = "",
    baseUrl: String = OpenRouterDefaultBaseUrl,
    stream: Boolean = false,
    avoidReplies: List<String> = emptyList(),
    temperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
    topP: Float = Consts.DEFAULT_AI_TOP_P,
    maxTokens: Int = Consts.DEFAULT_AI_MAX_TOKENS,
): JsonObject {
    val lineCount = text.lines().size
    val systemPrompt = WingmanPrompts.openerSystemPrompt(lineCount, customSystemPrompt)
    val userPrompt = WingmanPrompts.openerUserPrompt(text, avoidReplies, profileBlock)

    return buildJsonObject {
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    },
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", userPrompt)
                    },
                )
            },
        )
        if (model.isNotBlank()) put("model", model)
        put("temperature", temperature.toDouble())
        put("top_p", topP.toDouble())
        put("max_tokens", maxTokens)
        put(
            "response_format",
            buildJsonObject {
                put("type", "json_schema")
                put(
                    "json_schema",
                    buildJsonObject {
                        put("name", "generated_replies")
                        put("strict", true)
                        put(
                            "schema",
                            buildJsonObject {
                                put("type", "object")
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put(
                                            "lines",
                                            buildJsonObject {
                                                put("type", "array")
                                                put(
                                                    "description",
                                                    "Opening replies, one per input prompt",
                                                )
                                                put(
                                                    "items",
                                                    buildJsonObject {
                                                        put("type", "string")
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                                put(
                                    "required",
                                    buildJsonArray {
                                        add("lines")
                                    },
                                )
                                put("additionalProperties", false)
                            },
                        )
                    },
                )
            },
        )
        // OpenRouter-only routing preference; fail instead of silently degrading to
        // unvalidated JSON on endpoints without structured-output support
        if (baseUrl.contains("openrouter.ai")) {
            put(
                "provider",
                buildJsonObject {
                    put("require_parameters", true)
                },
            )
        }
        if (stream) put("stream", true)
    }
}

internal fun parseGeneratedContent(
    content: String,
    expectedLineCount: Int,
): Result<List<String>> =
    runCatching {
        val cleaned = content.replace("```json", "").replace("```", "").trim()
        val bracketed =
            cleaned
                .substringAfter('[', "")
                .substringBeforeLast(']', "")
                .takeIf(String::isNotEmpty)
                ?.let { "[$it]" }
        val generatedLines =
            sequenceOf(content.trim(), cleaned, bracketed)
                .filterNotNull()
                .mapNotNull { candidate ->
                    runCatching { extractLines(wingmanJson.parseToJsonElement(candidate)) }.getOrNull()
                }.firstOrNull()
                ?: cleaned
                    .lines()
                    .filter(String::isNotBlank)
                    .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                    .takeIf(List<String>::isNotEmpty)
                ?: error("Failed to parse response")

        generatedLines.take(expectedLineCount) + List((expectedLineCount - generatedLines.size).coerceAtLeast(0)) { "" }
    }

private fun extractLines(element: JsonElement): List<String>? =
    when (element) {
        // structured-output schema shape
        is JsonObject -> (element["lines"] as? JsonArray)?.map { it.jsonPrimitive.content }
        // legacy lenient path: bare arrays from models/providers that ignore the schema
        is JsonArray -> element.map { it.jsonPrimitive.content }
        else -> null
    }

internal fun apiErrorMessage(
    body: String?,
    code: Int,
    message: String,
): String =
    runCatching {
        wingmanJson
            .parseToJsonElement(body.orEmpty())
            .jsonObject["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull()
        .takeUnless { it.isNullOrBlank() }
        ?: "HTTP $code: $message"
