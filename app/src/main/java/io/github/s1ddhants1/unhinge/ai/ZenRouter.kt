package io.github.s1ddhants1.unhinge.ai

import io.github.s1ddhants1.unhinge.Consts
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import java.security.SecureRandom
import java.util.Locale

const val ZenChatBaseUrl = "https://opencode.ai/zen/v1/chat/completions"
const val ZenMessagesBaseUrl = "https://opencode.ai/zen/v1/messages"
const val ZenResponsesDefaultBaseUrl = "https://opencode.ai/zen/v1/responses"
const val ZenGeminiBase = "https://opencode.ai/zen/v1/models"

/**
 * Zen model → wire protocol + endpoint routing and OpenCode client emulation.
 *
 * Canonical endpoints per https://opencode.ai/v2/docs/console/models:
 * - `/v1/responses` (OpenAI Responses): GPT, Grok, Muse Spark
 * - `/v1/chat/completions` (OpenAI chat): Qwen Max, DeepSeek, GLM, Kimi, MiniMax, free chat
 * - `/v1/messages` (Anthropic Messages): Claude, Qwen Flash/Plus
 * - `/v1/models/<id>` (Google native): Gemini
 *
 * OpenCode Free Tier Emulation:
 * - Session ID encoding: `ses_` + 12 hex chars (timestamp-encoded with bitwise NOT `~n`) + 14 base62 random chars.
 * - Authorization: `Bearer public` when no user API key is provided.
 * - Trace header: standard W3C `traceparent`.
 */
object ZenRouter {
    const val OPENCODE_PROJECT_ID = "fbfbb8ecca77fbf4f585927700f1baa979cb6a94"
    const val OPENCODE_USER_AGENT = "opencode/latest/2.0.12/cli"
    const val OPENCODE_CLIENT = "cli"

    val FreeModels: List<String> =
        listOf(
            "big-pickle",
            "space-bunny-free",
            "muse-spark-1.3-contributor-free",
            "muse-spark-1.2-contributor-free",
            "deepseek-v4-flash-free",
            "glm-5-free",
            "glm-4.7-free",
            "qwen3.6-plus-free",
            "minimax-m3-free",
            "minimax-m2.5-free",
            "ling-3.0-flash-free",
            "mimo-v2.6-flash-free",
            "nemotron-3-super-free",
            "grok-code",
        )

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
            "muse-spark-1.3-contributor-free", "muse-spark-1.2-contributor-free",
        )

    private val MessagesModels =
        setOf(
            "claude-fable-5-1", "claude-fable-5",
            "claude-opus-5-5", "claude-opus-5", "claude-opus-4-8", "claude-opus-4-7",
            "claude-opus-4-6", "claude-opus-4-5",
            "claude-sonnet-5-5", "claude-sonnet-5", "claude-sonnet-4-6", "claude-sonnet-4-5",
            "claude-sonnet-4", "claude-haiku-4-5",
            "qwen3.8-flash", "qwen3.7-max", "qwen3.7-plus", "qwen3.6-plus", "qwen3.5-plus", "qwen3.6-plus-free",
            "minimax-m3-free", "minimax-m2.5-free",
        )

    private val GeminiModels =
        setOf(
            "gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash",
            "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.1-pro", "gemini-3-flash",
        )

    fun isFreeModel(model: String): Boolean {
        val id = sanitizeModelId(model).lowercase()
        return id.isBlank() ||
            id.contains("free") ||
            id == "big-pickle" ||
            id == "grok-code" ||
            FreeModels.contains(id)
    }

    fun isZenUrl(url: String): Boolean = url.contains("opencode.ai", ignoreCase = true)

    fun resolve(model: String): Triple<LlmProtocol, String, String> {
        val id = sanitizeModelId(model).lowercase()
        return when {
            id.isBlank() -> Triple(LlmProtocol.OpenAiResponses, Consts.ZEN_DEFAULT_BASE_URL, Consts.ZEN_DEFAULT_MODEL)
            ResponsesModels.contains(id) -> Triple(LlmProtocol.OpenAiResponses, ZenResponsesDefaultBaseUrl, id)
            MessagesModels.contains(id) -> Triple(LlmProtocol.AnthropicMessages, ZenMessagesBaseUrl, id)
            GeminiModels.contains(id) -> Triple(LlmProtocol.GoogleGemini, "$ZenGeminiBase/$id", id)
            else -> Triple(LlmProtocol.OpenAiChatCompletions, ZenChatBaseUrl, id)
        }
    }

    /**
     * Generates a timestamp-encoded session ID strictly matching OpenCode's internal XC(true) algorithm.
     * Encodes `~((now * 4096) + p)` into 6 hex bytes (12 chars), followed by 14 base62 random chars.
     */
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

    fun generateTraceparent(): String {
        val random = SecureRandom()
        val traceBytes = ByteArray(16)
        val spanBytes = ByteArray(8)
        random.nextBytes(traceBytes)
        random.nextBytes(spanBytes)
        val traceHex = traceBytes.joinToString("") { String.format(Locale.US, "%02x", it) }
        val spanHex = spanBytes.joinToString("") { String.format(Locale.US, "%02x", it) }
        return "00-$traceHex-$spanHex-01"
    }

    fun injectZenHeaders(
        builder: Request.Builder,
        apiKey: String,
        sessionId: String = generateSessionId(),
    ) {
        val effectiveKey = apiKey.trim().ifBlank { "public" }
        builder.header("Authorization", "Bearer $effectiveKey")
        builder.header("User-Agent", OPENCODE_USER_AGENT)
        builder.header("x-opencode-client", OPENCODE_CLIENT)
        builder.header("x-opencode-project", OPENCODE_PROJECT_ID)
        builder.header("x-opencode-session", sessionId)
        builder.header("x-session-affinity", sessionId)
        builder.header("x-session-id", sessionId)
        builder.header("traceparent", generateTraceparent())
    }

    /**
     * Minimal tool definition for models like `big-pickle` that enforce tool capability contracts
     * in their free tier gateway validator.
     */
    val DefaultZenChatTools: JsonArray =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "function")
                    put(
                        "function",
                        buildJsonObject {
                            put("name", "submit_reply")
                            put("description", "Deliver generated response to user")
                            put(
                                "parameters",
                                buildJsonObject {
                                    put("type", "object")
                                    put(
                                        "properties",
                                        buildJsonObject {
                                            put(
                                                "reply",
                                                buildJsonObject {
                                                    put("type", "string")
                                                    put("description", "Generated reply")
                                                },
                                            )
                                        },
                                    )
                                    put("required", buildJsonArray { add("reply") })
                                },
                            )
                        },
                    )
                },
            )
        }
}
