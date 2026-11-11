package io.github.s1ddhants1.unhinge.ai

import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

const val ZenChatBaseUrl = "https://opencode.ai/inference/openai/v1/chat/completions"
const val ZenMessagesBaseUrl = "https://opencode.ai/inference/anthropic/v1/messages"
const val ZenResponsesDefaultBaseUrl = "https://opencode.ai/inference/openai/v1/responses"
const val ZenGeminiBase = "https://opencode.ai/inference/google/v1beta/models"

object ZenRouter {
    const val OPENCODE_PROJECT_ID = "fbfbb8ecca77fbf4f585927700f1baa979cb6a94"
    const val OPENCODE_CLIENT = "cli"
    private const val ZEN_ENV_BLOCK =
        "Here is some useful information about the environment you are running in:\n<env>\n  Working directory: /app\n</env>"

    var currentVersion: String = Consts.DEFAULT_ZEN_OPENCODE_VERSION

    fun getUserAgent(): String = "opencode/latest/$currentVersion/cli"

    val OPENCODE_USER_AGENT: String
        get() = getUserAgent()

    val FreeModels: List<String> =
        listOf(
            "deepseek-v4.1-flash-free",
            "big-pickle",
            "beam-free",
            "flint-40b-free",
            "glm-5.3-flash-free",
            "iceman-free",
            "inkling-2-small-free",
            "longcat-2.5-free",
            "mimo-v2.6-flash-free",
            "minimax-m3.1-flash-free",
            "nemotron-3.5-lightning-free",
            "nemotron-3.5-super-vl-free",
        )

    val DefaultPaidModels: List<String> =
        listOf(
            "claude-sonnet-5",
            "claude-haiku-4-5",
            "claude-opus-5",
            "gpt-5.6-sol",
            "gpt-5.5",
            "gemini-3.8-flash",
            "gemini-3.7-flash",
            "deepseek-v4.1-flash",
            "deepseek-v4-pro",
            "glm-5.3-flash",
            "qwen3.8-flash",
            "kimi-k2.5",
            "minimax-m3",
        )

    private val DeprecatedFreeModels =
        setOf(
            "space-bunny-free",
            "cat-jam-free",
            "deepseek-v4-flash-free",
            "exo-free",
            "fledge-alpha-free",
            "glm-4.7-free",
            "glm-5-free",
            "grok-code",
            "hy3-free",
            "hy3-preview-free",
            "kimi-k2.5-free",
            "laguna-s-2.1-free",
            "ling-2.6-flash-free",
            "ling-3.0-flash-fin-free",
            "ling-3.0-flash-free",
            "ling-3.0-tiny-free",
            "ling-3.1-flash-free",
            "longcat-2.0-free",
            "mimo-v2-flash-free",
            "mimo-v2-omni-free",
            "mimo-v2-pro-free",
            "mimo-v2.5-free",
            "minimax-m2.1-free",
            "minimax-m2.5-free",
            "minimax-m3-free",
            "muse-spark-1.2-contributor-free",
            "muse-spark-1.2-free",
            "muse-spark-1.3-contributor-free",
            "muse-spark-1.3-free",
            "nemotron-3-super-free",
            "nemotron-3-ultra-free",
            "north-mini-code-free",
            "qwen3.6-plus-free",
            "ring-2.6-1t-free",
            "step-5-preview-free",
            "trinity-large-preview-free",
            "x-preview-f-free",
        )

    val dynamicFreeModels: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val dynamicAllModels: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()
    val dynamicResponsesModels: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val dynamicMessagesModels: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val dynamicGeminiModels: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val allFreeModels: List<String>
        get() =
            if (dynamicAllModels.isNotEmpty() && dynamicFreeModels.isNotEmpty()) {
                dynamicAllModels.filter { isFreeModel(it) }.ifEmpty { dynamicFreeModels.toList() }.distinct()
            } else {
                FreeModels
            }

    val allAvailableModels: List<String>
        get() {
            if (dynamicAllModels.isEmpty()) {
                return (FreeModels + DefaultPaidModels).distinct()
            }
            val free = dynamicAllModels.filter { isFreeModel(it) }
            val paid = dynamicAllModels.filter { !isFreeModel(it) }
            return (free + paid).distinct()
        }

