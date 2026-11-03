package io.github.s1ddhants1.unhinge.ai

import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client and network utilities for all LLM protocol implementations.
 * Eliminates redundant OkHttpClient instances and disparate timeout configurations.
 */
object AiHttpClient {
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    fun isTransientHttpCode(code: Int): Boolean =
        code == 500 || code == 502 || code == 503 || code == 529

    fun isTransientFailure(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("HTTP 500", ignoreCase = true) ||
            message.contains("HTTP 502", ignoreCase = true) ||
            message.contains("HTTP 503", ignoreCase = true) ||
            message.contains("HTTP 529", ignoreCase = true) ||
            message.contains("temporarily unavailable", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("Unable to resolve host", ignoreCase = true)
    }
}
