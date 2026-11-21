package io.github.s1ddhants1.unhinge.ai.transport

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal class Tls13Connection(
    private val host: String,
    private val input: InputStream,
    private val output: OutputStream,
    private val random: SecureRandom = SecureRandom(),
) {
    private class Keys(val key: ByteArray, val iv: ByteArray, val chacha: Boolean)

    private class Record(val type: Int, val header: ByteArray, val payload: ByteArray)

    private var suite = 0
    private var hashName = "SHA-256"
    private var readKeys: Keys? = null
    private var writeKeys: Keys? = null
    private var readSequence = 0L
    private var writeSequence = 0L
    private var pending = ByteArray(0)
    private val transcript = ByteArrayOutputStream()

    fun handshake() {
        val x25519 = X25519.generateKeyPair(random)
        val clientRandom = ByteArray(32).also(random::nextBytes)
        val sessionId = ByteArray(32).also(random::nextBytes)
        val clientHello = OpenCodeClientHello.build(host, x25519.second, clientRandom, sessionId)
        transcript.write(clientHello)
        writeRecord(22, clientHello)

        val serverHello = readHandshake()
        requireHandshakeType(serverHello, 2, "ServerHello")
        transcript.write(serverHello)

        val reader = TlsReader(serverHello, 4)
        reader.u16()
        val serverRandom = reader.raw(32)
        if (serverRandom.contentEquals(HELLO_RETRY_REQUEST_RANDOM)) {
            throw TlsException("server requested a HelloRetryRequest")
        }
        reader.vec8()
        suite = reader.u16()
        reader.u8()
        val extensions = parseExtensions(reader.vec16())
        hashName = when (suite) {
            0x1301, 0x1303 -> "SHA-256"
            0x1302 -> "SHA-384"
            else -> throw TlsException("server selected unsupported cipher suite ${suite.toString(16)}")
        }
        val version = extensions[0x002b] ?: throw TlsException("server omitted supported_versions")
        if (version.size != 2 || TlsReader(version).u16() != 0x0304) {
            throw TlsException("server did not negotiate TLS 1.3")
        }
        val keyShare = extensions[0x0033] ?: throw TlsException("server omitted key_share")
        val shareReader = TlsReader(keyShare)
        val group = shareReader.u16()
        val serverShare = shareReader.vec16()
        if (group != 0x001d) throw TlsException("server selected unsupported group ${group.toString(16)}")
        val sharedSecret = X25519.sharedSecret(x25519.first, serverShare)

        val hashLength = MessageDigest.getInstance(hashName).digestLength
        val zeros = ByteArray(hashLength)
        val emptyHash = MessageDigest.getInstance(hashName).digest()
        val earlySecret = TlsHkdf.extract(hashName, zeros, zeros)
        val derivedEarly = TlsHkdf.expandLabel(hashName, earlySecret, "derived", emptyHash, hashLength)
        val handshakeSecret = TlsHkdf.extract(hashName, derivedEarly, sharedSecret)
        val handshakeHash = transcriptHash()
        val clientHandshakeSecret = TlsHkdf.expandLabel(hashName, handshakeSecret, "c hs traffic", handshakeHash, hashLength)
        val serverHandshakeSecret = TlsHkdf.expandLabel(hashName, handshakeSecret, "s hs traffic", handshakeHash, hashLength)
        readKeys = keysFor(serverHandshakeSecret)
        writeKeys = keysFor(clientHandshakeSecret)
        readSequence = 0
        writeSequence = 0

        val encryptedExtensions = readHandshake()
        requireHandshakeType(encryptedExtensions, 8, "EncryptedExtensions")
        transcript.write(encryptedExtensions)
        verifyAlpn(parseExtensions(TlsReader(encryptedExtensions, 4).vec16()))

        val certificate = readHandshake()
        requireHandshakeType(certificate, 11, "Certificate")
        val chain = parseCertificateMessage(certificate)
        transcript.write(certificate)
        verifyChain(chain)

        val certificateVerify = readHandshake()
        requireHandshakeType(certificateVerify, 15, "CertificateVerify")
        verifyCertificateVerify(certificateVerify, transcriptHash(), chain.first())
        transcript.write(certificateVerify)

        val serverFinished = readHandshake()
        requireHandshakeType(serverFinished, 20, "Finished")
        val serverFinishedKey = TlsHkdf.expandLabel(hashName, serverHandshakeSecret, "finished", ByteArray(0), hashLength)
        val expectedVerifyData = TlsHkdf.hmac(hashName, serverFinishedKey, transcriptHash())
        val actualVerifyData = TlsReader(serverFinished, 4).raw(hashLength)
        if (!expectedVerifyData.contentEquals(actualVerifyData)) {
            throw TlsException("server Finished verification failed")
        }
        transcript.write(serverFinished)

        val derivedHandshake = TlsHkdf.expandLabel(hashName, handshakeSecret, "derived", emptyHash, hashLength)
        val masterSecret = TlsHkdf.extract(hashName, derivedHandshake, zeros)
        val applicationHash = transcriptHash()
        val clientApplicationSecret = TlsHkdf.expandLabel(hashName, masterSecret, "c ap traffic", applicationHash, hashLength)
        val serverApplicationSecret = TlsHkdf.expandLabel(hashName, masterSecret, "s ap traffic", applicationHash, hashLength)

        val clientFinishedKey = TlsHkdf.expandLabel(hashName, clientHandshakeSecret, "finished", ByteArray(0), hashLength)
        val clientVerifyData = TlsHkdf.hmac(hashName, clientFinishedKey, applicationHash)
        val clientFinished = TlsWriter().u8(20).u24(clientVerifyData.size).raw(clientVerifyData).bytes()
        transcript.write(clientFinished)
        writeEncrypted(22, clientFinished)

        readKeys = keysFor(serverApplicationSecret)
        writeKeys = keysFor(clientApplicationSecret)
        readSequence = 0
        writeSequence = 0
    }

    fun writeApplicationData(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + MAX_PLAINTEXT, data.size)
            writeEncrypted(23, data.copyOfRange(offset, end))
            offset = end
        }
    }

    fun readApplicationData(): ByteArray {
        while (true) {
            val record = readRecord()
            when (record.type) {
                20 -> continue
                21 -> throw TlsException("server closed the connection with an alert")
                23 -> {
                    val keys = readKeys ?: throw TlsException("no read keys available")
                    val (type, content) = decrypt(record, keys)
                    when (type) {
                        23 -> return content
                        21 -> throw TlsException("server sent an encrypted alert")
                        22 -> continue
                        else -> throw TlsException("unexpected inner content type $type")
                    }
                }
                else -> throw TlsException("unexpected record type ${record.type}")
            }
        }
    }

    fun close() {
        val keys = writeKeys ?: return
        runCatching {
            val inner = byteArrayOf(1, 0, 21)
            val header = TlsWriter().u8(23).u16(0x0303).u16(inner.size + 16).bytes()
            val cipher = aead(keys, Cipher.ENCRYPT_MODE, nonce(keys.iv, writeSequence))
            cipher.updateAAD(header)
            output.write(header)
            output.write(cipher.doFinal(inner))
            output.flush()
        }
    }

    private fun keysFor(secret: ByteArray): Keys {
        val keyLength = when (suite) {
            0x1301 -> 16
            0x1302, 0x1303 -> 32
            else -> throw TlsException("unsupported cipher suite")
        }
        return Keys(
            key = TlsHkdf.expandLabel(hashName, secret, "key", ByteArray(0), keyLength),
            iv = TlsHkdf.expandLabel(hashName, secret, "iv", ByteArray(0), 12),
            chacha = suite == 0x1303,
        )
    }

    private fun aead(keys: Keys, mode: Int, nonce: ByteArray): Cipher {
        val cipher = if (keys.chacha) {
            runCatching { Cipher.getInstance("ChaCha20-Poly1305") }.getOrElse {
                Cipher.getInstance("ChaCha20/Poly1305/NoPadding")
            }
        } else {
            Cipher.getInstance("AES/GCM/NoPadding")
        }
        val keySpec = SecretKeySpec(keys.key, if (keys.chacha) "ChaCha20" else "AES")
        val parameters = if (keys.chacha) IvParameterSpec(nonce) else GCMParameterSpec(128, nonce)
        cipher.init(mode, keySpec, parameters)
        return cipher
    }

    private fun nonce(iv: ByteArray, sequence: Long): ByteArray {
        val nonce = iv.copyOf()
        for (index in 0 until 8) {
            nonce[nonce.size - 1 - index] =
                (nonce[nonce.size - 1 - index].toInt() xor ((sequence ushr (8 * index)) and 0xff).toInt()).toByte()
        }
        return nonce
    }

    private fun writeRecord(type: Int, payload: ByteArray) {
        output.write(TlsWriter().u8(type).u16(0x0301).u16(payload.size).bytes())
        output.write(payload)
        output.flush()
    }

    private fun writeEncrypted(type: Int, payload: ByteArray) {
        val keys = writeKeys ?: throw TlsException("no write keys available")
        val inner = payload + type.toByte()
        val header = TlsWriter().u8(23).u16(0x0303).u16(inner.size + 16).bytes()
        val cipher = aead(keys, Cipher.ENCRYPT_MODE, nonce(keys.iv, writeSequence))
        writeSequence++
        cipher.updateAAD(header)
        output.write(header)
        output.write(cipher.doFinal(inner))
        output.flush()
    }

    private fun readRecord(): Record {
        val header = readFully(5)
        val type = header[0].toInt() and 0xff
        val length = ((header[3].toInt() and 0xff) shl 8) or (header[4].toInt() and 0xff)
        return Record(type, header, readFully(length))
    }

    private fun decrypt(record: Record, keys: Keys): Pair<Int, ByteArray> {
        val cipher = aead(keys, Cipher.DECRYPT_MODE, nonce(keys.iv, readSequence))
        readSequence++
        cipher.updateAAD(record.header)
        val plaintext = cipher.doFinal(record.payload)
        var end = plaintext.size
        while (end > 0 && plaintext[end - 1] == 0.toByte()) end--
        if (end == 0) throw TlsException("empty inner plaintext")
        val type = plaintext[end - 1].toInt() and 0xff
        return type to plaintext.copyOfRange(0, end - 1)
    }

    private fun readHandshake(): ByteArray {
        while (true) {
            if (pending.size >= 4) {
                val length = ((pending[1].toInt() and 0xff) shl 16) or
                    ((pending[2].toInt() and 0xff) shl 8) or
                    (pending[3].toInt() and 0xff)
                if (pending.size >= 4 + length) {
                    val message = pending.copyOfRange(0, 4 + length)
                    pending = pending.copyOfRange(4 + length, pending.size)
                    return message
                }
            }
            val record = readRecord()
            val content = when (record.type) {
                20 -> continue
                21 -> throw TlsException("server closed the connection with an alert")
                22 -> record.payload
                23 -> {
                    val keys = readKeys ?: throw TlsException("no read keys available")
                    val (type, data) = decrypt(record, keys)
                    if (type == 21) throw TlsException("server sent an encrypted alert")
                    if (type != 22) throw TlsException("unexpected inner content type $type")
                    data
                }
                else -> throw TlsException("unexpected record type ${record.type}")
            }
            pending += content
        }
    }

    private fun transcriptHash(): ByteArray = MessageDigest.getInstance(hashName).digest(transcript.toByteArray())

    private fun parseExtensions(data: ByteArray): Map<Int, ByteArray> {
        val reader = TlsReader(data)
        val extensions = LinkedHashMap<Int, ByteArray>()
        while (reader.remaining > 0) {
            val type = reader.u16()
            extensions[type] = reader.vec16()
        }
        return extensions
    }

    private fun verifyAlpn(extensions: Map<Int, ByteArray>) {
        val alpn = extensions[0x0010] ?: throw TlsException("server did not confirm ALPN")
        val protocols = TlsReader(alpn).vec16()
        val selected = String(TlsReader(protocols).vec8(), Charsets.US_ASCII)
        if (selected != "http/1.1") throw TlsException("unexpected ALPN protocol $selected")
    }

    private fun parseCertificateMessage(message: ByteArray): List<X509Certificate> {
        val reader = TlsReader(message, 4)
        reader.vec8()
        val entries = TlsReader(reader.vec24())
        val factory = CertificateFactory.getInstance("X.509")
        val chain = ArrayList<X509Certificate>()
        while (entries.remaining > 0) {
            val der = entries.vec24()
            entries.vec16()
            chain.add(factory.generateCertificate(der.inputStream()) as X509Certificate)
        }
        if (chain.isEmpty()) throw TlsException("server sent an empty certificate chain")
        return chain
    }

    private fun verifyChain(chain: List<X509Certificate>) {
        val leaf = chain.first()
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        val trustManager = trustManagerFactory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            ?: throw TlsException("no X509 trust manager available")
        val authType = if (leaf.publicKey.algorithm.equals("EC", ignoreCase = true)) "ECDHE_ECDSA" else "ECDHE_RSA"
        trustManager.checkServerTrusted(chain.toTypedArray(), authType)
        verifyHostname(leaf)
    }

    private fun verifyHostname(certificate: X509Certificate) {
        val names = certificate.subjectAlternativeNames
            ?.filter { it.size >= 2 && it[0] == 2 }
            ?.mapNotNull { it[1] as? String }
            .orEmpty()
        if (names.none { matchesHostname(it, host) }) {
            throw TlsException("certificate is not valid for $host")
        }
    }

    private fun matchesHostname(pattern: String, hostname: String): Boolean {
        val normalizedPattern = pattern.trimEnd('.').lowercase()
        val normalizedHost = hostname.trimEnd('.').lowercase()
        if (normalizedPattern == normalizedHost) return true
        if (!normalizedPattern.startsWith("*.")) return false
        val suffix = normalizedPattern.substring(1)
        if (!normalizedHost.endsWith(suffix)) return false
        val prefix = normalizedHost.substring(0, normalizedHost.length - suffix.length)
        return prefix.isNotEmpty() && !prefix.contains('.')
    }

    private fun verifyCertificateVerify(message: ByteArray, transcriptHash: ByteArray, leaf: X509Certificate) {
        val reader = TlsReader(message, 4)
        val algorithm = reader.u16()
        val signature = reader.vec16()
        val context = "TLS 1.3, server CertificateVerify".toByteArray(Charsets.US_ASCII)
        val signedContent = ByteArray(64) { 0x20 } + context + byteArrayOf(0) + transcriptHash
        val verifier = when (algorithm) {
            0x0403 -> Signature.getInstance("SHA256withECDSA")
            0x0503 -> Signature.getInstance("SHA384withECDSA")
            0x0603 -> Signature.getInstance("SHA512withECDSA")
            0x0804 -> Signature.getInstance("RSASSA-PSS").also {
                it.setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
            }
            0x0805 -> Signature.getInstance("RSASSA-PSS").also {
                it.setParameter(PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, 48, 1))
            }
            0x0806 -> Signature.getInstance("RSASSA-PSS").also {
                it.setParameter(PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, 1))
            }
            0x0401 -> Signature.getInstance("SHA256withRSA")
            0x0501 -> Signature.getInstance("SHA384withRSA")
            0x0601 -> Signature.getInstance("SHA512withRSA")
            else -> throw TlsException("unsupported CertificateVerify algorithm ${algorithm.toString(16)}")
        }
        verifier.initVerify(leaf.publicKey)
        verifier.update(signedContent)
        if (!verifier.verify(signature)) throw TlsException("CertificateVerify signature is invalid")
    }

    private fun requireHandshakeType(message: ByteArray, type: Int, name: String) {
        if (message[0].toInt() != type) throw TlsException("expected $name, got handshake type ${message[0]}")
    }

    private fun readFully(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read < 0) throw TlsException("connection closed during TLS handshake")
            offset += read
        }
        return buffer
    }

    private companion object {
        const val MAX_PLAINTEXT = 16384

        val HELLO_RETRY_REQUEST_RANDOM: ByteArray = "cf21ad74e59a6111be1d8c021e65b891c2a211167abb8c5e079e09e2c8a8339c"
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
