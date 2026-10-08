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
import kotlinx.serialization.json.JsonPrimitive
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
        structured: Boolean = true,
        reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
        promptTemplate: String? = null,
        directionalStimulus: String = "",
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
                            structured = useStructured,
                            reasoningEffort = reasoningEffort,
                            promptTemplate = promptTemplate,
                            directionalStimulus = directionalStimulus,
                        )
                    val targetUrl = baseUrl.ifBlank { OpenRouterDefaultBaseUrl }
                    val request =
                        Request
                            .Builder()
                            .url(targetUrl)
                            .apply {
                                if (ZenRouter.isZenUrl(targetUrl)) {
                                    ZenRouter.injectZenHeaders(this, apiKey, safeModel)
                                } else {
                                    if (apiKey.isNotBlank()) {
                                        addHeader("Authorization", "Bearer ${apiKey.trim()}")
                                    }
                                    addHeader("HTTP-Referer", "https://github.com/s1ddhants1/unhinge")
                                    addHeader("X-Title", "Unhinge")
                                }
                            }.addHeader("Content-Type", "application/json")
                            .post(body.toString().toRequestBody(jsonMediaType))
                            .build()

                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body.string()
                        if (!response.isSuccessful) {
                            val structuredRejection = isStructuredOutputError(responseBody, response.code)
                            if (useStructured && structuredRejection) {
                                useStructured = false
                            }
                            val error = friendlyGenerationError(responseBody, response.code, response.message, baseUrl = targetUrl, model = safeModel)

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
    structured: Boolean = true,
    reasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
    promptTemplate: String? = null,
    directionalStimulus: String = "",
): JsonObject {
    val lineCount = text.lines().size
    val systemPrompt = WingmanPrompts.openerSystemPrompt(lineCount, customSystemPrompt)
    val userPrompt = WingmanPrompts.openerUserPrompt(
        text = text,
        avoidReplies = avoidReplies,
        profileBlock = profileBlock,
        template = promptTemplate,
        directionalStimulus = directionalStimulus,
    )
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
        if (ZenRouter.isZenUrl(baseUrl) && ZenRouter.isFreeModel(safeModel)) {
            put("tools", ZenRouter.DefaultZenChatTools)
        }

        if (stream) {
            put("stream", true)
            if (ZenRouter.isZenUrl(baseUrl)) {
                put("stream_options", buildJsonObject { put("include_usage", true) })
            }
        }
    }
}

fun sanitizeModelId(model: String): String = model.trim().trimStart('~').trim()

