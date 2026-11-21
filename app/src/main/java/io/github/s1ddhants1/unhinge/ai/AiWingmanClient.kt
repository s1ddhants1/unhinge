package io.github.s1ddhants1.unhinge.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object AiWingmanClient {
    val modelCatalog: AiModelCatalog = AiModelCatalog()

    val linesSchema: JsonObject = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put(
            "properties",
            buildJsonObject {
                put(
                    "lines",
                    buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put(
                            "items",
                            buildJsonObject {
                                put("type", JsonPrimitive("string"))
                            },
                        )
                    },
                )
            },
        )
        put("required", JsonArray(listOf(JsonPrimitive("lines"))))
    }

    private class FreeModelSession {
        val lock = Mutex()
        val throttled = mutableMapOf<String, Long>()
        var lastBusyFailure: Throwable? = null
        var dailyFailure: Throwable? = null
        var dailyResetMillis = 0L
    }

    private val freeModelSessions = mutableMapOf<String, FreeModelSession>()
    private val freeModelSessionsLock = Mutex()

    private suspend fun freeModelSession(apiKey: String): FreeModelSession =
        freeModelSessionsLock.withLock { freeModelSessions.getOrPut(apiKey) { FreeModelSession() } }

    suspend fun recheckFreeQuota(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = freeModelSession(apiKey)
            session.lock.withLock {
                if (session.dailyFailure != null && System.currentTimeMillis() < session.dailyResetMillis) {
                    val request = Request.Builder()
                        .url("https://openrouter.ai/api/v1/key")
                        .header("Authorization", "Bearer ${apiKey.trim()}")
                        .get()
                        .build()
                    AiHttpClient.execute(request).use { response ->
                        val text = response.text()
                        if (!response.isSuccessful) throw AiHttpException(response.code, text, response.headers)
                        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                        val data = root?.get("data") as? JsonObject
                        val quota = data?.get("free_model_daily_requests") as? JsonObject
                        val remaining = (quota?.get("remaining") as? JsonPrimitive)?.intOrNull
                        if (remaining != null && remaining > 0) session.dailyFailure = null
                    }
                }
            }
        }
    }

    private suspend fun withFreeModelFallback(
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        request: suspend (String) -> Result<String>,
    ): Result<String> {
        val preset = AiProviderPresets.find(provider) ?: return request(model)
        if (!AiProviderPresets.isFreeModel(model) || !preset.publicModelList) {
            return request(model)
        }

        val session = freeModelSession(apiKey)
        session.lock.lock()
        try {
            val now = System.currentTimeMillis()
            session.dailyFailure?.takeIf { now < session.dailyResetMillis }?.let { return Result.failure(it) }
            session.dailyFailure = null
            val attempted = mutableSetOf<String>()
            var candidates: List<String>? = null
            var selected = model.takeUnless { (session.throttled[it] ?: 0L) > now }

            while (true) {
                if (selected == null) {
                    if (candidates == null) {
                        candidates = modelCatalog
                            .models(preset, apiKey, preset.endpoint)
                            .getOrDefault(emptyList())
                            .filter { it.free && AiProviderPresets.isFreeModel(it.id) }
                            .map { it.id }
                    }
                    selected = candidates.firstOrNull {
                        it !in attempted && (session.throttled[it] ?: 0L) <= System.currentTimeMillis()
                    }
                }
                val candidate = selected ?: return Result.failure(
                    session.lastBusyFailure ?: AiException(AiFailure.MODEL_BUSY, "Free models are busy"),
                )
                attempted += candidate
                val result = request(candidate)
                if (result.isSuccess) {
                    return result
                }
                val error = checkNotNull(result.exceptionOrNull())
                when (error.aiFailure()) {
                    AiFailure.FREE_DAILY_LIMIT -> {
                        session.dailyFailure = error
                        session.dailyResetMillis = (error as? AiHttpException)?.retryAtMillis(now)
                            ?: ((now / 86_400_000L + 1L) * 86_400_000L)
                        return result
                    }
                    AiFailure.MODEL_BUSY -> {
                        session.throttled[candidate] = (error as? AiHttpException)?.retryAtMillis(now) ?: (now + 60_000L)
                        session.lastBusyFailure = error
                        selected = null
                    }
                    else -> return result
                }
            }
        } finally {
            session.lock.unlock()
        }
    }

    suspend fun requestStructuredAi(
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        schemaName: String = "wingman_reply",
        schema: JsonObject = linesSchema,
        maxTokens: Int = 1024,
        temperature: Double = 0.7,
        topP: Double = 0.95,
        reasoningEffort: String = "low",
        onModelSelected: (String) -> Unit = {},
    ): Result<String> = retryAiRequest {
        val preset = AiProviderPresets.find(provider)
        val effectiveModel = model.ifBlank { preset?.defaultModel.orEmpty() }
        val effectiveBase = baseUrl.ifBlank { preset?.endpoint.orEmpty() }
        val protocol = AiProviderPresets.protocolFor(provider, effectiveBase, effectiveModel)
        val effectiveSystemPrompt = if (OpenCodeEmulation.isOpenCode(provider, effectiveBase)) {
            OpenCodeEmulation.withZenEnvironment(systemPrompt)
        } else {
            systemPrompt
        }

        when (protocol) {
            AiProtocol.ANTHROPIC -> {
                requestAnthropicCompatible(
                    apiKey = apiKey,
                    baseUrl = AiProviderPresets.requestUrl(provider, effectiveBase, effectiveModel),
                    model = effectiveModel,
                    systemPrompt = effectiveSystemPrompt,
                    userPrompt = userPrompt,
                    schemaName = schemaName,
                    schema = schema,
                    maxTokens = maxTokens,
                    temperature = temperature,
                )
            }
            AiProtocol.GEMINI -> {
                requestGeminiCompatible(
                    apiKey = apiKey,
                    baseUrl = effectiveBase,
                    model = effectiveModel,
                    systemPrompt = effectiveSystemPrompt,
                    userPrompt = userPrompt,
                    schema = schema,
                    maxTokens = maxTokens,
                    temperature = temperature,
                )
            }
            AiProtocol.OPENAI -> {
                withFreeModelFallback(provider, apiKey, effectiveBase, effectiveModel) { selectedModel ->
                    requestOpenAiCompatible(
                        provider = provider,
                        apiKey = apiKey,
                        baseUrl = effectiveBase,
                        model = selectedModel,
                        systemPrompt = effectiveSystemPrompt,
                        userPrompt = userPrompt,
                        schemaName = schemaName,
                        schema = schema,
                        maxTokens = maxTokens,
                        temperature = temperature,
                        topP = topP,
                        reasoningEffort = reasoningEffort,
                    ).onSuccess { onModelSelected(selectedModel) }
                }
            }
        }
    }

    private suspend fun requestOpenAiCompatible(
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        schemaName: String,
        schema: JsonObject,
        maxTokens: Int,
        temperature: Double,
        topP: Double = 0.95,
        reasoningEffort: String = "low",
    ): Result<String> {
        val openCode = OpenCodeEmulation.isOpenCode(provider, baseUrl)
        val effectiveSystemPrompt = if (openCode) OpenCodeEmulation.withFreeTierPrefix(systemPrompt) else systemPrompt

        fun body(responseFormat: JsonObject?, includeReasoning: Boolean = true) = buildJsonObject {
            if (model.isNotBlank()) put("model", JsonPrimitive(model))
            put(
                "messages",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("role", JsonPrimitive("system"))
                            put("content", JsonPrimitive(effectiveSystemPrompt))
                        },
                        buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put("content", JsonPrimitive(userPrompt))
                        },
                    ),
                ),
            )
            put("temperature", JsonPrimitive(temperature))
            put("top_p", JsonPrimitive(topP))
            put("max_tokens", JsonPrimitive(maxTokens))
            if (includeReasoning && (provider.equals("OpenRouter", ignoreCase = true) || baseUrl.contains("openrouter.ai"))) {
                put(
                    "reasoning",
                    buildJsonObject {
                        if (AiProviderPresets.openRouterOfflineModels.any { it.id == model }) put("enabled", JsonPrimitive(false))
                        put("exclude", JsonPrimitive(true))
                    },
                )
            }
            if (responseFormat != null) put("response_format", responseFormat)
            if (openCode) put("stream", JsonPrimitive(true))
        }

        return requestWithStructuredFallback(
            url = baseUrl.ifBlank { "https://openrouter.ai/api/v1/chat/completions" },
            bodies = listOf(
                body(openAiResponseFormat(schemaName, schema)),
                body(buildJsonObject { put("type", JsonPrimitive("json_object")) }),
                body(null),
                body(null, includeReasoning = false),
            ).distinct(),
            parse = ::extractOpenAiContent,
            applyHeaders = {
                if (openCode) {
                    OpenCodeEmulation.injectHeaders(this, apiKey)
                } else {
                    if (apiKey.isNotBlank()) header("Authorization", "Bearer ${apiKey.trim()}")
                    header("HTTP-Referer", "https://github.com/s1ddhants1/unhinge")
                    header("X-Title", "Unhinge")
                }
            },
        )
    }

    private suspend fun requestAnthropicCompatible(
        apiKey: String,
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        schemaName: String,
        schema: JsonObject,
        maxTokens: Int,
        temperature: Double,
    ): Result<String> = withContext(Dispatchers.IO) {
        val toolName = "return_$schemaName"
        val body = buildAnthropicRequestBody(
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            toolName = toolName,
            schema = schema,
            maxTokens = maxTokens,
            temperature = temperature,
        )
        val reqBuilder = Request.Builder()
            .url(baseUrl.ifBlank { "https://api.anthropic.com/v1/messages" })
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (OpenCodeEmulation.isOpenCode("Anthropic", baseUrl)) {
            OpenCodeEmulation.injectHeaders(reqBuilder, apiKey)
        } else {
            reqBuilder.header("anthropic-version", "2023-06-01")
            if (apiKey.isNotBlank()) reqBuilder.header("x-api-key", apiKey.trim())
        }

        try {
            AiHttpClient.execute(reqBuilder.build()).use { response ->
                val text = response.readTextLimited(AiModelCatalog.MODEL_RESPONSE_LIMIT_BYTES.toLong())
                    ?: return@withContext Result.failure(AiException(AiFailure.REQUEST_FAILED, "Response exceeded size limit"))
                if (!response.isSuccessful) {
                    return@withContext Result.failure(AiHttpException(response.code, text, response.headers))
                }
                parseAiCompletion(text, ::extractAnthropicContent)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun buildAnthropicRequestBody(
        model: String,
        systemPrompt: String,
        userPrompt: String,
        toolName: String,
        schema: JsonObject,
        maxTokens: Int,
        temperature: Double,
    ): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(model.ifBlank { "claude-haiku-5-5" }))
        put("max_tokens", JsonPrimitive(maxTokens))
        put("temperature", JsonPrimitive(temperature))
        put("system", JsonPrimitive(systemPrompt))
        put(
            "messages",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(userPrompt))
                    },
                ),
            ),
        )
        put(
            "tools",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("name", JsonPrimitive(toolName))
                        put("description", JsonPrimitive("Return structured wingman suggestions."))
                        put("input_schema", schema)
                    },
                ),
            ),
        )
        put(
            "tool_choice",
            buildJsonObject {
                put("type", JsonPrimitive("tool"))
                put("name", JsonPrimitive(toolName))
            },
        )
    }

    private suspend fun requestGeminiCompatible(
        apiKey: String,
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        schema: JsonObject,
        maxTokens: Int,
        temperature: Double,
    ): Result<String> = withContext(Dispatchers.IO) {
        val geminiPreset = AiProviderPresets.find("Gemini")
            ?: return@withContext Result.failure(IllegalStateException("Gemini preset is not configured"))
        val effectiveModel = model.ifBlank { geminiPreset.defaultModel }
        if (effectiveModel.isBlank()) return@withContext Result.failure(IllegalArgumentException("Gemini model is required"))
        val resolvedBase = baseUrl.ifBlank { geminiPreset.endpoint }
        if (resolvedBase.isBlank()) return@withContext Result.failure(IllegalArgumentException("Gemini endpoint is required"))

        val url = when {
            resolvedBase.contains("{model}") -> resolvedBase.replace("{model}", effectiveModel)
            effectiveModel.isNotBlank() -> resolvedBase.replace(Regex("/models/[^/:]+(:generateContent)")) {
                "/models/$effectiveModel${it.groupValues[1]}"
            }
            else -> resolvedBase
        }

        val body = buildGeminiRequestBody(systemPrompt, userPrompt, temperature, maxTokens, schema)
        val reqBuilder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (OpenCodeEmulation.isOpenCode("Gemini", url)) {
            OpenCodeEmulation.injectHeaders(reqBuilder, apiKey)
        } else if (apiKey.isNotBlank() && !url.contains("key=")) {
            reqBuilder.header("x-goog-api-key", apiKey.trim())
        }

        try {
            AiHttpClient.execute(reqBuilder.build()).use { response ->
                val text = response.readTextLimited(AiModelCatalog.MODEL_RESPONSE_LIMIT_BYTES.toLong())
                    ?: return@withContext Result.failure(AiException(AiFailure.REQUEST_FAILED, "Response exceeded size limit"))
                if (!response.isSuccessful) {
                    return@withContext Result.failure(AiHttpException(response.code, text, response.headers))
                }
                parseAiCompletion(text, ::extractGeminiContent)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun buildGeminiRequestBody(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        maxTokens: Int,
        schema: JsonObject,
    ): JsonObject = buildJsonObject {
        put(
            "contents",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put(
                            "parts",
                            JsonArray(listOf(buildJsonObject { put("text", JsonPrimitive("$systemPrompt\n\n$userPrompt")) })),
                        )
                    },
                ),
            ),
        )
        put(
            "generationConfig",
            buildJsonObject {
                put("temperature", JsonPrimitive(temperature))
                put("maxOutputTokens", JsonPrimitive(maxTokens))
                put("responseMimeType", JsonPrimitive("application/json"))
                put("responseSchema", geminiSchema(schema))
            },
        )
    }

    private fun geminiSchema(schema: JsonObject): JsonObject = buildJsonObject {
        schema.forEach { (key, value) ->
            if (key != "additionalProperties") {
                put(key, if (key == "type") JsonPrimitive(value.jsonPrimitive.content.uppercase()) else value.toGeminiSchemaValue())
            }
        }
    }

    private fun JsonElement.toGeminiSchemaValue(): JsonElement = when (this) {
        is JsonObject -> geminiSchema(this)
        is JsonArray -> JsonArray(map { it.toGeminiSchemaValue() })
        is JsonPrimitive -> {
            if (isString && contentOrNull in setOf("object", "array", "string", "boolean", "number", "integer")) {
                JsonPrimitive(content.uppercase())
            } else {
                this
            }
        }
    }

    private suspend fun requestWithStructuredFallback(
        url: String,
        bodies: List<JsonObject>,
        parse: (String) -> String?,
        applyHeaders: Request.Builder.() -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        bodies.forEachIndexed { bodyIndex, body ->
            val reqBuilder = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            reqBuilder.applyHeaders()

            try {
                AiHttpClient.execute(reqBuilder.build()).use { response ->
                    val responseText = response.readTextLimited(AiModelCatalog.MODEL_RESPONSE_LIMIT_BYTES.toLong())
                        ?: return@use
                    if (!response.isSuccessful) {
                        if (bodyIndex < bodies.lastIndex && response.code in setOf(400, 422)) return@use
                        return@withContext Result.failure(
                            AiHttpException(
                                response.code,
                                responseText,
                                response.headers,
                            ),
                        )
                    }
                    val isEventStream = response.headers["Content-Type"]?.contains("text/event-stream", ignoreCase = true) == true ||
                        responseText.trimStart().startsWith("data:")
                    val normalized = if (isEventStream) {
                        collapseEventStream(responseText)
                    } else {
                        responseText
                    }
                    val completion = parseAiCompletion(normalized, parse)
                    if (completion.isSuccess) {
                        return@withContext completion
                    }
                    if (bodyIndex == bodies.lastIndex) {
                        return@withContext completion
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (bodyIndex == bodies.lastIndex) return@withContext Result.failure(e)
            }
        }
        Result.failure(AiException(AiFailure.REQUEST_FAILED, "AI request failed"))
    }

    private fun collapseEventStream(text: String): String {
        val content = StringBuilder()
        var finishReason: String? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (!line.startsWith("data:")) return@forEach
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty() || payload == "[DONE]") return@forEach
            val chunk = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return@forEach
            val choice = (chunk["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@forEach
            val deltaOrMessage = (choice["delta"] as? JsonObject) ?: (choice["message"] as? JsonObject)
            (deltaOrMessage?.get("content") as? JsonPrimitive)?.contentOrNull?.let { chunkText ->
                content.append(chunkText)
            }
            ((choice["finish_reason"] as? JsonPrimitive)?.contentOrNull
                ?: (deltaOrMessage?.get("finish_reason") as? JsonPrimitive)?.contentOrNull)?.let {
                finishReason = it
            }
        }
        return buildJsonObject {
            put(
                "choices",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("message", buildJsonObject { put("content", JsonPrimitive(content.toString())) })
                            finishReason?.let { put("finish_reason", JsonPrimitive(it)) }
                        },
                    ),
                ),
            )
        }.toString()
    }

    private fun parseAiCompletion(
        responseText: String,
        parse: (String) -> String?,
    ): Result<String> {
        val responseJson = runCatching { Json.parseToJsonElement(responseText).jsonObject }.getOrNull()
        val choice = (responseJson?.get("choices") as? JsonArray)?.firstOrNull() as? JsonObject
        val candidate = (responseJson?.get("candidates") as? JsonArray)?.firstOrNull() as? JsonObject
        val finish = choice?.get("finish_reason") ?: candidate?.get("finishReason") ?: responseJson?.get("stop_reason")
        val finishReason = (finish as? JsonPrimitive)?.contentOrNull
        val incomplete = finishReason in setOf("length", "MAX_TOKENS", "max_tokens")
        val content = parse(responseText)?.takeIf(String::isNotBlank)

        if (incomplete) {
            return Result.failure(
                AiException(AiFailure.INCOMPLETE_RESPONSE, "AI output reached the token limit"),
            )
        }
        if (responseJson == null) {
            return Result.failure(
                AiException(AiFailure.INVALID_RESPONSE, "AI response was not JSON"),
            )
        }
        if (responseJson["error"] != null || choice?.get("error") != null || finishReason in setOf("error", "content_filter", "SAFETY")) {
            return Result.failure(
                AiException(classifyAiHttpFailure(200, responseText), "AI provider rejected the completion"),
            )
        }
        if (content.isNullOrBlank()) {
            return Result.failure(
                AiException(AiFailure.EMPTY_RESPONSE, "AI response was empty"),
            )
        }
        return Result.success(content)
    }

    private fun extractOpenAiContent(responseText: String): String? = runCatching {
        val message = Json.parseToJsonElement(responseText)
            .jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?: return null
        message["content"]
            ?.let(::extractTextContent)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: message["tool_calls"]
                ?.jsonArray
                ?.firstNotNullOfOrNull { call ->
                    call.jsonObject["function"]
                        ?.jsonObject
                        ?.get("arguments")
                        ?.let { if (it is JsonPrimitive) it.contentOrNull else it.toString() }
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                }
    }.getOrNull()

    private fun extractAnthropicContent(responseText: String): String? = runCatching {
        val content = Json.parseToJsonElement(responseText).jsonObject["content"]?.jsonArray ?: return null
        content.firstNotNullOfOrNull { part ->
            part.jsonObject
                .takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "tool_use" }
                ?.get("input")
                ?.toString()
        }?.trim()
            ?: content.mapNotNull { part ->
                part.jsonObject
                    .takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
                    ?.get("text")
                    ?.let(::extractTextContent)
            }.joinToString("")
                .trim()
                .takeIf(String::isNotBlank)
    }.getOrNull()

    private fun extractTextContent(element: JsonElement): String? = when (element) {
        JsonNull -> null
        is JsonPrimitive -> element.contentOrNull
        is JsonArray -> element.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull
                is JsonObject -> item["text"]?.let(::extractTextContent)
                else -> null
            }
        }.joinToString("").takeIf(String::isNotBlank)
        is JsonObject -> element["text"]?.let(::extractTextContent)
    }

    private fun extractGeminiContent(responseText: String): String? = runCatching {
        Json.parseToJsonElement(responseText)
            .jsonObject["candidates"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("content")
            ?.jsonObject
            ?.get("parts")
            ?.jsonArray
            ?.filter { (it as? JsonObject)?.get("thought") != JsonPrimitive(true) }
            ?.let(::JsonArray)
            ?.let(::extractTextContent)
            ?.trim()
    }.getOrNull()

    private fun parseJsonObjectPayload(content: String): JsonObject? = parseJsonPayload(content, '{', '}') as? JsonObject

    private fun parseJsonPayload(
        content: String,
        opening: Char,
        closing: Char,
    ): JsonElement? {
        for (start in content.indices.filter { content[it] == opening }) {
            var depth = 0
            var quoted = false
            var escaped = false
            for (index in start..<content.length) {
                val char = content[index]
                if (quoted) {
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '"' -> quoted = false
                    }
                } else {
                    when (char) {
                        '"' -> quoted = true
                        opening -> depth++
                        closing -> depth--
                    }
                    if (depth == 0) {
                        val parsed = runCatching { Json.parseToJsonElement(content.substring(start, index + 1)) }.getOrNull()
                        if (parsed != null) return parsed
                        break
                    }
                }
            }
        }
        return null
    }

    fun parseJsonLinesPayload(content: String): List<String>? {
        val json = parseJsonObjectPayload(content)
        val lines = (json?.get("lines") as? JsonArray)?.let(::parseStringLines)
        if (!lines.isNullOrEmpty()) return lines
        return (parseJsonPayload(content, '[', ']') as? JsonArray)?.let(::parseStringLines)
    }

    private fun parseStringLines(lines: JsonArray): List<String>? =
        lines.mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }

    private fun openAiResponseFormat(
        name: String,
        schema: JsonObject,
    ): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("json_schema"))
        put(
            "json_schema",
            buildJsonObject {
                put("name", JsonPrimitive(name))
                put("strict", JsonPrimitive(true))
                put("schema", schema)
            },
        )
    }

    suspend fun probeConnection(
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
    ): Result<Pair<String, Long>> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val userPrompt = """{"lines":["Hey, how are you?"]}"""
        val systemPrompt = "You are a test assistant. Return a JSON object with a 'lines' array containing 1 friendly reply greeting."

        if (AiProviderPresets.isFreeModel(model) && provider.equals("OpenRouter", ignoreCase = true)) {
            recheckFreeQuota(apiKey)
        }

        val result = requestStructuredAi(
            provider = provider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            maxTokens = 64,
            temperature = 0.3,
        )

        val latency = System.currentTimeMillis() - start
        result.mapCatching { raw ->
            val lines = parseJsonLinesPayload(raw)
            val reply = lines?.firstOrNull() ?: raw.take(100)
            reply to latency
        }
    }
}
