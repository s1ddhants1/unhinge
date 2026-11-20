package io.github.s1ddhants1.unhinge.ai

import okhttp3.Request
import java.security.SecureRandom
import java.util.Locale

object OpenCodeEmulation {
    const val OPENCODE_PROJECT_ID = "fbfbb8ecca77fbf4f585927700f1baa979cb6a94"
    const val OPENCODE_CLIENT = "cli"
    const val OPENCODE_VERSION = "2.3.2"
    val USER_AGENT: String = "opencode/latest/$OPENCODE_VERSION/cli"

    private const val ZEN_ENV_BLOCK =
        "Here is some useful information about the environment you are running in:\n<env>\n  Working directory: /app\n</env>"

    fun isOpenCode(provider: String, url: String = ""): Boolean {
        val norm = AiProviderPresets.normalizeProvider(provider)
        return norm.equals("OpenCodeZen", ignoreCase = true) ||
            norm.equals("OpenCodeGo", ignoreCase = true) ||
            url.contains("opencode.ai", ignoreCase = true)
    }

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

    fun injectHeaders(
        builder: Request.Builder,
        apiKey: String,
        sessionId: String = generateSessionId(),
    ) {
        val effectiveKey = apiKey.trim().ifBlank { "public" }
        val (traceparent, b3) = generateTraceHeaders()
        builder.header("Authorization", "Bearer $effectiveKey")
        builder.header("User-Agent", USER_AGENT)
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
}