fun isTransientHttpCode(code: Int): Boolean = code == 500 || code == 502 || code == 503 || code == 529

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
    provider: String = "",
    baseUrl: String = "",
    model: String = "",
): String {
    val detail = apiErrorMessage(body, code, message)
    val isZen = provider.equals("Zen", ignoreCase = true) || ZenRouter.isZenUrl(baseUrl)
    val isFree = isZen && ZenRouter.isFreeModel(model)
    val isClaude = provider.equals("Claude", ignoreCase = true) || baseUrl.contains("anthropic.com", ignoreCase = true)
    val isGemini = provider.equals("Gemini", ignoreCase = true) || baseUrl.contains("googleapis.com", ignoreCase = true)
    val isOpenAi = provider.equals("OpenAI", ignoreCase = true) || baseUrl.contains("openai.com", ignoreCase = true)
    val isOpenRouter = provider.equals("OpenRouter", ignoreCase = true) || baseUrl.contains("openrouter.ai", ignoreCase = true)

    if (isZen) {
        return when (code) {
            401 -> when {
                detail.contains("ModelError", ignoreCase = true) || detail.contains("not supported", ignoreCase = true) ->
                    "Zen model error ($detail). The selected model may not be supported on the free tier; try switching to space-bunny-free in Settings."
                isFree ->
                    "Zen free-tier authorization failed ($detail). No API key is needed for free models; check your connection or try space-bunny-free."
                else ->
                    "Invalid Zen API key ($detail). Check your API key in Settings (opencode.ai/auth)."
            }
            402 -> "Insufficient Zen credits ($detail). Switch to a free model (space-bunny-free) or add credits at opencode.ai/auth."
            403 -> when {
                detail.contains("FreeTierError", ignoreCase = true) || detail.contains("within OpenCode", ignoreCase = true) ->
                    "Zen free-tier restriction ($detail). Try switching to space-bunny-free or configure a Zen API key."
                else -> "Zen request forbidden ($detail)."
            }
            404 -> "Zen model not found ($detail). Pick another model in Settings (e.g. space-bunny-free)."
            429 -> "Zen rate limited ($detail). Wait a minute and retry, or switch to another model."
            500, 502, 503, 529 -> "Zen model temporarily unavailable (HTTP $code: $detail). Retry or switch model in Settings."
            else -> "Zen generation failed: $detail"
        }
    }

    if (isClaude) {
        return when (code) {
            401 -> "Invalid Anthropic API key ($detail). Check your key in Settings (console.anthropic.com/settings/keys)."
            402 -> "Insufficient Anthropic credits ($detail). Add credits at console.anthropic.com."
            403 -> "Anthropic request forbidden ($detail)."
            404 -> "Anthropic model not found ($detail). Pick another model in Settings."
            429 -> "Anthropic rate limited ($detail). Wait a minute and retry."
            500, 502, 503, 529 -> "Anthropic service temporarily unavailable (HTTP $code: $detail). Retry or switch model in Settings."
            else -> "Anthropic generation failed: $detail"
        }
    }

    if (isGemini) {
        return when (code) {
            401 -> "Invalid Gemini API key ($detail). Check your key in Settings (aistudio.google.com/apikey)."
            403 -> "Gemini request forbidden ($detail). Check key permissions at aistudio.google.com."
            404 -> "Gemini model not found ($detail). Pick another model in Settings."
            429 -> "Gemini rate limited ($detail). Wait a minute and retry."
            500, 502, 503, 529 -> "Gemini service temporarily unavailable (HTTP $code: $detail). Retry or switch model in Settings."
            else -> "Gemini generation failed: $detail"
        }
    }

    if (isOpenAi) {
        return when (code) {
            401 -> "Invalid OpenAI API key ($detail). Check your key in Settings (platform.openai.com/api-keys)."
            402 -> "Insufficient OpenAI credits ($detail). Add credits at platform.openai.com."
            403 -> "OpenAI request forbidden ($detail)."
            404 -> "OpenAI model not found ($detail). Pick another model in Settings."
            429 -> "OpenAI rate limited ($detail). Wait a minute and retry."
            500, 502, 503, 529 -> "OpenAI service temporarily unavailable (HTTP $code: $detail). Retry or switch model in Settings."
            else -> "OpenAI generation failed: $detail"
        }
    }

    if (isOpenRouter) {
        return when (code) {
            401 -> "Invalid API key ($detail). Check the key in Settings (openrouter.ai/keys, starts with sk-or-)."
            402 -> "Insufficient credits ($detail). Free models (:free) still need a valid key; add credits or raise the key limit."
            403 -> "Request forbidden ($detail). Key is valid but blocked by guardrail/moderation/permissions."
            404 -> "Model not found ($detail). Pick another model in Settings."
            429 -> "Rate limited ($detail). Wait a minute and retry."
            500, 502, 503, 529 -> "Model temporarily unavailable (HTTP $code: $detail). Retry or switch model in Settings."
            else -> "Generation failed: $detail"
        }
    }

    return when (code) {
        401 -> "Invalid API key ($detail). Check the key in Settings."
        402 -> "Insufficient credits ($detail). Check your provider account balance."
        403 -> "Request forbidden ($detail)."
        404 -> "Model not found ($detail). Pick another model in Settings."
        429 -> "Rate limited ($detail). Wait a minute and retry."
        500, 502, 503, 529 -> "Service temporarily unavailable (HTTP $code: $detail). Retry or switch model in Settings."
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
                ?: run {
                    val rawLines = cleaned.lines().map { it.trim() }.filter { it.isNotBlank() }
                    val filteredLines = rawLines.filterNot { line ->
                        line.startsWith("#") ||
                        line.startsWith("---") ||
                        line.startsWith("***") ||
                        (line.startsWith("**") && line.endsWith("**") && line.length < 35) ||
                        line.startsWith("Why it works", ignoreCase = true)
                    }
                    filteredLines
                        .map { line ->
                            line.removePrefix(">")
                                .trim()
                                .removePrefix("-")
                                .replace(Regex("^\\d+[.):]\\s*"), "")
                                .removeSurrounding("\"")
                                .removeSurrounding("'")
                                .trim()
                        }
                        .filter(String::isNotBlank)
                        .takeIf(List<String>::isNotEmpty)
                }
                ?: error("Failed to parse response")

        generatedLines.take(expectedLineCount) + List((expectedLineCount - generatedLines.size).coerceAtLeast(0)) { "" }
    }

private fun extractLines(element: JsonElement): List<String>? =
    when (element) {

        is JsonObject -> {
            val linesArray = (element["lines"] as? JsonArray)
                ?: (element["replies"] as? JsonArray)
                ?: (element["openers"] as? JsonArray)
                ?: (element["suggestions"] as? JsonArray)
                ?: element.values.firstOrNull { it is JsonArray } as? JsonArray
            linesArray?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                ?: (element["reply"] as? JsonPrimitive)?.content?.let { listOf(it) }
                ?: (element["opener"] as? JsonPrimitive)?.content?.let { listOf(it) }
                ?: (element["line"] as? JsonPrimitive)?.content?.let { listOf(it) }
                ?: element.values.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }.takeIf { it.isNotEmpty() }
        }

        is JsonArray -> element.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
        is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }?.let { listOf(it) }
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
