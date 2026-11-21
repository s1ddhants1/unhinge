package io.github.s1ddhants1.unhinge.ai.transport

import io.github.s1ddhants1.unhinge.ai.AiHttpResponse
import okhttp3.Headers
import okhttp3.Request
import okio.Buffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.zip.GZIPInputStream

internal object OpenCodeTransport {
    private const val CONNECT_TIMEOUT_MILLIS = 30_000
    private const val READ_TIMEOUT_MILLIS = 120_000
    private const val MAX_RESPONSE_BYTES = 16L * 1024 * 1024

    private val HEADER_ORDER = listOf(
        "Authorization",
        "Content-Type",
        "User-Agent",
        "b3",
        "traceparent",
        "x-opencode-client",
        "x-opencode-project",
        "x-opencode-session",
        "x-opencode-session-id",
        "x-session-affinity",
        "x-session-id",
    )

    fun execute(request: Request): AiHttpResponse {
        val url = request.url
        if (!url.isHttps) throw IOException("OpenCode transport requires https, got ${url.scheme}")
        val body = request.body?.let { payload -> Buffer().also(payload::writeTo).readByteArray() }
        val head = buildHead(request, body)
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(url.host, if (url.port != 0) url.port else 443), CONNECT_TIMEOUT_MILLIS)
            socket.soTimeout = READ_TIMEOUT_MILLIS
            val connection = Tls13Connection(url.host, socket.getInputStream(), socket.getOutputStream())
            connection.handshake()
            connection.writeApplicationData(if (body != null) head + body else head)
            val reader = ResponseReader(connection)
            val statusLine = reader.readLine()
            val statusParts = statusLine.split(' ')
            if (statusParts.size < 2) throw IOException("malformed HTTP status line: $statusLine")
            val code = statusParts[1].toIntOrNull() ?: throw IOException("malformed HTTP status code: $statusLine")
            val headers = Headers.Builder()
            while (true) {
                val line = reader.readLine()
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator <= 0) continue
                headers.add(line.substring(0, separator).trim(), line.substring(separator + 1).trim())
            }
            val responseHeaders = headers.build()
            val contentLength = responseHeaders["Content-Length"]?.toLongOrNull()
            val chunked = responseHeaders["Transfer-Encoding"]?.contains("chunked", ignoreCase = true) == true
            val payload = when {
                request.method.equals("HEAD", ignoreCase = true) || code == 204 || code == 304 -> ByteArray(0)
                chunked -> reader.readChunked()
                contentLength != null -> reader.readExactly(contentLength)
                else -> reader.readToEnd()
            }
            val decoded = if (responseHeaders["Content-Encoding"]?.contains("gzip", ignoreCase = true) == true) {
                GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
            } else {
                payload
            }
            connection.close()
            return AiHttpResponse(code, responseHeaders, null, decoded)
        } catch (error: TlsException) {
            throw IOException("OpenCode TLS handshake failed: ${error.message}", error)
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun buildHead(request: Request, body: ByteArray?): ByteArray {
        val url = request.url
        val path = buildString {
            append(url.encodedPath.ifEmpty { "/" })
            url.encodedQuery?.let { append('?').append(it) }
        }
        val head = StringBuilder()
        head.append(request.method).append(' ').append(path).append(" HTTP/1.1\r\n")
        HEADER_ORDER.forEach { name ->
            val value = if (name == "Content-Type") {
                request.header(name) ?: request.body?.contentType()?.toString()
            } else {
                request.header(name)
            }
            if (value != null) head.append(name).append(": ").append(value).append("\r\n")
        }
        head.append("Connection: keep-alive\r\n")
        head.append("Accept: */*\r\n")
        head.append("Host: ").append(url.host)
        if (url.port != 0 && url.port != 443) head.append(':').append(url.port)
        head.append("\r\n")
        head.append("Accept-Encoding: gzip\r\n")
        if (body != null) head.append("Content-Length: ").append(body.size).append("\r\n")
        head.append("\r\n")
        return head.toString().toByteArray(Charsets.US_ASCII)
    }

    private class ResponseReader(private val connection: Tls13Connection) {
        private var buffer = ByteArray(0)
        private var consumed = 0L

        private fun fill() {
            buffer += connection.readApplicationData()
        }

        fun readByte(): Int {
            while (buffer.isEmpty()) fill()
            val value = buffer[0].toInt() and 0xff
            buffer = buffer.copyOfRange(1, buffer.size)
            consumed++
            if (consumed > MAX_RESPONSE_BYTES) throw IOException("OpenCode response exceeded size limit")
            return value
        }

        fun readLine(): String {
            val line = ByteArrayOutputStream()
            while (true) {
                val byte = readByte()
                if (byte == '\r'.code) {
                    val next = readByte()
                    if (next != '\n'.code) line.write(next)
                    return line.toString("UTF-8")
                }
                line.write(byte)
            }
        }

        fun readExactly(count: Long): ByteArray {
            if (count > MAX_RESPONSE_BYTES) throw IOException("OpenCode response exceeded size limit")
            val output = ByteArrayOutputStream(count.toInt())
            while (output.size() < count) {
                if (buffer.isEmpty()) fill()
                val take = minOf(buffer.size.toLong(), count - output.size()).toInt()
                output.write(buffer, 0, take)
                buffer = buffer.copyOfRange(take, buffer.size)
                consumed += take
            }
            return output.toByteArray()
        }

        fun readToEnd(): ByteArray {
            val output = ByteArrayOutputStream()
            output.write(buffer)
            consumed += buffer.size
            buffer = ByteArray(0)
            while (true) {
                val chunk = runCatching { connection.readApplicationData() }.getOrElse { return output.toByteArray() }
                if (chunk.isEmpty()) break
                output.write(chunk)
                consumed += chunk.size
                if (consumed > MAX_RESPONSE_BYTES) throw IOException("OpenCode response exceeded size limit")
            }
            return output.toByteArray()
        }

        fun readChunked(): ByteArray {
            val output = ByteArrayOutputStream()
            while (true) {
                val sizeLine = readLine()
                val size = sizeLine.substringBefore(';').trim().toIntOrNull(16)
                    ?: throw IOException("malformed chunk size: $sizeLine")
                if (size == 0) {
                    while (readLine().isNotEmpty()) { }
                    return output.toByteArray()
                }
                output.write(readExactly(size.toLong()))
                readLine()
            }
        }
    }
}
