package io.github.s1ddhants1.unhinge.ai.transport

import java.io.ByteArrayOutputStream

internal class TlsWriter {
    private val out = ByteArrayOutputStream(256)

    fun u8(value: Int): TlsWriter {
        out.write(value and 0xff)
        return this
    }

    fun u16(value: Int): TlsWriter {
        out.write((value ushr 8) and 0xff)
        out.write(value and 0xff)
        return this
    }

    fun u24(value: Int): TlsWriter {
        out.write((value ushr 16) and 0xff)
        out.write((value ushr 8) and 0xff)
        out.write(value and 0xff)
        return this
    }

    fun raw(bytes: ByteArray): TlsWriter {
        out.write(bytes)
        return this
    }

    fun vec8(block: TlsWriter.() -> Unit): TlsWriter {
        val child = TlsWriter().apply(block).bytes()
        u8(child.size)
        return raw(child)
    }

    fun vec16(block: TlsWriter.() -> Unit): TlsWriter {
        val child = TlsWriter().apply(block).bytes()
        u16(child.size)
        return raw(child)
    }

    fun vec24(block: TlsWriter.() -> Unit): TlsWriter {
        val child = TlsWriter().apply(block).bytes()
        u24(child.size)
        return raw(child)
    }

    fun bytes(): ByteArray = out.toByteArray()
}

internal class TlsReader(private val data: ByteArray, private var position: Int = 0) {
    fun u8(): Int {
        if (position >= data.size) throw TlsException("truncated TLS message")
        return data[position++].toInt() and 0xff
    }

    fun u16(): Int = (u8() shl 8) or u8()

    fun u24(): Int = (u8() shl 16) or (u8() shl 8) or u8()

    fun raw(length: Int): ByteArray {
        if (length < 0 || position + length > data.size) throw TlsException("truncated TLS message")
        return data.copyOfRange(position, position + length).also { position += length }
    }

    fun vec8(): ByteArray = raw(u8())

    fun vec16(): ByteArray = raw(u16())

    fun vec24(): ByteArray = raw(u24())

    val remaining: Int get() = data.size - position
}

internal class TlsException(message: String) : Exception(message)