    private val ResponsesModels =
        setOf(
            "gpt-6.1-sol", "gpt-6-astra", "gpt-6-luna", "gpt-6-sol",
            "gpt-5.6-luna", "gpt-5.6-sol", "gpt-5.6-terra",
            "gpt-5.5", "gpt-5.5-pro", "gpt-5.4", "gpt-5.4-mini", "gpt-5.4-nano", "gpt-5.4-pro",
            "gpt-5.3-codex", "gpt-5.3-codex-spark",
            "gpt-5.2", "gpt-5.2-codex",
            "gpt-5.1", "gpt-5.1-codex", "gpt-5.1-codex-max", "gpt-5.1-codex-mini",
            "gpt-5", "gpt-5-codex", "gpt-5-nano",
            "grok-4.7", "grok-4.6", "grok-4.5", "grok-build-0.1",
            "muse-ember-1.0", "muse-spark-1.2", "muse-spark-1.3", "muse-wonder-1.0",
        )

    private val MessagesModels =
        setOf(
            "claude-fable-5-2", "claude-fable-5-1", "claude-fable-5",
            "claude-opus-5-5", "claude-opus-5", "claude-opus-4-8", "claude-opus-4-7",
            "claude-opus-4-6", "claude-opus-4-5",
            "claude-sonnet-5-5", "claude-sonnet-5", "claude-sonnet-4-6", "claude-sonnet-4-5",
            "claude-haiku-5-5", "claude-haiku-4-5",
            "qwen3.8-flash", "qwen3.6-plus", "qwen3.5-plus",
        )

    private val GeminiModels =
        setOf(
            "gemini-4-carbon", "gemini-4-argon",
            "gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash",
            "gemini-3.5-flash", "gemini-3.5-flash-lite",
        )

    private fun hasDynamicModel(id: String): Boolean =
        dynamicAllModels.any { it.equals(id, ignoreCase = true) }

    fun normalizeModelId(model: String): String {
        val sanitized = sanitizeModelId(model).removePrefix("opencode/")
        val id = sanitized.lowercase(Locale.US)
        if (id.isNotBlank() && hasDynamicModel(id)) {
            return sanitized
        }
        return when {
            id == "longcat-2.5-preview-free" -> "longcat-2.5-free"
            id in DeprecatedFreeModels -> Consts.ZEN_DEFAULT_MODEL
            else -> sanitized
        }
    }

    fun isFreeModel(model: String): Boolean {
        val id = normalizeModelId(model).lowercase(Locale.US)
        if (id.isBlank()) return true
        if (dynamicAllModels.isNotEmpty()) {
            if (id in dynamicFreeModels) return true
            if (hasDynamicModel(id)) return false
        }
        return id in FreeModels ||
            id in dynamicFreeModels ||
            id.endsWith("-free") ||
            id == "big-pickle"
    }

    fun isResponsesModel(id: String): Boolean {
        val lower = id.lowercase(Locale.US)
        if (lower in dynamicResponsesModels) return true
        if (hasDynamicModel(lower)) return false
        return lower in ResponsesModels ||
            lower.startsWith("gpt-") ||
            lower.startsWith("grok-") ||
            lower.startsWith("muse-")
    }

    fun isMessagesModel(id: String): Boolean {
        val lower = id.lowercase(Locale.US)
        if (lower in dynamicMessagesModels) return true
        if (hasDynamicModel(lower)) return false
        return lower in MessagesModels ||
            lower.startsWith("claude-") ||
            lower == "qwen3.5-plus" ||
            lower == "qwen3.6-plus" ||
            lower == "qwen3.8-flash"
    }

    fun isGeminiModel(id: String): Boolean {
        val lower = id.lowercase(Locale.US)
        if (lower in dynamicGeminiModels) return true
        if (hasDynamicModel(lower)) return false
        return lower in GeminiModels || lower.startsWith("gemini-")
    }

