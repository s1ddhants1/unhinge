package io.github.s1ddhants1.unhinge.ai.transport

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object TlsHkdf {
    fun macName(hash: String): String = when (hash) {
        "SHA-256" -> "HmacSHA256"
        "SHA-384" -> "HmacSHA384"
        else -> throw TlsException("unsupported hash $hash")
    }

    fun extract(hash: String, salt: ByteArray, input: ByteArray): ByteArray {
        val mac = Mac.getInstance(macName(hash))
        val effectiveSalt = if (salt.isEmpty()) ByteArray(mac.macLength) else salt
        mac.init(SecretKeySpec(effectiveSalt, macName(hash)))
        return mac.doFinal(input)
    }

    fun expand(hash: String, key: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance(macName(hash))
        mac.init(SecretKeySpec(key, macName(hash)))
        val output = ByteArray(length)
        var produced = 0
        var block = ByteArray(0)
        var counter = 1
        while (produced < length) {
            mac.reset()
            mac.update(block)
            mac.update(info)
            mac.update(counter.toByte())
            block = mac.doFinal()
            val take = minOf(block.size, length - produced)
            System.arraycopy(block, 0, output, produced, take)
            produced += take
            counter++
        }
        return output
    }

    fun expandLabel(hash: String, secret: ByteArray, label: String, context: ByteArray, length: Int): ByteArray {
        val fullLabel = "tls13 $label".toByteArray(Charsets.US_ASCII)
        val info = TlsWriter()
            .u16(length)
            .vec8 { raw(fullLabel) }
            .vec8 { raw(context) }
            .bytes()
        return expand(hash, secret, info, length)
    }

    fun hmac(hash: String, key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(macName(hash))
        mac.init(SecretKeySpec(key, macName(hash)))
        return mac.doFinal(data)
    }
}
