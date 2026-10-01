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
        structured: Boolean = true,
    ): Result<List<String>> =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext Result.failure(Exception("Input text is empty"))

            val safeModel = sanitizeModelId(model)
            var useStructured = structured
            repeat(maxRetries) { attempt ->
                try {
                    val body =
                        buildGenerationRequest(
                            text = text,
                            model = safeModel,
                            customSystemPrompt = customSystemPrompt,
                            profileBlock = profileBlock,
                            baseUrl = baseUrl.ifBlank { OpenRouterDefaultBaseUrl },
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

                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body.string()
                        if (!response.isSuccessful) {
                            val structuredRejection = isStructuredOutputError(responseBody, response.code)
                            if (useStructured && structuredRejection) {
                                useStructured = false
                            }
                            val error = friendlyGenerationError(responseBody, response.code, response.message)
                            // Retry transient 5xx with backoff; structured-output rejections get
                            // one lenient retry without response_format instead of failing fast.
                            val retryable = isTransientHttpCode(response.code) ||
                                (structuredRejection && attempt < maxRetries - 1)
                            if (retryable) throw Exception(error)
                            return@withContext Result.failure(Exception(error))
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
                    delay(1000L * (1 shl attempt))
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
    structured: Boolean = true,
): JsonObject {
    val lineCount = text.lines().size
    val systemPrompt = WingmanPrompts.openerSystemPrompt(lineCount, customSystemPrompt)
    val userPrompt = WingmanPrompts.openerUserPrompt(text, avoidReplies, profileBlock)
    val safeModel = sanitizeModelId(model)

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
        if (safeModel.isNotBlank()) put("model", safeModel)
        put("temperature", temperature.toDouble())
        put("top_p", topP.toDouble())
        put("max_tokens", maxTokens)
        if (structured) {
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
        }
        // Lenient routing: no provider.require_parameters. Forcing it shrinks the
        // OpenRouter provider pool to models with strict structured-output support
        // and surfaces as HTTP 503 "No available model provider". Parsing below
        // already handles bare-array / plain-text fallbacks, so prefer availability.
        if (stream) put("stream", true)
    }
}

/** Strips UI-list artifacts (leading `~`) and surrounding whitespace from model IDs. */
fun sanitizeModelId(model: String): String = model.trim().trimStart('~').trim()

/** Transient upstream codes worth retrying with backoff (OpenRouter uses 529 for overloaded). */
fun isTransientHttpCode(code: Int): Boolean = code == 500 || code == 502 || code == 503 || code == 529

/**
 * True when the failure plausibly blames structured outputs: explicit schema
 * keywords, or OpenRouter's empty-provider-pool signal on 4xx/5xx.
 */
fun isStructuredOutputError(body: String?, code: Int): Boolean {
    val text = body.orEmpty()
    val mentionsStructured = text.contains("response_format", ignoreCase = true) ||
        text.contains("json_schema", ignoreCase = true) ||
        text.contains("require_parameters", ignoreCase = true) ||
        text.contains("structured", ignoreCase = true) ||
        text.contains("No available model provider", ignoreCase = true) ||
        text.contains("No endpoints", ignoreCase = true)
    return mentionsStructured && (code in 400..499 || isTransientHttpCode(code))
}

internal fun friendlyGenerationError(
    body: String?,
    code: Int,
    message: String,
): String {
    val detail = apiErrorMessage(body, code, message)
    return when (code) {
        401, 403 -> "Invalid API key ($detail). Check the key in Settings."
        404 -> "Model not found ($detail). Pick another model in Settings."
        429 -> "Rate limited ($detail). Wait a minute and retry."
        500, 502, 503, 529 -> "Model temporarily unavailable (HTTP $code: $detail). Retry or switch model in Settings."
        else -> "Generation failed: $detail"
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