    fun normalizeBaseUrl(url: String): String {
        if (url.isBlank()) return url
        val normalized = url.replace("opencode.ai/zen/v1", "opencode.ai/inference/v1", ignoreCase = true)
        return when {
            normalized.contains("opencode.ai/inference/v1/chat/completions", ignoreCase = true) ->
                normalized.replace("opencode.ai/inference/v1/chat/completions", "opencode.ai/inference/openai/v1/chat/completions", ignoreCase = true)
            normalized.contains("opencode.ai/inference/v1/responses", ignoreCase = true) ->
                normalized.replace("opencode.ai/inference/v1/responses", "opencode.ai/inference/openai/v1/responses", ignoreCase = true)
            normalized.contains("opencode.ai/inference/v1/messages", ignoreCase = true) ->
                normalized.replace("opencode.ai/inference/v1/messages", "opencode.ai/inference/anthropic/v1/messages", ignoreCase = true)
            normalized.contains("opencode.ai/inference/v1/models", ignoreCase = true) ->
                normalized.replace("opencode.ai/inference/v1/models", "opencode.ai/inference/google/v1beta/models", ignoreCase = true)
            else -> normalized
        }
    }

    fun isZenUrl(url: String): Boolean =
        url.contains("opencode.ai", ignoreCase = true) ||
            url.contains("/zen/v1", ignoreCase = true) ||
            url.contains("/inference/", ignoreCase = true)

    fun withZenEnvironment(systemPrompt: String): String {
        if (systemPrompt.contains("<env>") && systemPrompt.contains("Working directory:")) {
            return systemPrompt
        }
        return if (systemPrompt.isBlank()) {
            ZEN_ENV_BLOCK
        } else {
            "$systemPrompt\n\n$ZEN_ENV_BLOCK"
        }
    }

    fun resolve(model: String): Triple<LlmProtocol, String, String> {
        val normalized = normalizeModelId(model)
        val id = normalized.lowercase()
        return when {
            id.isBlank() -> Triple(LlmProtocol.OpenAiChatCompletions, Consts.ZEN_DEFAULT_BASE_URL, Consts.ZEN_DEFAULT_MODEL)
            isResponsesModel(id) -> Triple(LlmProtocol.OpenAiResponses, ZenResponsesDefaultBaseUrl, normalized)
            isMessagesModel(id) -> Triple(LlmProtocol.AnthropicMessages, ZenMessagesBaseUrl, normalized)
            isGeminiModel(id) -> Triple(LlmProtocol.GoogleGemini, "$ZenGeminiBase/$normalized", normalized)
            else -> Triple(LlmProtocol.OpenAiChatCompletions, ZenChatBaseUrl, normalized)
        }
    }

    fun generateSessionId(): String {
        val now = System.currentTimeMillis()
        val p = 1L
        val n = (now shl 12) + p
        val a = n.inv()
        val sb = StringBuilder("ses_")
        for (m in 0 until 6) {
            val shift = 40 - 8 * m
            val byteVal = ((a shr shift) and 0xffL).toInt()
            sb.append(String.format(Locale.US, "%02x", byteVal))
        }
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        val random = SecureRandom()
        val randomBytes = ByteArray(14)
        random.nextBytes(randomBytes)
        for (b in randomBytes) {
            val unsigned = b.toInt() and 0xff
            sb.append(alphabet[unsigned % 62])
        }
        return sb.toString()
    }

    fun generateTraceHeaders(): Pair<String, String> {
        val random = SecureRandom()
        val traceBytes = ByteArray(16)
        val spanBytes = ByteArray(8)
        val parentSpanBytes = ByteArray(8)
        random.nextBytes(traceBytes)
        random.nextBytes(spanBytes)
        random.nextBytes(parentSpanBytes)
        val traceHex = traceBytes.joinToString("") { String.format(Locale.US, "%02x", it) }
        val spanHex = spanBytes.joinToString("") { String.format(Locale.US, "%02x", it) }
        val parentHex = parentSpanBytes.joinToString("") { String.format(Locale.US, "%02x", it) }
        return "00-$traceHex-$spanHex-01" to "$traceHex-$spanHex-1-$parentHex"
    }

