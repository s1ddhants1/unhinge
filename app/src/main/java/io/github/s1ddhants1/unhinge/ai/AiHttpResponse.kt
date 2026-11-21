package io.github.s1ddhants1.unhinge.ai

import okhttp3.Headers
import okhttp3.Response
import java.io.Closeable

class AiHttpResponse internal constructor(
    val code: Int,
    val headers: Headers,
    private val source: Response?,
    private val payload: ByteArray?,
) : Closeable {
    val isSuccessful: Boolean get() = code in 200..299

    fun text(): String = payload?.toString(Charsets.UTF_8) ?: source!!.body.string()

    fun readTextLimited(maxBytes: Long): String? {
        payload?.let { return if (it.size > maxBytes) null else it.toString(Charsets.UTF_8) }
        val body = source!!.body
        if (body.contentLength() > maxBytes) return null
        val buffered = body.source()
        return try {
            if (buffered.request(maxBytes + 1L) && buffered.buffer.size > maxBytes) null else buffered.readUtf8()
        } catch (_: Exception) {
            null
        }
    }

    override fun close() {
        source?.close()
    }
}

internal fun Response.toAiHttpResponse(): AiHttpResponse = AiHttpResponse(code, headers, this, null)
