package io.github.s1ddhants1.unhinge.ai

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AiModelInfo(
    val id: String,
    val name: String = id,
    val inputUsdPerMTok: Double? = null,
    val outputUsdPerMTok: Double? = null,
    val contextLength: Int? = null,
    val tokensPerSecond: Double? = null,
) {
    val free: Boolean get() = inputUsdPerMTok == 0.0 && outputUsdPerMTok == 0.0
}

class AiModelCatalog(
    private val httpClient: OkHttpClient = AiHttpClient.client,
) {
    private val cache = mutableMapOf<String, List<AiModelInfo>>()
    private val availableIds = mutableMapOf<String, Set<String>>()
    private val throughputCache = mutableMapOf<String, Double?>()
    private val lock = Mutex()
    private val modelLoadLock = Mutex()
    private var modelsDev: Map<String, ModelsDevProvider>? = null

    suspend fun models(
        preset: AiProviderPreset,
        apiKey: String,
        baseUrl: String,
        refresh: Boolean = false,
    ): Result<List<AiModelInfo>> = modelLoadLock.withLock {
        withContext(Dispatchers.IO) {
            val cacheKey = cacheKey(preset, apiKey, baseUrl)
            if (!refresh) {
                lock.withLock { cache[cacheKey] }?.let { return@withContext Result.success(it) }
            }
            try {
                val fromApi = if (preset.modelsEndpoint.isNotBlank()) {
                    fetchProviderModels(preset, apiKey, baseUrl, cacheKey)
                } else null

                if (fromApi?.isFailure == true) {
                    lock.withLock { availableIds.remove(cacheKey) }
                }

                val dev = preset.modelsDevId?.let { devProvider(it) }
                var result = fromApi?.getOrNull()?.let { priceFromModelsDev(it, dev, preset) }

                if (result.isNullOrEmpty() && (preset.id != "OpenRouter" || fromApi?.isSuccess != true)) {
                    result = dev?.let(::parseModelsDevModels)?.let { priceFromModelsDev(it, dev, preset) }
                }

                if (result.isNullOrEmpty() && preset.id == "OpenRouter" && fromApi?.isSuccess != true) {
                    result = filterAndSort(AiProviderPresets.openRouterOfflineModels)
                }

                if (result == null) {
                    return@withContext Result.failure(fromApi?.exceptionOrNull() ?: IllegalStateException("No model list available"))
                }

                lock.withLock { cache[cacheKey] = result }
                Result.success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun cacheKey(
        preset: AiProviderPreset,
        apiKey: String,
        baseUrl: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = Base64.encodeToString(digest.digest(apiKey.trim().toByteArray()), Base64.NO_WRAP)
        return "${preset.id}|$baseUrl|$hash"
    }

    suspend fun validatedModel(
        preset: AiProviderPreset,
        apiKey: String,
        baseUrl: String,
        savedModel: String,
    ): String {
        val effectiveBaseUrl = if (preset.id == "Gemini" && baseUrl == AiProviderPresets.LEGACY_GEMINI_ENDPOINT) {
            preset.endpoint
        } else {
            baseUrl
        }
        if (!preset.editableEndpoint && effectiveBaseUrl != preset.endpoint) return savedModel
        val models = models(preset, apiKey, effectiveBaseUrl).getOrNull() ?: return savedModel
        val ids = lock.withLock { availableIds[cacheKey(preset, apiKey, effectiveBaseUrl)] } ?: return savedModel
        if (ids.isEmpty() || savedModel in ids) return savedModel
        val candidates = models.filter { it.id in ids }
        return defaultModel(preset, candidates, freeOnly = savedModel.endsWith(":free")) ?: savedModel
    }

    suspend fun throughput(modelId: String): Double? = withContext(Dispatchers.IO) {
        lock.withLock { if (throughputCache.containsKey(modelId)) return@withContext throughputCache[modelId] }
        val value = try {
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/models/$modelId/endpoints")
                .header("HTTP-Referer", "https://github.com/s1ddhants1/unhinge")
                .header("X-Title", "Unhinge")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body.string()
                    parseOpenRouterThroughput(body)
                } else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        lock.withLock { throughputCache[modelId] = value }
        value
    }

    private suspend fun fetchProviderModels(
        preset: AiProviderPreset,
        apiKey: String,
        baseUrl: String,
        cacheKey: String,
    ): Result<List<AiModelInfo>> {
        if (apiKey.isBlank() && !preset.publicModelList) return Result.failure(IllegalStateException("API key required"))
        val root = baseUrl.ifBlank { preset.endpoint }
        val url = AiProviderPreset(preset.id, preset.group, preset.protocol, root)
            .modelsEndpoint
            .let {
                when (preset.protocol) {
                    AiProtocol.ANTHROPIC -> "$it?limit=1000"
                    AiProtocol.GEMINI -> "$it?pageSize=1000"
                    else -> it
                }
            }

        return try {
            val reqBuilder = Request.Builder().url(url).get()
            if (preset.id == "OpenRouter" || url.contains("openrouter.ai")) {
                reqBuilder.header("HTTP-Referer", "https://github.com/s1ddhants1/unhinge")
                reqBuilder.header("X-Title", "Unhinge")
            }
            if (apiKey.isNotBlank()) {
                when (preset.protocol) {
                    AiProtocol.ANTHROPIC -> {
                        reqBuilder.header("x-api-key", apiKey.trim())
                        reqBuilder.header("anthropic-version", "2023-06-01")
                    }
                    AiProtocol.GEMINI -> {
                        reqBuilder.header("x-goog-api-key", apiKey.trim())
                    }
                    AiProtocol.OPENAI -> {
                        reqBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
                    }
                }
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                with(AiHttpClient) {
                    val body = response.readTextLimited(MODEL_RESPONSE_LIMIT_BYTES.toLong())
                        ?: return Result.failure(IllegalStateException("Model list exceeds size limit"))
                    if (!response.isSuccessful) {
                        Result.failure(IllegalStateException("Model list failed (HTTP ${response.code})"))
                    } else {
                        val rootObject = Json.parseToJsonElement(body).jsonObject
                        val entries = rootObject[if (preset.protocol == AiProtocol.GEMINI) "models" else "data"].asObjects()
                        val idKey = if (preset.protocol == AiProtocol.GEMINI) "name" else "id"
                        val ids = entries.mapNotNull { it.string(idKey)?.removePrefix("models/") }.toSet()
                        val incomplete = rootObject["has_more"]?.jsonPrimitive?.booleanOrNull == true ||
                            !rootObject.string("nextPageToken").isNullOrBlank()
                        lock.withLock {
                            if (incomplete) availableIds.remove(cacheKey) else availableIds[cacheKey] = ids
                        }
                        Result.success(
                            when (preset.protocol) {
                                AiProtocol.ANTHROPIC -> parseAnthropicModels(rootObject)
                                AiProtocol.GEMINI -> parseGeminiModels(rootObject)
                                AiProtocol.OPENAI -> parseOpenAiModels(rootObject, textOnly = preset.id == "OpenRouter")
                            }
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun devProvider(id: String): ModelsDevProvider? {
        val root = modelsDev ?: try {
            val req = Request.Builder().url("https://models.dev/api.json").get().build()
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return null
                with(AiHttpClient) {
                    val body = response.readTextLimited(MODEL_RESPONSE_LIMIT_BYTES.toLong()) ?: return null
                    modelsDevJson.decodeFromString<Map<String, ModelsDevProvider>>(body).also { modelsDev = it }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return root?.get(id)
    }

    private fun priceFromModelsDev(
        models: List<AiModelInfo>,
        dev: ModelsDevProvider?,
        preset: AiProviderPreset,
    ): List<AiModelInfo> {
        val entries = dev?.models
        val sorted = models.filter { keep(it, entries, preset) }.map { model ->
            if (model.inputUsdPerMTok != null || entries == null) return@map model
            val cost = entries[model.id]?.cost ?: return@map model
            model.copy(
                inputUsdPerMTok = cost.input,
                outputUsdPerMTok = cost.output,
            )
        }
        return filterAndSort(sorted)
    }

    private fun keep(
        model: AiModelInfo,
        known: Map<String, ModelsDevModel>?,
        preset: AiProviderPreset,
    ): Boolean {
        if (known == null) return true
        val entry = known[model.id]
        if (entry != null && !modelsDevTextModel(entry)) return false
        return when {
            preset.mixedProtocols -> entry == null || implemented(entry)
            preset.group == AiProviderGroup.DIRECT && preset.protocol == AiProtocol.OPENAI -> entry != null
            else -> true
        }
    }

    companion object {
        const val MODEL_RESPONSE_LIMIT_BYTES = 16 * 1024 * 1024
        private val modelsDevJson = Json { ignoreUnknownKeys = true }

        fun parseOpenAiModels(
            root: JsonObject,
            textOnly: Boolean,
        ): List<AiModelInfo> =
            root["data"]
                .asObjects()
                .filter { openAiTextModel(it, requireMetadata = textOnly) && activeModel(it) }
                .mapNotNull { entry ->
                    val id = entry.string("id") ?: return@mapNotNull null
                    val pricing = entry["pricing"] as? JsonObject
                    AiModelInfo(
                        id = id,
                        name = entry.string("name") ?: id,
                        inputUsdPerMTok = pricing?.number("prompt")?.times(1_000_000),
                        outputUsdPerMTok = pricing?.number("completion")?.times(1_000_000),
                        contextLength = entry["context_length"]?.jsonPrimitive?.intOrNull,
                    )
                }

        fun parseAnthropicModels(root: JsonObject): List<AiModelInfo> =
            root["data"]
                .asObjects()
                .mapNotNull { entry ->
                    val id = entry.string("id") ?: return@mapNotNull null
                    AiModelInfo(id, entry.string("display_name") ?: id)
                }

        fun parseGeminiModels(root: JsonObject): List<AiModelInfo> =
            root["models"]
                .asObjects()
                .filter { entry ->
                    (entry["supportedGenerationMethods"] as? JsonArray)?.any { it.jsonPrimitive.contentOrNull == "generateContent" } == true
                }.mapNotNull { entry ->
                    val id = entry.string("name")?.removePrefix("models/") ?: return@mapNotNull null
                    AiModelInfo(id, entry.string("displayName") ?: id, contextLength = entry["inputTokenLimit"]?.jsonPrimitive?.intOrNull)
                }

        private fun parseModelsDevModels(provider: ModelsDevProvider): List<AiModelInfo> =
            provider.models.mapNotNull { (id, entry) ->
                if (!implemented(entry) || !modelsDevTextModel(entry)) return@mapNotNull null
                AiModelInfo(
                    id = id,
                    name = entry.name ?: id,
                    inputUsdPerMTok = entry.cost?.input,
                    outputUsdPerMTok = entry.cost?.output,
                    contextLength = entry.limit?.context,
                )
            }

        fun parseOpenRouterThroughput(body: String): Double? =
            Json.parseToJsonElement(body)
                .jsonObject["data"]
                ?.jsonObject
                ?.get("endpoints")
                .asObjects()
                .mapNotNull { endpoint ->
                    when (val value = endpoint["throughput_last_30m"]) {
                        is JsonObject -> value.number("p50")
                        is JsonPrimitive -> value.doubleOrNull
                        else -> null
                    }
                }.maxOrNull()

        private fun implemented(entry: ModelsDevModel): Boolean {
            val npm = entry.provider?.npm ?: return true
            return npm == "@ai-sdk/anthropic" || npm == "@ai-sdk/openai-compatible"
        }

        private fun JsonElement?.asObjects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

        private fun JsonObject.modalities(key: String): List<String> =
            ((this["architecture"] as? JsonObject)?.get("${key}_modalities") as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty()

        fun defaultModel(
            preset: AiProviderPreset,
            models: List<AiModelInfo>,
            freeOnly: Boolean = false,
        ): String? {
            val candidates = filterAndSort(models).filter { !freeOnly || (it.free && it.id.endsWith(":free")) }
            val preferred = if (freeOnly) AiProviderPresets.FREE_AI_MODEL else preset.defaultModel
            return candidates.firstOrNull { it.id == preferred }?.id ?: candidates.firstOrNull()?.id
        }

        fun filterAndSort(models: List<AiModelInfo>): List<AiModelInfo> =
            models
                .filter { model ->
                    val input = model.inputUsdPerMTok
                    val output = model.outputUsdPerMTok
                    !excludedId.containsMatchIn(model.id) &&
                        (input == null || (input.isFinite() && input in 0.0..1.0)) &&
                        (output == null || (output.isFinite() && output in 0.0..5.0))
                }.distinctBy { it.id }
                .sortedWith(
                    compareByDescending<AiModelInfo> { it.free }
                        .thenBy { (it.inputUsdPerMTok ?: Double.MAX_VALUE) + (it.outputUsdPerMTok ?: Double.MAX_VALUE) }
                        .thenBy { it.id },
                )

        private val excludedId =
            Regex(
                "^~|preview|experimental|deprecated|:batch|embed|realtime|audio|tts|whisper|transcri|image|safeguard|prompt-guard|moderation",
                RegexOption.IGNORE_CASE,
            )

        private fun activeModel(entry: JsonObject): Boolean = activeModel(entry.string("status"), entry.string("expiration_date"))

        private fun activeModel(
            status: String?,
            expiration: String?,
        ): Boolean {
            if (status in listOf("deprecated", "preview", "alpha", "beta")) return false
            if (expiration == null) return true
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            return expiration > today
        }

        private fun modelsDevTextModel(entry: ModelsDevModel): Boolean =
            activeModel(entry.status, entry.expirationDate) &&
                (entry.modalities?.input?.contains("text") != false) &&
                (entry.modalities?.output == null || entry.modalities.output == listOf("text"))

        private fun openAiTextModel(
            entry: JsonObject,
            requireMetadata: Boolean,
        ): Boolean {
            val input = entry.modalities("input")
            val output = entry.modalities("output")
            val endpoints = entry["endpoints"] as? JsonArray
            if (endpoints != null && endpoints.none { it.jsonPrimitive.contentOrNull?.endsWith("/chat/completions") == true }) return false
            if (input.isEmpty() && output.isEmpty()) return !requireMetadata
            return input.contains("text") && output == listOf("text")
        }
    }
}

@Serializable
private data class ModelsDevProvider(
    val models: Map<String, ModelsDevModel> = emptyMap(),
)

@Serializable
private data class ModelsDevModel(
    val name: String? = null,
    val cost: ModelsDevCost? = null,
    val limit: ModelsDevLimit? = null,
    val provider: ModelsDevImplementation? = null,
    val modalities: ModelsDevModalities? = null,
    val status: String? = null,
    @SerialName("expiration_date") val expirationDate: String? = null,
)

@Serializable
private data class ModelsDevCost(
    val input: Double? = null,
    val output: Double? = null,
)

@Serializable
private data class ModelsDevLimit(
    val context: Int? = null,
)

@Serializable
private data class ModelsDevImplementation(
    val npm: String? = null,
)

@Serializable
private data class ModelsDevModalities(
    val input: List<String>? = null,
    val output: List<String>? = null,
)