    fun generateTraceparent(): String = generateTraceHeaders().first

    fun injectZenHeaders(
        builder: Request.Builder,
        apiKey: String,
        model: String = "",
        sessionId: String = generateSessionId(),
    ) {
        val effectiveKey = if (isFreeModel(model)) {
            if (model.isBlank() && apiKey.isNotBlank()) apiKey.trim() else "public"
        } else {
            apiKey.trim().ifBlank { "public" }
        }
        val (traceparent, b3) = generateTraceHeaders()
        builder.header("Authorization", "Bearer $effectiveKey")
        builder.header("User-Agent", getUserAgent())
        builder.header("x-opencode-client", OPENCODE_CLIENT)
        builder.header("x-opencode-project", OPENCODE_PROJECT_ID)
        builder.header("x-opencode-session", sessionId)
        builder.header("x-opencode-session-id", sessionId)
        builder.header("x-session-affinity", sessionId)
        builder.header("x-session-id", sessionId)
        builder.header("X-Session-Id", sessionId)
        builder.header("traceparent", traceparent)
        builder.header("b3", b3)
    }

    private val syncMutex = Mutex()
    private var cachedPrefs: PreferencesManager? = null

    fun updateModelRoster(models: List<String>) {
        if (models.isEmpty()) return
        val unique = models.distinct()
        dynamicAllModels.clear()
        dynamicAllModels.addAll(unique)

        dynamicFreeModels.clear()
        dynamicResponsesModels.clear()
        dynamicMessagesModels.clear()
        dynamicGeminiModels.clear()

        for (m in unique) {
            val id = m.lowercase(Locale.US)
            if (id.endsWith("-free") || id == "big-pickle") {
                dynamicFreeModels.add(m)
            }
            when {
                id.startsWith("claude-") || id == "qwen3.5-plus" || id == "qwen3.6-plus" || id == "qwen3.8-flash" -> {
                    dynamicMessagesModels.add(m)
                }
                id.startsWith("gemini-") -> {
                    dynamicGeminiModels.add(m)
                }
                id.startsWith("gpt-") || id.startsWith("muse-") || id.startsWith("grok-") -> {
                    dynamicResponsesModels.add(m)
                }
            }
        }
    }

    private fun isVersionOlder(candidate: String, baseline: String): Boolean {
        val candParts = candidate.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        val baseParts = baseline.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(candParts.size, baseParts.size)
        for (i in 0 until maxLen) {
            val c = candParts.getOrElse(i) { 0 }
            val b = baseParts.getOrElse(i) { 0 }
            if (c != b) return c < b
        }
        return false
    }

    fun initFromPreferences(prefs: PreferencesManager?) {
        if (prefs == null) return
        cachedPrefs = prefs
        val savedVer = prefs.zenOpencodeVersion
        val needsCacheReset = savedVer.isBlank() || isVersionOlder(savedVer, Consts.DEFAULT_ZEN_OPENCODE_VERSION)
        if (needsCacheReset) {
            currentVersion = Consts.DEFAULT_ZEN_OPENCODE_VERSION
            prefs.zenOpencodeVersion = Consts.DEFAULT_ZEN_OPENCODE_VERSION
            prefs.zenLastSyncTimestamp = 0L
        } else {
            currentVersion = savedVer
        }

        val currentBase = prefs.openRouterBaseUrl
        if (isZenUrl(currentBase)) {
            val normalizedBase = normalizeBaseUrl(currentBase)
            if (normalizedBase != currentBase) {
                prefs.openRouterBaseUrl = normalizedBase
            }
        }
        if (prefs.aiProvider.equals("Zen", ignoreCase = true) && prefs.openRouterModel.isNotBlank()) {
            val migratedModel = normalizeModelId(prefs.openRouterModel)
            if (migratedModel != prefs.openRouterModel) {
                prefs.openRouterModel = migratedModel
            }
        }

        val cachedAll = prefs.zenCachedAllModels
        if (!needsCacheReset && cachedAll.isNotBlank()) {
            val list = cachedAll.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (list.isNotEmpty() && "space-bunny-free" !in list) {
                updateModelRoster(list)
                val cachedFree = prefs.zenCachedFreeModels
                if (cachedFree.isNotBlank()) {
                    val freeList = cachedFree.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    dynamicFreeModels.clear()
                    dynamicFreeModels.addAll(freeList)
                }
            } else if ("space-bunny-free" in list) {
                prefs.zenLastSyncTimestamp = 0L
            }
        }
    }

