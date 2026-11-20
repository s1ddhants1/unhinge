package io.github.s1ddhants1.unhinge.ai

import android.content.Context
import io.github.s1ddhants1.unhinge.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Headers
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class AiFailure(val messageRes: Int) {
    DATA_POLICY(R.string.ai_failure_privacy),
    AUTHENTICATION(R.string.ai_failure_auth),
    CREDIT_LIMIT(R.string.ai_failure_credit),
    RATE_LIMIT(R.string.ai_failure_rate),
    FREE_DAILY_LIMIT(R.string.ai_failure_daily),
    FREE_MINUTE_LIMIT(R.string.ai_failure_minute),
    MODEL_BUSY(R.string.ai_failure_busy),
    MODEL_UNAVAILABLE(R.string.ai_failure_model),
    NETWORK(R.string.ai_failure_network),
    EMPTY_RESPONSE(R.string.ai_failure_response),
    INVALID_RESPONSE(R.string.ai_failure_response),
    INCOMPLETE_RESPONSE(R.string.ai_failure_response),
    TIMEOUT(R.string.ai_failure_timeout),
    REQUEST_FAILED(R.string.ai_failure_response);

    fun userFriendlyMessage(context: Context): String = try {
        context.getString(messageRes)
    } catch (_: Throwable) {
        name.replace('_', ' ').lowercase()
    }
}

class AiException(
    val failure: AiFailure,
    message: String,
) : IllegalStateException(message)

class AiHttpException(
    val statusCode: Int,
    val responseBody: String,
    val headers: Headers = Headers.headersOf(),
) : IllegalStateException("AI request failed (HTTP $statusCode)") {
    val failure: AiFailure = classifyAiHttpFailure(statusCode, responseBody, headers)

    fun retryAtMillis(now: Long = System.currentTimeMillis()): Long? {
        val reset = headers["X-RateLimit-Reset"]?.toLongOrNull()?.let { if (it < 1_000_000_000_000L) it * 1_000L else it }
        return reset?.takeIf { it > now }
            ?: headers["Retry-After"]?.toLongOrNull()?.takeIf { it in 1..86_400 }?.let { now + it * 1_000L }
    }
}

fun classifyAiHttpFailure(
    statusCode: Int,
    responseBody: String,
    headers: Headers = Headers.headersOf(),
): AiFailure {
    val root = runCatching { Json.parseToJsonElement(responseBody) as? JsonObject }.getOrNull()
    val choice = (root?.get("choices") as? JsonArray)?.firstOrNull() as? JsonObject
    val error = (root?.get("error") ?: choice?.get("error")) as? JsonObject
    val metadata = error?.get("metadata") as? JsonObject
    val type = ((metadata?.get("error_type") ?: error?.get("error_type") ?: error?.get("type") ?: root?.get("type")) as? JsonPrimitive)?.contentOrNull
    val message = (error?.get("message") as? JsonPrimitive)?.contentOrNull.orEmpty().lowercase()
    val code = (error?.get("code") as? JsonPrimitive)?.intOrNull ?: statusCode

    return when {
        "data policy" in message || "privacy settings" in message -> {
            AiFailure.DATA_POLICY
        }
        type in setOf("authentication", "authentication_error", "AuthError") || code == 401 -> {
            AiFailure.AUTHENTICATION
        }
        type in setOf("payment_required", "InsufficientQuotaError") || code == 402 -> {
            AiFailure.CREDIT_LIMIT
        }
        type in setOf("rate_limit_exceeded", "FreeUsageLimitError") || code == 429 -> {
            when {
                "free-models-per-day" in message || "daily" in message || "per day" in message || "free usage limit" in message -> {
                    AiFailure.FREE_DAILY_LIMIT
                }
                "free-models-per-min" in message || "per minute" in message -> {
                    AiFailure.FREE_MINUTE_LIMIT
                }
                headers.names().any { it.startsWith("X-RateLimit-", ignoreCase = true) } -> {
                    AiFailure.RATE_LIMIT
                }
                "upstream" in message || metadata?.get("provider_name") != null || metadata?.get("provider_code") != null -> {
                    AiFailure.MODEL_BUSY
                }
                else -> {
                    AiFailure.RATE_LIMIT
                }
            }
        }
        type in setOf("not_found", "provider_unavailable", "provider_overloaded") || code in setOf(404, 502, 503) -> {
            AiFailure.MODEL_UNAVAILABLE
        }
        type == "timeout" || code == 408 || code == 504 -> {
            AiFailure.TIMEOUT
        }
        else -> {
            AiFailure.REQUEST_FAILED
        }
    }
}

fun Throwable.aiFailure(): AiFailure = when (this) {
    is AiHttpException -> failure
    is AiException -> failure
    is SocketTimeoutException -> AiFailure.TIMEOUT
    is UnknownHostException, is ConnectException, is IOException -> AiFailure.NETWORK
    else -> AiFailure.REQUEST_FAILED
}

suspend fun <T> retryAiRequest(request: suspend () -> Result<T>): Result<T> {
    suspend fun attempt(): Result<T> = try {
        currentCoroutineContext().ensureActive()
        request()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    val first = attempt()
    val error = first.exceptionOrNull() ?: return first
    if (error is CancellationException) throw error
    val statusCode = (error as? AiHttpException)?.statusCode
    if (error is IllegalArgumentException || statusCode in setOf(401, 402, 403, 404, 429) ||
        error.aiFailure() in setOf(
            AiFailure.DATA_POLICY,
            AiFailure.AUTHENTICATION,
            AiFailure.CREDIT_LIMIT,
            AiFailure.RATE_LIMIT,
            AiFailure.FREE_DAILY_LIMIT,
            AiFailure.FREE_MINUTE_LIMIT,
            AiFailure.MODEL_BUSY,
        )
    ) {
        return first
    }
    delay(1_000)
    val retry = attempt()
    retry.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    return retry
}
