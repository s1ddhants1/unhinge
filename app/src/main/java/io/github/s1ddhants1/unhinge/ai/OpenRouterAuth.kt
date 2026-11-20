package io.github.s1ddhants1.unhinge.ai

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom

class OpenRouterAuth(
    private val client: OkHttpClient = AiHttpClient.client,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private var session: Session? = null

    private class Session(
        val verifier: String,
        val createdAt: Long,
        var used: Boolean = false,
    )

    fun start(): String {
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        val verifier = base64UrlEncode(randomBytes)
        val challenge = challenge(verifier)
        session = Session(verifier, now())
        return Uri.parse("https://openrouter.ai/auth")
            .buildUpon()
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("key_label", "Unhinge")
            .build()
            .toString()
    }

    fun pendingSession(): String? = session?.takeUnless { it.used }?.let { "${it.createdAt}:${it.verifier}" }

    fun restore(pending: String): Boolean {
        val parts = pending.split(':', limit = 2)
        val createdAt = parts.firstOrNull()?.toLongOrNull()
        val verifier = parts.getOrNull(1)
        session = if (createdAt != null && verifier?.matches(Regex("[A-Za-z0-9_-]{43}")) == true && now() - createdAt in 0..600_000) {
            Session(verifier, createdAt)
        } else {
            null
        }
        return session != null
    }

    fun cancel() {
        session = null
    }

    suspend fun exchange(code: String): String = withContext(Dispatchers.IO) {
        val current = checkNotNull(session) { "No authorization in progress" }
        check(!current.used && now() - current.createdAt in 0..600_000) { "Authorization expired or already used" }
        val trimmedCode = code.trim()
        require(trimmedCode.isNotBlank() && trimmedCode.length <= 2048) { "Invalid authorization code" }
        current.used = true
        try {
            val payload = """{"code":"$trimmedCode","code_verifier":"${current.verifier}","code_challenge_method":"S256"}"""
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/auth/keys")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                check(response.isSuccessful) { "Authorization failed (HTTP ${response.code}): $body" }
                val key = runCatching {
                    Json.parseToJsonElement(body).jsonObject["key"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                check(!key.isNullOrBlank()) { "Authorization returned no key: $body" }
                check(session === current) { "Authorization cancelled" }
                key
            }
        } finally {
            if (session === current) session = null
        }
    }

    companion object {
        fun challenge(verifier: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(verifier.toByteArray(Charsets.US_ASCII))
            return base64UrlEncode(hash)
        }

        private fun base64UrlEncode(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).trim()
    }
}