    fun saveToPreferences(prefs: PreferencesManager? = null) {
        val target = prefs ?: cachedPrefs ?: return
        target.zenOpencodeVersion = currentVersion
        target.zenCachedFreeModels = dynamicFreeModels.joinToString(",")
        target.zenCachedAllModels = dynamicAllModels.joinToString(",")
        target.zenLastSyncTimestamp = System.currentTimeMillis()
    }

    fun onGatewayResponse(responseCode: Int, url: String) {
        if (isZenUrl(url) && (responseCode == 401 || responseCode == 403 || responseCode == 404)) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    syncCatalogAndVersion(force = true)
                } catch (_: Exception) {}
            }
        }
    }

    private fun parseVersionIfValid(body: String, path: String? = null): String? {
        val json = zenJson.parseToJsonElement(body).jsonObject
        val raw = if (path == "brew") {
            json["versions"]?.jsonObject?.get("stable")?.jsonPrimitive?.contentOrNull
        } else {
            json["version"]?.jsonPrimitive?.contentOrNull
        }
        return raw?.takeIf { it.isNotBlank() && it.matches(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+.*$")) }
    }

    suspend fun fetchLatestVersion(client: OkHttpClient? = null): String? = withContext(Dispatchers.IO) {
        val candidates = listOf(
            Triple("${Consts.ZEN_UPDATE_API_URL}?current=$currentVersion", getUserAgent(), null),
            Triple(Consts.ZEN_NPM_REGISTRY_URL, "Unhinge-Android", null),
            Triple(Consts.ZEN_BREW_FORMULA_URL, "Unhinge-Android", "brew"),
        )
        for ((url, ua, format) in candidates) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", ua)
                    .build()
                val http = client ?: AiHttpClient.clientFor(url)
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val ver = parseVersionIfValid(resp.body.string(), format)
                        if (ver != null) return@withContext ver
                    }
                }
            } catch (_: Exception) {}
        }
        null
    }

    private fun parseReasoningSupport(reasoningObj: JsonObject): ModelReasoningCatalog.ReasoningSupport? {
        if (reasoningObj["supported"]?.jsonPrimitive?.booleanOrNull == false) {
            return ModelReasoningCatalog.ReasoningSupport.Unsupported
        }
        val effortLevels = reasoningObj["effort"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.filter { it.isNotBlank() }
        if (!effortLevels.isNullOrEmpty()) {
            return ModelReasoningCatalog.ReasoningSupport.Effort(effortLevels)
        }
        if (reasoningObj["toggle"]?.jsonPrimitive?.booleanOrNull == true) {
            return ModelReasoningCatalog.ReasoningSupport.Toggle
        }
        val budgetObj = reasoningObj["budget"] as? JsonObject
        if (budgetObj != null) {
            val min = budgetObj["min"]?.jsonPrimitive?.intOrNull ?: 1024
            val max = budgetObj["max"]?.jsonPrimitive?.intOrNull ?: 81920
            return ModelReasoningCatalog.ReasoningSupport.BudgetTokens(minTokens = min, maxTokens = max)
        }
        if (reasoningObj["supported"]?.jsonPrimitive?.booleanOrNull == true) {
            return ModelReasoningCatalog.ReasoningSupport.Fixed
        }
        return null
    }

    suspend fun fetchLatestModels(client: OkHttpClient? = null): List<String> = withContext(Dispatchers.IO) {
        val models = mutableListOf<String>()
        val freeSet = mutableSetOf<String>()
        val responsesSet = mutableSetOf<String>()
        val messagesSet = mutableSetOf<String>()
        val geminiSet = mutableSetOf<String>()
        val reasoningMap = mutableMapOf<String, ModelReasoningCatalog.ReasoningSupport>()
        try {
            val req = Request.Builder()
                .url(Consts.ZEN_MODELS_URL)
                .header("User-Agent", getUserAgent())
                .header("Authorization", "Bearer public")
                .build()
            val http = client ?: AiHttpClient.clientFor(Consts.ZEN_MODELS_URL)
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body.string()
                    val root = zenJson.parseToJsonElement(body).jsonObject
                    val data = root["data"]?.jsonArray ?: return@use
                    for (elem in data) {
                        val obj = elem.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "test" } ?: continue
                        models.add(id)
                        val lower = id.lowercase(Locale.US)
                        val inputPrice = obj["cost"]?.jsonObject
                            ?.get("text")?.jsonObject
                            ?.get("input")?.jsonObject
                            ?.get("price")?.jsonPrimitive?.doubleOrNull
                        val isFree = if (inputPrice != null) inputPrice == 0.0 else (lower.endsWith("-free") || lower == "big-pickle")
                        if (isFree) freeSet.add(lower)

                        val apiObj = obj["api"] as? JsonObject
                        if (apiObj != null) {
                            when {
                                "responses" in apiObj -> responsesSet.add(lower)
                                "messages" in apiObj -> messagesSet.add(lower)
                                "generate-content" in apiObj -> geminiSet.add(lower)
                            }
                        }

                        val reasoningObj = obj["capabilities"]?.jsonObject?.get("reasoning") as? JsonObject
                        if (reasoningObj != null) {
                            parseReasoningSupport(reasoningObj)?.let { reasoningMap[lower] = it }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (models.isNotEmpty()) {
            val unique = models.distinct()
            dynamicAllModels.clear()
            dynamicAllModels.addAll(unique)
            dynamicFreeModels.clear()
            dynamicFreeModels.addAll(freeSet)
            dynamicResponsesModels.clear()
            dynamicResponsesModels.addAll(responsesSet)
            dynamicMessagesModels.clear()
            dynamicMessagesModels.addAll(messagesSet)
            dynamicGeminiModels.clear()
            dynamicGeminiModels.addAll(geminiSet)
            ModelReasoningCatalog.updateDynamicCapabilities(reasoningMap)
        }
        models
    }

    data class SyncResult(
        val success: Boolean,
        val version: String,
        val freeCount: Int,
        val totalCount: Int,
        val errorMessage: String? = null,
    )

    suspend fun syncCatalogAndVersion(
        prefs: PreferencesManager? = null,
        force: Boolean = false,
        client: OkHttpClient? = null,
    ): SyncResult = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val effectivePrefs = prefs ?: cachedPrefs
            val now = System.currentTimeMillis()
            val lastSync = effectivePrefs?.zenLastSyncTimestamp ?: 0L
            if (!force && now - lastSync < 6 * 3600 * 1000L && dynamicAllModels.isNotEmpty()) {
                return@withContext SyncResult(
                    success = true,
                    version = currentVersion,
                    freeCount = allFreeModels.size,
                    totalCount = allAvailableModels.size,
                )
            }

            var newVer: String? = null
            var modelsFetched: List<String> = emptyList()

            try {
                newVer = fetchLatestVersion(client)
                if (!newVer.isNullOrBlank() && !isVersionOlder(newVer, Consts.DEFAULT_ZEN_OPENCODE_VERSION)) {
                    currentVersion = newVer
                }
            } catch (_: Exception) {}

            try {
                modelsFetched = fetchLatestModels(client)
            } catch (_: Exception) {}

            val success = newVer != null || modelsFetched.isNotEmpty()
            if (success) {
                saveToPreferences(effectivePrefs)
            }

            SyncResult(
                success = success,
                version = currentVersion,
                freeCount = allFreeModels.size,
                totalCount = allAvailableModels.size,
                errorMessage = if (!success) "Could not connect to OpenCode update gateway" else null,
            )
        }
    }

    private val zenJson = AiHttpClient.json

    val DefaultZenChatTools: JsonArray
        get() = ZenToolDefinitions.DefaultZenChatTools

    val DefaultZenResponsesTools: JsonArray
        get() = ZenToolDefinitions.DefaultZenResponsesTools
}
