package io.github.s1ddhants1.unhinge.ai

import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

const val ZenChatBaseUrl = "https://opencode.ai/inference/v1/chat/completions"
const val ZenMessagesBaseUrl = "https://opencode.ai/inference/v1/messages"
const val ZenResponsesDefaultBaseUrl = "https://opencode.ai/inference/v1/responses"
const val ZenGeminiBase = "https://opencode.ai/inference/v1/models"

object ZenRouter {
    const val OPENCODE_PROJECT_ID = "fbfbb8ecca77fbf4f585927700f1baa979cb6a94"
    const val OPENCODE_CLIENT = "cli"

    var currentVersion: String = Consts.DEFAULT_ZEN_OPENCODE_VERSION

    fun getUserAgent(): String = "opencode/latest/$currentVersion/cli"

    val OPENCODE_USER_AGENT: String
        get() = getUserAgent()

    val FreeModels: List<String> =
        listOf(
            "space-bunny-free",
            "cat-jam-free",
            "deepseek-v4-flash-free",
            "deepseek-v4.1-flash-free",
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
            "longcat-2.5-preview-free",
            "mimo-v2-flash-free",
            "mimo-v2-omni-free",
            "mimo-v2-pro-free",
            "mimo-v2.5-free",
            "mimo-v2.6-flash-free",
            "minimax-m2.1-free",
            "minimax-m2.5-free",
            "minimax-m3-free",
            "muse-spark-1.2-contributor-free",
            "muse-spark-1.2-free",
            "muse-spark-1.3-contributor-free",
            "muse-spark-1.3-free",
            "nemotron-3-super-free",
            "nemotron-3-ultra-free",
            "nemotron-3.5-lightning-free",
            "north-mini-code-free",
            "qwen3.6-plus-free",
            "ring-2.6-1t-free",
            "step-5-preview-free",
            "trinity-large-preview-free",
            "x-preview-f-free",
            "big-pickle",
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
            "deepseek-v4-flash",
            "deepseek-v4-pro",
            "glm-5.3-flash",
            "qwen3.8-flash",
            "kimi-k2.5",
            "minimax-m3",
        )

