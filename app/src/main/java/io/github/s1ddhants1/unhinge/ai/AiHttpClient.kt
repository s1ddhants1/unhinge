package io.github.s1ddhants1.unhinge.ai

import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.util.concurrent.TimeUnit

object AiHttpClient {
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val zenTlsSpec: ConnectionSpec =
        ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
            .cipherSuites(
                "TLS_AES_128_GCM_SHA256",
                "TLS_AES_256_GCM_SHA384",
                "TLS_CHACHA20_POLY1305_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
                "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
                "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
                "TLS_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_RSA_WITH_AES_128_CBC_SHA",
                "TLS_RSA_WITH_AES_256_CBC_SHA",
            )
            .build()

    val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    val zenClient: OkHttpClient =
        client.newBuilder()
            .connectionSpecs(listOf(zenTlsSpec, ConnectionSpec.MODERN_TLS))
            .build()

    fun clientFor(url: String): OkHttpClient =
        if (ZenRouter.isZenUrl(url)) zenClient else client

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