    val dynamicFreeModels: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val dynamicAllModels: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()
    val dynamicResponsesModels: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val dynamicMessagesModels: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val dynamicGeminiModels: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val allFreeModels: List<String>
        get() = (FreeModels + dynamicFreeModels).distinct()

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
            "gpt-6-astra", "gpt-6-sol", "gpt-6.1-sol", "gpt-6-luna",
            "gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna",
            "gpt-5.5", "gpt-5.5-pro", "gpt-5.4", "gpt-5.4-pro", "gpt-5.4-mini", "gpt-5.4-nano",
            "gpt-5.3-codex", "gpt-5.3-codex-spark",
            "gpt-5.2", "gpt-5.2-codex",
            "gpt-5.1", "gpt-5.1-codex", "gpt-5.1-codex-max", "gpt-5.1-codex-mini",
            "gpt-5", "gpt-5-codex", "gpt-5-nano",
            "grok-4.7", "grok-4.6", "grok-4.5", "grok-build-0.1",
            "muse-spark-1.3", "muse-spark-1.2",
            "muse-spark-1.3-contributor-free", "muse-spark-1.3-free",
            "muse-spark-1.2-contributor-free", "muse-spark-1.2-free",
        )

    private val MessagesModels =
        setOf(
            "claude-3-5-haiku",
            "claude-fable-5-1", "claude-fable-5",
            "claude-opus-5-5", "claude-opus-5", "claude-opus-4-8", "claude-opus-4-7",
            "claude-opus-4-6", "claude-opus-4-5", "claude-opus-4-1",
            "claude-sonnet-5-5", "claude-sonnet-5", "claude-sonnet-4-6", "claude-sonnet-4-5",
            "claude-sonnet-4", "claude-haiku-5-5", "claude-haiku-4-5",
            "qwen3.8-flash", "qwen3.6-plus", "qwen3.6-plus-free", "qwen3.5-plus",
            "minimax-m3-free", "minimax-m2.5-free", "minimax-m2.1-free",
        )

    private val GeminiModels =
        setOf(
            "gemini-4-argon",
            "gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash",
            "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.1-pro", "gemini-3-pro", "gemini-3-flash",
        )

    fun normalizeModelId(model: String): String {
        val sanitized = sanitizeModelId(model)
        val id = sanitized.lowercase()
        return when (id) {
            "muse-spark-1.3-free" -> "muse-spark-1.3-contributor-free"
            "muse-spark-1.2-free" -> "muse-spark-1.2-contributor-free"
            else -> sanitized
        }
    }

    fun isFreeModel(model: String): Boolean {
        val id = normalizeModelId(model).lowercase()
        return id.isBlank() ||
            id in FreeModels ||
            id in dynamicFreeModels ||
            id.contains("free") ||
            id == "big-pickle" ||
            id == "grok-code"
    }

    fun isResponsesModel(id: String): Boolean =
        id in ResponsesModels ||
            id in dynamicResponsesModels ||
            id.startsWith("gpt-") ||
            id.startsWith("muse-spark-") ||
            id.startsWith("grok-")

    fun isMessagesModel(id: String): Boolean =
        id in MessagesModels ||
            id in dynamicMessagesModels ||
            id.startsWith("claude-") ||
            id.startsWith("qwen-") ||
            id.startsWith("jev-") ||
            id.startsWith("minimax-")

    fun isGeminiModel(id: String): Boolean =
        id in GeminiModels ||
            id in dynamicGeminiModels ||
            id.startsWith("gemini-")

    fun normalizeBaseUrl(url: String): String {
        if (url.isBlank()) return url
        return if (url.contains("opencode.ai/zen/v1", ignoreCase = true)) {
            url.replace("opencode.ai/zen/v1", "opencode.ai/inference/v1", ignoreCase = true)
        } else {
            url
        }
    }

    fun isZenUrl(url: String): Boolean =
        url.contains("opencode.ai", ignoreCase = true) ||
            url.contains("/zen/v1", ignoreCase = true) ||
            url.contains("/inference/v1", ignoreCase = true)

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

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
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
            val id = m.lowercase()
            if (id.contains("free") || id == "big-pickle" || id == "grok-code") {
                dynamicFreeModels.add(m)
            }
            when {
                id.startsWith("claude-") || id.startsWith("qwen-") || id.startsWith("jev-") || id.startsWith("minimax-") -> {
                    dynamicMessagesModels.add(m)
                }
                id.startsWith("gemini-") -> {
                    dynamicGeminiModels.add(m)
                }
                id.startsWith("gpt-") || id.startsWith("muse-spark-") || id.startsWith("grok-") -> {
                    dynamicResponsesModels.add(m)
                }
            }
        }
    }

    fun initFromPreferences(prefs: PreferencesManager?) {
        if (prefs == null) return
        cachedPrefs = prefs
        val savedVer = prefs.zenOpencodeVersion
        if (savedVer.isNotBlank()) {
            currentVersion = savedVer
        }
        val cachedAll = prefs.zenCachedAllModels
        if (cachedAll.isNotBlank()) {
            val list = cachedAll.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (list.isNotEmpty()) {
                updateModelRoster(list)
            }
        }
        val cachedFree = prefs.zenCachedFreeModels
        if (cachedFree.isNotBlank()) {
            val freeList = cachedFree.split(",").map { it.trim() }.filter { it.isNotBlank() }
            for (f in freeList) {
                if (!dynamicFreeModels.contains(f)) {
                    dynamicFreeModels.add(f)
                }
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

    suspend fun fetchLatestVersion(client: OkHttpClient = httpClient): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(Consts.ZEN_NPM_REGISTRY_URL)
                .header("User-Agent", "Unhinge-Android")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body.string()
                    val json = zenJson.parseToJsonElement(body).jsonObject
                    val ver = json["version"]?.jsonPrimitive?.contentOrNull
                    if (!ver.isNullOrBlank() && ver.matches(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+.*$"))) {
                        return@withContext ver
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val req = Request.Builder()
                .url(Consts.ZEN_BREW_FORMULA_URL)
                .header("User-Agent", "Unhinge-Android")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body.string()
                    val json = zenJson.parseToJsonElement(body).jsonObject
                    val ver = json["versions"]?.jsonObject?.get("stable")?.jsonPrimitive?.contentOrNull
                    if (!ver.isNullOrBlank() && ver.matches(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+.*$"))) {
                        return@withContext ver
                    }
                }
            }
        } catch (_: Exception) {}

        null
    }

    suspend fun fetchLatestModels(client: OkHttpClient = httpClient): List<String> = withContext(Dispatchers.IO) {
        val models = mutableListOf<String>()
        try {
            val req = Request.Builder()
                .url(Consts.ZEN_MODELS_URL)
                .header("User-Agent", getUserAgent())
                .header("Authorization", "Bearer public")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body.string()
                    val root = zenJson.parseToJsonElement(body).jsonObject
                    val data = root["data"]?.jsonArray ?: return@use
                    for (elem in data) {
                        val id = elem.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: continue
                        if (id.isNotBlank() && id != "test") {
                            models.add(id)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
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
        client: OkHttpClient = httpClient,
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
                if (!newVer.isNullOrBlank()) {
                    currentVersion = newVer
                }
            } catch (_: Exception) {}

            try {
                modelsFetched = fetchLatestModels(client)
                if (modelsFetched.isNotEmpty()) {
                    updateModelRoster(modelsFetched)
                }
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
